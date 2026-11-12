package net.classicremastered.minecraft.player;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.entity.BossThrownBlock;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.level.itemstack.Equipment;
import net.classicremastered.minecraft.level.itemstack.GravityGunItem;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.level.itemstack.SwordItem;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.model.Vec3D;

public final class DeveloperPowers {
    private boolean enabled;
    private boolean rapidPunch;
    private int nextPunch, nextDodge, dodgeUntil, nextThrow, swordStart, nextSword, swordId;
    private boolean thrust;
    private int punchStart = -100, kickStart = -100, dodgeStart = -100, nextKick;
    private int aimedBlock, aimedSlot;
    private float dodgeSide;
    private boolean leftPunch;
    private boolean showMobHealthBar = true;

    public boolean toggle() {
        enabled = !enabled;
        dodgeUntil = 0;
        swordId = 0;
        aimedBlock = 0;
        punchStart = kickStart = dodgeStart = -100;
        nextPunch = nextDodge = nextThrow = nextKick = nextSword = 0;
        return enabled;
    }

    public boolean active(Player player) {
        return enabled && player.health > 0 && player.minecraft != null && player.minecraft.developer
                && player.level != null && !player.level.isNetworkMode();
    }

    public float movementMultiplier(Player player) {
        return active(player) ? 2.5f : 1f;
    }

    public void throwBlock(Player player) {
        int blockId = aimedBlock;
        boolean ready = aimingBlock(player);
        aimedBlock = 0;
        if (!ready || player.tickCount < nextThrow)
            return;
        if (!player.level.creativeMode && !player.inventory.removeSelected(1))
            return;

        Vec3D aim = aimDirection(player);
        Vec3D velocity = new Vec3D(aim.x * 1.65f, aim.y * 1.65f, aim.z * 1.65f);
        player.level
                .addEntity(new BossThrownBlock(player.level, player, blockId, player.x, player.y, player.z, velocity));
        player.level.playSound("random/bow", player, .65f, .8f);
        nextThrow = player.tickCount + 12;
    }

    public void beginBlockAim(Player player) {
        if (!active(player) || player.tickCount < nextThrow)
            return;
        int selected = player.inventory.getSelected();
        if (selected > 0 && selected < 256 && Block.blocks[selected] != null) {
            aimedBlock = selected;
            aimedSlot = player.inventory.selected;
        }
    }

    public boolean aimingBlock(Player player) {
        return active(player) && aimedBlock > 0 && aimedBlock == player.inventory.getSelected()
                && aimedSlot == player.inventory.selected && player.minecraft.currentScreen == null
                && player.minecraft.hasMouse;
    }

    public void cancelBlockAim() {
        aimedBlock = 0;
    }

    public boolean showMobHealthBar() {
        return showMobHealthBar;
    }

    public void toggleMobHealthBar() {
        showMobHealthBar = !showMobHealthBar;
    }

    public int incomingDamage(Player player, Entity attacker, int damage) {
        boolean mobAttack = attacker instanceof Mob && !(attacker instanceof Player);
        if (attacker instanceof net.classicremastered.minecraft.entity.Arrow arrow)
            mobAttack = arrow.getOwner() instanceof Mob && !(arrow.getOwner() instanceof Player);
        return active(player) && mobAttack && damage > 0 ? Math.max(1, (damage + 3) / 4) : damage;
    }

    public void swingPunch(Player player) {
        if (!fastPunch(player) || player.tickCount < nextPunch)
            return;
        punchStart = player.tickCount;
        leftPunch = !leftPunch;
    }

    public float punchPose(Player player, float partial) {
        return pose(player, punchStart, 5, partial);
    }

    public float kickPose(Player player, float partial) {
        return pose(player, kickStart, 12, partial);
    }

    public float dodgePose(Player player, float partial) {
        return pose(player, dodgeStart, 10, partial);
    }

    private float pose(Player player, int start, int duration, float partial) {
        float progress = (player.tickCount - start + partial) / duration;
        return !active(player) || progress < 0 || progress >= 1 ? 0 : (float) Math.sin(progress * Math.PI);
    }

    public boolean leftPunch() {
        return leftPunch;
    }

    public float dodgeSide() {
        return dodgeSide;
    }

