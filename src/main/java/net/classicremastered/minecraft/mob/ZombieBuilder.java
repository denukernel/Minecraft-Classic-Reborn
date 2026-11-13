package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.CombatRules;
import net.classicremastered.minecraft.Difficulty;
import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.entity.Arrow;
import net.classicremastered.minecraft.entity.DroppedItem;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.itemstack.Equipment;
import net.classicremastered.minecraft.level.itemstack.TurretItems;
import net.classicremastered.minecraft.level.structure.CobblestoneTower;
import net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.util.CreativeModeHelper;
import net.classicremastered.nbt.CompoundTag;

/**
 * Builds supported stone steps along its approach rather than along its gaze.
 */
public class ZombieBuilder extends Zombie {

    public static final long serialVersionUID = 0L;
    public boolean poseBreaking, posePlacing;
    private CobblestoneTower.Entrance tower;
    private boolean leavingTower;
    private transient boolean checkedLegacyTower;
    private static final int TEAR_TICKS = 120;
    private transient CrossbowTurret theftTarget;
    private transient int theftTicks;
    private boolean stolenHead;
    private int stolenAmmo, stolenAmmoId, stolenDamage, shotCooldown;
    private boolean stolenSpread;
    private float stolenSpeed;

    private transient net.classicremastered.minecraft.mob.ai.goal.zombie.BuildStepsGoal building;
    private transient net.classicremastered.minecraft.mob.ai.goal.GoalSelector construction;
    public ZombieBuilder(Level level, float x, float y, float z) {
        super(level, x, y, z);
        ai = new net.classicremastered.minecraft.mob.ai.zombie.ZombieBuilderAI();
    }

    public net.classicremastered.minecraft.mob.ai.goal.zombie.BuildStepsGoal buildStepsGoal() {
        if (building == null)
            building = new net.classicremastered.minecraft.mob.ai.goal.zombie.BuildStepsGoal(this);
        return building;
    }

    public boolean isBuilderBreaking() {
        return theftTicks > 0 || poseBreaking;
    }

    public boolean isBuilderPlacing() {
        return theftTicks == 0 && posePlacing;
    }

    public boolean hasStolenHead() {
        return stolenHead;
    }

    public void assignTower(CobblestoneTower.Entrance entrance) {
        tower = entrance;
        leavingTower = false;
        checkedLegacyTower = entrance != null;
    }

    public boolean isTowerResident() {
        return tower != null && !leavingTower;
    }

    public boolean canFollow(Entity target) {
        return !isTowerResident() || tower.contains(target.x, target.bb.y0, target.z);
    }

    public void leaveTowerFor(Player target) {
        if (tower != null && !tower.contains(target.x, target.bb.y0, target.z)) {
            leavingTower = true;
        }
    }

