package net.classicremastered.minecraft.path;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Mob;

import java.util.*;

/** Bounded A* for grounded mobs. Nodes contain feet height rather than eye/center height. */
public final class Pathfinder {
    static final int SEARCH_RADIUS = 24;

    private Pathfinder() {}

    public static boolean supportsLadders(Mob mob) {
        return mob instanceof net.classicremastered.minecraft.mob.HumanoidMob
                || mob instanceof net.classicremastered.minecraft.mob.Villager
                || mob instanceof net.classicremastered.minecraft.mob.StickFigure;
    }

    public static float dropLimit(Mob mob) {
        if (mob instanceof net.classicremastered.minecraft.mob.Chicken) return 8;
        if (mob instanceof net.classicremastered.minecraft.mob.ZombieBuilder) return 3;
        return 3
                + (mob.ai instanceof net.classicremastered.minecraft.mob.ai.combat.BasicAttackAI
                        ? Math.min(3, Math.max(0, (mob.health - 1) / 4))
                        : 0);
    }

    public static boolean supportsSwimming(Mob mob) {
        return mob.ai instanceof net.classicremastered.minecraft.mob.ai.BasicAI
                && !(mob instanceof net.classicremastered.minecraft.mob.Enderman)
                && !(mob instanceof net.classicremastered.minecraft.mob.CrossbowTurret);
    }

    public static boolean canEnterWater(Mob mob) {
        return supportsSwimming(mob)
                && mob instanceof net.classicremastered.minecraft.mob.Zombie zombie
                && !(mob instanceof net.classicremastered.minecraft.mob.Skeleton)
                && zombie.canBurnInSunlight();
    }

    public static boolean waterBetween(Level level, Mob mob, float x, float feetY, float z) {
        float dx = x - mob.x, dz = z - mob.z;
        int samples = Math.max(1, (int) Math.ceil(Math.hypot(dx, dz) * 2));
        for (int i = 1; i <= samples; i++) {
            float t = (float) i / samples;
            int bx = (int) Math.floor(mob.x + dx * t);
            int by = (int) Math.floor(mob.bb.y0 + (feetY - mob.bb.y0) * t);
            int bz = (int) Math.floor(mob.z + dz * t);
            if (level.isWater(bx, by, bz) || level.isWater(bx, by - 1, bz)) return true;
        }
        return false;
    }

    public static final class Node {

        public final int x, y, z;
        public final float feetY;
        public final Node parent;

        public Node(int x, int y, int z, Node parent) {
            this(x, (float) y, z, parent);
        }

        public Node(int x, float feetY, int z, Node parent) {
            this.x = x;
            this.y = (int) Math.floor(feetY);
            this.feetY = feetY;
            this.z = z;
            this.parent = parent;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Node n && n.x == x && n.feetY == feetY && n.z == z;
        }

        @Override
        public int hashCode() {
            return (x * 73856093) ^ ((int) (feetY * 2) * 19349663) ^ (z * 83492791);
        }
    }

    private record Cell(int x, int y, int z) {}

    private record Entry(Node node, float cost, float score, long order) {}

    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public static float offset(float width) {
        return ((int) Math.ceil(width) & 1) == 0 ? 0f : 0.5f;
    }

    public static int cell(float coordinate, float width) {
        return Math.round(coordinate - offset(width));
    }

    public static boolean canWalkStraight(Level level, Mob mob, float x, float feetY, float z) {
        return new GroundClearance(level, mob).walkableLine(mob.x, mob.bb.y0, mob.z, x, feetY, z);
    }

    public static boolean hasStandingRoom(Level level, Mob mob, float x, float feetY, float z) {
        GroundClearance ground = new GroundClearance(level, mob);
        float space = 0.4f + mob.bbWidth * 0.5f;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                if (!ground.supportedAt(x + dx * space, feetY, z + dz * space)) return false;
        return true;
    }

    /** Compatibility overload: maxNodes is an expansion budget, not a distance. */
    public static List<Node> findPath(
            Level level, int sx, int sy, int sz, int tx, int ty, int tz, int maxNodes) {
        return search(
                new GroundClearance(level, 0.6f, 1.8f),
                sx,
                sy,
                sz,
                tx,
                ty,
                tz,
                maxNodes,
                SEARCH_RADIUS,
                false);
    }

    public static List<Node> findPath(Level level, Mob mob, int tx, int ty, int tz, int maxNodes) {
        return findPath(level, mob, tx, (float) ty, tz, maxNodes);
    }

    public static List<Node> findPath(
            Level level, Mob mob, int tx, float ty, int tz, int maxNodes) {
        return findPath(new GroundClearance(level, mob), mob, tx, ty, tz, maxNodes);
    }

    public static List<Node> findChasePath(
            Level level, Mob mob, float x, float feetY, float z, int maxNodes) {
        return findChasePath(new GroundClearance(level, mob), mob, x, feetY, z, maxNodes);
    }

