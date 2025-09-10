// File: src/net/classicremastered/minecraft/chat/commands/MobVoteCommand.java
package net.classicremastered.minecraft.chat.commands;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.*;
import net.classicremastered.minecraft.player.Player;

public final class MobVoteCommand implements Command {

    @Override
    public String getName() {
        return "mobvote";
    }

    @Override
    public String getUsage() {
        return "/mobvote - spawns all vote mobs as parody";
    }

    @Override
    public void execute(Minecraft mc, Player sender, String[] args) {
        if (mc == null || sender == null || sender.level == null) return;
        Level lvl = sender.level;

        float x = sender.x + 2, y = sender.y, z = sender.z + 2;

        // Example set of "mob vote" candidates
        Mob[] candidates = new Mob[] {
            new ZombieBuilder(lvl, x, y, z),
            new Bee(lvl, x + 1, y, z),
            new IronGolem(lvl, x - 1, y, z)
        };

        for (Mob m : candidates) {
            lvl.addEntity(m);
        }

        if (mc.hud != null) {
            mc.hud.addChat("&eMob Vote Parody: &7All mobs spawned instead of 1!");
        }
    }
}
