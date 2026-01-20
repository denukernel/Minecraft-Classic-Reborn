package net.classicremastered.minecraft.mob.ai;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.entity.Arrow;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Enderman;
import net.classicremastered.minecraft.particle.SmokeParticle;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.util.MathHelper;

import java.util.*;

public final class EndermanAI extends BasicAI {
    private static final long serialVersionUID = 1L;

    private boolean angry = false;
    private int teleportCooldown = 0;
    // Throttle daylight escape teleports (ticks). 60 = ~3s at 20 tps.
    private int sunTeleportCooldown = 0;

    // Tweak these to tune play-feel:
    private static final float MELEE_REACH = 2.8F; // radial reach from attack origin
    private static final float ATTACK_ORIGIN_Y_OFF = 1.8F; // offset above feet for "hand/torso" origin
    private static final int ARROW_PREDICT_TICKS = 20; // ticks ahead to predict arrows

    // Water damage handling
    private int waterDamageTimer = 0; // ticks until next water damage
    private static final int WATER_DAMAGE_INTERVAL = 20; // 20 ticks = 1s
    private static final int WATER_DAMAGE_AMOUNT = 2; // damage applied each interval

    // Aggressive escape tuning (always try to escape underwater)
    private static final int ESCAPE_RADIUS = 48;
    private static final int ESCAPE_ATTEMPTS = 64;

    // Pathfinding tuning
    private static final int PATH_RADIUS = 24; // search radius (blocks) for A* (local)
    private static final int PATH_MAX_NODES = 800; // cap for A*
    private List<int[]> currentPath = null; // cached path (list of [tx,tz])
    private int pathIndex = 0;
    private static final int MAX_STEP_UP = 1; // max Y difference allowed when walking (classic-ish)

    // GOD-OF-MISS DODGE TUNING (kept from your aggressive dodge)
    private static final float DODGE_RADIUS_MIN = 2.5f;
    private static final float DODGE_RADIUS_MAX = 9.0f;
    private static final int DODGE_ANGLE_STEPS = 16;
    private static final float[] DODGE_HEIGHTS = new float[] { -1.5f, 0.0f, 1.5f, 3.0f };

    // ----------------- CENTRALIZED TELEPORT FX -----------------
    /** Set pos, zero velocity, set cooldown, spawn teleport particles. */
    private void performTeleport(Enderman mob, float cx, float cy, float cz, int cooldown) {
        if (mob == null)
            return;
        mob.setPos(cx, cy, cz);
        mob.xd = mob.yd = mob.zd = 0;

        // unify cooldown + FX
        teleportCooldown = cooldown;
        spawnTeleportParticles(mob);
        // Optional: play a sound here if you expose one (e.g., "enderman.teleport")
    }

    // Explicitly search for a non-burning (shaded) spot.
    private boolean teleportToShade(Enderman mob, int radius, int attempts) {
        if (mob == null || mob.level == null)
            return false;
        Level lvl = mob.level;

        float ox = mob.x, oy = mob.y, oz = mob.z;

        for (int i = 0; i < attempts; i++) {
            int tx = (int) (mob.x + (lvl.random.nextFloat() - 0.5F) * 2f * radius);
            int tz = (int) (mob.z + (lvl.random.nextFloat() - 0.5F) * 2f * radius);
            if (!lvl.isInBounds(tx, 0, tz))
                continue;
            int ty = lvl.getHighestTile(tx, tz);
            if (ty <= 0)
                continue;
            if (!lvl.isSolidTile(tx, ty - 1, tz))
                continue;

            float cx = tx + 0.5F, cy = ty, cz = tz + 0.5F;
            AABB candidate = buildCandidateBB(mob, cx, cy, cz);
            if (candidateIntersectsLiquid(lvl, candidate))
                continue;
            if (!lvl.isFree(candidate))
                continue;
            if (lvl.getCubes(candidate).size() > 0)
                continue;

            // Probe: place, test burn; if safe, commit with proper FX
            mob.setPos(cx, cy, cz);
            if (!lvl.shouldUndeadBurnAt(mob)) {
                performTeleport(mob, cx, cy, cz, 40);
                return true;
            }
        }

        // restore if nothing worked
        mob.setPos(ox, oy, oz);
        return false;
    }