    static List<Node> findChasePath(
            GroundClearance ground, Mob mob, float x, float feetY, float z, int maxNodes) {
        if (ground.needsDryApproach(x, feetY, z)) {
            return findPath(
                    ground,
                    mob,
                    cell(x, ground.width),
                    feetY,
                    cell(z, ground.width),
                    maxNodes,
                    true);
        }
        Node goal = ground.chaseGoal(x, feetY, z);
        return goal == null ? null : findPath(ground, mob, goal.x, goal.feetY, goal.z, maxNodes);
    }

    public static Node findWaterRefuge(Level level, Mob mob) {
        GroundClearance ground = new GroundClearance(level, mob, null, true);
        int sx = cell(mob.x, ground.width), sz = cell(mob.z, ground.width);
        int sy = (int) Math.floor(mob.bb.y0);
        List<Node> candidates = new ArrayList<>();
        for (int x = sx - 16; x <= sx + 16; x++) {
            for (int z = sz - 16; z <= sz + 16; z++) {
                for (int y = Math.min(level.depth - 2, sy + 2); y >= Math.max(0, sy - 8); y--) {
                    if (ground.waterLanding(x, y + 1, z)) {
                        candidates.add(new Node(x, y + 1, z, null));
                        break;
                    }
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(n -> heuristic(n.x, n.feetY, n.z, sx, sy, sz)));
        for (int i = 0; i < Math.min(candidates.size(), 8); i++) {
            Node candidate = candidates.get(i);
            if (findPath(ground, mob, candidate.x, candidate.feetY, candidate.z, 384) != null)
                return candidate;
        }
        return null;
    }

    public static Node findShadeRefuge(Level level, Mob mob) {
        GroundClearance ground = new GroundClearance(level, mob);
        int sx = cell(mob.x, ground.width), sz = cell(mob.z, ground.width);
        int sy = (int) Math.floor(mob.bb.y0);
        List<Node> candidates = new ArrayList<>();
        for (int x = sx - 10; x <= sx + 10; x++) {
            for (int z = sz - 10; z <= sz + 10; z++) {
                float cx = x + ground.offset, cz = z + ground.offset;
                for (int y = sy + 3; y >= sy - 3; y--) {
                    if (ground.supportedAt(cx, y, cz)
                            && !level.hasDirectSun(cx, y + mob.bbHeight * .85f, cz)) {
                        candidates.add(new Node(x, y, z, null));
                    }
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(n -> heuristic(n.x, n.feetY, n.z, sx, sy, sz)));
        for (int i = 0; i < Math.min(candidates.size(), 8); i++) {
            Node candidate = candidates.get(i);
            if (findPath(ground, mob, candidate.x, candidate.feetY, candidate.z, 512) != null)
                return candidate;
        }
        return null;
    }

    public static List<Node> findPathWithRemovableFire(
            Level level,
            Mob mob,
            float x,
            float feetY,
            float z,
            int maxNodes,
            java.util.function.Predicate<net.classicremastered.minecraft.level.RedstoneEngine.Pos>
                    removableFire) {
        return findChasePath(
                new GroundClearance(level, mob, removableFire), mob, x, feetY, z, maxNodes);
    }

    static List<Node> findPath(
            GroundClearance ground, Mob mob, int tx, float ty, int tz, int maxNodes) {
        return findPath(ground, mob, tx, ty, tz, maxNodes, false);
    }

    private static List<Node> findPath(
            GroundClearance ground,
            Mob mob,
            int tx,
            float ty,
            int tz,
            int maxNodes,
            boolean dryApproach) {
        int sx = cell(mob.x, ground.width), sz = cell(mob.z, ground.width);
        float sy = Math.round(mob.bb.y0 * 2f) / 2f;
        if (Math.abs(mob.bb.y0 - sy) < 0.12f
                && ground.standable(sx, sy, sz)
                && ground.bodyLineClear(
                        mob.x,
                        Math.max(mob.bb.y0, sy),
                        mob.z,
                        sx + ground.offset,
                        sz + ground.offset)) {
            return search(ground, sx, sy, sz, tx, ty, tz, maxNodes, SEARCH_RADIUS, dryApproach);
        }
        Node start = null;
        float best = Float.POSITIVE_INFINITY;
        // A mob can land with its leading edge on a tread before its center crosses
        // the grid boundary. Anchor a replan on reachable ground at that height,
        // rather than sending it backward to the lower cell under its center.
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                for (float delta : new float[] {0, -0.5f, 0.5f, -1, 1, -1.5f, 1.5f, -2, 2}) {
                    int x = sx + dx, z = sz + dz;
                    float y = sy + delta;
                    if (!ground.standable(x, y, z)) continue;
                    float px = x + ground.offset - mob.x,
                            pz = z + ground.offset - mob.z,
                            py = y - mob.bb.y0;
                    float score = px * px + pz * pz + py * py * 4;
                    if (score >= best
                            || !ground.bodyLineClear(
                                    mob.x,
                                    Math.max(mob.bb.y0, y),
                                    mob.z,
                                    x + ground.offset,
                                    z + ground.offset)) continue;
                    start = new Node(x, y, z, null);
                    best = score;
                }
        return start == null
                ? null
                : search(
                        ground,
                        start.x,
                        start.feetY,
                        start.z,
                        tx,
                        ty,
                        tz,
                        maxNodes,
                        SEARCH_RADIUS,
                        dryApproach);
    }

    private static float heuristic(int x, float y, int z, int tx, float ty, int tz) {
        return Math.abs(x - tx) + Math.abs(z - tz) + Math.abs(y - ty) * 0.1f;
    }

    private static List<Node> result(Node node) {
        LinkedList<Node> result = new LinkedList<>();
        for (Node current = node; current != null; current = current.parent)
            result.addFirst(current);
        return result;
    }

    private static List<Node> search(
            GroundClearance ground,
            int sx,
            float sy,
            int sz,
            int tx,
            float ty,
            int tz,
            int budget,
            int radius,
            boolean dryApproach) {
        if (!ground.standable(sx, sy, sz)) return null;
        boolean distant = Math.max(Math.abs(tx - sx), Math.abs(tz - sz)) > radius;
        if (!distant && !dryApproach && !ground.standable(tx, ty, tz)) return null;
        PriorityQueue<Entry> open =
                new PriorityQueue<>(
                        Comparator.comparingDouble(Entry::score).thenComparingLong(Entry::order));
        Map<Cell, Float> costs = new HashMap<>();
        Map<Cell, Boolean> walkable = new HashMap<>();
        Node start = new Node(sx, sy, sz, null), best = start;
        float bestDistance = heuristic(sx, sy, sz, tx, ty, tz);
        costs.put(new Cell(sx, Math.round(sy * 2), sz), 0f);
        long order = 0;
        open.add(new Entry(start, 0, bestDistance, order++));
        int expansions = 0;
        while (!open.isEmpty() && expansions < Math.max(1, Math.min(4096, budget))) {
            Entry entry = open.poll();
            Node current = entry.node;
            if (entry.cost
                    > costs.getOrDefault(
                            new Cell(current.x, Math.round(current.feetY * 2), current.z),
                            Float.POSITIVE_INFINITY)) continue;
            expansions++;
            if (current.x == tx && current.feetY == ty && current.z == tz) return result(current);
            float distance = heuristic(current.x, current.feetY, current.z, tx, ty, tz);
            if (distance < bestDistance) {
                best = current;
                bestDistance = distance;
            }
            if (ground.climbing && current.feetY == (int) current.feetY) {
                for (int dy : new int[] {1, -1}) {
                    float y = current.feetY + dy;
                    if (Math.abs(y - sy) > 24
                            || !ground.climbEdge(current.x, current.feetY, current.z, y)) continue;
                    Cell cell = new Cell(current.x, Math.round(y * 2), current.z);
                    float cost = entry.cost + 1.25f;
                    if (cost >= costs.getOrDefault(cell, Float.POSITIVE_INFINITY)) continue;
                    costs.put(cell, cost);
                    Node next = new Node(current.x, y, current.z, current);
                    open.add(
                            new Entry(
                                    next,
                                    cost,
                                    cost + heuristic(next.x, y, next.z, tx, ty, tz),
                                    order++));
                }
            }
            for (int[] direction : DIRECTIONS) {
                int x = current.x + direction[0], z = current.z + direction[1];
                float water = ground.waterLandingBelow(x, current.feetY, z);
                float descent =
                        Float.isNaN(water)
                                ? ground.maxDrop
                                : Math.max(ground.maxDrop, current.feetY - water);
                for (float dy = 1; dy >= -descent; dy -= .5f) {
                    float y = current.feetY + dy;
                    if (Math.abs(x - sx) > radius
                            || Math.abs(z - sz) > radius
                            || y - sy > (ground.climbing ? 24 : 8)
                            || !ground.swimming && sy - y > (ground.climbing ? 24 : 8)) continue;
                    Cell cell = new Cell(x, Math.round(y * 2), z);
                    if (!walkable.computeIfAbsent(cell, c -> ground.standable(c.x, c.y / 2f, c.z)))
                        continue;
                    if (!ground.transition(current.x, current.feetY, current.z, x, y, z)) continue;
                    float damage =
                            ground.waterLanding(x, y, z)
                                    ? 0
                                    : (float) Math.ceil(Math.max(0, -dy - 3.01f));
                    float cost =
                            entry.cost + 1f + (dy > 0 ? 0.4f : dy < 0 ? 0.15f : 0f) + damage * 8f;
                    if (cost >= costs.getOrDefault(cell, Float.POSITIVE_INFINITY)) continue;
                    costs.put(cell, cost);
                    Node next = new Node(x, y, z, current);
                    open.add(new Entry(next, cost, cost + heuristic(x, y, z, tx, ty, tz), order++));
                }
            }
        }
        // Distant goals can use a bounded route segment; enclosed local goals stop
        // safely.
        return dryApproach || distant && best != start ? result(best) : null;
    }
}
