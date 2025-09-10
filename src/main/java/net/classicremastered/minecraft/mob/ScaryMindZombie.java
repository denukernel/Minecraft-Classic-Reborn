package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.ai.BasicAI;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.player.Player;

public class ScaryMindZombie extends Zombie {

    private static final float TRIGGER_RANGE    = 10.0f;
    private static final float TRIGGER_FOV_DEG  = 30.0f;   // camera cone
    private static final int   CONTROL_TICKS    = 60;      // ~3s
    private static final float CONTROL_NUDGE    = 0.08f;
    private static final int   CONTROL_COOLDOWN = 160;     // ~8s

    private int cooldownTicks;

    public ScaryMindZombie(Level level, float x, float y, float z) {
        super(level, x, y, z);
        BasicAI passive = new BasicAI();
        this.ai = passive;
        passive.bind(level, this);
        this.makeStepSound = false;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) return;

        if (cooldownTicks > 0) { cooldownTicks--; return; }

        Player p = level.getNearestPlayer(this.x, this.y, this.z, TRIGGER_RANGE);
        if (p != null && playerAimingAtMyHead(p, TRIGGER_FOV_DEG)) {
            p.startMindControl(p.x, p.y, p.z, CONTROL_TICKS, CONTROL_NUDGE);
            if (p.minecraft != null && p.minecraft.hud != null) {
                p.minecraft.hud.addChat("&5[MC]&7 control start (" + CONTROL_TICKS + ")");
            }
            cooldownTicks = CONTROL_COOLDOWN;
        }
    }

    @Override
    public void die(Entity killer) { super.die(killer); }

    // precise eye-contact with head (yaw+pitch, LOS, FOV cone)
    private boolean playerAimingAtMyHead(Player p, float fovDeg) {
        if (p == null || level == null) return false;

        final float RAD = (float)Math.PI / 180.0f;
        float pyaw   = p.yRot * RAD;
        float ppitch = p.xRot * RAD;

        float lookX = -net.classicremastered.util.MathHelper.sin(pyaw) * net.classicremastered.util.MathHelper.cos(ppitch);
        float lookY = -net.classicremastered.util.MathHelper.sin(ppitch);
        float lookZ =  net.classicremastered.util.MathHelper.cos(pyaw) * net.classicremastered.util.MathHelper.cos(ppitch);

        Vec3D eye  = new Vec3D(p.x, p.y + p.heightOffset, p.z);
        Vec3D head = new Vec3D(this.x, this.y + this.heightOffset, this.z);

        if (level.clip(eye, head) != null) return false; // blocked

        float dx = (float)(head.x - eye.x);
        float dy = (float)(head.y - eye.y);
        float dz = (float)(head.z - eye.z);
        float len = net.classicremastered.util.MathHelper.sqrt(dx*dx + dy*dy + dz*dz);
        if (len <= 0.0001f || len > TRIGGER_RANGE) return false;

        dx /= len; dy /= len; dz /= len;
        float dot = dx*lookX + dy*lookY + dz*lookZ; // cos(angle)
        float cosMax = (float)Math.cos(fovDeg * RAD * 0.5f);
        return dot >= cosMax;
    }
}
