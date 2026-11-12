package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.entity.Arrow;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.itemstack.TurretItems;
import net.classicremastered.minecraft.level.itemstack.TurretUpgrade;
import net.classicremastered.minecraft.model.CrossbowTurretModel;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.TextureManager;
import net.classicremastered.nbt.CompoundTag;

import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/** Tier 1 crossbow turret from Turret Mod Rebirth. */
public final class CrossbowTurret extends Mob {
    public static final short REGISTRY_ID = 77;
    public static final int MAX_HEALTH = 20;
    public static final int AMMO_CAPACITY = 256;

    private static final int RANGE_ABOVE = 4;
    private static final int RANGE_BELOW = 8;
    private static final java.util.Map<TurretVariant, CrossbowTurretModel> MODELS =
            new java.util.EnumMap<>(TurretVariant.class);
    public TurretVariant variant = TurretVariant.CROSSBOW;
    private transient Mob trackedTarget;
    private transient boolean aligned;
    private float aimYaw, aimPitch;
    private transient float previousAimYaw, previousAimPitch;
    private transient Mob lastShotTarget;
    private transient Vec3D lastShotAim;
    private transient boolean followUpAvailable;
    private transient RoverTurretDrive drive;
    private transient boolean hasTicked;

    public String owner = "";
    public int ammo;
    private boolean roverBullets;
    private boolean headless;
    public boolean enabled = true;
    private int reloadTicks;
    private java.util.Set<Short> targets;
    private int upgrades;
    private transient int repairTicks;

    public boolean targets(short id) {
        return targets == null ? MobRegistry.defaultTurretTarget(id) : targets.contains(id);
    }

    public void toggleTarget(short id) {
        if (targets == null) {
            targets = new java.util.HashSet<>(MobRegistry.turretTargets().keySet());
            targets.removeIf(candidate -> !MobRegistry.defaultTurretTarget(candidate));
        }
        if (!targets.remove(id)) {
            targets.add(id);
        }
    }

    public boolean upgraded(int index) {
        return TurretUpgrade.at(index) != null && (upgrades & (1 << index)) != 0;
    }

    private boolean upgraded(TurretUpgrade upgrade) {
        return upgraded(upgrade.ordinal());
    }

    public int maxHealth() {
        return MAX_HEALTH + 5 * Integer.bitCount(upgrades & 15);
    }

    public int ammoCapacity() {
        return variant.capacity * (upgraded(TurretUpgrade.STORAGE) ? 2 : 1);
    }

    public int reloadDuration() {
        return Math.round(
                variant.reload
                        * (upgraded(TurretUpgrade.RELOAD_II)
                                ? .5f
                                : upgraded(TurretUpgrade.RELOAD_I) ? .85f : 1f));
    }

    public int firingRange() {
        return Math.round(variant.range * rangeMultiplier());
    }

    private float rangeMultiplier() {
        return upgraded(TurretUpgrade.RANGE_II)
                ? 1.5f
                : upgraded(TurretUpgrade.RANGE_I) ? 1.25f : 1f;
    }

    public boolean changeUpgrade(Player player, int index, boolean install) {
        TurretUpgrade upgrade = TurretUpgrade.at(index);
        if (upgrade == null || !canControl(player) || upgraded(upgrade) == install) {
            return false;
        }
        int raw = upgrade.item + 256;
        int oldMaxHealth = maxHealth();
        int oldReload = reloadDuration();
        if (install) {
            if (upgrade.previous != null && !upgraded(upgrade.previous)
                    || !player.level.creativeMode && !player.inventory.removeResource(raw)) {
                return false;
            }
            upgrades |= 1 << index;
        } else {
            TurretUpgrade next = TurretUpgrade.at(index + 1);
            if (next != null && next.previous == upgrade && upgraded(next)
                    || upgrade == TurretUpgrade.STORAGE && ammo > variant.capacity
                    || !player.inventory.addResource(raw)) {
                return false;
            }
            upgrades &= ~(1 << index);
        }
        if (maxHealth() != oldMaxHealth) {
            health = lastHealth = install ? maxHealth() : Math.min(health, maxHealth());
            invulnerableTime = 0;
            repairTicks = 0;
        }
        int newReload = reloadDuration();
        if (reloadTicks > 0 && newReload != oldReload)
            reloadTicks = (reloadTicks * newReload + oldReload - 1) / oldReload;
        return true;
    }