    @Override
    protected void update() {
        super.update();

        if (!(mob instanceof Enderman))
            return;
        Enderman enderman = (Enderman) mob;

        if (level == null || level.player == null)
            return;
        if (!(level.player instanceof Player))
            return;
        Player player = (Player) level.player;

        // --- Cooldowns ---
        if (teleportCooldown > 0)
            teleportCooldown--;
        if (sunTeleportCooldown > 0)
            sunTeleportCooldown--;

        // --- If currently submerged: aggressively try to escape every tick.
        if (enderman.isInWater() || (enderman.bb != null && level.containsAnyLiquid(enderman.bb))) {
            boolean escaped = teleportOutOfWater(enderman, ESCAPE_RADIUS, ESCAPE_ATTEMPTS);
            if (escaped) {
                teleportCooldown = 0;
                waterDamageTimer = 0;
                angry = false;
                attackTarget = null;
                enderman.isAttacking = false;
                running = false;
                currentPath = null;
                return;
            }
            if (--waterDamageTimer <= 0) {
                waterDamageTimer = WATER_DAMAGE_INTERVAL;
                enderman.hurt(null, WATER_DAMAGE_AMOUNT);
            }
            angry = false;
            attackTarget = null;
            enderman.isAttacking = false;
            running = false;
            return;
        } else {
            if (waterDamageTimer < 0)
                waterDamageTimer = 0;
        }

        // --- Daylight burning: damage + shade teleports throttled (~3s) ---
        if (level.shouldUndeadBurnAt(enderman)) {
            enderman.hurt(null, 1);

            if (sunTeleportCooldown == 0) {
                boolean escaped = teleportToShade(enderman, 48, 64);
                if (!escaped) {
                    // One random attempt this cycle as a fallback
                    escaped = teleportRandom(enderman);
                }
                if (escaped) {
                    if (teleportCooldown < 40)
                        teleportCooldown = 40; // respect generic TP cooldown
                    sunTeleportCooldown = 60; // ~3s @ 20 tps
                } else {
                    // Retry sooner if still exposed, but not every tick
                    sunTeleportCooldown = 20; // 1s
                }
            }

            // While escaping sun, skip combat/pathing for this tick
            angry = false;
            attackTarget = null;
            enderman.isAttacking = false;
            running = false;
            currentPath = null;
            return;
        }

        boolean creativePlayer = level.creativeMode || player.creativeInvulnerable;
        if (creativePlayer) {
            angry = false;
            attackTarget = null;
            enderman.isAttacking = false;
            running = false;
            if (teleportCooldown == 0 && level.random.nextInt(200) == 0) {
                teleportRandom(enderman);
            }
            return;
        }

        if (!angry && teleportCooldown == 0 && level.random.nextInt(200) == 0) {
            teleportRandom(enderman);
        }

        if (!angry && isPlayerLookingAtMe(player, enderman)) {
            angry = true;
            // invalidate any walk path when becoming angry
            currentPath = null;
            pathIndex = 0;
        }

        if (!angry) {
            enderman.isAttacking = false;
            attackTarget = null;
            if (teleportCooldown == 0 && level.random.nextInt(200) == 0) {
                teleportRandom(enderman);
            }
            return;
        }

        // === Combat AI ===
        attackTarget = player;

        float dx = player.x - enderman.x;
        float dy = (player.y + player.heightOffset) - (enderman.y + enderman.heightOffset);
        float dz = player.z - enderman.z;
        float dist2 = dx * dx + dy * dy + dz * dz;
        float dist = MathHelper.sqrt(dist2);

        // Face player smoothly
        float targetYaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float dyaw = targetYaw - enderman.yRot;
        while (dyaw < -180.0F)
            dyaw += 360.0F;
        while (dyaw >= 180.0F)
            dyaw -= 360.0F;
        enderman.yRot += dyaw * 0.3F;

        // --- Melee movement ---
        this.running = true;
        this.yya = runSpeed * 1.3F;
        enderman.isAttacking = false;

        if (enderman.attackTime <= 0) {
            if (enderman.bb != null && player.bb != null) {
                float attackOriginY = enderman.y + ATTACK_ORIGIN_Y_OFF;
                float mobMinY = enderman.bb.y0;
                float mobMaxY = enderman.bb.y1;
                if (attackOriginY < mobMinY)
                    attackOriginY = mobMinY + 0.5f;
                if (attackOriginY > mobMaxY)
                    attackOriginY = mobMaxY - 0.1f;

                float closestX = clamp(enderman.x, player.bb.x0, player.bb.x1);
                float closestY = clamp(attackOriginY, player.bb.y0, player.bb.y1);
                float closestZ = clamp(enderman.z, player.bb.z0, player.bb.z1);

                float ddx = enderman.x - closestX;
                float ddy = attackOriginY - closestY;
                float ddz = enderman.z - closestZ;
                float sq = ddx * ddx + ddy * ddy + ddz * ddz;

                if (sq <= MELEE_REACH * MELEE_REACH) {
                    if (!(player instanceof Player && (level.creativeMode || player.creativeInvulnerable))) {
                        player.hurt(enderman, 2);
                        enderman.attackTime = Enderman.ATTACK_DURATION;
                        enderman.isAttacking = true;
                    }
                }
            }
        }

        // --- Arrow dodge (aggressive) ---
        if (enderman.bb != null) {
            List<Entity> nearby = level.findEntities(enderman, enderman.bb.grow(24, 12, 24));
            for (Entity e : nearby) {
                if (!(e instanceof Arrow))
                    continue;
                Arrow arrow = (Arrow) e;
                if (arrow.getOwner() == enderman)
                    continue;
                boolean dodged = tryDodgeArrow(enderman, arrow);
                if (dodged) {
                    teleportCooldown = 0;
                    angry = false;
                    attackTarget = null;
                    enderman.isAttacking = false;
                    running = false;
                    currentPath = null;
                    return;
                }
            }
        }

        // --- Pathfinder: try to walk around water if direct forward would step into
        // water ---
        // Compute normalized XZ direction toward player:
        float dirLen = (float) Math.sqrt(dx * dx + dz * dz);
        float forwardX = dirLen > 1e-5f ? dx / dirLen : 0f;
        float forwardZ = dirLen > 1e-5f ? dz / dirLen : 0f;

        if (wouldWalkIntoWater(enderman, forwardX, forwardZ)) {
            // Attempt to find a path around water (A*) to player's column (tx,tz)
            int startTx = (int) Math.floor(enderman.x);
            int startTz = (int) Math.floor(enderman.z);
            int rawGoalTx = (int) Math.floor(player.x);
            int rawGoalTz = (int) Math.floor(player.z);

            // Choose a nearby dry tile near the player as the path goal to avoid routing
            // into lakes.
            int[] dry = findNearestDryTile(level, rawGoalTx, rawGoalTz, PATH_RADIUS);
            int goalTx = (dry != null) ? dry[0] : rawGoalTx;
            int goalTz = (dry != null) ? dry[2] : rawGoalTz;

            // If we don't have a cached path, find one
            if (currentPath == null || pathIndex >= (currentPath != null ? currentPath.size() : 0)) {
                currentPath = findPathAvoidingWater(enderman, startTx, startTz, goalTx, goalTz, PATH_RADIUS,
                        PATH_MAX_NODES);
                pathIndex = 0;
            }

            if (currentPath != null && pathIndex < currentPath.size()) {
                int[] next = currentPath.get(pathIndex);
                float tx = next[0] + 0.5f;
                float tz = next[1] + 0.5f;
                // steer towards next node
                float sx = tx - enderman.x;
                float sz = tz - enderman.z;
                float slen = (float) Math.sqrt(sx * sx + sz * sz);
                if (slen < 0.4f) {
                    // reached this node -> advance
                    pathIndex++;
                } else {
                    float nx = sx / slen;
                    float nz = sz / slen;
                    // set movement toward node
                    this.running = true;
                    this.yya = runSpeed * 1.2f;
                    // face direction immediately (helps movement)
                    float yaw = (float) (Math.atan2(nz, nx) * 180.0D / Math.PI) - 90.0F;
                    enderman.yRot += (yaw - enderman.yRot) * 0.5f;
                }
                return;
            } else {
                // No path found: fallback to teleport (only if allowed)
                if (teleportCooldown == 0) {
                    teleportNearPlayer(enderman, player);
                    return;
                }
            }
        }

        // --- LOS blocked -> teleport closer (ensuring dry safe spot) ---
        boolean losBlocked = !hasLineOfSight(enderman, player);
        if (losBlocked && teleportCooldown == 0) {
            teleportNearPlayer(enderman, player);
            currentPath = null;
            return;
        }

        // --- Too far -> teleport closer ---
        if (dist > 16F && teleportCooldown == 0) {
            teleportNearPlayer(enderman, player);
            currentPath = null;
            return;
        }

        // Random jitter teleport while close
        if (teleportCooldown == 0 && level.random.nextInt(400) == 0) {
            teleportRandom(enderman);
            currentPath = null;
        }
    }

