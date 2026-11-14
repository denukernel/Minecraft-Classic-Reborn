package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.entity.BoatEntity;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.CrossbowTurret;
import net.classicremastered.minecraft.player.Player;

public final class TurretItems {
    public static final int TURRET = 47;
    public static final int BOLT = 48;
    public static final int REPAIR = 49;
    public static final int CONTROL = 50;
    public static final int CRATE = 51;
    public static final int PACKED = 52;
    public static final int FIRST_UPGRADE = 53;
    public static final int RANGE_I = 70;
    public static final int RANGE_II = 71;
    public static final int BULLET = 98;
    public static final int HEAD = 208;

    public static int upgradeItem(int index) {
        return TurretUpgrade.at(index).item;
    }

    public static int upgradeIndex(int raw) {
        TurretUpgrade upgrade = TurretUpgrade.fromItem(raw);
        return upgrade == null ? -1 : upgrade.ordinal();
    }

    private TurretItems() {}

    public static void register() {
        new Item(TURRET, "Crossbow Turret", "/turret/crossbow_item.png", 1);
        new Item(BOLT, "Crossbow Bolt", "/turret/bolt.png");
        new Item(REPAIR, "Turret Repair Kit", "/turret/repair.png");
        new Item(CONTROL, "Turret Control Unit", "/turret/control.png", 1);
        new Item(CRATE, "Empty Turret Crate", "/turret/crate.png", 1);
        new Item(PACKED, "Packed Crossbow Turret", "/turret/crossbow_item.png", 1);
        for (var variant : net.classicremastered.minecraft.mob.TurretVariant.values()) {
            if (variant.item != TURRET) {
                new Item(
                        variant.item,
                        variant.label + " Turret",
                        "/turret/" + variant.asset + "_item.png",
                        1);
            }
        }
        new Item(64, "Legacy Shotgun Shell", "/turret/shell.png");
        new Item(BULLET, "Bullet", "/turret/bullet_ammo.png");
        new Item(HEAD, "Recovered Turret Head", "/turret/recovered_head.png", 1);
        new Item(65, "Harpoon", "/turret/harpoon_ammo.png");
        new Item(66, "Brain Nano Arrow", "/turret/nano_arrow.png");
        for (TurretUpgrade upgrade : TurretUpgrade.values()) {
            new Item(upgrade.item, upgrade.label + " Upgrade", "/turret/control.png", 1);
        }
    }

    public static boolean isPlacementItem(int raw) {
        return raw == PACKED + 256
                || net.classicremastered.minecraft.mob.TurretVariant.fromItem(raw) != null;
    }

    public static void place(Player player, int x, int y, int z, int face, int raw) {
        Level level = player.level;
        boolean packed = raw == PACKED + 256;
        var crate = player.inventory.crate(player.inventory.selected);
        var variant =
                packed && crate != null
                        ? net.classicremastered.minecraft.mob.TurretVariant.fromSave(
                                crate.variantId())
                        : net.classicremastered.minecraft.mob.TurretVariant.fromItem(raw);
        if (level.isNetworkMode()
                || level.blockMap == null
                || variant == null
                || face != 1
                || !level.isInBounds(x, y + 1, z)
                || packed && crate == null) {
            return;
        }
        Block support = Block.blocks[level.getTile(x, y, z)];
        boolean water = level.isWater(x, y, z);
        if (support == null
                || (water
                        ? variant != net.classicremastered.minecraft.mob.TurretVariant.HARPOON
                        : !support.isCube() && support != Block.SLAB)) {
            return;
        }
        float feet = y + (support == Block.SLAB ? .5f : 1f);
        CrossbowTurret turret = new CrossbowTurret(level, x + .5f, feet + .875f, z + .5f);
        if (occupied(level, turret) || !level.getCubes(turret.bb).isEmpty()) {
            return;
        }
        finishPlacement(player, raw, turret, variant, crate);
    }

