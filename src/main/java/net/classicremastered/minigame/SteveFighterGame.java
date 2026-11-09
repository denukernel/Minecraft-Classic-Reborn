package net.classicremastered.minigame;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.EnumSet;
import java.util.function.Consumer;

/** Fixed 20 Hz simulation, independent of rendering and the normal Minecraft world. */
public final class SteveFighterGame {
    public static final int WIDTH = 426, HEIGHT = 240, FLOOR = 190;
    public enum Kind {
        ZOMBIE, SKELETON, CREEPER
    }

    public enum SoundCue {
        SWING("random/bow", .3f, 1.6f), HIT("damage/hit", .5f, 1f), JUMP("random/pop", .2f, 1.3f), DASH("random/bow",
                .25f, 1.9f), STEP("step/grass", .18f, 1f), LAND("step/grass", .3f, .85f), HURT("random/classic_hurt",
                        .65f, 1f), DEFLECT("random/bowhit", .5f, 1.5f), ZOMBIE_HURT("mob/zombie/hurt", .4f,
                                1f), ZOMBIE_DEATH("mob/zombie/death", .5f, 1f), SKELETON_HURT("mob/skeleton/hurt", .4f,
                                        1f), SKELETON_DEATH("mob/skeleton/death", .5f, 1f), CREEPER_HURT(
                                                "mob/creeper/say", .35f,
                                                1f), CREEPER_DEATH("mob/creeper/death", .5f, 1f), BOW("random/bow", .4f,
                                                        1f), FUSE("random/fuse", .4f, 1f), EXPLODE("random/explode",
                                                                .65f, 1f), COUNTDOWN("note/hat", .3f, 1f), ROUND_START(
                                                                        "note/pling", .55f,
                                                                        1.2f), ROUND_CLEAR("random/levelup", .5f,
                                                                                1f), GAME_OVER("note/bass", .7f, .65f);

        public final String key;
        public final float volume, pitch;
        SoundCue(String key, float volume, float pitch) {
            this.key = key;
            this.volume = volume;
            this.pitch = pitch;
        }
    }

    // Coalesce identical events in one tick so a crowd hit does not overload the mixer.
    private final EnumSet<SoundCue> soundCues = EnumSet.noneOf(SoundCue.class);

    public void drainSounds(boolean enabled, boolean mobSounds, Consumer<SoundCue> output) {
        EnumSet<SoundCue> pending = EnumSet.copyOf(soundCues);
        soundCues.clear();
        if (enabled)
            for (SoundCue cue : pending) {
                if (mobSounds || !cue.key.startsWith("mob/"))
                    output.accept(cue);
            }
    }

    public static final class Mob {
        public final Kind kind;
        public float x;
        public int health, cooldown = 25, flash, fuse;
        public Mob(Kind kind, float x, int round) {
            this.kind = kind;
            this.x = x;
            health = (kind == Kind.ZOMBIE ? 3 : 2) + Math.min(3, (round - 1) / 4);
        }
    }

    public static final class Arrow {
        public float x, y, vx;
        public Arrow(float x, float y, float vx) {
            this.x = x;
            this.y = y;
            this.vx = vx;
        }
    }

    public static final class Spark {
        public float x, y, vx, vy;
        public int life = 12, color;
        Spark(float x, float y, Random random, int color) {
            this.x = x;
            this.y = y;
            this.color = color;
            vx = (random.nextFloat() - .5f) * 5;
            vy = -random.nextFloat() * 3 - 1;
        }
    }

    public final List<Mob> mobs = new ArrayList<>();
    public final List<Arrow> arrows = new ArrayList<>();
    public final List<Spark> sparks = new ArrayList<>();
    public float x = WIDTH / 2f, y = FLOOR, vy;
    public int facing = 1, health = 20, round, score, kills, ticks;
    public int attackCooldown, swing, invulnerable, dashCooldown, dashTicks;
    public int intermission = 40, remainingToSpawn, spawnCooldown;
    public boolean dead;
    private boolean jumpHeld, dashHeld;
    private final Random random;

    public SteveFighterGame(long seed) {
        random = new Random(seed);
    }