    // --- PATHFINDER A* implementation (local grid) ---
    private static final class Node {
        int x, z;
        int gx;
        float f;
        Node parent;

        Node(int x, int z) {
            this.x = x;
            this.z = z;
            this.gx = Integer.MAX_VALUE;
            this.f = Float.MAX_VALUE;
            this.parent = null;
        }

        public String key() {
            return x + ":" + z;
        }
    }

    /**
     * Find a path on the XZ grid from start -> goal avoiding liquid and steep
     * steps. Returns a list of [tx,tz] blocks (excluding start) or null if not
     * found.
     */
    private List<int[]> findPathAvoidingWater(Enderman mob, int sx, int sz, int gx, int gz, int radius, int maxNodes) {
        if (mob == null || mob.level == null)
            return null;
        Level lvl = mob.level;

        // quick bounds check
        if (!lvl.isInBounds(gx, 0, gz))
            return null;

        // clamp search box
        int minX = Math.max(0, Math.min(sx, gx) - radius);
        int maxX = Math.min(lvl.width - 1, Math.max(sx, gx) + radius);
        int minZ = Math.max(0, Math.min(sz, gz) - radius);
        int maxZ = Math.min(lvl.height - 1, Math.max(sz, gz) + radius);

        Comparator<Node> cmp = Comparator.comparingDouble(n -> n.f);
        PriorityQueue<Node> open = new PriorityQueue<>(cmp);
        Map<String, Node> all = new HashMap<>();
        Set<String> closed = new HashSet<>();

        Node start = new Node(sx, sz);
        start.gx = 0;
        start.f = heuristic(sx, sz, gx, gz);
        open.add(start);
        all.put(start.key(), start);

        int nodes = 0;
        int[][] neigh = new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { 1, -1 }, { -1, 1 },
                { -1, -1 } }; // 8-neigh