    public static void place(Player player, BoatEntity boat, int raw) {
        Level level = player.level;
        if (level != boat.level
                || level.isNetworkMode()
                || level.blockMap == null
                || player.inventory.getSelected() != raw
                || player.inventory.getCount(player.inventory.selected) <= 0) return;
        var crate = player.inventory.crate(player.inventory.selected);
        var variant =
                raw == PACKED + 256 && crate != null
                        ? net.classicremastered.minecraft.mob.TurretVariant.fromSave(
                                crate.variantId())
                        : net.classicremastered.minecraft.mob.TurretVariant.fromItem(raw);
        if (variant == null) return;
        CrossbowTurret turret = new CrossbowTurret(level, boat.x, boat.y, boat.z);
        turret.mount(boat);
        if (turret.riding == boat) finishPlacement(player, raw, turret, variant, crate);
    }

    private static void finishPlacement(
            Player player,
            int raw,
            CrossbowTurret turret,
            net.classicremastered.minecraft.mob.TurretVariant variant,
            PackedTurret crate) {
        if (player.inventory.getSelected() != raw
                || player.inventory.getCount(player.inventory.selected) <= 0) {
            return;
        }
        turret.variant = variant;
        turret.owner = CrossbowTurret.playerName(player);
        boolean packed = raw == PACKED + 256;
        if (packed) {
            turret.restore(crate);
        }
        turret.setGuardPost();
        player.level.addEntity(turret);
        if (packed || !player.level.creativeMode) {
            player.inventory.removeSelected(1);
        }
    }

    private static boolean occupied(Level level, CrossbowTurret turret) {
        for (Object entity : level.findEntities(turret, turret.bb)) {
            if (entity instanceof net.classicremastered.minecraft.Entity other && !other.removed) {
                return true;
            }
        }
        return false;
    }

    public static void interact(Player player, CrossbowTurret turret, int raw) {
        if (!turret.canControl(player)) {
            return;
        }
        if (raw == HEAD + 256) {
            turret.attachHead(player);
        } else if (raw == CONTROL + 256 && player.minecraft != null) {
            if (turret.hasHead()) {
                player.minecraft.setCurrentScreen(
                        new net.classicremastered.minecraft.gui.TurretControlScreen(turret));
            } else if (player.minecraft.hud != null) {
                player.minecraft.hud.addChat("&cMissing turret head. Reattach a recovered head.");
            }
        } else if (upgradeIndex(raw) >= 0) {
            turret.changeUpgrade(player, upgradeIndex(raw), true);
        } else if (raw == REPAIR + 256 && turret.health < turret.maxHealth()) {
            if (player.level.creativeMode || player.inventory.removeResource(raw)) {
                turret.health = Math.min(turret.maxHealth(), turret.health + 10);
            }
        } else {
            turret.loadAmmo(player, raw);
        }
    }

    public static void migrateLegacyCrate(Player player) {
        if (player.packedTurretHealth <= 0) {
            return;
        }
        var crate =
                new PackedTurret(
                        player.packedTurretHealth,
                        player.packedTurretAmmo,
                        player.packedTurretEnabled,
                        0,
                        null);
        var inventory = player.inventory;
        for (int slot = 0; slot < inventory.totalSize(); slot++) {
            if (inventory.getId(slot) == PACKED + 256
                    && inventory.getCount(slot) == 1
                    && inventory.crate(slot) == null) {
                inventory.setCrate(slot, crate);
                player.packedTurretHealth = 0;
                return;
            }
        }
        if (inventory.addCrate(crate)) {
            player.packedTurretHealth = 0;
        }
    }

    public static boolean pack(Player player, CrossbowTurret turret) {
        if (!turret.canControl(player)) {
            return false;
        }
        var inventory = player.inventory;
        for (int slot = 0; slot < inventory.totalSize(); slot++) {
            if (inventory.getId(slot) != CRATE + 256 || inventory.getCount(slot) <= 0) {
                continue;
            }
            int count = inventory.getCount(slot);
            if (count == 1) {
                inventory.setCrate(slot, turret.packedData());
            } else if (inventory.addCrate(turret.packedData())) {
                inventory.setIdCount(slot, CRATE + 256, count - 1);
            } else {
                continue;
            }
            turret.remove();
            return true;
        }
        return false;
    }
}
