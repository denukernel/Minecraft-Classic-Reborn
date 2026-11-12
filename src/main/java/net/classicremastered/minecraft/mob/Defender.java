package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.entity.Arrow;
import net.classicremastered.minecraft.entity.DroppedItem;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.itemstack.Equipment;
import net.classicremastered.minecraft.level.itemstack.ItemRegistry;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.nbt.CompoundTag;

/** A hired, persistent companion. Equipment transfers always move real inventory items. */
public final class Defender extends HumanoidMob {
    public static final short REGISTRY_ID = 78;
    public static final int PRICE = 30;
    public String owner = "";

    public Defender(Level level, float x, float y, float z) {
        super(level, x, y, z);
        ItemRegistry.bootstrap();
        modelName = "humanoid";
        textureName = "/char.png";
        allowAlpha = true;
        customName = "Defender";
        setSize(.6f, 1.8f);
        heightOffset = 1.62f;
        health = lastHealth = 20;
        persistent = equipmentRolled = true;
        helmet = armor = legacyArmor = false;
        equipment[4] = Equipment.SWORDS[0] + 256;
        ai = new DefenderAI();
        if (level != null && level.player instanceof Player player)
            owner = CrossbowTurret.playerName(player);
        setPos(x, y, z);
    }

    public Player ownerPlayer() {
        return level != null
                        && level.player instanceof Player player
                        && !player.removed
                        && player.health > 0
                        && owner.equals(CrossbowTurret.playerName(player))
                ? player
                : null;
    }

    public boolean canControl(Player player) {
        return player != null
                && level != null
                && !level.isNetworkMode()
                && player.level == level
                && player.health > 0
                && !player.isSpectator()
                && !removed
                && health > 0
                && distanceToSqr(player) <= 25
                && owner.equals(CrossbowTurret.playerName(player));
    }

    public static boolean isEnemy(Entity entity) {
        return entity instanceof Mob enemy
                && enemy.health > 0
                && !enemy.removed
                && !(enemy instanceof Player)
                && !(enemy instanceof Defender)
                && !(enemy instanceof CrossbowTurret)
                && !enemy.corruptedToDefendPlayer
                && (MobRegistry.isTurretHostile(enemy)
                        || enemy instanceof Villager villager && villager.isHostile()
                        || enemy instanceof IronGolem golem && !golem.builtByPlayer);
    }

    public static boolean purchase(Player player) {
        if (player == null
                || player.level == null
                || player.level.isNetworkMode()
                || player.level.creativeMode
                || player.isSpectator()
                || player.health <= 0
                || player.coins < PRICE) return false;
        Level level = player.level;
        Defender defender = new Defender(level, player.x, player.y, player.z);
        int baseY = (int) Math.floor(player.bb.y0);
        int baseX = (int) Math.floor(player.x), baseZ = (int) Math.floor(player.z);
        for (int radius = 2; radius <= 4; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    for (int dy = 1; dy >= -2; dy--) {
                        int x = baseX + dx, y = baseY + dy, z = baseZ + dz;
                        if (!level.isInBounds(x, y + 2, z) || !level.isSolidTile(x, y - 1, z))
                            continue;
                        defender.setPos(x + .5f, y + defender.bbHeight / 2f, z + .5f);
                        if (!level.getCubes(defender.bb).isEmpty()
                                || level.containsAnyLiquid(defender.bb)
                                || !level.isFree(defender.bb)) continue;
                        defender.owner = CrossbowTurret.playerName(player);
                        level.addEntity(defender);
                        player.coins -= PRICE;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean equip(Player player, int inventorySlot) {
        if (!canControl(player)
                || inventorySlot < 0
                || inventorySlot >= player.inventory.totalSize()) return false;
        int raw = player.inventory.getId(inventorySlot);
        int count = player.inventory.getCount(inventorySlot);
        Equipment.Gear gear = Equipment.gear(raw);
        if (gear == null || count <= 0) return false;
        int slot = gear.slot >= 0 ? gear.slot : 4;
        int previous = equipment[slot];
        if (count == 1) {
            player.inventory.setIdCount(inventorySlot, previous, previous > 0 ? 1 : 0);
        } else {
            if (previous > 0 && !player.inventory.addResource(previous)) return false;
            player.inventory.setIdCount(inventorySlot, raw, count - 1);
        }
        equipment[slot] = raw;
        return true;
    }

    public boolean unequip(Player player, int slot) {
        if (!canControl(player)
                || slot < 0
                || slot >= equipment.length
                || equipment[slot] <= 0
                || !player.inventory.addResource(equipment[slot])) return false;
        equipment[slot] = 0;
        return true;
    }

    @Override
    public void renderModel(
            net.classicremastered.minecraft.render.TextureManager textures,
            float swing,
            float amount,
            float age,
            float yaw,
            float pitch,
            float scale) {
        org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_ENABLE_BIT);
        try {
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_ALPHA_TEST);
            super.renderModel(textures, swing, amount, age, yaw, pitch, scale);
        } finally {
            org.lwjgl.opengl.GL11.glPopAttrib();
        }
    }

    @Override
    public void hurt(Entity source, int damage) {
        Entity responsible = source instanceof Arrow arrow ? arrow.getOwner() : source;
        if (responsible instanceof Player) return;
        super.hurt(source, damage);
    }

    @Override
    public void die(Entity killer) {
        if (dead || removed) return;
        for (int slot = 0; slot < equipment.length; slot++) {
            if (level != null && !level.isNetworkMode() && level.doMobLoot && equipment[slot] > 0)
                level.addEntity(new DroppedItem(level, x, y, z, equipment[slot] - 256));
            equipment[slot] = 0;
        }
        super.die(killer);
    }

    @Override
    public void savePersistence(CompoundTag tag) {
        super.savePersistence(tag);
        tag.putString("DefenderOwner", owner);
    }

    @Override
    public void loadPersistence(CompoundTag tag) {
        super.loadPersistence(tag);
        owner = tag.getString("DefenderOwner");
        persistent = true;
        ai = new DefenderAI();
    }
}
