package net.classicremastered.minigame;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Fixed 20 Hz simulation, independent of rendering and the normal Minecraft world. */
public final class SteveFighterGame {
    public static final int WIDTH = 426, HEIGHT = 240, FLOOR = 190;
    public enum Kind { ZOMBIE, SKELETON, CREEPER }

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
        public Arrow(float x, float y, float vx) { this.x = x; this.y = y; this.vx = vx; }
    }

    public static final class Spark {
        public float x, y, vx, vy;
        public int life = 12, color;
        Spark(float x, float y, Random random, int color) {
            this.x = x; this.y = y; this.color = color;
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

    public SteveFighterGame(long seed) { random = new Random(seed); }

    public void tick(boolean left, boolean right, boolean jump, boolean attack, boolean dash) {
        if (dead) return;
        ticks++;
        if (attackCooldown > 0) attackCooldown--;
        if (swing > 0) swing--;
        if (invulnerable > 0) invulnerable--;
        if (dashCooldown > 0) dashCooldown--;
        int direction = (right ? 1 : 0) - (left ? 1 : 0);
        if (direction != 0 && dashTicks == 0) facing = direction;
        if (jump && !jumpHeld && y >= FLOOR) vy = -7;
        if (dash && !dashHeld && dashCooldown == 0) {
            dashTicks = 5; dashCooldown = 45; invulnerable = Math.max(invulnerable, 6);
        }
        jumpHeld = jump; dashHeld = dash;
        x += dashTicks > 0 ? facing * 6 : direction * 2.4f;
        if (dashTicks > 0) dashTicks--;
        x = Math.max(14, Math.min(WIDTH - 14, x));
        y += vy; vy += .65f;
        if (y >= FLOOR) { y = FLOOR; vy = 0; }
        if (attack) attack();

        if (intermission > 0) {
            if (--intermission == 0) {
                round++;
                remainingToSpawn = 3 + Math.min(round, 15) * 2;
                spawnCooldown = 0;
            }
        } else if (remainingToSpawn > 0 && --spawnCooldown <= 0) {
            Kind kind = Kind.ZOMBIE;
            if (round >= 2 && random.nextInt(3) == 0) kind = Kind.SKELETON;
            if (round >= 3 && random.nextInt(4) == 0) kind = Kind.CREEPER;
            mobs.add(new Mob(kind, random.nextBoolean() ? 12 : WIDTH - 12, round));
            remainingToSpawn--; spawnCooldown = Math.max(12, 40 - round * 2);
        }

        for (Mob mob : mobs) {
            if (mob.health <= 0) continue;
            if (mob.flash > 0) mob.flash--;
            if (mob.cooldown > 0) mob.cooldown--;
            float distance = x - mob.x;
            int toward = distance >= 0 ? 1 : -1;
            float speed = .65f + Math.min(.6f, round * .04f);
            if (mob.kind == Kind.SKELETON) {
                if (Math.abs(distance) > 130) mob.x += toward * speed;
                else if (Math.abs(distance) < 85) mob.x -= toward * speed;
                if (mob.cooldown == 0) {
                    arrows.add(new Arrow(mob.x, FLOOR - 19, toward * 3.5f));
                    mob.cooldown = Math.max(32, 65 - round);
                }
            } else {
                if (Math.abs(distance) > 15) mob.x += toward * speed;
                if (mob.kind == Kind.CREEPER) {
                    if (Math.abs(distance) < 37 && y > FLOOR - 42) mob.fuse++;
                    else mob.fuse = Math.max(0, mob.fuse - 2);
                    if (mob.fuse >= 24) {
                        burst(mob.x, FLOOR - 15, 0xFFFFC65C);
                        if (Math.abs(distance) < 58 && y > FLOOR - 55) hurt(5, toward);
                        mob.health = 0;
                    }
                } else if (Math.abs(distance) < 20 && y > FLOOR - 27 && mob.cooldown == 0) {
                    hurt(2, toward); mob.cooldown = 25;
                }
            }
            mob.x = Math.max(10, Math.min(WIDTH - 10, mob.x));
        }
        mobs.removeIf(mob -> mob.health <= 0);
        arrows.removeIf(arrow -> {
            arrow.x += arrow.vx;
            if (Math.abs(arrow.x - x) < 11 && arrow.y >= y - 32 && arrow.y <= y) {
                hurt(2, arrow.vx > 0 ? 1 : -1); return true;
            }
            return arrow.x < 0 || arrow.x > WIDTH;
        });
        for (Spark spark : sparks) {
            spark.x += spark.vx; spark.y += spark.vy; spark.vy += .3f; spark.life--;
        }
        sparks.removeIf(spark -> spark.life <= 0);
        if (!dead && intermission == 0 && remainingToSpawn == 0 && mobs.isEmpty()) {
            intermission = 70;
            health = Math.min(20, health + 4);
            score += round * 100;
            arrows.clear();
        }
    }

    public void attack() {
        if (dead || attackCooldown > 0) return;
        attackCooldown = 10; swing = 5;
        for (Mob mob : mobs) {
            float distance = (mob.x - x) * facing;
            if (distance >= -6 && distance < 48 && y > FLOOR - 42 && mob.health > 0) {
                mob.health -= 2; mob.flash = 5; mob.fuse = 0;
                mob.x = Math.max(10, Math.min(WIDTH - 10, mob.x + facing * 14));
                burst(mob.x, FLOOR - 20, 0xFFB9F6FF);
                if (mob.health <= 0) { score += 25; kills++; }
            }
        }
        // Sword swings can also deflect incoming arrows.
        arrows.removeIf(a -> (a.x - x) * facing >= 0 && (a.x - x) * facing < 48
                && a.y >= y - 36 && a.y <= y + 4);
    }

    private void hurt(int damage, int direction) {
        if (invulnerable > 0 || dead) return;
        health = Math.max(0, health - damage);
        invulnerable = 20;
        x = Math.max(14, Math.min(WIDTH - 14, x + direction * 10));
        burst(x, y - 20, 0xFFFF6666);
        dead = health == 0;
    }

    private void burst(float px, float py, int color) {
        for (int i = 0; i < 7; i++) sparks.add(new Spark(px, py, random, color));
    }
}