    public void kick(Player player) {
        if (!active(player) || player.tickCount < nextKick)
            return;
        kickStart = player.tickCount;
        nextKick = kickStart + 16;
        Vec3D aim = aimDirection(player);
        Mob closest = null;
        float nearest = 3.5f;
        for (Object entity : player.level.findEntities(player, player.bb.grow(3.5f, 3.5f, 3.5f))) {
            if (!(entity instanceof Mob target) || target.health <= 0 || target.removed)
                continue;
            float cy = (target.bb.y0 + target.bb.y1) * .5f;
            float dx = target.x - player.x, dy = cy - player.y, dz = target.z - player.z;
            float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance >= nearest || dx * aim.x + dy * aim.y + dz * aim.z < distance * .65f)
                continue;
            if (player.level.clip(new Vec3D(player.x, player.y, player.z), new Vec3D(target.x, cy, target.z),
                    true) != null)
                continue;
            closest = target;
            nearest = distance;
        }
        if (closest != null) {
            rapidPunch = true;
            try {
                closest.hurt(player, 10);
                closest.xd += aim.x * 1.2f;
                closest.yd = Math.max(closest.yd, .45f);
                closest.zd += aim.z * 1.2f;
            } finally {
                rapidPunch = false;
            }
        }
    }

    private static Vec3D aimDirection(Player player) {
        return GravityGunItem.look(player);
    }

    public boolean fastPunch(Player player) {
        return active(player) && player.inventory.getSelected() <= 0;
    }

    public boolean isRapidPunch() {
        return rapidPunch;
    }

    public void punch(Player player, Entity target, int damage) {
        if (!fastPunch(player) || player.tickCount < nextPunch)
            return;
        if (punchStart != player.tickCount)
            swingPunch(player);
        nextPunch = player.tickCount + 3;
        rapidPunch = true;
        try {
            target.hurt(player, Math.max(6, damage * 3));
            Vec3D aim = aimDirection(player);
            target.xd += aim.x * .35f;
            target.yd = Math.max(target.yd, .2f);
            target.zd += aim.z * .35f;
        } finally {
            rapidPunch = false;
        }
    }

    public void dodge(Player player, float strafe, float forward) {
        if (!active(player) || player.tickCount < nextDodge || player.isFlying)
            return;
        if (strafe == 0 && forward == 0)
            forward = 1;
        float magnitude = Math.max(1f, (float) Math.hypot(strafe, forward));
        strafe /= magnitude;
        forward /= magnitude;
        float yaw = (float) Math.toRadians(player.yRot);
        float sin = (float) Math.sin(yaw), cos = (float) Math.cos(yaw);
        player.xd = .85f * (strafe * cos - forward * sin);
        player.zd = .85f * (forward * cos + strafe * sin);
        nextDodge = player.tickCount + 20;
        dodgeUntil = player.tickCount + 5;
        dodgeStart = player.tickCount;
        dodgeSide = strafe == 0 ? 1 : Math.signum(strafe);
    }

    public boolean dodging(Player player) {
        return active(player) && player.tickCount < dodgeUntil;
    }

    public static boolean isSword(int raw) {
        int id = raw - 256;
        for (int sword : Equipment.SWORDS)
            if (id == sword)
                return true;
        return id >= 0 && id < Item.items.length && Item.items[id] instanceof SwordItem;
    }

    public void swordAttack(Player player, int selected, boolean thrust) {
        if (player.tickCount < nextSword)
            return;
        this.thrust = thrust;
        swordId = selected;
        swordStart = player.tickCount;
        nextSword = swordStart + 14;

        float reach = thrust ? 4f : 3f;
        Vec3D aim = aimDirection(player);
        Vec3D eye = new Vec3D(player.x, player.y, player.z);
        Mob closest = null;
        float closestDistance = Float.POSITIVE_INFINITY;
        for (Object entity : player.level.findEntities(player, player.bb.grow(reach, reach, reach)).toArray()) {
            if (!(entity instanceof Mob target) || target.health <= 0 || target.removed)
                continue;
            float centerY = (target.bb.y0 + target.bb.y1) * .5f;
            float dx = target.x - eye.x, dy = centerY - eye.y, dz = target.z - eye.z;
            float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > reach || dx * aim.x + dy * aim.y + dz * aim.z < distance * (thrust ? .93 : .42))
                continue;
            if (player.level.clip(new Vec3D(eye.x, eye.y, eye.z), new Vec3D(target.x, centerY, target.z), true) != null)
                continue;
            if (!thrust)
                target.hurt(player, Equipment.damage(selected) + 2);
            else if (distance < closestDistance) {
                closest = target;
                closestDistance = distance;
            }
        }
        if (closest != null)
            closest.hurt(player, Equipment.damage(selected) + 5);
    }

    /** Positive poses are sweeps, negative poses are thrusts. */
    public float swordPose(Player player, int selected, float partial) {
        if (!active(player) || selected != swordId || selected != player.inventory.getSelected())
            return 0;
        float progress = (player.tickCount - swordStart + partial) / (thrust ? 9f : 12f);
        if (progress >= 1)
            return 0;
        float arc = (float) Math.sin(progress * Math.PI);
        return thrust ? -arc : arc;
    }
}