        while (!open.isEmpty() && nodes < maxNodes) {
            Node cur = open.poll();
            if (cur == null)
                break;
            nodes++;
            if (cur.x == gx && cur.z == gz) {
                // reconstruct path
                List<int[]> path = new ArrayList<>();
                Node p = cur;
                while (p != null && !(p.x == sx && p.z == sz)) {
                    path.add(new int[] { p.x, p.z });
                    p = p.parent;
                }
                Collections.reverse(path);
                return path;
            }

            closed.add(cur.key());

            for (int[] d : neigh) {
                int nx = cur.x + d[0];
                int nz = cur.z + d[1];
                if (nx < minX || nx > maxX || nz < minZ || nz > maxZ)
                    continue;
                if (!lvl.isInBounds(nx, 0, nz))
                    continue;

                // candidate must be walkable: highest tile >0, solid below, not liquid at top
                int ny = lvl.getHighestTile(nx, nz);
                if (ny <= 0)
                    continue;
                if (!lvl.isSolidTile(nx, ny - 1, nz))
                    continue;
                // ensure step up/down not too large
                int curY = lvl.getHighestTile(cur.x, cur.z);
                if (Math.abs(ny - curY) > MAX_STEP_UP)
                    continue;

                float cost = (d[0] != 0 && d[1] != 0) ? 1.4142f : 1.0f; // diagonal cost
                String key = nx + ":" + nz;
                if (closed.contains(key))
                    continue;

                // reject if candidate AABB intersects liquid or blocks/entities
                float cx = nx + 0.5f;
                float cz = nz + 0.5f;
                AABB candidate = buildCandidateBB(mob, cx, ny, cz);
                if (candidateIntersectsLiquid(lvl, candidate))
                    continue;
                if (!lvl.isFree(candidate))
                    continue;
                if (lvl.getCubes(candidate).size() > 0)
                    continue;

                int ng = cur.gx + Math.round(cost * 10); // multiply to keep ints
                Node node = all.get(key);
                if (node == null) {
                    node = new Node(nx, nz);
                    all.put(node.key(), node);
                }
                if (ng < node.gx) {
                    node.gx = ng;
                    node.parent = cur;
                    node.f = ng + Math.round(heuristic(nx, nz, gx, gz) * 10);
                    open.remove(node); // remove if present to update priority
                    open.add(node);
                }
            }
        }

