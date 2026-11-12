package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;

public class Villager extends Mob {
    private static final long serialVersionUID = 3112391374784077778L;
    private java.util.List<net.classicremastered.minecraft.level.itemstack.VillagerTrades.Offer>
            savedOffers;

    public java.util.List<net.classicremastered.minecraft.level.itemstack.VillagerTrades.Offer>
            tradeOffers() {
        if (savedOffers == null)
            savedOffers =
                    net.classicremastered.minecraft.level.itemstack.VillagerTrades.randomOffers(
                            level.random);
        return savedOffers;
    }

    @Override
    public void savePersistence(net.classicremastered.nbt.CompoundTag tag) {
        super.savePersistence(tag);
        net.classicremastered.minecraft.level.itemstack.VillagerTrades.save(this, tag);
    }

    @Override
    public void loadPersistence(net.classicremastered.nbt.CompoundTag tag) {
        super.loadPersistence(tag);
        savedOffers = net.classicremastered.minecraft.level.itemstack.VillagerTrades.load(tag);
    }

    public boolean persistent = true;
    private int deathTicks = 0;

    // --- Home anchor ---
    public boolean hasHome = false;
    public float homeX = 0f, homeY = 0f, homeZ = 0f;
    public float homeRadius = 3.0f;
    public boolean hasHomeDoor;
    public int homeDoorX, homeDoorY, homeDoorZ;

    public void setHomeDoor(int x, int y, int z) {
        if (!hasHome) throw new IllegalStateException("assign a home before its doorway");
        if (hasHomeDoor && level != null) level.villagerDoors().unregister(this);
        homeDoorX = x;
        homeDoorY = y;
        homeDoorZ = z;
        hasHomeDoor = true;
        if (level != null) level.villagerDoors().register(this);
    }

    // --- Trading ---
    public int tradeCooldown = 0;

    // --- Behavior state ---
    private int hurtCounter = 0;
    private boolean hostile = false;
    private boolean murderMessageShown = false;
    private int bossBattleCounter = 0;
    private int idleSoundTimer = 0;

    public Villager(Level level, float x, float y, float z) {
        super(level);
        this.setSize(0.6F, 1.8F);
        this.heightOffset = 1.62F;
        this.health = 20;
        this.textureName = "/mob/villager.png";
        this.modelName = "villager";
        initSounds();

        this.coinDrop = -2;
        this.setPos(x, y, z);
    }

    private void initSounds() {
        soundIdle = "mob/villager/idle";
        soundHurt = "mob/villager/hit";
        soundDeath = "mob/villager/death";
        float pitch = this instanceof BabyVillager ? 1.5f : 1f;
        idlePitchMin = idlePitchMax = pitch;
        hurtPitchMin = hurtPitchMax = pitch;
        deathPitchMin = deathPitchMax = pitch;
    }

    private void readObject(java.io.ObjectInputStream input)
            throws java.io.IOException, ClassNotFoundException {
        input.defaultReadObject();
        initSounds();
    }

    public void setHome(float x, float y, float z, float radius) {
        if (hasHomeDoor && level != null) level.villagerDoors().unregister(this);
        hasHomeDoor = false;
        this.homeX = x;
        this.homeY = y;
        this.homeZ = z;
        this.homeRadius = radius;
        this.hasHome = true;
        this.ai = new net.classicremastered.minecraft.mob.ai.villager.VillagerAI(x, y, z, radius);
    }

    public void clearHome() {
        if (hasHomeDoor && level != null) level.villagerDoors().unregister(this);
        hasHomeDoor = false;
        this.hasHome = false;
    }

    // --- Trading logic ---
    public void onRightClick(Player p) {
        if (this.health <= 0 || hostile) return;

        if (tradeCooldown > 0) {
            if (p.minecraft != null && p.minecraft.hud != null) {
                p.minecraft.hud.addChat("&cVillager refuses to trade (scared)!");
            }
            return;
        }

        if (net.classicremastered.minecraft.level.itemstack.VillagerTrades.available(this, p)
                && p.minecraft != null) {
            p.minecraft.setCurrentScreen(
                    new net.classicremastered.minecraft.gui.VillagerTradingScreen(this));
        }
    }

