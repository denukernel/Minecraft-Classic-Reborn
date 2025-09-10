package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.MovingObjectPosition;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.liquid.LiquidType;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.ai.BasicAttackAI;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.util.MathHelper;

/**
 * ZombieBuilder — “engineer zombie”
 *  • Breaks blocks that block line of sight to the player
 *  • Climbs toward players by placing blocks and jumping
 *  • Places one block per jump; forces itself forward to climb reliably
 */
public class ZombieBuilder extends Zombie {
    public static final long serialVersionUID = 0L;

    // ---- tuning ----
    private static final float AGGRO_RADIUS      = 40f;
    private static final int   BREAK_COOLDOWN    = 6;
    private static final int   PLACE_COOLDOWN    = 6;
    private static final int   MAX_BLOCK_HP      = 40;
    private static final float BREAK_REACH       = 2.2f;
    private static final Block PLACE_BLOCK       = Block.COBBLESTONE;

    // Jump cadence
    private static final int   JUMP_PERIOD_TICKS = 40;   // ~2s
    private static final float JUMP_IMPULSE      = 0.42f;
    private static final float JUMP_FORWARD_PUSH = 0.25f;

    // state
    private int breakCd = 0, placeCd = 0;
    private int breakingX, breakingY, breakingZ, breakingHP;
    private int jumpCd = 0;

    // pose flags (for animation)
    public boolean poseBreaking = false;
    public boolean posePlacing  = false;
    private int poseBreakTimer = 0, posePlaceTimer = 0;

    public ZombieBuilder(Level level, float x, float y, float z) {
        super(level, x, y, z);
        if (this.ai instanceof BasicAttackAI) {
            ((BasicAttackAI)this.ai).bind(level, this);
        }
    }

    private void flagBreakingPose() { poseBreaking = true; posePlaceTimer = 0; poseBreakTimer = 6; }
    private void flagPlacingPose()  { posePlacing  = true; poseBreakTimer = 0; posePlaceTimer = 6; }
    public boolean isBuilderBreaking() { return poseBreaking; }
    public boolean isBuilderPlacing()  { return posePlacing;  }

    @Override
    public void tick() {
        super.tick();

        if (this.level == null || this.health <= 0) return;
        if (breakCd > 0) breakCd--;
        if (placeCd > 0) placeCd--;

        // pose window decay
        if (poseBreakTimer > 0 && --poseBreakTimer == 0) poseBreaking = false;
        if (posePlaceTimer > 0 && --posePlaceTimer == 0) posePlacing  = false;

        if (jumpCd > 0) jumpCd--;

        // No griefing in Creative
        if (this.level.creativeMode) return;

        Player target = this.level.getNearestPlayer(this.x, this.y, this.z, AGGRO_RADIUS);
        if (target == null) return;

        int selfY   = MathHelper.floor(this.y);
        int playerY = MathHelper.floor(target.y);
        int dy      = playerY - selfY;

        final boolean climbingMode = (dy >= 2);

        // suppress random jumping from AI when climbing
        if (this.ai instanceof net.classicremastered.minecraft.mob.ai.BasicAI) {
            ((net.classicremastered.minecraft.mob.ai.BasicAI)this.ai).suppressRandomJump = climbingMode;
        }

        if (climbingMode) {
            doClimbTowards(target);
            return;
        }

        // otherwise break walls
        tryBreakWallToPlayer(target);
    }

    // ========== CLIMB LOGIC ==========
    private void doClimbTowards(Player target) {
        if (!this.onGround || jumpCd > 0) return;

        int fy = MathHelper.floor(this.y);
        int fx = MathHelper.floor(this.x + MathHelper.cos(this.yRot * (float)Math.PI / 180F));
        int fz = MathHelper.floor(this.z + MathHelper.sin(this.yRot * (float)Math.PI / 180F));

        // place one block in front if possible
        if (placeCd == 0 && isAir(fx, fy, fz) && hasSupport(fx, fy, fz)) {
            this.level.setTile(fx, fy, fz, PLACE_BLOCK.id);
            placeCd = PLACE_COOLDOWN;
            flagPlacingPose();
        }

        // force jump forward
        this.yd = JUMP_IMPULSE;
        this.xd += -MathHelper.sin(this.yRot * (float)Math.PI / 180F) * JUMP_FORWARD_PUSH;
        this.zd +=  MathHelper.cos(this.yRot * (float)Math.PI / 180F) * JUMP_FORWARD_PUSH;

        jumpCd = JUMP_PERIOD_TICKS;
    }

    // ========== BREAK LOGIC ==========
    private void tryBreakWallToPlayer(Player target) {
        if (breakCd > 0) return;

        Vec3D from = new Vec3D(this.x, this.y + this.heightOffset, this.z);
        Vec3D to   = new Vec3D(target.x, target.y + target.heightOffset, target.z);
        MovingObjectPosition hit = this.level.clip(from, to);
        if (hit == null) { breakingHP = 0; return; }

        int bx = hit.x, by = hit.y, bz = hit.z;
        float cx = bx + 0.5f, cy = by + 0.5f, cz = bz + 0.5f;
        float dx = cx - this.x, dy = cy - (this.y + this.heightOffset), dz = cz - this.z;
        float dist = MathHelper.sqrt(dx*dx + dy*dy + dz*dz);
        if (dist > BREAK_REACH) return;

        if (!isBreakableSolid(bx, by, bz)) { breakingHP = 0; return; }

        breakCd = BREAK_COOLDOWN;
        int id = this.level.getTile(bx, by, bz);
        Block b = Block.blocks[id];

        if (breakingHP <= 0 || bx != breakingX || by != breakingY || bz != breakingZ) {
            breakingX = bx; breakingY = by; breakingZ = bz;
            int hp = (b != null ? b.getHardness() : 4);
            breakingHP = Math.max(1, Math.min(MAX_BLOCK_HP, (hp <= 0 ? 4 : hp)));
        }

        breakingHP -= 4;
        flagBreakingPose();
        if (this.level.particleEngine != null && b != null) {
            b.spawnBreakParticles(this.level, bx, by, bz, this.level.particleEngine);
        }

        if (breakingHP <= 0) {
            if (b != null) b.onBreak(this.level, bx, by, bz);
            this.level.setTile(bx, by, bz, 0);
            breakingHP = 0;
        }
    }

    // ========== HELPERS ==========
    private boolean isAir(int x, int y, int z) {
        if (!this.level.isInBounds(x, y, z)) return false;
        return this.level.getTile(x, y, z) == 0 && this.level.getLiquid(x, y, z) == LiquidType.NOT_LIQUID;
    }

    private boolean hasSupport(int x, int y, int z) {
        return this.level.isSolidTile(x, y - 1, z)
            || this.level.isSolidTile(x + 1, y, z)
            || this.level.isSolidTile(x - 1, y, z)
            || this.level.isSolidTile(x, y, z + 1)
            || this.level.isSolidTile(x, y, z - 1);
    }

    private boolean isBreakableSolid(int x, int y, int z) {
        if (!this.level.isInBounds(x, y, z)) return false;
        int id = this.level.getTile(x, y, z);
        if (id <= 0) return false;
        Block b = Block.blocks[id];
        if (b == null || !b.isSolid()) return false;
        if (b.getLiquidType() != LiquidType.NOT_LIQUID) return false;
        return b != Block.BEDROCK && b != Block.OBSIDIAN && b != Block.GLASS && b != Block.LEAVES && b != Block.TNT;
    }

    @Override
    public void die(Entity killer) { super.die(killer); }
}