    public boolean usesBullets() {
        return variant.usesBullets() || variant == TurretVariant.ROVER && roverBullets;
    }

    public boolean hasHead() {
        return !headless;
    }

    boolean detachHead() {
        if (headless || removed || health <= 0) return false;
        headless = true;
        ammo = 0;
        reloadTicks = 0;
        trackedTarget = null;
        return true;
    }

    public boolean attachHead(Player player) {
        if (!headless || !canControl(player)) return false;
        if (!level.creativeMode && !player.inventory.removeResource(TurretItems.HEAD + 256))
            return false;
        headless = false;
        return true;
    }

    public void loadAmmo(Player player, int raw) {
        boolean bullet = raw == TurretItems.BULLET + 256;
        if (headless
                || !canControl(player)
                || !(variant.acceptsAmmo(raw) || variant == TurretVariant.ROVER && bullet)
                || variant == TurretVariant.ROVER && ammo > 0 && roverBullets != bullet) return;
        int count = player.inventory.getCount(player.inventory.selected);
        if (player.inventory.getSelected() != raw) return;
        while (count-- > 0
                && ammo < ammoCapacity()
                && (level.creativeMode || player.inventory.removeResource(raw))) {
            if (variant == TurretVariant.ROVER) roverBullets = bullet;
            ammo++;
        }
    }

    public void unload(Player player) {
        if (canControl(player)) {
            int raw = (usesBullets() ? TurretItems.BULLET : variant.ammo) + 256;
            while (ammo > 0 && player.inventory.addResource(raw)) {
                ammo--;
            }
        }
    }

    @Override
    public boolean isCreativeModeAllowed() {
        return true;
    }

    public CrossbowTurret(Level level, float x, float y, float z) {
        super(level);
        setSize(.875f, 1.75f);
        heightOffset = bbHeight * .5f;
        setPos(x, y, z);
        health = MAX_HEALTH;
        persistent = true;
        equipmentRolled = true;
        makeStepSound = false;
    }

    public static String playerName(Player player) {
        return player.minecraft != null && player.minecraft.session != null
                ? player.minecraft.session.username
                : "Player";
    }

    Player creditedOwner() {
        return level.getPlayer() instanceof Player player
                        && !owner.isEmpty()
                        && owner.equals(playerName(player))
                ? player
                : null;
    }

    public boolean canControl(Player player) {
        return !removed
                && health > 0
                && !level.isNetworkMode()
                && player.level == level
                && distanceToSqr(player) <= 36
                && (owner.isEmpty() || owner.equals(playerName(player)));
    }

    public boolean canTarget(Entity entity) {
        return entity instanceof Mob mob
                && mob.health > 0
                && !mob.removed
                && !mob.corruptedToDefendPlayer
                && (MobRegistry.isTurretHostile(mob) || mob instanceof IronGolem)
                && (variant != TurretVariant.BRAIN_NANO || DefenderAI.canConvert(mob))
                && targets(MobRegistry.idOf(mob));
    }

