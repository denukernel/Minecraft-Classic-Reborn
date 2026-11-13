package net.classicremastered.minecraft;

import net.classicremastered.minecraft.level.itemstack.Equipment;
import net.classicremastered.minecraft.level.itemstack.ToolItem;
import net.classicremastered.minecraft.level.tile.LadderBlock;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.particle.CombatParticle;
import net.classicremastered.minecraft.player.DeveloperPowers;
import net.classicremastered.minecraft.player.Player;

final class PlayerCombat {
    private static final String MELEE_DAMAGE = "modern_melee";
    private static final int[] AXE_DAMAGE = {7, 9, 9, 9, 7};
    private static final float VANILLA_ATTACK_VOLUME = .7f;
    private final Minecraft mc;
    private Player player;
    private int heldItem;
    private int lastAttack;

    PlayerCombat(Minecraft mc) {
        this.mc = mc;
    }

    boolean enabled() {
        return mc.settings != null
                && mc.settings.modernCombat
                && mc.level != null
                && !mc.level.isNetworkMode()
                && !mc.isOnline()
                && mc.player != null;
    }

    void updateHeldItem() {
        if (!enabled()) {
            player = null;
            return;
        }
        int selected = mc.player.inventory.getSelected();
        if (player != mc.player) {
            player = mc.player;
            heldItem = selected;
            lastAttack = mc.ticks - 25;
        } else if (heldItem != selected) {
            heldItem = selected;
            lastAttack = mc.ticks;
        }
    }

    float charge(float partial) {
        if (player != mc.player) return 1;
        return Math.max(
                0, Math.min(1, (mc.ticks - lastAttack + partial) * attackSpeed(heldItem) / 20));
    }

    private static float attackSpeed(int raw) {
        if (DeveloperPowers.isSword(raw)) return 1.6f;
        ToolItem tool = Equipment.miningTool(raw);
        if (tool != null) return tool.kind() == ToolItem.Kind.PICKAXE ? 1.2f : 1.0f;
        return switch (axeTier(raw)) {
            case 0, 1 -> .8f;
            case 2 -> .9f;
            case 3, 4 -> 1.0f;
            default -> 4.0f;
        };
    }

    private static int axeTier(int raw) {
        for (int tier = 0; tier < Equipment.AXES.length; tier++) {
            if (raw == Equipment.AXES[tier] + 256) return tier;
        }
        return -1;
    }

    void miss() {
        updateHeldItem();
        lastAttack = mc.ticks;
        player.level.playSound("combat/miss", player, 1, 1);
    }

    void attack(Entity target) {
        updateHeldItem();
        float charge = charge(.5f);
        lastAttack = mc.ticks;
        boolean charged = charge > .9f;
        boolean running = player.input != null && player.input.running;
        boolean critical = charged && target instanceof Mob && !running && canCrit();
        var gear = Equipment.gear(heldItem);
        int axe = axeTier(heldItem);
        float tool = Equipment.miningToolDamage(heldItem);
        float base =
                tool > 0
                        ? tool
                        : axe >= 0
                                ? AXE_DAMAGE[axe]
                                : gear != null && gear.damage > 0 ? gear.damage : 1;
        float damage = base * (.2f + .8f * charge * charge) * (critical ? 1.5f : 1);
        Mob mob = target instanceof Mob living ? living : null;
        int health = mob == null ? 0 : mob.health;
        target.hurt(player, Math.max(1, Math.round(damage)), MELEE_DAMAGE);
        if (mob != null && mob.health >= health) {
            if (charged) player.level.playSound("combat/miss", player, 1, 1);
            else feedback(target, CombatParticle.Kind.WEAK, "weak", 3);
            return;
        }

        if (critical) {
            feedback(target, CombatParticle.Kind.CRITICAL, "crit", 8);
        } else if (charged && running && mob != null) {
            sprintKnockback(mob);
        } else if (charged
                && mob != null
                && !running
                && player.onGround
                && DeveloperPowers.isSword(heldItem)
                && Math.hypot(player.x - player.xo, player.z - player.zo) < .25) {
            sweep(target);
        } else if (!charged) {
            feedback(target, CombatParticle.Kind.WEAK, "weak", 3);
        } else {
            player.level.playSound("combat/strong", player, 1, 1);
        }
    }

    private boolean canCrit() {
        int x = (int) Math.floor(player.x);
        int y = (int) Math.floor(player.bb.y0);
        int z = (int) Math.floor(player.z);
        return player.fallDistance > 0
                && player.yd < 0
                && !player.onGround
                && !player.isFlying
                && player.riding == null
                && !player.isInWater()
                && !LadderBlock.isLadder(player.level.getTile(x, y, z))
                && !LadderBlock.isLadder(player.level.getTile(x, y + 1, z));
    }

    private void sprintKnockback(Mob target) {
        var direction = player.aimDirection(1);
        double horizontal = Math.hypot(direction.x, direction.z);
        if (horizontal > .001) {
            float boost = (float) (.5 / horizontal);
            target.xd += direction.x * boost;
            target.zd += direction.z * boost;
        }
        player.xd *= .6f;
        player.zd *= .6f;
        feedback(target, CombatParticle.Kind.KNOCKBACK, "knockback", 5);
    }

    private void sweep(Entity target) {
        for (Object nearby :
                new java.util.ArrayList<>(
                        player.level.findEntities(player, target.bb.grow(1, .25f, 1)))) {
            if (nearby instanceof Mob mob
                    && mob != target
                    && mob.health > 0
                    && !mob.removed
                    && mob.isPickable()
                    && withinSweepReach(mob)) {
                mob.hurt(player, 1, MELEE_DAMAGE);
            }
        }
        feedback(target, CombatParticle.Kind.SWEEP, "sweep", 1);
    }

    private boolean withinSweepReach(Mob mob) {
        float x = distanceToInterval(player.x, mob.bb.x0, mob.bb.x1);
        float y = distanceToInterval(player.y, mob.bb.y0, mob.bb.y1);
        float z = distanceToInterval(player.z, mob.bb.z0, mob.bb.z1);
        float reach = mc.gamemode.getReachDistance();
        return x * x + y * y + z * z <= reach * reach;
    }

    private static float distanceToInterval(float position, float min, float max) {
        return Math.max(0, Math.max(min - position, position - max));
    }

    private void feedback(Entity target, CombatParticle.Kind kind, String sound, int count) {
        player.level.playSound("combat/" + sound, player, VANILLA_ATTACK_VOLUME, 1);
        if (player.level.particleEngine == null) return;
        float y = (target.bb.y0 + target.bb.y1) * .5f;
        for (int i = 0; i < count; i++) {
            float spread = count == 1 ? 0 : .65f;
            float x = target.x + (player.level.random.nextFloat() - .5f) * spread;
            float z = target.z + (player.level.random.nextFloat() - .5f) * spread;
            player.level.particleEngine.spawnParticle(
                    new CombatParticle(player.level, x, y, z, kind));
        }
    }
}