    public void tick(boolean left, boolean right, boolean jump, boolean attack, boolean dash) {
        if (dead)
            return;
        ticks++;
        if (attackCooldown > 0)
            attackCooldown--;
        if (swing > 0)
            swing--;
        if (invulnerable > 0)
            invulnerable--;
        if (dashCooldown > 0)
            dashCooldown--;
        int direction = (right ? 1 : 0) - (left ? 1 : 0);
        if (direction != 0 && dashTicks == 0)
            facing = direction;
        if (jump && !jumpHeld && y >= FLOOR) {
            vy = -7;
            soundCues.add(SoundCue.JUMP);
        }
        if (dash && !dashHeld && dashCooldown == 0) {
            dashTicks = 5;
            dashCooldown = 45;
            invulnerable = Math.max(invulnerable, 6);
            soundCues.add(SoundCue.DASH);
        }
        jumpHeld = jump;
        dashHeld = dash;
        x += dashTicks > 0 ? facing * 6 : direction * 2.4f;
        if (dashTicks > 0)
            dashTicks--;
        x = Math.max(14, Math.min(WIDTH - 14, x));
        boolean airborne = y < FLOOR;
        y += vy;
        vy += .65f;
        if (y >= FLOOR) {
            y = FLOOR;
            vy = 0;
            if (airborne)
                soundCues.add(SoundCue.LAND);
            else if (direction != 0 && dashTicks == 0 && ticks % 8 == 0)
                soundCues.add(SoundCue.STEP);
        }
        if (attack)
            attack();

        if (intermission > 0) {
            if (intermission % 20 == 0)
                soundCues.add(SoundCue.COUNTDOWN);
            if (--intermission == 0) {
                soundCues.add(SoundCue.ROUND_START);
                round++;
                remainingToSpawn = 3 + Math.min(round, 15) * 2;
                spawnCooldown = 0;
            }
        } else if (remainingToSpawn > 0 && --spawnCooldown <= 0) {
            Kind kind = Kind.ZOMBIE;
            if (round >= 2 && random.nextInt(3) == 0)
                kind = Kind.SKELETON;
            if (round >= 3 && random.nextInt(4) == 0)
                kind = Kind.CREEPER;
            mobs.add(new Mob(kind, random.nextBoolean() ? 12 : WIDTH - 12, round));
            remainingToSpawn--;
            spawnCooldown = Math.max(12, 40 - round * 2);
        }

        for (Mob mob : mobs) {
            if (mob.health <= 0)
                continue;
            if (mob.flash > 0)
                mob.flash--;
            if (mob.cooldown > 0)
                mob.cooldown--;
            float distance = x - mob.x;
            int toward = distance >= 0 ? 1 : -1;
            float speed = .65f + Math.min(.6f, round * .04f);
            if (mob.kind == Kind.SKELETON) {
                if (Math.abs(distance) > 130)
                    mob.x += toward * speed;
                else if (Math.abs(distance) < 85)
                    mob.x -= toward * speed;
                if (mob.cooldown == 0) {
                    arrows.add(new Arrow(mob.x, FLOOR - 19, toward * 3.5f));
                    soundCues.add(SoundCue.BOW);
                    mob.cooldown = Math.max(32, 65 - round);
                }
            } else {
                if (Math.abs(distance) > 15)
                    mob.x += toward * speed;
                if (mob.kind == Kind.CREEPER) {
                    if (Math.abs(distance) < 37 && y > FLOOR - 42) {
                        if (mob.fuse == 0)
                            soundCues.add(SoundCue.FUSE);
                        mob.fuse++;
                    } else
                        mob.fuse = Math.max(0, mob.fuse - 2);
                    if (mob.fuse >= 24) {
                        soundCues.add(SoundCue.EXPLODE);
                        burst(mob.x, FLOOR - 15, 0xFFFFC65C);
                        if (Math.abs(distance) < 58 && y > FLOOR - 55)
                            hurt(5, toward);
                        mob.health = 0;
                    }
                } else if (Math.abs(distance) < 20 && y > FLOOR - 27 && mob.cooldown == 0) {
                    hurt(2, toward);
                    mob.cooldown = 25;
                }
            }
            mob.x = Math.max(10, Math.min(WIDTH - 10, mob.x));
        }
        mobs.removeIf(mob -> mob.health <= 0);
        arrows.removeIf(arrow -> {
            arrow.x += arrow.vx;
            if (Math.abs(arrow.x - x) < 11 && arrow.y >= y - 32 && arrow.y <= y) {
                hurt(2, arrow.vx > 0 ? 1 : -1);
                return true;
            }
            return arrow.x < 0 || arrow.x > WIDTH;
        });
        for (Spark spark : sparks) {
            spark.x += spark.vx;
            spark.y += spark.vy;
            spark.vy += .3f;
            spark.life--;
        }
        sparks.removeIf(spark -> spark.life <= 0);
        if (!dead && intermission == 0 && remainingToSpawn == 0 && mobs.isEmpty()) {
            intermission = 70;
            soundCues.add(SoundCue.ROUND_CLEAR);
            health = Math.min(20, health + 4);
            score += round * 100;
            arrows.clear();
        }
    }

    public void attack() {
        if (dead || attackCooldown > 0)
            return;
        attackCooldown = 10;
        swing = 5;
        soundCues.add(SoundCue.SWING);
        for (Mob mob : mobs) {
            float distance = (mob.x - x) * facing;
            if (distance >= -6 && distance < 48 && y > FLOOR - 42 && mob.health > 0) {
                mob.health -= 2;
                mob.flash = 5;
                mob.fuse = 0;
                mob.x = Math.max(10, Math.min(WIDTH - 10, mob.x + facing * 14));
                burst(mob.x, FLOOR - 20, 0xFFB9F6FF);
                soundCues.add(SoundCue.HIT);
                soundCues.add(mob.kind == Kind.ZOMBIE
                        ? (mob.health <= 0 ? SoundCue.ZOMBIE_DEATH : SoundCue.ZOMBIE_HURT)
                        : mob.kind == Kind.SKELETON
                                ? (mob.health <= 0 ? SoundCue.SKELETON_DEATH : SoundCue.SKELETON_HURT)
                                : (mob.health <= 0 ? SoundCue.CREEPER_DEATH : SoundCue.CREEPER_HURT));
                if (mob.health <= 0) {
                    score += 25;
                    kills++;
                }
            }
        }
        // Sword swings can also deflect incoming arrows.
        if (arrows.removeIf(a -> (a.x - x) * facing >= 0 && (a.x - x) * facing < 48 && a.y >= y - 36 && a.y <= y + 4))
            soundCues.add(SoundCue.DEFLECT);
    }

    private void hurt(int damage, int direction) {
        if (invulnerable > 0 || dead)
            return;
        health = Math.max(0, health - damage);
        soundCues.add(SoundCue.HURT);
        invulnerable = 20;
        x = Math.max(14, Math.min(WIDTH - 14, x + direction * 10));
        burst(x, y - 20, 0xFFFF6666);
        dead = health == 0;
        if (dead)
            soundCues.add(SoundCue.GAME_OVER);
    }

    private void burst(float px, float py, int color) {
        for (int i = 0; i < 7; i++)
            sparks.add(new Spark(px, py, random, color));
    }
}