    @Override
    public void aiStep() {
        previousAimYaw = aimYaw;
        previousAimPitch = aimPitch;
        if (level.isNetworkMode() || health <= 0) {
            return;
        }
        if (headless) {
            fall(0f, 0f);
            return;
        }
        TurretShot shot = null;
        if (variant == TurretVariant.ROVER) {
            if (drive == null) setGuardPost();
            shot = drive.tick(this);
        } else {
            fall(0f, 0f);
        }
        if (!enabled || health <= 0) {
            trackedTarget = null;
            aligned = false;
            return;
        }
        if (reloadTicks > 0) {
            reloadTicks--;
        }
        if (variant != TurretVariant.ROVER && ammo > 0) shot = findShot();
        trackedTarget = shot == null ? null : shot.target();
        if (shot == null) {
            aligned = false;
            return;
        }
        float desiredYaw = shot.yaw();
        float desiredPitch = shot.pitch();
        float tracking =
                upgraded(TurretUpgrade.TRACKING_II)
                        ? 2f
                        : upgraded(TurretUpgrade.TRACKING_I) ? 1.5f : 1f;
        float yawStep = 12f * tracking;
        float pitchStep = 8f * tracking;
        aimYaw += Math.max(-yawStep, Math.min(yawStep, angleDifference(desiredYaw, aimYaw)));
        aimPitch += Math.max(-pitchStep, Math.min(pitchStep, desiredPitch - aimPitch));
        aligned =
                Math.abs(angleDifference(desiredYaw, aimYaw)) <= 2f
                        && Math.abs(desiredPitch - aimPitch) <= 2f;
        if (ammo > 0 && reloadTicks == 0 && aligned) {
            Vec3D muzzle = shot.muzzle();
            int spread = variant.shotgun() ? 12 : 0;
            for (int yawOffset = -spread; yawOffset <= spread; yawOffset += 6) {
                level.addEntity(
                        new Arrow(
                                level,
                                this,
                                muzzle.x,
                                muzzle.y,
                                muzzle.z,
                                desiredYaw + yawOffset,
                                desiredPitch,
                                projectileSpeed()));
            }
            if (variant == TurretVariant.PREDICTABLE) {
                boolean followUp = isFollowUp(shot.target());
                lastShotTarget = shot.target();
                lastShotAim = center(shot.target());
                followUpAvailable = !followUp;
            }
            level.playSound("random/bow", this, .8f, 1f);
            int recycle =
                    upgraded(TurretUpgrade.RECYCLER_II)
                            ? 2
                            : upgraded(TurretUpgrade.RECYCLER_I) ? 1 : 0;
            if (recycle == 0 || level.random.nextInt(4) >= recycle) {
                ammo--;
            }
            reloadTicks = reloadDuration();
        }
    }

    public void setGuardPost() {
        if (variant == TurretVariant.ROVER) {
            drive = new RoverTurretDrive(level, x, bb.y0, z);
        }
    }

    void fall(float dx, float dz) {
        float downward = (yd - .08f) * .98f;
        // Harpoons can be deployed directly on water.
        int waterY = (int) Math.floor(bb.y0 - .001f);
        if (variant == TurretVariant.HARPOON
                && Math.abs(bb.y0 - (waterY + 1f)) < .001f
                && level.isWater((int) Math.floor(x), waterY, (int) Math.floor(z))) {
            yd = 0f;
            return;
        }
        xd = dx;
        yd = downward;
        zd = dz;
        move(dx, yd, dz);
    }

    float projectileSpeed() {
        float speed = usesBullets() ? 1.4f : 1f;
        return variant == TurretVariant.ROVER ? speed * rangeMultiplier() : speed;
    }

    TurretShot findShot() {
        int range = firingRange();
        Vec3D muzzle = muzzle();
        float scanRange = range + TurretShot.MAX_LEAD_DISTANCE + Arrow.HALF_WIDTH;
        float vertical = variant == TurretVariant.ROVER ? range : RANGE_BELOW;
        List<Entity> nearby =
                new ArrayList<>(
                        level.findEntities(this, bb.grow(scanRange, vertical + 2f, scanRange)));
        if (variant == TurretVariant.PREDICTABLE && isFollowUp(lastShotTarget)) {
            TurretShot followUp = aimAt(lastShotTarget, muzzle, range * range, nearby);
            if (followUp != null) return followUp;
        }
        TurretShot locked = aimAt(trackedTarget, muzzle, range * range, nearby);
        if (locked != null) return locked;

        TurretShot bestShot = null;
        float bestDistance = range * range;
        for (Entity entity : nearby) {
            if (!(entity instanceof Mob target) || target == trackedTarget) continue;
            TurretShot candidate = aimAt(target, muzzle, bestDistance, nearby);
            if (candidate != null) {
                bestDistance = center(target).distanceSquared(muzzle);
                bestShot = candidate;
            }
        }
        return bestShot;
    }

