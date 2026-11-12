package net.classicremastered.minecraft.mob;

public enum TurretVariant {
    CROSSBOW("Crossbow", "crossbow_turret", 47, 48, 256, 16, 20, 4),
    SHOTGUN("Arrow Shotgun", "shotgun", 60, 37, 64, 8, 30, 2),
    HARPOON("Harpoon", "harpoon", 61, 65, 64, 16, 40, 8),
    BRAIN_NANO("Brain Nano", "brain_nano", 62, 66, 64, 16, 40, 0),
    PREDICTABLE("Predictable", "predictable", 63, 48, 256, 16, 20, 4),
    ROVER("Rover", "rover", 79, 48, 256, 16, 20, 4), BULLET("Bullet", "bullet", 96, 98, 128, 16, 12, 3),
    BULLET_SHOTGUN("Bullet Shotgun", "bullet_shotgun", 97, 98, 64, 8, 32, 2);

    public final String label, asset;
    public final int item, ammo, capacity, range, reload, damage;

    TurretVariant(String label, String asset, int item, int ammo, int capacity, int range, int reload,
            int damage) {
        this.label = label;
        this.asset = asset;
        this.item = item;
        this.ammo = ammo;
        this.capacity = capacity;
        this.range = range;
        this.reload = reload;
        this.damage = damage;
    }

    public static TurretVariant fromItem(int raw) {
        for (TurretVariant variant : values()) {
            if (variant.item + 256 == raw)
                return variant;
        }
        return null;
    }

    public static TurretVariant fromSave(String value) {
        for (TurretVariant variant : values()) {
            if (variant.asset.equals(value))
                return variant;
        }
        return CROSSBOW;
    }

    public boolean shotgun() {
        return this == SHOTGUN || this == BULLET_SHOTGUN;
    }

    public boolean usesBullets() {
        return this == BULLET || this == BULLET_SHOTGUN;
    }

    public boolean acceptsAmmo(int raw) {
        return raw == ammo + 256 || this == SHOTGUN && raw == 64 + 256;
    }

    public String texture() {
        return this == CROSSBOW ? "/turret/cobblestone_oak.png" : "/turret/" + asset + ".png";
    }
}