    @Override
    public void aiStep() {
        if (hostile
                && ai instanceof net.classicremastered.minecraft.mob.ai.BasicAI basic
                && !(ai instanceof net.classicremastered.minecraft.mob.ai.villager.VillagerAI)) {
            Mob target =
                    net.classicremastered.minecraft.mob.ai.combat.HostileTargets.nearest(
                            this, 32, basic.attackTarget);
            basic.attackTarget =
                    target != null
                                    && target.health > 0
                                    && !net.classicremastered.minecraft.util.CreativeModeHelper
                                            .shouldIgnoreTarget(target)
                            ? target
                            : null;
        }
        super.aiStep();
    }

    @Override
    public void tick() {
        updateHostility();
        super.tick();

        if (this.health > 0) {
            this.removed = false;
            this.deathTicks = 0;
        } else {
            if (this.deathTicks == 0) {
                this.xd = this.yd = this.zd = 0.0F;
                this.noPhysics = true;
            }
            if (++this.deathTicks >= 20) this.remove();
            return;
        }

        if (tradeCooldown > 0) tradeCooldown--;

        Player p = (this.level != null) ? (Player) this.level.player : null;
        if (p != null && p.isSpectator()) return;

        Entity combatTarget =
                ai instanceof net.classicremastered.minecraft.mob.ai.BasicAI basic
                        ? basic.attackTarget
                        : p;
        if (hostile && combatTarget instanceof Mob living && living.health > 0) {
            // --- Skip hostile logic in Creative ---
            if (this.level.creativeMode) {
                hostile = false; // reset, so Creative player never stays flagged
                return;
            }

            bossBattleCounter++;

            if (!murderMessageShown
                    && p != null
                    && p.minecraft != null
                    && p.minecraft.hud != null) {
                p.minecraft.hud.addChat("&4You are chosen as a murderer, suffer the consequences!");
                murderMessageShown = true;
            }

            // Boss battle pling loop (safe/clamped)
            if (bossBattleCounter % 6 == 0) {
                int step = (bossBattleCounter / 6) % 16;
                float basePitch;
                if (step < 8) {
                    basePitch = (step % 2 == 0) ? 0.6f : 0.6f * (float) Math.pow(2.0, 3.0 / 12.0);
                } else {
                    basePitch =
                            (step % 2 == 0)
                                    ? 0.6f * (float) Math.pow(2.0, 3.0 / 12.0)
                                    : 0.6f * (float) Math.pow(2.0, 6.0 / 12.0);
                }

                safePlaySound("note/pling", 0.5f, basePitch);
                float harmonyPitch = basePitch * (float) Math.pow(2.0, 3.0 / 12.0);
                safePlaySound("note/pling", 0.3f, harmonyPitch);
            }

            // Creepy bat idle sound
            if (--idleSoundTimer <= 0) {
                safePlaySound("mob/bat/idle", 0.4f, 0.8f + this.level.random.nextFloat() * 0.4f);
                idleSoundTimer = 100 + this.level.random.nextInt(100);
            }

            Entity target =
                    ai instanceof net.classicremastered.minecraft.mob.ai.BasicAI basic
                            ? basic.attackTarget
                            : p;
            if (target == null || target.removed) return;
            // Chase the player
            float dx = target.x - this.x;
            float dz = target.z - this.z;
            float dist2 = dx * dx + dz * dz;
            if (dist2 < 16 * 16) {
                float mag = (float) Math.sqrt(dist2);
                this.running = true;

                if (mag < 2.0f
                        && this.attackTime <= 0
                        && level.clip(
                                        new net.classicremastered.minecraft.model.Vec3D(
                                                x, bb.y0 + bbHeight * 0.85f, z),
                                        new net.classicremastered.minecraft.model.Vec3D(
                                                target.x,
                                                target.bb.y0 + target.bbHeight * 0.5f,
                                                target.z))
                                == null) {
                    // ✅ Skip hurting Creative players
                    if (!this.level.creativeMode) {
                        target.hurt(this, 2);
                    }
                    this.attackTime = 20;
                }
            }
        }
    }

