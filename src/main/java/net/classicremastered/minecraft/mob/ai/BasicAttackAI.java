package net.classicremastered.minecraft.mob.ai;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.item.Arrow;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.util.MathHelper;

public class BasicAttackAI extends BasicAI {

    public static final long serialVersionUID = 0L;
    public int damage = 6;

    // --- Detour (strafe) state ---
    private boolean detouring = false;
    private int detourTicks = 0;
    private int detourMaxTicks = 20; // ~1s at 20 TPS per attempt
    private int detourDir = 1; // +1 = right strafe, -1 = left
    private int detourCooldown = 0; // wait before trying the opposite side again

    // --- Stuck / micro-path state ---
    private int stuckTicks = 0;
    private float lastProgressX, lastProgressZ;

    // Waypoint from micro BFS (grid cell) and time-to-live
    private int wpX = Integer.MIN_VALUE, wpY = Integer.MIN_VALUE, wpZ = Integer.MIN_VALUE;
    private int wpTTL = 0;

    // ---- runmul helpers (scale both movement and turning) ----
    private static float clampf(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }
    private float yawStep()   { return 12f * clampf(this.runMultiplier, 0.5f, 2.0f); }   // per-tick yaw
    private float pitchStep() { return  8f * clampf(this.runMultiplier, 0.5f, 1.5f); }   // per-tick pitch

    // Per-state run multipliers (tweak to taste)
    private static final float CHASE_RUNMUL    = 1.10f; // straight chase
    private static final float WAYPOINT_RUNMUL = 1.55f; // micro-path follow
    private static final float DETOUR_RUNMUL   = 1.40f; // strafe detour
    private static final float WANDER_RUNMUL   = 1.00f; // idle/wander

    @Override
    protected void update() {
        super.update();

        if (this.mob == null || this.level == null) return;
        if (this.mob.health <= 0) return;

        // If Creative → no aggression
        if (this.level.creativeMode) {
            this.attackTarget = null;
            this.running = false;
            this.jumping = false;
            this.runMultiplier = WANDER_RUNMUL;
            this.xxa *= 0.90F;
            this.yya *= 0.90F;
            return;
        }

        // If the world's player is dead, clear target and idle
        net.classicremastered.minecraft.player.Player lp =
                (this.level.player instanceof net.classicremastered.minecraft.player.Player)
                        ? (net.classicremastered.minecraft.player.Player) this.level.player
                        : null;
        if (lp != null && lp.health <= 0) {
            this.attackTarget = null;
            this.running = false;
            this.jumping = false;
            this.runMultiplier = WANDER_RUNMUL;
            this.xxa = 0.0F;
            this.yya = 0.0F;
            return;
        }

        // Drop invalid/removed or dead target
        if (this.attackTarget != null && (!isAliveEntity(this.attackTarget) || this.attackTarget.removed)) {
            this.attackTarget = null;
            this.running = false;
            this.jumping = false;
            this.runMultiplier = WANDER_RUNMUL;
        }

        this.doAttack();

        if (this.attackTarget == null) {
            // when target lost, fall back to wander defaults
            this.running = false;
            this.jumping = false;
            this.runMultiplier = WANDER_RUNMUL;
        }
    }

    /** True if the entity is alive (Player/Mob) and not removed. */
    private static boolean isAliveEntity(Entity e) {
        if (e == null) return false;
        if (e instanceof net.classicremastered.minecraft.player.Player) {
            return ((net.classicremastered.minecraft.player.Player) e).health > 0 && !e.removed;
        }
        if (e instanceof net.classicremastered.minecraft.mob.Mob) {
            return ((net.classicremastered.minecraft.mob.Mob) e).health > 0 && !e.removed;
        }
        return !e.removed;
    }

    public void bind(net.classicremastered.minecraft.level.Level level, net.classicremastered.minecraft.mob.Mob mob) {
        this.level = level;
        this.mob = mob;
    }
    /** Probe nearby for a 1-block step that reduces vertical delta and heads toward the target. */
    private boolean scanForLocalStepUpTowardTarget(int radius) {
        if (this.mob == null || this.attackTarget == null || this.level == null) return false;

        int sx = fi(mob.x), sy = fi(mob.y), sz = fi(mob.z);
        float best = Float.MAX_VALUE;
        int bx = 0, by = 0, bz = 0;

        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx == 0 && dz == 0) continue;
                int tx = sx + dx, tz = sz + dz;

