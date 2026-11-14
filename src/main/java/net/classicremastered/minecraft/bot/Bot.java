package net.classicremastered.minecraft.bot;

import net.classicremastered.minecraft.bot.ai.BotExpertPvPAI;
import net.classicremastered.minecraft.bot.ai.BotInputFollow;
import net.classicremastered.minecraft.bot.ai.BotPvPAI;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.HumanoidMob;
import net.classicremastered.nbt.CompoundTag;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class Bot extends HumanoidMob {
    public enum Type {
        FOLLOW,
        PVP,
        EXPERTPVP;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static List<String> ids() {
            return Arrays.stream(values()).map(Type::id).toList();
        }

        public static Type fromId(String id) {
            for (Type type : values()) {
                if (type.id().equalsIgnoreCase(id)) return type;
            }
            return null;
        }
    }

    private Type type;

    public Bot(Level level, float x, float y, float z) {
        this(level, x, y, z, Type.FOLLOW);
    }

    public Bot(Level level, float x, float y, float z, Type type) {
        super(level, x, y, z);
        modelName = "humanoid";
        textureName = "/char.png";
        allowAlpha = true;
        health = lastHealth = 20;
        soundHurt = soundDeath = "random/classic_hurt";
        helmet = armor = false;
        persistent = true;
        setType(type);
    }

    public Type type() {
        return type;
    }

    private void setType(Type type) {
        this.type = type;
        ai =
                switch (type) {
                    case FOLLOW -> new BotInputFollow(this);
                    case PVP -> new BotPvPAI(this);
                    case EXPERTPVP -> new BotExpertPvPAI(this);
                };
    }

    @Override
    public void savePersistence(CompoundTag tag) {
        super.savePersistence(tag);
        tag.putString("BotType", type.id());
    }

    @Override
    public void loadPersistence(CompoundTag tag) {
        super.loadPersistence(tag);
        Type savedType = Type.fromId(tag.getString("BotType"));
        if (savedType != null) setType(savedType);
    }

    @Override
    public boolean isCreativeModeAllowed() {
        return true;
    }
}