    private TurretShot aimAt(Mob target, Vec3D muzzle, float maxDistance, List<Entity> nearby) {
        if (!canTarget(target) || !target.isShootable()) return null;
        Vec3D center = center(target);
        float dy = center.y - muzzle.y;
        if (center.distanceSquared(muzzle) > maxDistance
                || variant != TurretVariant.ROVER && (dy < -RANGE_BELOW || dy > RANGE_ABOVE))
            return null;
        TurretShot shot = TurretShot.aim(this, target, muzzle, center, true, nearby);
        return shot == null ? TurretShot.aim(this, target, muzzle, center, false, nearby) : shot;
    }

    private boolean isFollowUp(Mob target) {
        return followUpAvailable
                && target != null
                && target == lastShotTarget
                && center(target).distanceSquared(lastShotAim) > .35f * .35f;
    }

    private Vec3D muzzle() {
        return new Vec3D(x, bb.y0 + (variant == TurretVariant.HARPOON ? 1.7f : 1.5f), z);
    }

    public String firingStatus() {
        if (headless) return "Missing turret head";
        if (!enabled) return "Firing disabled";
        if (ammo <= 0) return "No ammunition";
        if (!canTarget(trackedTarget)) return "No clear target";
        if (!aligned) return "Aligning";
        return reloadTicks > 0 ? "Reloading" : "Ready";
    }

    private static float angleDifference(float target, float current) {
        float delta = (target - current) % 360f;
        if (delta > 180f) delta -= 360f;
        if (delta < -180f) delta += 360f;
        return delta;
    }

    private static Vec3D center(Mob mob) {
        return new Vec3D(mob.x, (mob.bb.y0 + mob.bb.y1) * .5f, mob.z);
    }

    public int projectileDamage() {
        int bonus = upgraded(TurretUpgrade.POWER_II) ? 2 : upgraded(TurretUpgrade.POWER_I) ? 1 : 0;
        return variant.damage == 0 ? 0 : variant.damage + bonus;
    }

    @Override
    public void hurt(Entity source, int damage) {
        if (upgraded(TurretUpgrade.FIRE_SEAL)
                && ("fire".equals(damageCause)
                        || "burn".equals(damageCause)
                        || "lava".equals(damageCause))) {
            return;
        }
        if (damage > 0
                && (source != null && !"fall".equals(damageCause)
                        || "explosion".equals(damageCause))) {
            float multiplier =
                    upgraded(TurretUpgrade.ARMOR_II)
                            ? .6f
                            : upgraded(TurretUpgrade.ARMOR_I) ? .8f : 1f;
            damage = Math.max(1, Math.round(damage * multiplier));
        }
        int oldHealth = health;
        super.hurt(source, damage);
        if (health < oldHealth) repairTicks = 0;
    }

    @Override
    protected void causeFallDamage(float distance) {
        if (!upgraded(TurretUpgrade.SHOCK_ABSORBER)) {
            super.causeFallDamage(distance);
        }
    }

    public void hitWithArrow(Arrow arrow, Entity target, int damage) {
        if (!canTarget(target)) return;
        if (variant == TurretVariant.BRAIN_NANO) {
            DefenderAI.convert((Mob) target);
        } else {
            target.hurt(arrow, damage);
        }
    }

