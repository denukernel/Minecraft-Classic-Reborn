package net.classicremastered.minecraft.mob;

import java.util.function.ToIntFunction;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.entity.Arrow;
import net.classicremastered.minecraft.entity.DroppedBlock;
import net.classicremastered.minecraft.entity.DroppedItem;
import net.classicremastered.minecraft.entity.ThrownEnderPearl;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.tile.BuilderDoorBlock;
import net.classicremastered.minecraft.level.itemstack.BowItem;
import net.classicremastered.minecraft.level.itemstack.BucketItem;
import net.classicremastered.minecraft.level.itemstack.Equipment;
import net.classicremastered.minecraft.level.itemstack.FoodItem;
import net.classicremastered.minecraft.level.itemstack.GravityGunItem;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.level.itemstack.ItemRegistry;
import net.classicremastered.minecraft.level.itemstack.MobBattle;
import net.classicremastered.minecraft.level.itemstack.PackedTurret;
import net.classicremastered.minecraft.level.itemstack.TurretItems;
import net.classicremastered.minecraft.model.HumanoidModel;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Inventory;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.nbt.CompoundTag;
import net.classicremastered.nbt.ListTag;

/** A hired, persistent companion. Equipment transfers always move real inventory items. */
public final class Defender extends HumanoidMob implements Companion {
    private static final long serialVersionUID = -3007143288636179014L;
    public static final short REGISTRY_ID = 78;
    public static final int PRICE = 30;
    public static final int PACK_SIZE = 9;
    public static final int MAX_SKILL_LEVEL = 3;
    private static final int BUCKET_PICKUP_TICKS = 16;
    private static final int BUCKET_RETURN_TICKS = 6;
    private static final int FIRE_BUCKET_AIM_TICKS = 2;
    private static final int PEARL_RELEASE_TICK = 3;
    private static final int PEARL_THROW_TICKS = 9;

    public enum Skill {
        VITALITY("Vitality", "+6 max health per rank"),
        GUARD("Guard", "Reduces incoming combat damage"),
        COMBAT("Combat", "+1 weapon damage per rank"),
        WATER_CLUTCH("Water Clutch", "Break falls with a packed water bucket."),
        COMBAT_EXPERIENCE("Combat Experience", "Rank 1: run and strike in melee."),
        ENDER_PEARL_USE("Ender Pearl Use", "Pack pearls to catch up or escape danger.");

        public final String label;
        public final String description;

        Skill(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public String upgradeDescription(int rank) {
            if (this != COMBAT_EXPERIENCE) return description;
            return switch (rank) {
                case 0 -> description;
                case 1 -> "Rank 2: sidestep strikes and arrows.";
                default -> "Rank 3: swing melee weapons faster.";
            };
        }
    }

    public String owner = "";
    private final Inventory pack = new Inventory();
    private final int[] skillLevels = new int[Skill.values().length];
    private int foodCooldown;
    private transient FoodItem eatingFood;
    private transient int eatingSlot, eatingTicks;
    private transient BucketRecovery bucketRecovery;
    private transient int bucketPickupDelay;
    private transient int bucketAimTicks, bucketReturnTicks;
    private transient int fireBucketAimTicks;
    private transient Mob fireBucketTarget;
    private transient Vec3D bucketLookTarget;
    private transient PearlThrow pearlThrow;
    private transient int pearlThrowTick;

    private record BucketRecovery(int slot, int x, int y, int z) {}

    private record PearlThrow(
            Vec3D landing,
            Vec3D velocity,
            int flightTicks,
            float yaw,
            float pitch,
            float previousPitch) {}

