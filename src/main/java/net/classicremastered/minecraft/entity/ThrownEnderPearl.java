package net.classicremastered.minecraft.entity;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.level.liquid.LiquidType;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.particle.SmokeParticle;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.CameraView;
import net.classicremastered.minecraft.render.ItemMeshRenderer;
import net.classicremastered.minecraft.render.TextureManager;
import net.classicremastered.util.MathHelper;
import org.lwjgl.opengl.GL11;

public class ThrownEnderPearl extends Entity {
    private static final long serialVersionUID = -1856424994218433223L;
    private final Entity owner;
    private int time;
    private transient Vec3D lastSafeLanding;

    public ThrownEnderPearl(Level level, Entity owner, float x, float y, float z, float yaw, float pitch, float speed) {
        super(level);
        this.owner = owner;
        setSize(0.25F, 0.25F);
        setPos(x, y, z);
        xo = x;
        yo = y;
        zo = z;

        float azimuth = -yaw * 0.017453292F - (float) Math.PI;
        float elevation = -pitch * 0.017453292F;
        float horizontalSpeed = MathHelper.cos(elevation) * speed;
        xd = MathHelper.sin(azimuth) * horizontalSpeed;
        yd = MathHelper.sin(elevation) * speed;
        zd = MathHelper.cos(azimuth) * horizontalSpeed;
    }

    @Override
    public void tick() {
        if (++time > 100 || owner == null || owner.removed || owner.level != level) {
            remove();
            return;
        }

        xo = x;
        yo = y;
        zo = z;
        Vec3D start = new Vec3D(x, y, z);
        Vec3D end = start.add(xd, yd, zd);
        AABB sweep = bb.expand(xd, yd, zd);
        if (owner instanceof Player player && level.getCubes(landingBox(player, start)).isEmpty()) {
            lastSafeLanding = start;
        }

        for (Object cube : level.getCubes(sweep)) {
            AABB block = (AABB) cube;
            if (block.contains(start) || block.clip(start, end) != null) {
                teleport();
                return;
            }
        }
        for (Entity target : level.blockMap.getEntities(this, sweep.grow(0.1F, 0.1F, 0.1F))) {
            if (target != owner && target instanceof Mob mob && target.isPickable() && mob.health > 0) {
                AABB hitbox = target.bb.grow(0.1F, 0.1F, 0.1F);
                if (hitbox.contains(start) || hitbox.clip(start, end) != null) {
                    teleport();
                    return;
                }
            }
        }

        setPos(end.x, end.y, end.z);
        float drag = level.containsLiquid(bb, LiquidType.WATER) ? 0.8F : 0.99F;
        xd *= drag;
        yd = yd * drag - 0.03F;
        zd *= drag;
    }

    private static AABB landingBox(Player player, Vec3D feet) {
        float halfWidth = player.bbWidth * 0.5F;
        return new AABB(feet.x - halfWidth, feet.y, feet.z - halfWidth,
                feet.x + halfWidth, feet.y + player.bbHeight, feet.z + halfWidth);
    }

    private void teleport() {
        remove();
        if (level.isNetworkMode() || !(owner instanceof Player player) || player.health <= 0
                || lastSafeLanding == null) {
            return;
        }

        // Use the last clear point on the incoming path, with room for the player's full hitbox.
        AABB landing = landingBox(player, lastSafeLanding);
        if (!level.getCubes(landing).isEmpty()) {
            return;
        }

        spawnTeleportParticles(player.x, player.y, player.z);
        player.ySlideOffset = 0.0F;
        player.setPos(lastSafeLanding.x, lastSafeLanding.y + player.heightOffset, lastSafeLanding.z);
        player.bb = landing;
        player.xo = player.x;
        player.yo = player.y;
        player.zo = player.z;
        player.fallDistance = 0.0F;
        player.onGround = false;
        spawnTeleportParticles(player.x, player.y, player.z);
        level.playSound("random/explode", player, 1.0F, 1.5F);

        if (!level.creativeMode) {
            player.hurt(null, 5);
        }
    }

    private void spawnTeleportParticles(float px, float py, float pz) {
        if (level.particleEngine != null) {
            for (int i = 0; i < 15; i++) {
                float rx = (level.random.nextFloat() - 0.5F) * 1.5F;
                float ry = (level.random.nextFloat() - 0.5F) * 1.5F;
                float rz = (level.random.nextFloat() - 0.5F) * 1.5F;
                level.particleEngine.spawnParticle(new SmokeParticle(level, px + rx, py + ry, pz + rz));
            }
        }
    }

    @Override
    public void render(TextureManager tm, float partial) {
        if (Item.ENDER_PEARL == null) {
            return;
        }

        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(xo + (x - xo) * partial, yo + (y - yo) * partial, zo + (z - zo) * partial);
            if (level.player instanceof Player cameraPlayer) {
                int mode = level.minecraft == null ? 0 : level.minecraft.cameraMode;
                CameraView camera = CameraView.resolve(cameraPlayer, mode, partial);
                GL11.glRotatef(-camera.yaw(), 0.0F, 1.0F, 0.0F);
                GL11.glRotatef(-camera.pitch(), 1.0F, 0.0F, 0.0F);
            }
            GL11.glRotatef(30.0F, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef((time + partial) * 18.0F, 0.0F, 0.0F, 1.0F);
            GL11.glScalef(0.5F, 0.5F, 0.5F);
            ItemMeshRenderer.renderItem(tm, Item.ENDER_PEARL, 1.0F);
        } finally {
            GL11.glPopMatrix();
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}
