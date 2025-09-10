package net.classicremastered.minecraft.mob;

import static net.classicremastered.minecraft.mob.ai.BossAttackAI.*;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;

public final class IllusionumBoss extends BossZombieBase {

    private int vanishTimer = 0;  // away/invisible window
    private int flurryTimer = 0;  // rapid hits window
    private int glitchTimer = 0;  // mischief timer (HUD/hotbar)
    private int cloneCD = 0;

    private float parkX, parkY, parkZ;

    public IllusionumBoss(Level l, float x, float y, float z) {
        super(l, x, y, z);
        this.health = 250;
        try { ((net.classicremastered.minecraft.mob.ai.BasicAttackAI)this.ai).runSpeed = 1.05f; } catch (Throwable ignored){}
    }

    @Override protected void onEnrage() {
        cloneCD = 0;
        if (level != null && level.minecraft != null)
            level.minecraft.hud.addChat("&5Illusionum fractures reality!");
    }

    @Override protected void bossTick() {
        Player p = target(this, 20f);
        if (p == null) return;

        if (!enraged && this.health <= 125) { enraged = true; onEnrage(); }

        // Handle vanish / reappear cycle
        if (vanishTimer > 0) {
            vanishTimer--;
            if (vanishTimer == 0) {
                // reappear near target, start flurry
                float dx = (level.random.nextFloat()-0.5f)*4f;
                float dz = (level.random.nextFloat()-0.5f)*4f;
                teleport(p.x + dx, p.y, p.z + dz);
                flurryTimer = enraged ? 100 : 80; // 5s/4s of rapid hits
                if (level.minecraft != null)
                    level.minecraft.hud.addChat("&dIllusionum strikes from nowhere!");
            }
            // during vanish, keep mischief running
            ensureGlitch(20); // extend for 1s slices
            return;
        }

        if (flurryTimer > 0) {
            flurryTimer--;
            // every 5 ticks (0.25s) deal 2 damage and tiny camera jitter
            if ((flurryTimer % 5) == 0) {
                p.hurt(this, 2);
                jitterCamera(p);
            }
            if (flurryTimer == 0) glitchTimer = 0;
            return;
        }

        // Normal cadence: illusions + sometimes vanish
        if (cloneCD <= 0) {
            spawnIllusions( enraged ? 4 : 3 );
            cloneCD = enraged ? 120 : 160;
        } else cloneCD--;

        if (abilityCD <= 0) {
            vanishFor( enraged ? 120 : 100 ); // 6s/5s
            abilityCD = enraged ? 220 : 260;
        } else abilityCD--;
    }

    private void spawnIllusions(int n) {
        if (level == null) return;
        for (int i = 0; i < n; i++) {
            float ox = (level.random.nextFloat() - 0.5f) * 6.0f;
            float oz = (level.random.nextFloat() - 0.5f) * 6.0f;
            level.addEntity(new IllusionClone(level, this.x + ox, this.y, this.z + oz));
        }
        if (level.minecraft != null)
            level.minecraft.hud.addChat("&5Illusionum multiplies!");
    }

    private void vanishFor(int ticks) {
        if (level == null) return;
        parkX = this.x; parkY = this.y; parkZ = this.z;
        teleport(this.x + 1024f, -64f, this.z + 1024f);
        vanishTimer = ticks;
        ensureGlitch(40);
        if (level.minecraft != null)
            level.minecraft.hud.addChat("&7Illusionum vanishes...");
    }

    private void teleport(float x,float y,float z) {
        this.setPos(x, y, z);
        this.xo = x; this.yo = y; this.zo = z;
    }

    private void ensureGlitch(int extendTicks) {
        glitchTimer = Math.max(glitchTimer, extendTicks);
        tryHotbarScramble();
    }

    private void tryHotbarScramble() {
        try {
            if (level != null && level.player instanceof Player) {
                Player p = (Player) level.player;
                if (p.inventory != null && p.inventory.slots != null && p.inventory.slots.length >= 9) {
                    int a = level.random.nextInt(9), b = level.random.nextInt(9);
                    int tmp = p.inventory.slots[a]; p.inventory.slots[a] = p.inventory.slots[b]; p.inventory.slots[b] = tmp;
                    int t2 = p.inventory.count[a];  p.inventory.count[a]  = p.inventory.count[b];  p.inventory.count[b]  = t2;
                }
            }
        } catch (Throwable ignored){}
    }

    private void jitterCamera(Player p) {
        p.yRot += (level.random.nextFloat()-0.5f) * 6f;
        p.xRot += (level.random.nextFloat()-0.5f) * 3f;
    }
}