    @Override
    public boolean isUnderWater() {
        return variant != TurretVariant.HARPOON
                && !upgraded(TurretUpgrade.WATER_SEAL)
                && super.isUnderWater();
    }

    @Override
    public void knockback(Entity source, int damage, float dx, float dz) {}

    @Override
    public boolean isShootable() {
        return health > 0;
    }

    @Override
    public void tick() {
        hasTicked = true;
        super.tick();
        if (!level.isNetworkMode()
                && health > 0
                && health < maxHealth()
                && upgraded(TurretUpgrade.REPAIR)) {
            if (++repairTicks >= 100) {
                health++;
                repairTicks = 0;
            }
        } else {
            repairTicks = 0;
        }
        if (health > 0 || level.particleEngine == null) return;
        int count = removed ? 16 : deathTime % 3 == 1 ? 3 : 0;
        for (int i = 0; i < count; i++) {
            float dx = (level.random.nextFloat() - .5f) * (removed ? 1.5f : .55f);
            float dz = (level.random.nextFloat() - .5f) * (removed ? 1.5f : .55f);
            var smoke =
                    new net.classicremastered.minecraft.particle.SmokeParticle(
                            level,
                            x + dx,
                            bb.y0 + .65f + level.random.nextFloat() * .45f,
                            z + dz,
                            removed ? .12f : .05f);
            level.particleEngine.spawnParticle(smoke);
        }
    }

    @Override
    public void render(TextureManager textures, float partial) {
        var model = MODELS.computeIfAbsent(variant, CrossbowTurretModel::new);
        GL11.glPushMatrix();
        try {
            boolean harpoon = variant == TurretVariant.HARPOON;
            float hurt = hurtDuration > 0 ? Math.max(0, (hurtTime - partial) / hurtDuration) : 0;
            float collapse = health <= 0 ? Math.min(1, (deathTime + partial) / 20f) : 0;
            float jolt = (float) Math.sin((1 - hurt) * Math.PI * 3) * hurt;
            float renderX = hasTicked ? xo + (x - xo) * partial : x;
            float renderY = renderFeetY(partial);
            float renderZ = hasTicked ? zo + (z - zo) * partial : z;
            GL11.glTranslatef(renderX, renderY, renderZ);
            GL11.glRotatef(9 * jolt + 20 * collapse * collapse, 0, 0, 1);
            GL11.glTranslatef(0, (harpoon ? .45f : 1.5f) - .22f * collapse, 0);
            GL11.glScalef(1, harpoon ? 1 : -1, harpoon ? 1 : -1);
            GL11.glColor4f(
                    1 - .5f * collapse,
                    1 - .6f * hurt - .35f * collapse,
                    1 - .6f * hurt - .35f * collapse,
                    1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures.load(variant.texture()));
            float yaw = previousAimYaw + (aimYaw - previousAimYaw) * partial;
            float pitch = previousAimPitch + (aimPitch - previousAimPitch) * partial;
            pitch = pitch * (1 - collapse) + 55 * collapse + 12 * jolt;
            model.render(
                    harpoon ? yaw + 180 : -yaw,
                    harpoon ? -pitch : pitch,
                    drive == null ? 0f : drive.chassisYaw(partial),
                    drive == null ? 0f : drive.wheelPitch(partial),
                    !headless);
        } finally {
            GL11.glColor4f(1, 1, 1, 1);
            GL11.glPopMatrix();
        }
    }

    @Override
    public void savePersistence(CompoundTag tag) {
        super.savePersistence(tag);
        tag.putString("TurretOwner", owner);
        tag.putFloat("TurretFeetY", bb.y0);
        tag.putFloat("TurretAimYaw", aimYaw);
        tag.putFloat("TurretAimPitch", aimPitch);
        saveTurretData(tag);
        if (variant == TurretVariant.ROVER && drive != null) drive.save(tag);
    }