    public Defender(Level level, float x, float y, float z) {
        super(level, x, y, z);
        ItemRegistry.bootstrap();
        textureName = "/char.png";
        allowAlpha = true;
        customName = "Defender";
        setSize(.6f, 1.8f);
        heightOffset = 1.62f;
        health = lastHealth = 20;
        pack.resizeGrid(0);
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

    @Override
    public boolean canControl(Player player) {
        return Companion.canControl(this, owner, player);
    }

    @Override
    public Mob mob() {
        return this;
    }

    @Override
    public void assignOwner(Player player) {
        owner = CrossbowTurret.playerName(player);
    }

    public static boolean isEnemy(Entity entity) {
        return entity instanceof Mob enemy
                && isAttackable(enemy)
                && !(enemy instanceof Enderman)
                && (!(enemy instanceof Spider)
                        || enemy.level != null && enemy.getBrightness(0) < .5f)
                && (MobRegistry.isTurretHostile(enemy)
                        || enemy instanceof SkeletonTrap
                        || enemy instanceof IronGolem
                        || enemy instanceof Villager villager && villager.isHostile());
    }

    private static boolean isAttackable(Mob enemy) {
        return enemy.health > 0
                && !enemy.removed
                && !(enemy instanceof Player)
                && !(enemy instanceof Defender)
                && !(enemy instanceof CrossbowTurret)
                && !(enemy instanceof IronGolem golem && golem.builtByPlayer)
                && !enemy.corruptedToDefendPlayer;
    }

    static boolean isWorkerEnemy(Mob enemy) {
        return isEnemy(enemy) || isAttackable(enemy) && MobRegistry.isTurretHostile(enemy);
    }

    boolean canRespondTo(Entity entity) {
        return entity instanceof Mob enemy && enemy.level == level && isAttackable(enemy);
    }

    public boolean canAttack(Entity entity) {
        if (!canRespondTo(entity)) return false;
        if (isEnemy(entity) || !(entity instanceof Pufferfish) && MobBattle.target(this) == entity)
            return true;
        Player player = ownerPlayer();
        return player != null
                        && (player.recentMobAttacker() == entity
                                || player.recentMobAttack() == entity
                                        && isNeutralEnemy((Mob) entity))
                || ai instanceof DefenderAI defend
                        && (defend.isRetaliatingAgainst(entity)
                                || defend.isProtectingWorkerFrom(entity));
    }

    private static boolean isNeutralEnemy(Mob enemy) {
        return enemy instanceof Enderman
                || enemy instanceof Spider
                || MobRegistry.turretTargetGroup(MobRegistry.idOf(enemy))
                        == MobRegistry.TargetGroup.NEUTRAL;
    }

    public static boolean purchase(Player player) {
        return Companion.hire(player, REGISTRY_ID, PRICE);
    }

    public Inventory pack() {
        return pack;
    }

    public int skillLevel(Skill skill) {
        return skillLevels[skill.ordinal()];
    }

    public void initializeSpawnSkills(ToIntFunction<Skill> ranks) {
        for (Skill skill : Skill.values()) {
            skillLevels[skill.ordinal()] = Math.max(0, Math.min(ranks.applyAsInt(skill), skillLimit(skill)));
        }
        restoreHealth(maxHealth());
    }

    public int upgradeCost(Skill skill) {
        return level != null && level.creativeMode
                ? 0
                : 10 * (Math.min(skillLevel(skill), MAX_SKILL_LEVEL) + 1);
    }

    public boolean upgrade(Player player, Skill skill) {
        if (!canControl(player) || skillMaxed(skill)) return false;
        int cost = upgradeCost(skill);
        if (cost > 0 && player.coins < cost) return false;
        player.coins -= cost;
        skillLevels[skill.ordinal()]++;
        if (skill == Skill.VITALITY) {
            heal(6);
        }
        return true;
    }

    public boolean skillMaxed(Skill skill) {
        return skillLevel(skill) >= skillLimit(skill);
    }

    public int skillLimit(Skill skill) {
        if (skill == Skill.WATER_CLUTCH || skill == Skill.ENDER_PEARL_USE) return 1;
        if (skill == Skill.COMBAT_EXPERIENCE) return MAX_SKILL_LEVEL;
        return level != null && level.creativeMode ? Integer.MAX_VALUE : MAX_SKILL_LEVEL;
    }

    public int maxHealth() {
        return (int) Math.min(Integer.MAX_VALUE, 20 + 6L * skillLevel(Skill.VITALITY));
    }

    int guardDamage(int damage) {
        int rank = skillLevel(Skill.GUARD);
        double remaining = (100 - 12 * Math.min(rank, MAX_SKILL_LEVEL)) / 100.0;
        // Keep creative ranks useful past the survival cap while preserving a minimum hit.
        remaining /= 1 + .15 * Math.max(0, rank - MAX_SKILL_LEVEL);
        return damage <= 0 ? damage : Math.max(1, (int) Math.round(damage * remaining));
    }

    public int weaponDamage(int baseDamage) {
        // Leave room for the percentage calculation in the armor system.
        return (int)
                Math.min(
                        (Integer.MAX_VALUE - 99) / 100,
                        (long) baseDamage + skillLevel(Skill.COMBAT));
    }

    public static int equipmentSlot(int raw) {
        Equipment.Gear gear = Equipment.gear(raw);
        if (gear != null) return gear.slot < 0 ? 4 : gear.slot;
        int id = raw - 256;
        if (id >= 0
                && id < Item.items.length
                && (Item.items[id] instanceof BowItem
                        || Item.items[id] instanceof GravityGunItem
                        || Equipment.miningTool(raw) != null)) return 4;
        return -1;
    }

    public boolean hasBow() {
        return equipment[4] == Item.BOW.id + 256;
    }

    boolean hasSword() {
        for (int sword : Equipment.SWORDS) {
            if (equipment[4] == sword + 256) return true;
        }
        return false;
    }

    @Override
    public String activity() {
        return ai instanceof DefenderAI defenderAI ? defenderAI.escortActivity() : "";
    }

    public int bowDrawTicks() {
        return health > 0
                        && !removed
                        && !isEating()
                        && !isUsingBucket()
                        && !isThrowingPearl()
                        && hasBow()
                        && ai instanceof DefenderAI defenderAI
                ? defenderAI.bowDrawTicks()
                : -1;
    }

    @Override
    public float renderHeadYaw(float partial) {
        if (isUsingBucket() && bucketLookTarget != null) {
            return (float) Math.IEEEremainder(bucketYaw() - renderBodyYaw(partial), 360);
        }
        if (isThrowingPearl() && !isUsingBucket()) {
            return yRotO + (yRot - yRotO) * partial - renderBodyYaw(partial);
        }
        return super.renderHeadYaw(partial);
    }

    @Override
    public float renderHeadPitch(float partial) {
        if (isUsingBucket()) {
            float normalPitch = super.renderHeadPitch(partial);
            float aim =
                    bucketReturnTicks > 0 ? (bucketReturnTicks - partial) / BUCKET_RETURN_TICKS : 1;
            float pitch = 82;
            if (bucketLookTarget != null) {
                pitch =
                        (float)
                                -Math.toDegrees(
                                        Math.atan2(
                                                bucketLookTarget.y - (bb.y0 + getEyeHeight(1)),
                                                Math.hypot(
                                                        bucketLookTarget.x - x,
                                                        bucketLookTarget.z - z)));
            }
            return normalPitch + (pitch - normalPitch) * aim;
        }
        return isThrowingPearl() || bowDrawTicks() >= 0
                ? xRotO + (xRot - xRotO) * partial
                : super.renderHeadPitch(partial);
    }

    public boolean hasGravityGun() {
        return equipment[4] == Item.GRAVITYGUN.id + 256;
    }

    public boolean equip(Player player, int inventorySlot) {
        return player != null && equip(player, player.inventory, inventorySlot);
    }

    @Override
    public boolean equip(Player player, Inventory inventory, int inventorySlot) {
        return Companion.super.equip(player, inventory, inventorySlot);
    }

    @Override
    public void tick() {
        if (health > 0 && !dead && !removed && level != null && !level.isNetworkMode()) {
            if (bucketAimTicks > 0) bucketAimTicks--;
            if (bucketReturnTicks > 0 && --bucketReturnTicks == 0) bucketLookTarget = null;
            if (bucketRecovery != null && --bucketPickupDelay <= 0) recoverBucketWater();
            prepareClutch();
            tickFireBucket();
            if (isThrowingPearl()) tickPearlThrow();
            if (foodCooldown > 0) foodCooldown--;
            if (!isUsingBucket() && !isThrowingPearl()) {
                if (isEating()) tickEating();
                else if (foodCooldown == 0 && health * 5L <= maxHealth() * 3L)
                    startEatingFromPack();
            }
        } else {
            eatingFood = null;
            bucketRecovery = null;
            bucketAimTicks = fireBucketAimTicks = bucketReturnTicks = 0;
            fireBucketTarget = null;
            bucketLookTarget = null;
            pearlThrow = null;
        }
        super.tick();
    }

    @Override
    public void move(float dx, float dy, float dz) {
        if (bucketAimTicks > 0 && dy < 0) {
            dx = xd = 0;
            dz = zd = 0;
        }
        if (health > 0 && !isInKnockback() && dx * dx + dz * dz > .0001f) {
            BuilderDoorBlock.openFor(this, dx, dz);
        }
        super.move(dx, dy, dz);
    }

    private void prepareClutch() {
        if (yd >= 0 || onGround || bucketRecovery != null || !canClutch()) return;
        float lookAhead = -Math.max(1.5f, -yd * 3);
        if (fallDistance - lookAhead <= 3.01f) return;
        int slot = waterBucketSlot();
        if (slot < 0) return;
        float drop = lookAhead;
        for (Object collider : level.getCubes(bb.expand(0, lookAhead, 0))) {
            drop = ((AABB) collider).clipYCollide(bb, drop);
        }
        if (drop == lookAhead || fallDistance - drop <= 3.01f) return;
        BucketRecovery landing = findBucketLanding(bb.cloneMove(0, drop, 0), slot);
        if (landing != null && !level.isWater(landing.x(), landing.y(), landing.z())) {
            bucketAimTicks = 2;
        }
    }

    private boolean canUseFireBucket() {
        return !(riding != null
                || isWetForFire()
                || isInLava()
                || (!onGround && (yd > 0 || level.getCubes(bb.cloneMove(0, -.01f, 0)).isEmpty()))
                || bucketAimTicks > 0
                || bucketRecovery != null
                || bucketReturnTicks > 0);
    }

    boolean needsExtinguishing(Mob target) {
        return target != null
                && target.level == level
                && !target.removed
                && target.health > 0
                && target.remainingFireTicks > 0
                && !target.isWetForFire()
                && !target.isInLava()
                && !(target instanceof Player player && player.isSpectator());
    }

    boolean beginFireBucket(Mob target) {
        if (!canUseFireBucket() || !needsExtinguishing(target)) return false;
        BucketRecovery landing = fireBucketLanding(target);
        if (landing == null) return false;
        fireBucketTarget = target;
        fireBucketAimTicks = FIRE_BUCKET_AIM_TICKS;
        bucketLookTarget = target == this ? null : bucketPoint(landing);
        faceBucketTarget();
        xd = zd = 0;
        return true;
    }

    private void tickFireBucket() {
        if (fireBucketTarget != this && remainingFireTicks > 0 && beginFireBucket(this)) return;
        Mob target = fireBucketTarget;
        if (target == null) return;
        BucketRecovery landing =
                canUseFireBucket() && needsExtinguishing(target) ? fireBucketLanding(target) : null;
        if (landing == null) {
            fireBucketTarget = null;
            fireBucketAimTicks = 0;
            bucketLookTarget = null;
            return;
        }
        if (target != this) bucketLookTarget = bucketPoint(landing);
        if (--fireBucketAimTicks == 0) {
            fireBucketTarget = null;
            if (placeBucketWater(landing)) target.extinguishFire();
            else bucketLookTarget = null;
        }
    }

    private BucketRecovery fireBucketLanding(Mob target) {
        int slot = waterBucketSlot();
        BucketRecovery landing = slot < 0 ? null : findBucketLanding(target.bb, slot);
        return landing != null
                        && !level.isWater(landing.x(), landing.y(), landing.z())
                        && (target == this || canReachBucket(bucketPoint(landing)))
                ? landing
                : null;
    }

    void faceBucketTarget() {
        if (bucketLookTarget != null) yRot = bucketYaw();
    }

    private float bucketYaw() {
        return (float) Math.toDegrees(Math.atan2(x - bucketLookTarget.x, bucketLookTarget.z - z));
    }

    private boolean canClutch() {
        return health > 0
                && !dead
                && !removed
                && level != null
                && !level.isNetworkMode()
                && skillLevel(Skill.WATER_CLUTCH) > 0;
    }

    public boolean hasWaterClutchReady() {
        return canClutch() && waterBucketSlot() >= 0;
    }

    @Override
    protected void causeFallDamage(float distance) {
        if (distance > 3.01f && canClutch() && clutchLanding()) return;
        super.causeFallDamage(distance);
    }

    private int waterBucketSlot() {
        return packedItemSlot(Item.BUCKET_WATER);
    }

    int packedItemSlot(Item item) {
        int id = item.id + 256;
        for (int slot = 0; slot < pack.totalSize(); slot++) {
            if (pack.getId(slot) == id && pack.getCount(slot) > 0) return slot;
        }
        return -1;
    }

    public void beginPearlThrow(Vec3D landing, Vec3D velocity, int flightTicks) {
        float yaw = (float) Math.toDegrees(Math.atan2(-velocity.x, velocity.z));
        float pitch =
                (float) -Math.toDegrees(Math.atan2(velocity.y, Math.hypot(velocity.x, velocity.z)));
        pearlThrow = new PearlThrow(landing, velocity, flightTicks, yaw, pitch, xRot);
        pearlThrowTick = 0;
        holdPearlAim();
    }

    public boolean isThrowingPearl() {
        return pearlThrow != null && health > 0 && !removed;
    }

    void holdPearlAim() {
        yRot = pearlThrow.yaw();
        xRot = pearlThrow.pitch();
        xd = zd = 0;
    }

    private void tickPearlThrow() {
        PearlThrow shot = pearlThrow;
        if (isUsingBucket() || riding != null || ++pearlThrowTick >= PEARL_THROW_TICKS) {
            finishPearlThrow();
            return;
        }
        holdPearlAim();
        if (pearlThrowTick == PEARL_RELEASE_TICK) {
            int slot = packedItemSlot(Item.ENDER_PEARL);
            if (slot < 0
                    || !ThrownEnderPearl.releaseForDefender(
                            this, shot.landing(), shot.velocity(), shot.flightTicks())) {
                finishPearlThrow();
                return;
            }
            pack.setIdCount(slot, Item.ENDER_PEARL.id + 256, pack.getCount(slot) - 1);
        }
    }

    private void finishPearlThrow() {
        xRot = pearlThrow.previousPitch();
        pearlThrow = null;
    }

    private boolean clutchLanding() {
        bucketAimTicks = 0;
        BucketRecovery landing = findBucketLanding(bb, waterBucketSlot());
        if (landing == null) return false;
        fireBucketTarget = null;
        fireBucketAimTicks = 0;
        bucketLookTarget = null;
        return level.isWater(landing.x(), landing.y(), landing.z()) || placeBucketWater(landing);
    }

    private boolean placeBucketWater(BucketRecovery landing) {
        int x = landing.x(), y = landing.y(), z = landing.z();
        if (!BucketItem.placeWater(level, x, y, z) || !level.isWater(x, y, z)) return false;
        pack.setIdCount(landing.slot(), Item.BUCKET_EMPTY.id + 256, 1);
        bucketRecovery = landing;
        bucketPickupDelay = BUCKET_PICKUP_TICKS;
        bucketReturnTicks = 0;
        return true;
    }

    private BucketRecovery findBucketLanding(AABB feet, int slot) {
        int y = (int) Math.ceil(feet.y0 - .001f);
        if (y - feet.y0 > .6f) return null;
        int centerX = (int) Math.floor((feet.x0 + feet.x1) * .5f);
        int centerZ = (int) Math.floor((feet.z0 + feet.z1) * .5f);
        BucketRecovery fallback = null;
        if (canClutchAt(centerX, y, centerZ, slot)) {
            fallback = new BucketRecovery(slot, centerX, y, centerZ);
            if (hasClutchSupport(feet, centerX, centerZ)) return fallback;
        }
        for (int x = (int) Math.floor(feet.x0); x < feet.x1; x++) {
            for (int z = (int) Math.floor(feet.z0); z < feet.z1; z++) {
                if ((x == centerX && z == centerZ) || !canClutchAt(x, y, z, slot)) continue;
                BucketRecovery candidate = new BucketRecovery(slot, x, y, z);
                if (hasClutchSupport(feet, x, z)) return candidate;
                if (fallback == null) fallback = candidate;
            }
        }
        return fallback;
    }

    private boolean hasClutchSupport(AABB feet, int x, int z) {
        // The collision shape can support the edge of the feet in a different cell from their
        // center.
        AABB contact =
                new AABB(
                        Math.max(feet.x0, x),
                        feet.y0 - .01f,
                        Math.max(feet.z0, z),
                        Math.min(feet.x1, x + 1),
                        feet.y0 + .01f,
                        Math.min(feet.z1, z + 1));
        for (Object candidate : level.getCubes(contact)) {
            AABB support = (AABB) candidate;
            if (Math.abs(support.y1 - feet.y0) < .01f && support.intersects(contact)) return true;
        }
        return false;
    }

    private boolean canClutchAt(int x, int y, int z, int slot) {
        return slot >= 0
                ? BucketItem.canPlaceWaterSource(level, x, y, z)
                : level.isInBounds(x, y, z) && level.isWater(x, y, z);
    }

    private void recoverBucketWater() {
        BucketRecovery recovery = bucketRecovery;
        bucketRecovery = null;
        int slot = recovery.slot();
        int x = recovery.x(), y = recovery.y(), z = recovery.z();
        if (pack.getId(slot) != Item.BUCKET_EMPTY.id + 256
                || pack.getCount(slot) <= 0
                || !level.isWater(x, y, z)) {
            bucketLookTarget = null;
            return;
        }
        if (!canReachBucket(bucketPoint(recovery))) {
            bucketLookTarget = null;
            return;
        }
        int filled = BucketItem.scoopSource(level, x, y, z);
        if (filled > 0) {
            pack.setIdCount(slot, filled, 1);
            bucketReturnTicks = BUCKET_RETURN_TICKS;
        } else {
            bucketLookTarget = null;
        }
    }

    private static Vec3D bucketPoint(BucketRecovery location) {
        return new Vec3D(location.x() + .5f, location.y() + .4f, location.z() + .5f);
    }

    private boolean canReachBucket(Vec3D point) {
        Vec3D eyes = new Vec3D(x, bb.y0 + getEyeHeight(1), z);
        return eyes.distanceSquared(point) <= 9 && level.clip(eyes, point, true) == null;
    }

    public boolean isUsingBucket() {
        return health > 0
                && !removed
                && (bucketAimTicks > 0
                        || fireBucketAimTicks > 0
                        || bucketRecovery != null
                        || bucketReturnTicks > 0);
    }

    public boolean isEating() {
        return eatingFood != null && health > 0 && !removed;
    }

    public int heldItem() {
        if (isUsingBucket()) {
            return (bucketRecovery != null ? Item.BUCKET_EMPTY.id : Item.BUCKET_WATER.id) + 256;
        }
        if (isThrowingPearl())
            return pearlThrowTick < PEARL_RELEASE_TICK ? Item.ENDER_PEARL.id + 256 : 0;
        return isEating() ? eatingFood.id + 256 : equipment[4];
    }

    private void startEatingFromPack() {
        for (int slot = 0; slot < PACK_SIZE; slot++) {
            int id = pack.getId(slot) - 256;
            if (id < 0
                    || id >= Item.items.length
                    || !(Item.items[id] instanceof FoodItem food)
                    || food.healing() <= 0) continue;
            eatingFood = food;
            eatingSlot = slot;
            eatingTicks = 0;
            foodCooldown = 40;
            return;
        }
    }

    private void tickEating() {
        int id = eatingFood.id + 256;
        int count = pack.getCount(eatingSlot);
        if (pack.getId(eatingSlot) != id || count <= 0 || health >= maxHealth()) {
            eatingFood = null;
            foodCooldown = 0;
            return;
        }
        if (FoodItem.isBiteTick(++eatingTicks)) {
            var random = java.util.concurrent.ThreadLocalRandom.current();
            float pitch = 1 + (random.nextFloat() - random.nextFloat()) * .2f;
            level.playSound("item/food/eat", this, random.nextBoolean() ? .5f : 1f, pitch);
        }
        if (eatingTicks >= FoodItem.EAT_TICKS) {
            pack.setIdCount(eatingSlot, id, count - 1);
            heal(eatingFood.healing());
            if (eatingFood.playsBurp()) level.playSound("item/food/burp", this, .5f, 1f);
            eatingFood = null;
        }
    }

    public void renderHandAction(
            HumanoidModel model, float yaw, float pitch, float scale, float partial) {
        float swing = handSwingProgress(partial);
        if (isUsingBucket()) {
            float reach = 1;
            if (bucketRecovery != null) {
                float elapsed = BUCKET_PICKUP_TICKS - bucketPickupDelay + partial;
                reach = Math.max(1 - elapsed / 5, (elapsed - 11) / 5);
                reach = Math.max(0, Math.min(1, reach));
            } else if (bucketReturnTicks > 0) {
                reach = Math.max(0, (bucketReturnTicks - partial) / BUCKET_RETURN_TICKS);
            }
            model.renderUsingBucket(scale, reach);
        } else if (isThrowingPearl()) {
            model.renderThrowingPearl(
                    scale,
                    yaw,
                    pitch,
                    (pearlThrowTick + partial) / PEARL_RELEASE_TICK,
                    (pearlThrowTick + partial - PEARL_RELEASE_TICK)
                            / (PEARL_THROW_TICKS - PEARL_RELEASE_TICK));
        } else if (hasBow() && !isEating()) {
            model.renderHoldingBow(yaw, pitch, scale, bowDrawTicks() >= 0, swing);
        } else if (hasGravityGun() && !isEating()) {
            model.renderHoldingGun(pitch, scale);
        } else {
            float eating = isEating() ? eatingTicks + partial : -1;
            model.renderHandAction(scale, heldItem() > 0, swing, 0, eating);
        }
    }

    @Override
    public void heal(int amount) {
        if (health <= 0 || health >= maxHealth() || amount <= 0) return;
        int restored = Math.min(amount, maxHealth() - health);
        health += restored;
        lastHealth = (int) Math.min(maxHealth(), (long) lastHealth + restored);
    }

    @Override
    public boolean unequip(Player player, int slot) {
        return Companion.super.unequip(player, slot);
    }

    @Override
    public void hurt(Entity source, int damage) {
        Entity responsible = source instanceof Arrow arrow ? arrow.getOwner() : source;
        if (responsible instanceof Player) return;
        DefenderAI defense = ai instanceof DefenderAI defenderAI ? defenderAI : null;
        if (damage > 0 && defense != null && defense.dodgeHit(this, source, responsible)) return;
        int previousHealth = health;
        super.hurt(source, damage);
        if (defense != null && responsible instanceof Mob threat && health < previousHealth) {
            defense.recordDamage(this, threat, previousHealth - health);
        }
    }

    @Override
    public void die(Entity killer) {
        if (dead || removed) return;
        for (int slot = 0; slot < equipment.length; slot++) {
            if (level != null && !level.isNetworkMode() && level.doMobLoot && equipment[slot] > 0)
                level.addEntity(
                        new DroppedItem(level, x, y, z, equipment[slot] - 256)
                                .withArmorWear(Equipment.wear(this, slot)));
            Equipment.equip(this, slot, 0, 0);
        }
        for (int slot = 0; slot < PACK_SIZE; slot++) {
            int id = pack.getId(slot);
            int count = pack.getCount(slot);
            if (level != null && !level.isNetworkMode() && level.doMobLoot && id > 0 && count > 0) {
                level.addEntity(
                        id < 256
                                ? new DroppedBlock(level, x, y, z, id, count)
                                : new DroppedItem(level, x, y, z, id - 256, count)
                                        .withArmorWear(pack.armorWear(slot))
                                        .withCrate(pack.crate(slot)));
            }
            pack.setIdCount(slot, -1, 0);
        }
        super.die(killer);
    }

    @Override
    public void savePersistence(CompoundTag tag) {
        super.savePersistence(tag);
        tag.putString("DefenderOwner", owner);
        CompoundTag skills = new CompoundTag("DefenderSkills");
        for (Skill skill : Skill.values()) skills.putInt(skill.label, skillLevel(skill));
        tag.put(skills);
        ListTag stacks = new ListTag("DefenderPack", (byte) 10);
        for (int slot = 0; slot < PACK_SIZE; slot++) {
            if (pack.getCount(slot) <= 0) continue;
            CompoundTag stack = new CompoundTag(null);
            stack.putInt("Slot", slot);
            stack.putInt("Id", pack.getId(slot));
            stack.putInt("Count", pack.getCount(slot));
            stack.putInt("Wear", pack.armorWear(slot));
            if (pack.crate(slot) != null) stack.put(pack.crate(slot).save());
            stacks.add(stack);
        }
        tag.put(stacks);
    }

    @Override
    public void loadPersistence(CompoundTag tag) {
        super.loadPersistence(tag);
        owner = tag.getString("DefenderOwner");
        CompoundTag skills = tag.getCompound("DefenderSkills");
        for (Skill skill : Skill.values()) {
            int rank = Math.max(0, skills.getInt(skill.label));
            skillLevels[skill.ordinal()] =
                    switch (skill) {
                        case WATER_CLUTCH, ENDER_PEARL_USE -> Math.min(rank, 1);
                        case COMBAT_EXPERIENCE -> Math.min(rank, MAX_SKILL_LEVEL);
                        default -> rank;
                    };
        }
        restoreHealth(Math.min(health, maxHealth()));
        for (int slot = 0; slot < PACK_SIZE; slot++) pack.setIdCount(slot, -1, 0);
        for (var entry : tag.getList("DefenderPack").list) {
            if (!(entry instanceof CompoundTag stack)) continue;
            int slot = stack.getInt("Slot");
            if (slot < 0 || slot >= PACK_SIZE) continue;
            int id = stack.getInt("Id");
            pack.setIdCount(slot, id, stack.getInt("Count"));
            pack.setArmorWear(slot, stack.getInt("Wear"));
            if (pack.getId(slot) == TurretItems.PACKED + 256)
                pack.setCrate(slot, PackedTurret.load(stack));
        }
        persistent = true;
        ai = new DefenderAI();
    }
}
