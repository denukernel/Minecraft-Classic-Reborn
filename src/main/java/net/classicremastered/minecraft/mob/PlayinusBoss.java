package net.classicremastered.minecraft.mob;

import static net.classicremastered.minecraft.mob.ai.BossAttackAI.*;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.player.Player;

public final class PlayinusBoss extends BossZombieBase {

    // Cooldowns
    private int cloneCD = 0;   // spawn fake-player clones
    private int wipeCD  = 0;   // inventory wipe
    private int rageCD  = 0;   // periodic rage damage
    private int maxClones = 5;

    public PlayinusBoss(Level l, float x, float y, float z) {
        super(l, x, y, z);
        this.health = 300;
        try {
            ((net.classicremastered.minecraft.mob.ai.BasicAttackAI)this.ai).runSpeed = 0.9f;
        } catch (Throwable ignored) {}
    }

    @Override protected void onEnrage() {
        rageCD = 0;
        if (level != null && level.minecraft != null) {
            level.minecraft.hud.addChat("&4Playinus enraged!");
        }
    }

    @Override
    protected void bossTick() {
        Player p = target(this, 18f);
        if (p == null) return;

        face(this, p.x, p.z, 5f);

        // Early enrage at 50% HP
        if (!enraged && this.health <= 150) { enraged = true; onEnrage(); }

        // 1) Spawn fake players around boss
        if (cloneCD <= 0) {
            int existing = countClones(10f);
            int toSpawn = Math.max(0, maxClones - existing);
            for (int i = 0; i < toSpawn; i++) spawnCloneNear(3.0f);
            cloneCD = 160; // ~8s
        } else cloneCD--;

        // 2) Inventory wipe (items only), keep sword/bow
        if (wipeCD <= 0) {
            eraseItemsExceptSwordBow(p);
            wipeCD = 220; // ~11s
        } else wipeCD--;

        // 3) Rage tick: +3 damage every 10s
        if (enraged) {
            if (rageCD <= 0) {
                p.hurt(this, 3);
                rageCD = 200;
                if (level != null && level.minecraft != null) {
                    level.minecraft.hud.addChat("&8[Rage]&7 Playinus strikes your mind!");
                }
            } else rageCD--;
        }
    }

    private int countClones(float r) {
        if (level == null || level.blockMap == null || level.blockMap.all == null) return 0;
        int c = 0;
        for (int i = 0; i < level.blockMap.all.size(); i++) {
            Object o = level.blockMap.all.get(i);
            if (o instanceof FakePlayerClone) {
                FakePlayerClone f = (FakePlayerClone)o;
                float dx = f.x - this.x, dy = f.y - this.y, dz = f.z - this.z;
                if (dx*dx+dy*dy+dz*dz <= r*r) c++;
            }
        }
        return c;
    }

    private void spawnCloneNear(float radius) {
        if (level == null) return;
        float ox = (level.random.nextFloat() - 0.5f) * 2f * radius;
        float oz = (level.random.nextFloat() - 0.5f) * 2f * radius;
        level.addEntity(new FakePlayerClone(level, this.x + ox, this.y, this.z + oz));
    }

    private void eraseItemsExceptSwordBow(Player p) {
        if (p == null || p.inventory == null) return;
        int[] slots = p.inventory.slots;
        int[] cnt   = p.inventory.count;
        if (slots == null || cnt == null) return;

        for (int i = 0; i < slots.length; i++) {
            int id = slots[i];
            if (id >= 256) { // items only (blocks < 256)
                int itemId = id - 256;
                if (itemId >= 0 && itemId < Item.items.length) {
                    Item it = Item.items[itemId];
                    String name = (it != null && it.name != null) ? it.name.toLowerCase(java.util.Locale.ROOT) : "";
                    boolean keep = name.contains("sword") || name.contains("bow");
                    if (!keep) {
                        slots[i] = -1;
                        cnt[i] = 0;
                    }
                } else {
                    slots[i] = -1; cnt[i] = 0;
                }
            }
        }
        if (level != null && level.minecraft != null) {
            level.minecraft.hud.addChat("&cPlayinus scrambled your inventory!");
        }
    }
}