    public boolean canBuildTowerExit(int x, int y, int z) {
        return tower != null && tower.contains(x + .5, y, z + .5);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private float towerX(float x) {
        return clamp(x, tower.x() - 3 + bbWidth * .5f, tower.x() + 4 - bbWidth * .5f);
    }

    private float towerZ(float z) {
        return clamp(z, tower.z() + 1 + bbWidth * .5f, tower.z() + 8 - bbWidth * .5f);
    }

    @Override
    public void setPos(float x, float y, float z) {
        if (isTowerResident()) {
            x = towerX(x);
            z = towerZ(z);
            y = clamp(y, tower.y() + bbHeight * .5f, tower.y() + 8.5f + bbHeight * .5f);
        }
        super.setPos(x, y, z);
    }

    @Override
    public void move(float dx, float dy, float dz) {
        if (isTowerResident()) {
            dx = towerX(x + dx) - x;
            dz = towerZ(z + dz) - z;
            dy = clamp(bb.y0 + dy, tower.y(), tower.y() + 8.5f) - bb.y0;
        }
        super.move(dx, dy, dz);
    }

    public CrossbowTurret headTarget(BasicAttackAI combat) {
        if (stolenHead || corruptedToDefendPlayer || level.isNetworkMode()
                || CombatRules.difficulty(level) != Difficulty.EXTREME) {
            resetTheft();
            return null;
        }
        if (usableHead(theftTarget) && distanceToSqr(theftTarget) <= 64f)
            return theftTarget;
        resetTheft();
        float best = 64f;
        for (Object entity : level.findEntities(this, bb.grow(8f, 2f, 8f))) {
            if (entity instanceof CrossbowTurret turret && usableHead(turret)
                    && distanceToSqr(turret) < best && combat.hasLineOfSight(this, turret)) {
                theftTarget = turret;
                best = distanceToSqr(turret);
            }
        }
        return theftTarget;
    }

    private boolean usableHead(CrossbowTurret turret) {
        return turret != null && !turret.removed && turret.health > 0 && turret.hasHead()
                && canFollow(turret);
    }

    public void tearAtHead(CrossbowTurret turret, boolean touching) {
        if (!touching || !usableHead(turret)) {
            theftTicks = 0;
            return;
        }
        if (++theftTicks % 12 == 1) {
            attackTime = 10;
            level.playSound("random/click", this, .5f, .7f);
        }
        if (theftTicks < TEAR_TICKS)
            return;
        int ammo = turret.ammo;
        if (turret.detachHead()) {
            stolenHead = true;
            persistent = true;
            stolenAmmo = ammo;
            stolenAmmoId = turret.usesBullets() ? TurretItems.BULLET : turret.variant.ammo;
            stolenDamage = Math.max(1, turret.projectileDamage());
            stolenSpeed = turret.projectileSpeed();
            stolenSpread = turret.variant.shotgun();
        }
        resetTheft();
    }

    private void resetTheft() {
        theftTarget = null;
        theftTicks = 0;
    }

    public boolean fireStolenHead(BasicAttackAI combat) {
        if (!stolenHead || stolenAmmo <= 0 || shotCooldown > 0 || corruptedToDefendPlayer
                || !(level.getPlayer() instanceof Player player) || player.health <= 0
                || CreativeModeHelper.shouldIgnoreTarget(player) || distanceToSqr(player) > 144f
                || !combat.hasLineOfSight(this, player))
            return false;
        float muzzleY = bb.y0 + bbHeight * .75f;
        float dx = player.x - x;
        float dz = player.z - z;
        float dy = (player.bb.y0 + player.bb.y1) * .5f - muzzleY;
        float range = (float) Math.hypot(dx, dz);
        float flight = range / stolenSpeed;
        dx += (player.x - player.xo) * flight;
        dz += (player.z - player.zo) * flight;
        float yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
        float pitch = (float) -Math.toDegrees(
                Math.atan2(dy + Arrow.TURRET_DROP * flight * (flight + 1f) * .5f, Math.hypot(dx, dz)));
        int spread = stolenSpread ? 12 : 0;
        for (int offset = -spread; offset <= spread; offset += 6)
            level.addEntity(Arrow.stolenHeadRound(level, this, x, muzzleY, z, yaw + offset, pitch,
                    stolenSpeed, stolenDamage, stolenAmmoId == TurretItems.BULLET));
        stolenAmmo--;
        shotCooldown = 24;
        attackTime = 10;
        level.playSound("random/bow", this, .8f, .85f);
        return true;
    }

    @Override
    public void tick() {
        if (!checkedLegacyTower) {
            if (persistent && equipment[0] == Equipment.ARMOR[1][0] + 256)
                tower = CobblestoneTower.residenceAt(level, x, bb.y0, z);
            checkedLegacyTower = true;
        }
        if (shotCooldown > 0)
            shotCooldown--;
        super.tick();
        if (health <= 0 || removed || theftTarget != null || stolenHead)
            return;
        if (construction == null)
            construction = new net.classicremastered.minecraft.mob.ai.goal.GoalSelector(buildStepsGoal());
        construction.tick();
    }

    @Override
    public void die(Entity killer) {
        if (!dead && stolenHead) {
            level.addEntity(new DroppedItem(level, x, y, z, TurretItems.HEAD));
            for (int remaining = stolenAmmo; remaining > 0; remaining -= 64)
                level.addEntity(new DroppedItem(level, x, y, z, stolenAmmoId, Math.min(64, remaining)));
            stolenHead = false;
            stolenAmmo = 0;
        }
        super.die(killer);
    }

    @Override
    public void savePersistence(CompoundTag tag) {
        super.savePersistence(tag);
        if (tower != null) {
            tag.putInt("BuilderTowerX", tower.x());
            tag.putInt("BuilderTowerY", tower.y());
            tag.putInt("BuilderTowerZ", tower.z());
            tag.putBoolean("BuilderLeavingTower", leavingTower);
        }
        if (!stolenHead) return;
        tag.putBoolean("StolenTurretHead", true);
        tag.putInt("StolenTurretAmmo", stolenAmmo);
        tag.putInt("StolenTurretAmmoId", stolenAmmoId);
        tag.putInt("StolenTurretDamage", stolenDamage);
        tag.putFloat("StolenTurretSpeed", stolenSpeed);
        tag.putBoolean("StolenTurretSpread", stolenSpread);
        tag.putInt("StolenTurretCooldown", shotCooldown);
    }

    @Override
    public void loadPersistence(CompoundTag tag) {
        super.loadPersistence(tag);
        if (tag.hasKey("BuilderTowerX")
                && tag.hasKey("BuilderTowerY")
                && tag.hasKey("BuilderTowerZ"))
            assignTower(
                    new CobblestoneTower.Entrance(
                            tag.getInt("BuilderTowerX"),
                            tag.getInt("BuilderTowerY"),
                            tag.getInt("BuilderTowerZ")));
        leavingTower = tag.getBoolean("BuilderLeavingTower");
        if (tag == null || !tag.getBoolean("StolenTurretHead")) return;
        stolenHead = true;
        stolenAmmo = Math.max(0, Math.min(512, tag.getInt("StolenTurretAmmo")));
        stolenAmmoId = tag.getInt("StolenTurretAmmoId");
        stolenDamage = Math.max(1, Math.min(10, tag.getInt("StolenTurretDamage")));
        float speed = tag.getFloat("StolenTurretSpeed");
        stolenSpeed = speed > 0 && speed < 3f ? speed : 1f;
        stolenSpread = tag.getBoolean("StolenTurretSpread");
        shotCooldown = Math.max(0, Math.min(24, tag.getInt("StolenTurretCooldown")));
    }
}