    private void updateHostility() {
        boolean next =
                level != null
                        && level.player instanceof Player player
                        && player.villagerReputation <= -100
                        && !level.creativeMode
                        && !player.isSpectator();
        if (hostile == next) return;
        hostile = next;
        bossBattleCounter = idleSoundTimer = 0;
        murderMessageShown = false;
        running = false;
        if (ai instanceof net.classicremastered.minecraft.mob.ai.BasicAI basic) {
            basic.attackTarget = null;
            basic.clearNavigation();
        }
    }

    private void safePlaySound(String key, float volume, float pitch) {
        if (this instanceof BabyVillager) return;
        if (health <= 0
                || removed
                || (level != null
                        && level.minecraft != null
                        && level.minecraft.settings != null
                        && !level.minecraft.settings.allowMobSounds)) return;
        if (this.level != null
                && this.level.minecraft != null
                && this.level.minecraft.soundPC != null) {
            float v = Math.max(0.05f, Math.min(1.0f, volume));
            float p = Math.max(0.25f, Math.min(2.0f, pitch));
            this.level.minecraft.soundPC.playSoundClean(key, v, p);
        }
    }

    @Override
    public void hurt(Entity attacker, int damage) {
        if (health <= 0 || dead || removed) return;
        if (attacker != null) {
            Entity actualAttacker = attacker;
            if (attacker instanceof net.classicremastered.minecraft.entity.Arrow) {
                actualAttacker =
                        ((net.classicremastered.minecraft.entity.Arrow) attacker).getOwner();
            }

            if (actualAttacker instanceof Mob && !(actualAttacker instanceof Player)) {
                boolean allowed =
                        actualAttacker instanceof Zombie && !(actualAttacker instanceof Skeleton)
                                || actualAttacker instanceof Defender && hostile;
                if (!allowed) {
                    return;
                }
            }
        }

        super.hurt(attacker, damage);

        if (attacker instanceof Player p) {
            this.hurtCounter++;
            if (this.hurtCounter >= 3) {
                if (!this.level.creativeMode) {
                    p.villagerReputation = Math.max(-100, p.villagerReputation - 2);
                    if (p.minecraft != null && p.minecraft.hud != null) {
                        p.minecraft.hud.addChat("&cVillagers distrust you (-2 reputation)");
                    }
                }
                this.hurtCounter = 0;
            }
        }
    }

    public boolean isHostile() {
        return this.hostile;
    }

    @Override
    public void die(Entity killer) {
        if (dead || removed) return;
        super.die(killer);

        if (net.classicremastered.minecraft.player.DeathMessages.resolveAttacker(killer)
                instanceof Player p) {
            if (!this.level.creativeMode) {
                p.villagerReputation = Math.max(-100, p.villagerReputation - 7);
                if (p.minecraft != null && p.minecraft.hud != null) {
                    p.minecraft.hud.addChat("&cYou killed a villager! (-7 reputation)");
                }
            }
            p.villagerKills++;

            java.util.List near = this.level.findEntities(this, this.bb.grow(5, 3, 5));
            for (Object o : near) {
                if (o instanceof Villager && o != this) {
                    Villager v = (Villager) o;
                    v.tradeCooldown = 600;
                    if (v.level.minecraft != null && v.level.minecraft.hud != null) {
                        v.level.minecraft.hud.addChat("&eVillager is too scared to trade!");
                    }
                }
            }
        }
    }
}