    public net.classicremastered.minecraft.level.itemstack.PackedTurret packedData() {
        return new net.classicremastered.minecraft.level.itemstack.PackedTurret(
                health, ammo, enabled, upgrades, targets, variant.asset, roverBullets, headless);
    }

    public void restore(net.classicremastered.minecraft.level.itemstack.PackedTurret crate) {
        variant = TurretVariant.fromSave(crate.variantId());
        upgrades = crate.upgrades() & TurretUpgrade.SAVE_MASK;
        health = lastHealth = Math.max(1, Math.min(maxHealth(), crate.health()));
        ammo = Math.max(0, Math.min(ammoCapacity(), crate.ammo()));
        roverBullets = variant == TurretVariant.ROVER && crate.roverBullets();
        headless = crate.headless();
        enabled = crate.enabled();
        targets = crate.targets() == null ? null : new java.util.HashSet<>(crate.targets());
    }

    private void saveTurretData(CompoundTag tag) {
        tag.putString("TurretVariant", variant.asset);
        tag.putInt("TurretHealth", health);
        tag.putInt("TurretUpgrades", upgrades);
        if (targets != null) {
            var list = new net.classicremastered.nbt.ListTag("TurretTargets", (byte) 2);
            for (short id : targets) {
                list.add(new net.classicremastered.nbt.ShortTag(null, id));
            }
            tag.put(list);
        }
        tag.putInt("TurretAmmo", ammo);
        if (variant == TurretVariant.ROVER) tag.putBoolean("RoverBullets", roverBullets);
        if (headless) tag.putBoolean("TurretHeadless", true);
        tag.putBoolean("TurretEnabled", enabled);
    }

    @Override
    public void loadPersistence(CompoundTag tag) {
        super.loadPersistence(tag);
        owner = tag.getString("TurretOwner");
        if (tag.hasKey("TurretFeetY") && Float.isFinite(tag.getFloat("TurretFeetY"))) {
            setPos(x, tag.getFloat("TurretFeetY") + heightOffset, z);
        }
        aimYaw = previousAimYaw = tag.hasKey("TurretAimYaw") ? tag.getFloat("TurretAimYaw") : yRot;
        aimPitch =
                previousAimPitch =
                        tag.hasKey("TurretAimPitch") ? tag.getFloat("TurretAimPitch") : xRot;
        loadTurretData(tag);
        if (variant == TurretVariant.ROVER
                && tag.hasKey("RoverPostX")
                && tag.hasKey("RoverPostY")
                && tag.hasKey("RoverPostZ")) {
            float postX = tag.getFloat("RoverPostX");
            float postY = tag.getFloat("RoverPostY");
            float postZ = tag.getFloat("RoverPostZ");
            if (Float.isFinite(postX) && Float.isFinite(postY) && Float.isFinite(postZ)) {
                drive = new RoverTurretDrive(level, postX, postY, postZ);
            }
        }
    }

    private void loadTurretData(CompoundTag tag) {
        variant = TurretVariant.fromSave(tag.getString("TurretVariant"));
        upgrades = tag.getInt("TurretUpgrades") & TurretUpgrade.SAVE_MASK;
        if (tag.hasKey("TurretHealth")) {
            health = lastHealth = Math.max(1, Math.min(maxHealth(), tag.getInt("TurretHealth")));
        }
        targets = null;
        if (tag.hasKey("TurretTargets")) {
            targets = new java.util.HashSet<>();
            var list = tag.getList("TurretTargets");
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i) instanceof net.classicremastered.nbt.ShortTag id) {
                    targets.add(id.value);
                }
            }
        }
        ammo = Math.max(0, Math.min(ammoCapacity(), tag.getInt("TurretAmmo")));
        roverBullets = variant == TurretVariant.ROVER && tag.getBoolean("RoverBullets");
        headless = tag.getBoolean("TurretHeadless");
        enabled = !tag.hasKey("TurretEnabled") || tag.getBoolean("TurretEnabled");
    }
}
