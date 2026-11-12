package net.classicremastered.minecraft.level.itemstack;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import net.classicremastered.nbt.CompoundTag;
import net.classicremastered.nbt.ListTag;
import net.classicremastered.nbt.ShortTag;

public record PackedTurret(
        int health, int ammo, boolean enabled, int upgrades, Set<Short> targets, String variantId,
        boolean roverBullets, boolean headless)
        implements Serializable {
    public PackedTurret(int health, int ammo, boolean enabled, int upgrades, Set<Short> targets) {
        this(health, ammo, enabled, upgrades, targets, "", false, false);
    }

    public PackedTurret(int health, int ammo, boolean enabled, int upgrades, Set<Short> targets,
            String variantId) {
        this(health, ammo, enabled, upgrades, targets, variantId, false, false);
    }

    public PackedTurret {
        variantId = variantId == null ? "" : variantId;
        targets = targets == null ? null : Set.copyOf(targets);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag("PackedTurret");
        tag.putString("TurretVariant", variantId);
        tag.putInt("TurretHealth", health);
        tag.putInt("TurretAmmo", ammo);
        if (roverBullets)
            tag.putBoolean("RoverBullets", true);
        if (headless)
            tag.putBoolean("TurretHeadless", true);
        tag.putBoolean("TurretEnabled", enabled);
        tag.putInt("TurretUpgrades", upgrades);
        if (targets != null) {
            ListTag list = new ListTag("TurretTargets", (byte) 2);
            for (short id : targets) {
                list.add(new ShortTag(null, id));
            }
            tag.put(list);
        }
        return tag;
    }

    public static PackedTurret load(CompoundTag parent) {
        if (!parent.hasKey("PackedTurret")) {
            return null;
        }
        CompoundTag tag = parent.getCompound("PackedTurret");
        Set<Short> targets = null;
        if (tag.hasKey("TurretTargets")) {
            targets = new HashSet<>();
            ListTag list = tag.getList("TurretTargets");
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i) instanceof ShortTag id) {
                    targets.add(id.value);
                }
            }
        }
        return new PackedTurret(
                tag.getInt("TurretHealth"),
                tag.getInt("TurretAmmo"),
                tag.getBoolean("TurretEnabled"),
                tag.getInt("TurretUpgrades"),
                targets,
                tag.getString("TurretVariant"), tag.getBoolean("RoverBullets"),
                tag.getBoolean("TurretHeadless"));
    }
}