        return null;
    }

    private float heuristic(int x, int z, int gx, int gz) {
        float dx = gx - x;
        float dz = gz - z;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    // --- dodge code (same ideas as before) ---
    private boolean tryDodgeArrow(Enderman mob, Arrow arrow) {
        if (mob == null || mob.level == null || arrow == null)
            return false;
        Level lvl = mob.level;

        int maxT = ARROW_PREDICT_TICKS;
        for (int t = 0; t <= maxT; t++) {
            float ax = arrow.x + arrow.xd * t;
            float ay = arrow.y + arrow.yd * t;
            float az = arrow.z + arrow.zd * t;

            float cx = (mob.bb.x0 + mob.bb.x1) * 0.5f;
            float cy = (mob.bb.y0 + mob.bb.y1) * 0.5f;
            float cz = (mob.bb.z0 + mob.bb.z1) * 0.5f;
            float dx = ax - cx;
            float dy = ay - cy;
            float dz = az - cz;
            float dist2 = dx * dx + dy * dy + dz * dz;

            final float THRESH_SQ = 3.5f * 3.5f;
            if (dist2 <= THRESH_SQ || arrow.bb.cloneMove(arrow.xd * t, arrow.yd * t, arrow.zd * t).intersects(mob.bb)) {
                float dirX = arrow.xd;
                float dirY = arrow.yd;
                float dirZ = arrow.zd;
                float mag = MathHelper.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
                if (mag < 1e-4f)
                    mag = 1f;
                dirX /= mag;
                dirY /= mag;
                dirZ /= mag;

                float perpX = -dirZ;
                float perpZ = dirX;
                float perpMag = MathHelper.sqrt(perpX * perpX + perpZ * perpZ);
                if (perpMag < 1e-4f) {
                    perpX = 1f;
                    perpZ = 0f;
                    perpMag = 1f;
                }
                perpX /= perpMag;
                perpZ /= perpMag;

                for (float r = DODGE_RADIUS_MIN; r <= DODGE_RADIUS_MAX; r += (DODGE_RADIUS_MAX - DODGE_RADIUS_MIN)
                        / 4f) {
                    for (int a = 0; a < DODGE_ANGLE_STEPS; a++) {
                        double ang = (2.0 * Math.PI * a) / DODGE_ANGLE_STEPS;
                        float sin = (float) Math.sin(ang), cos = (float) Math.cos(ang);
                        float dxh = perpX * cos - perpZ * sin;
                        float dzh = perpX * sin + perpZ * cos;

                        for (float vh : DODGE_HEIGHTS) {
                            float candX = ax + dxh * r;
                            float candY = ay + vh;
                            float candZ = az + dzh * r;

                            int gx = (int) Math.floor(candX);
                            int gz = (int) Math.floor(candZ);
                            if (!lvl.isInBounds(gx, 0, gz))
                                continue;

                            int gy = lvl.getHighestTile(gx, gz);
                            if (gy <= 0)
                                continue;
                            if (!lvl.isSolidTile(gx, gy - 1, gz))
                                continue;

                            float cxPos = gx + 0.5f;
                            float cyPos = gy;
                            float czPos = gz + 0.5f;

                            AABB candidate = buildCandidateBB(mob, cxPos, cyPos, czPos);
                            if (candidateIntersectsLiquid(lvl, candidate))
                                continue;
                            if (!lvl.isFree(candidate))
                                continue;
                            if (lvl.getCubes(candidate).size() > 0)
                                continue;

                            performTeleport(mob, cxPos, cyPos, czPos, 0);
                            return true;
                        }
                    }
                }

                for (int tries = 0; tries < 8; tries++) {
                    int tx = (int) (mob.x + (lvl.random.nextFloat() - 0.5f) * 32f);
                    int tz = (int) (mob.z + (lvl.random.nextFloat() - 0.5f) * 32f);
                    if (!lvl.isInBounds(tx, 0, tz))
                        continue;
                    int ty = lvl.getHighestTile(tx, tz);
                    if (ty <= 0)
                        continue;
                    if (!lvl.isSolidTile(tx, ty - 1, tz))
                        continue;

                    float cxPos = tx + 0.5f;
                    float cyPos = ty;
                    float czPos = tz + 0.5f;
                    AABB candidate = buildCandidateBB(mob, cxPos, cyPos, czPos);
                    if (candidateIntersectsLiquid(lvl, candidate))
                        continue;
                    if (!lvl.isFree(candidate))
                        continue;
                    if (lvl.getCubes(candidate).size() > 0)
                        continue;

                    performTeleport(mob, cxPos, cyPos, czPos, 0);
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    @Override
    public void hurt(Entity src, int dmg) {
        super.hurt(src, dmg);

        if (src instanceof Player) {
            Player p = (Player) src;
            if (level != null && (level.creativeMode || p.creativeInvulnerable)) {
                return;
            }
        }

        angry = true;

        if (!(mob instanceof Enderman))
            return;
        Enderman enderman = (Enderman) mob;

        if (src instanceof Arrow) {
            if (!enderman.isInWater() && teleportCooldown == 0) {
                teleportRandom(enderman);
                currentPath = null;
            }
        }
    }

    // ----------------- TELEPORT HELPERS -----------------

    private boolean teleportOutOfWater(Enderman mob, int radius, int attempts) {
        if (mob == null || mob.level == null)
            return false;
        Level lvl = mob.level;

        for (int i = 0; i < attempts; i++) {
            int tx = (int) (mob.x + (lvl.random.nextFloat() - 0.5F) * 2f * radius);
            int tz = (int) (mob.z + (lvl.random.nextFloat() - 0.5F) * 2f * radius);
            if (!lvl.isInBounds(tx, 0, tz))
                continue;
            int ty = lvl.getHighestTile(tx, tz);

            if (ty <= 0)
                continue;
            if (!lvl.isSolidTile(tx, ty - 1, tz))
                continue;

            float cx = tx + 0.5F;
            float cz = tz + 0.5F;
            float cy = ty;

            AABB candidate = buildCandidateBB(mob, cx, cy, cz);

            if (candidateIntersectsLiquid(lvl, candidate))
                continue;
            if (!lvl.isFree(candidate))
                continue;
            if (lvl.getCubes(candidate).size() > 0)
                continue;

            performTeleport(mob, cx, cy, cz, 0);
            return true;
        }
        return false;
    }

    private boolean candidateIntersectsLiquid(Level lvl, AABB candidate) {
        if (lvl == null || candidate == null)
            return true;
        return lvl.containsAnyLiquid(candidate);
    }

    private AABB buildCandidateBB(Enderman mob, float cx, float cy, float cz) {
        if (mob.bb == null) {
            float half = 0.6f;
            return new AABB(cx - half, cy, cz - half, cx + half, cy + 2.9f, cz + half);
        }
        float halfX = (mob.bb.x1 - mob.bb.x0) * 0.5f;
        float halfZ = (mob.bb.z1 - mob.bb.z0) * 0.5f;
        float height = mob.bb.y1 - mob.bb.y0;
        float bottomY = cy;
        final float EPS = 0.01f;
        return new AABB(cx - halfX + EPS, bottomY + EPS, cz - halfZ + EPS, cx + halfX - EPS, bottomY + height - EPS,
                cz + halfZ - EPS);
    }

    private boolean teleportNearPlayer(Enderman mob, Player player) {
        if (mob == null || mob.level == null)
            return false;
        Level lvl = mob.level;

        for (int tries = 0; tries < 12; tries++) {
            int tx = (int) (player.x + (lvl.random.nextFloat() - 0.5F) * 16);
            int tz = (int) (player.z + (lvl.random.nextFloat() - 0.5F) * 16);
            if (!lvl.isInBounds(tx, 0, tz))
                continue;
            int ty = lvl.getHighestTile(tx, tz);

            if (ty <= 0)
                continue;
            if (!lvl.isSolidTile(tx, ty - 1, tz))
                continue;

            float cx = tx + 0.5F;
            float cz = tz + 0.5F;
            float cy = ty;

            AABB candidate = buildCandidateBB(mob, cx, cy, cz);

            if (candidateIntersectsLiquid(lvl, candidate))
                continue;
            if (!lvl.isFree(candidate))
                continue;
            if (lvl.getCubes(candidate).size() > 0)
                continue;

            performTeleport(mob, cx, cy, cz, 40);
            return true;
        }
        return false;
    }

    private boolean teleportRandom(Enderman mob) {
        if (mob == null || mob.level == null)
            return false;
        Level lvl = mob.level;

        if (mob.isInWater() || (mob.bb != null && lvl.containsAnyLiquid(mob.bb)))
            return false;

        for (int tries = 0; tries < 16; tries++) {
            int tx = (int) (mob.x + (lvl.random.nextFloat() - 0.5F) * 32F);
            int tz = (int) (mob.z + (lvl.random.nextFloat() - 0.5F) * 32F);
            if (!lvl.isInBounds(tx, 0, tz))
                continue;
            int ty = lvl.getHighestTile(tx, tz);

            if (ty <= 0)
                continue;
            if (!lvl.isSolidTile(tx, ty - 1, tz))
                continue;

            float cx = tx + 0.5F;
            float cz = tz + 0.5F;
            float cy = ty;

            AABB candidate = buildCandidateBB(mob, cx, cy, cz);

            if (candidateIntersectsLiquid(lvl, candidate))
                continue;
            if (!lvl.isFree(candidate))
                continue;
            if (lvl.getCubes(candidate).size() > 0)
                continue;

            performTeleport(mob, cx, cy, cz, 40);
            return true;
        }
        return false;
    }

    private boolean isUnsafeWater(Level lvl, int x, int y, int z) {
        if (lvl == null)
            return true;
        return lvl.isWater(x, y, z) || lvl.isWater(x, y - 1, z) || lvl.isWater(x, y + 1, z);
    }

    private void spawnTeleportParticles(Enderman mob) {
        if (mob.level == null || mob.level.particleEngine == null)
            return;

        float bbWidth = 0.6f;
        float bbHeight = 2.9f;
        try {
            bbWidth = mob.bb != null ? (mob.bb.x1 - mob.bb.x0) * 0.5f : bbWidth;
            bbHeight = mob.bb != null ? (mob.bb.y1 - mob.bb.y0) : bbHeight;
        } catch (Throwable ignored) {
        }

        for (int i = 0; i < 20; i++) {
            float px = mob.x + (mob.level.random.nextFloat() - 0.5f) * bbWidth * 2f;
            float py = mob.y + mob.level.random.nextFloat() * bbHeight;
            float pz = mob.z + (mob.level.random.nextFloat() - 0.5f) * bbWidth * 2f;
            mob.level.particleEngine.spawnParticle(new SmokeParticle(mob.level, px, py, pz));
        }
    }

    // ----------------- helpers -----------------
    private boolean isPlayerLookingAtMe(Player player, Enderman mob) {
        float dx = (float) (mob.x - player.x);
        float dz = (float) (mob.z - player.z);
        float dy = (float) ((mob.y + mob.heightOffset) - (player.y + player.heightOffset));
        float dist = MathHelper.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.0001f)
            return false;
        dx /= dist;
        dy /= dist;
        dz /= dist;

        float yaw = (float) Math.toRadians(player.yRot);
        float pitch = (float) Math.toRadians(player.xRot);
        float lookX = -MathHelper.sin(yaw) * MathHelper.cos(pitch);
        float lookY = -MathHelper.sin(pitch);
        float lookZ = MathHelper.cos(yaw) * MathHelper.cos(pitch);

        float dot = dx * lookX + dy * lookY + dz * lookZ;
        return dot > 0.95F && player.distanceTo(mob) < 64;
    }

    private boolean hasLineOfSight(Entity from, Entity to) {
        if (level == null)
            return false;
        net.classicremastered.minecraft.model.Vec3D a = new net.classicremastered.minecraft.model.Vec3D(from.x, from.y + from.heightOffset,
                from.z);
        net.classicremastered.minecraft.model.Vec3D b = new net.classicremastered.minecraft.model.Vec3D(to.x, to.y + to.heightOffset, to.z);
        return level.clip(a, b) == null;
    }

    private static float clamp(float v, float a, float b) {
        return v < a ? a : (v > b ? b : v);
    }

    /**
     * Find nearest dry spot (highest solid tile) within `radius` of (gx,gz).
     * Returns int[]{tx, ty, tz} (ty = highest tile) or null if none found.
     */
    private int[] findNearestDryTile(Level lvl, int gx, int gz, int radius) {
        if (lvl == null || !lvl.isInBounds(gx, 0, gz))
            return null;

        // spiral/expanding square search — small and deterministic
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                int[] candX = new int[] { gx + dx, gx + dx };
                int[] candZ = new int[] { gz - r, gz + r };
                for (int k = 0; k < 2; k++) {
                    int tx = candX[k];
                    int tz = candZ[k];
                    if (!lvl.isInBounds(tx, 0, tz))
                        continue;
                    int ty = lvl.getHighestTile(tx, tz);
                    if (ty <= 0)
                        continue;
                    // ensure solid ground beneath and not water at top/below
                    if (!lvl.isSolidTile(tx, ty - 1, tz))
                        continue;
                    AABB candidate = buildCandidateBB((Enderman) mob, tx + 0.5f, ty, tz + 0.5f);
                    if (candidateIntersectsLiquid(lvl, candidate))
                        continue;
                    if (!lvl.isFree(candidate))
                        continue;
                    if (lvl.getCubes(candidate).size() > 0)
                        continue;
                    return new int[] { tx, ty, tz };
                }
            }
            for (int dz = -r + 1; dz <= r - 1; dz++) {
                int[] candZ = new int[] { gz + dz, gz + dz };
                int[] candX = new int[] { gx - r, gx + r };
                for (int k = 0; k < 2; k++) {
                    int tx = candX[k];
                    int tz = candZ[k];
                    if (!lvl.isInBounds(tx, 0, tz))
                        continue;
                    int ty = lvl.getHighestTile(tx, tz);
                    if (ty <= 0)
                        continue;
                    if (!lvl.isSolidTile(tx, ty - 1, tz))
                        continue;
                    AABB candidate = buildCandidateBB((Enderman) mob, tx + 0.5f, ty, tz + 0.5f);
                    if (candidateIntersectsLiquid(lvl, candidate))
                        continue;
                    if (!lvl.isFree(candidate))
                        continue;
                    if (lvl.getCubes(candidate).size() > 0)
                        continue;
                    return new int[] { tx, ty, tz };
                }
            }
        }
        return null;
    }

    /**
     * Returns true if stepping forward (for a few steps) would place the mob over
     * water or an unsafe column. This helps Endermen stop at the shoreline instead
     * of stepping in.
     */
    private boolean wouldWalkIntoWater(Enderman mob, float forwardX, float forwardZ) {
        if (mob == null || mob.level == null)
            return true;
        Level lvl = mob.level;

        final float step = 0.6f; // small step to probe immediate cell
        final int lookSteps = 3; // how many steps to scan ahead (tweakable)

        for (int s = 1; s <= lookSteps; s++) {
            float nx = mob.x + forwardX * (step * s);
            float nz = mob.z + forwardZ * (step * s);
            int bx = (int) Math.floor(nx);
            int bz = (int) Math.floor(nz);
            if (!lvl.isInBounds(bx, 0, bz))
                return true;

            int top = lvl.getHighestTile(bx, bz);
            if (top <= 0)
                return true;

            // if the column immediately has water at the top or below, consider unsafe
            if (lvl.isWater(bx, top - 1, bz) || lvl.isWater(bx, top, bz))
                return true;

            // candidate AABB for the mob feet at that column
            AABB candidate = buildCandidateBB(mob, bx + 0.5f, top, bz + 0.5f);
            if (candidateIntersectsLiquid(lvl, candidate))
                return true;
            if (!lvl.isFree(candidate))
                return true;
        }

        return false;
    }
}