                // “step up” cell: feet blocked, head clear, and the destination at y+1 is walkable
                if (solid(tx, sy, tz) && empty(tx, sy + 1, tz) && walkable(tx, sy + 1, tz)) {
                    // score: bias toward reducing vertical gap and moving nearer horizontally
                    float dh = Math.abs(this.attackTarget.y - (sy + 1));
                    float hx = Math.abs(this.attackTarget.x - (tx + 0.5f));
                    float hz = Math.abs(this.attackTarget.z - (tz + 0.5f));
                    float score = dh * 1.5f + (hx + hz);
                    if (score < best) {
                        best = score;
                        bx = tx; by = sy + 1; bz = tz;
                    }
                }
            }
        }

        if (best < Float.MAX_VALUE) {
            wpX = bx; wpY = by; wpZ = bz;
            wpTTL = 40; // ~2 seconds
            return true;
        }
        return false;
    }

    protected void doAttack() {
        if (this.level == null || this.mob == null) return;

        // If the world's player is dead, do not attack or acquire
        net.classicremastered.minecraft.player.Player lp =
                (this.level.player instanceof net.classicremastered.minecraft.player.Player)
                        ? (net.classicremastered.minecraft.player.Player) this.level.player
                        : null;
        if (lp != null && lp.health <= 0) {
            this.attackTarget = null;
            this.runMultiplier = WANDER_RUNMUL;
            return;
        }

        Entity player = this.level.getPlayer();
        float range = 16.0F;

        // Drop invalid/removed or dead target
        if (this.attackTarget != null && (!isAliveEntity(this.attackTarget) || this.attackTarget.removed)) {
            this.attackTarget = null;
        }

        // Acquire target if none (only if alive)
        if (player != null && this.attackTarget == null && isAliveEntity(player)) {
            float dx0 = player.x - this.mob.x;
            float dy0 = player.y - this.mob.y;
            float dz0 = player.z - this.mob.z;
            if (dx0 * dx0 + dy0 * dy0 + dz0 * dz0 < range * range) {
                this.attackTarget = player;
                lastProgressX = mob.x;
                lastProgressZ = mob.z;
                stuckTicks = 0;
            }
        }
        if (this.attackTarget == null) {
            this.runMultiplier = WANDER_RUNMUL;
            return;
        }

        // Distance
        float dx = this.attackTarget.x - this.mob.x;
        float dy = this.attackTarget.y - this.mob.y;
        float dz = this.attackTarget.z - this.mob.z;
        float d2 = dx * dx + dy * dy + dz * dz;
        float dist = MathHelper.sqrt(d2);

        // Occasionally drop very far targets
        if (d2 > range * range * 4.0F && this.random.nextInt(100) == 0) {
            this.attackTarget = null;
            this.runMultiplier = WANDER_RUNMUL;
            return;
        }

        // --- aim at target (smooth, with runmul-scaled steps) ---
        float targetYaw   = desiredYawTo(this.mob, this.attackTarget);
        float targetPitch = desiredPitchTo(this.mob, this.attackTarget);
        this.mob.yRot = approachAngle(this.mob.yRot, targetYaw,  yawStep());
        this.mob.xRot = approachAngle(this.mob.xRot, targetPitch, pitchStep());
        if (this.mob.xRot < -89f) this.mob.xRot = -89f;
        if (this.mob.xRot >  89f) this.mob.xRot =  89f;

        boolean los = hasLineOfSight(this.mob, this.attackTarget);
        boolean targetAbove = (this.attackTarget.y - this.mob.y) > 1.25f;

        // Stuck detection (no lateral progress while far)
        boolean stuck = updateStuck1(dist);

        // If LoS blocked, try a simple 1-block step jump immediately
        if (!los) tryStepUpFront1();

        // Melee if close + LoS + off cooldown (only if target is alive)
        if (dist < 2.0F && los && this.attackDelay == 0 && isAliveEntity(this.attackTarget)) {
            this.attack(this.attackTarget);
            return;
        }

        // Follow waypoint if active
        if (wpTTL > 0 && followWaypoint()) {
            this.runMultiplier = WAYPOINT_RUNMUL;
            this.running = true;
            return;
        }

        // Plan a new waypoint when blocked or stuck
        if (!los || stuck) {
            int tx = fi(attackTarget.x), ty = fi(attackTarget.y), tz = fi(attackTarget.z);
            if (planMicroWaypointToward(tx, ty, tz, targetAbove)) {
                this.runMultiplier = WAYPOINT_RUNMUL;
                this.running = true;
                followWaypoint();
                return;
            }
            if (targetAbove && scanForLocalStepUpTowardTarget(3)) {
                this.runMultiplier = WAYPOINT_RUNMUL;
                this.running = true;
                followWaypoint();
                return;
            }
        }

        // Fallback movement
        if (los) {
            // straight chase
            detourTicks = 0;
            this.jumping = false;
            this.running = true;
            this.runMultiplier = CHASE_RUNMUL;
            this.xxa = 0.0f;
            this.yya = 1.0f;
        } else {
            // start or continue short strafe detour
            if (!detouring && detourCooldown == 0) {
                detouring = true;
                detourTicks = 0;
                detourDir = (detourDir == 0) ? 1 : -detourDir; // alternate side
            }
            if (detouring) {
                this.runMultiplier = DETOUR_RUNMUL;
                runDetourToward(this.attackTarget);
            } else {
                // slow creep while waiting to retry
                this.xxa = 0f;
                this.yya = 0.4f;
                this.running = false;
                this.runMultiplier = WANDER_RUNMUL;
                if (detourCooldown > 0) detourCooldown--;
            }
        }
    }

    public boolean attack(Entity target) {
        if (this.level == null || this.mob == null) return false;
        if (this.level.creativeMode) return false;
        if (!hasLineOfSight(this.mob, target)) return false;

        this.mob.attackTime = 5;
        this.attackDelay = this.random.nextInt(20) + 10;

        int dmgRoll = (int)((this.random.nextFloat() + this.random.nextFloat()) / 2.0F * (float)this.damage + 1.0F);
        target.hurt(this.mob, dmgRoll);
        this.noActionTime = 0;
        return true;
    }

    @Override
    public void hurt(Entity src, int dmg) {
        super.hurt(src, dmg);
        if (this.level == null || this.mob == null) return;
        if (this.level.creativeMode) return;

        if (src instanceof Arrow) {
            src = ((Arrow)src).getOwner();
        }
        if (src != null && !src.getClass().equals(this.mob.getClass())) {
            this.attackTarget = src;
            lastProgressX = mob.x;
            lastProgressZ = mob.z;
            stuckTicks = 0;
        }
    }

    // ---------------- helpers ----------------

    private static int fi(float f) { return (int)Math.floor(f); }

    private boolean solid(int x,int y,int z){ return level.getTile(x,y,z) != 0; }
    private boolean empty(int x,int y,int z){ return level.getTile(x,y,z) == 0; }

    private boolean hasLineOfSight(Entity from, Entity to) {
        if (from == null || to == null || level == null) return false;
        float fromEyeY = from.y + (from.bbHeight > 0 ? from.bbHeight * 0.85f : 0.9f);
        float toMidY   = to.y   + (to.bbHeight   > 0 ? to.bbHeight   * 0.50f : 0.5f);
        Vec3D a = new Vec3D(from.x, fromEyeY, from.z);
        Vec3D b = new Vec3D(to.x,   toMidY,   to.z);
        return level.clip(a, b) == null;
    }

    private float wrapDegrees(float ang) {
        while (ang <= -180f) ang += 360f;
        while (ang >   180f) ang -= 360f;
        return ang;
    }

    /** Cell is walkable for a 1x2 mob: feet empty, head empty, and has ground (or <=1 drop). */
    private boolean walkable(int x,int y,int z){
        if (!empty(x,y,z) || !empty(x,y+1,z)) return false;
        if (solid(x,y-1,z)) return true;
        return solid(x,y-2,z); // allow 1-block drop
    }

    /** True if moving into (x,z) at y would fall more than 1 block. */
    private boolean wouldFallMoreThan1(int x,int y,int z){
        if (solid(x,y-1,z)) return false;
        if (solid(x,y-2,z)) return false;
        return true;
    }

    /** If block at feet ahead and head is clear → jump (simple 1-block step). */
    private void tryStepUpFront1(){
        int fx = fi(mob.x + MathHelper.cos(mob.yRot * 3.1415927F / 180.0F));
        int fz = fi(mob.z + MathHelper.sin(mob.yRot * 3.1415927F / 180.0F));
        int y  = fi(mob.y);
        if (solid(fx, y, fz) && empty(fx, y+1, fz)) this.jumping = true;
    }

    /** Simple stuck detector: no lateral progress while far from target. */
    private boolean updateStuck1(float distToTarget){
        float dx = mob.x - lastProgressX, dz = mob.z - lastProgressZ;
        float prog2 = dx*dx + dz*dz;
        if (distToTarget > 2.5f && prog2 < 0.0025f) {
            if (++stuckTicks > 20) return true; // ~1s
        } else {
            stuckTicks = 0;
            lastProgressX = mob.x; lastProgressZ = mob.z;
        }
        return false;
    }

    /** Micro BFS in a small bubble to find next best 1×2 cell toward (tx,ty,tz). Upward moves are prioritized when targetAbove = true. */
    private boolean planMicroWaypointToward(int tx, int ty, int tz, boolean targetAbove){
        int sx = fi(mob.x), sy = fi(mob.y), sz = fi(mob.z);

        final int RX=8, RY=3, RZ=8;
        final int W = RX*2+1, H = RY*2+1, D = RZ*2+1;
        final int MAX = W*H*D;

        int[] qx = new int[MAX];
        int[] qy = new int[MAX];
        int[] qz = new int[MAX];
        boolean[] vis = new boolean[MAX];
        int[] parent = new int[MAX];

        int head=0, tail=0;

        int sidx = (0+RX) + (0+RY)*W + (0+RZ)*W*H;
        qx[tail]=sx; qy[tail]=sy; qz[tail]=sz; parent[tail]=-1; vis[sidx]=true; tail++;

        final int[][] DIRS_UP = {
            { 0, 1, 0},{ 1, 1, 0},{-1, 1, 0},{ 0, 1, 1},{ 0, 1,-1},
            { 1, 0, 0},{-1, 0, 0},{ 0, 0, 1},{ 0, 0,-1},
            { 1,-1, 0},{-1,-1, 0},{ 0,-1, 1},{ 0,-1,-1}
        };
        final int[][] DIRS_FLAT = {
            { 1, 0, 0},{-1, 0, 0},{ 0, 0, 1},{ 0, 0,-1},
            { 0, 1, 0},{ 1, 1, 0},{-1, 1, 0},{ 0, 1, 1},{ 0, 1,-1},
            { 1,-1, 0},{-1,-1, 0},{ 0,-1, 1},{ 0,-1,-1}
        };
        final int[][] DIRS = targetAbove ? DIRS_UP : DIRS_FLAT;

        int bestI = -1; float bestH = Float.MAX_VALUE;
        int budget = 1200;
        while (head < tail && budget-- > 0){
            int cx= qx[head], cy= qy[head], cz= qz[head];

            float h = Math.abs(tx - cx) + Math.abs(ty - cy) + Math.abs(tz - cz);
            if (h < bestH) { bestH = h; bestI = head; if (h <= 0.5f) break; }

            for (int i=0;i<DIRS.length;i++){
                int nx = cx + DIRS[i][0];
                int ny = cy + DIRS[i][1];
                int nz = cz + DIRS[i][2];

                if (nx < sx-RX || nx > sx+RX || nz < sz-RZ || nz > sz+RZ || ny < sy-RY || ny > sy+RY) continue;

                int ix = nx - sx, iy = ny - sy, iz = nz - sz;
                int id = (ix+RX) + (iy+RY)*W + (iz+RZ)*W*H;
                if (vis[id]) continue;

                if (!walkable(nx, ny, nz)) continue;
                if (wouldFallMoreThan1(nx, ny, nz)) continue;

                qx[tail]=nx; qy[tail]=ny; qz[tail]=nz;
                parent[tail]=head;
                vis[id]=true;
                tail++;
                if (tail>=MAX) break;
            }
            head++;
        }

        if (bestI < 0) return false;

        // unwind to get next step from start
        int cur = bestI, par = parent[cur];
        while (par >= 0 && parent[par] >= 0) { cur = par; par = parent[cur]; }
        int nx = qx[cur], ny = qy[cur], nz = qz[cur];

        if (nx == sx && ny == sy && nz == sz) return false;

        wpX = nx; wpY = ny; wpZ = nz;
        wpTTL = 40; // ~2s
        return true;
    }

    private static final float RAD_TO_DEG = 57.2957795f;
    private static final boolean PITCH_UP_IS_NEGATIVE = false;

    private float desiredYawTo(Entity from, Entity to) {
        float dx = to.x - from.x;
        float dz = to.z - from.z;
        return (float)(Math.atan2((double)dz, (double)dx) * RAD_TO_DEG) - 90.0f;
    }

    private float desiredPitchTo(Entity from, Entity to) {
        float fromEyeY = from.y + (from.bbHeight > 0 ? from.bbHeight * 0.85f : 0.9f);
        float toMidY   = to.y   + (to.bbHeight   > 0 ? to.bbHeight   * 0.50f : 0.5f);
        float dy = toMidY - fromEyeY;
        float dx = to.x - from.x;
        float dz = to.z - from.z;
        float horiz = MathHelper.sqrt(dx * dx + dz * dz);
        float pitch = (float)(Math.atan2((double)dy, (double)horiz) * RAD_TO_DEG); // POSITIVE = UP
        return PITCH_UP_IS_NEGATIVE ? -pitch : pitch;
    }

    private float approachAngle(float current, float target, float maxStep) {
        float d = wrapDegrees(target - current);
        if (d >  maxStep) d =  maxStep;
        if (d < -maxStep) d = -maxStep;
        return current + d;
    }

    /** Probe nearby for a 1-block step that reduces vertical delta and heads toward target. */
    private void tryStepUpFront(){
        int fx = fi(mob.x + MathHelper.cos(mob.yRot * 3.1415927F / 180.0F));
        int fz = fi(mob.z + MathHelper.sin(mob.yRot * 3.1415927F / 180.0F));
        int y  = fi(mob.y);
        if (solid(fx, y, fz) && empty(fx, y+1, fz)) this.jumping = true;
    }

    /** Simple stuck detector: no lateral progress while far from target. */
    private boolean updateStuck(float distToTarget){
        float dx = mob.x - lastProgressX, dz = mob.z - lastProgressZ;
        float prog2 = dx*dx + dz*dz;
        if (distToTarget > 2.5f && prog2 < 0.0025f) {
            if (++stuckTicks > 20) return true; // ~1s
        } else {
            stuckTicks = 0;
            lastProgressX = mob.x; lastProgressZ = mob.z;
        }
        return false;
    }

    /** Follow active waypoint. */
    private boolean followWaypoint(){
        if (wpTTL <= 0) return false;
        wpTTL--;

        if (!walkable(wpX, wpY, wpZ)) { wpTTL = 0; return false; }

        float dx = (wpX + 0.5f) - mob.x;
        float dz = (wpZ + 0.5f) - mob.z;
        float desiredYaw = (float)(Math.atan2((double)dz, (double)dx) * 180.0D / Math.PI) - 90.0F;
        float dyaw = desiredYaw - mob.yRot;
        while (dyaw <= -180) dyaw += 360;
        while (dyaw > 180)   dyaw -= 360;
        float clamp = yawStep();
        mob.yRot += MathHelper.clamp(dyaw, -clamp, clamp);

        // step up if needed
        tryStepUpFront1();

        this.xxa = 0f;
        this.yya = 1.0f;
        this.running = true;              // Mob.travel will apply runMultiplier
        this.runMultiplier = WAYPOINT_RUNMUL;

        if (Math.abs(mob.x - (wpX + 0.5f)) < 0.2f && Math.abs(mob.z - (wpZ + 0.5f)) < 0.2f) {
            wpTTL = 0;
        }
        return true;
    }

    /** Short left/right detour while facing target. */
    private void runDetourToward(Entity target) {
        float dx = target.x - mob.x;
        float dz = target.z - mob.z;
        float desiredYaw = (float)(Math.atan2((double)dz, (double)dx) * 180.0D / Math.PI) - 90.0F;
        float dyaw = wrapDegrees(desiredYaw - mob.yRot);
        float maxTurn = yawStep();
        mob.yRot += MathHelper.clamp(dyaw, -maxTurn, maxTurn);

        float yawRad = mob.yRot * (float)Math.PI / 180f;
        float strafeX =  (float)Math.cos(yawRad) * detourDir;
        float strafeZ =  (float)Math.sin(yawRad) * detourDir;

        float step = 0.6f;
        float aheadX = mob.x + strafeX * step;
        float aheadZ = mob.z + strafeZ * step;

        int ax = fi(aheadX), ay = fi(mob.y), az = fi(aheadZ);
        if (wouldFallMoreThan1(ax, ay, az)) {
            detourDir = -detourDir;
            detourTicks = detourMaxTicks; // force timeout
            return;
        }

        if (solid(ax, ay, az) && empty(ax, ay+1, az)) {
            this.jumping = true;
        }

        this.xxa = detourDir * 0.9f;
        this.yya = 0.3f;
        this.running = true;
        this.runMultiplier = DETOUR_RUNMUL;

        if (++detourTicks >= detourMaxTicks) {
            detouring = false;
            detourCooldown = 10;
        }
    }
}
