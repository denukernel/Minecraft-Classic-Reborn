package net.classicremastered.minecraft.path;

import java.util.*;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.liquid.LiquidType;
import net.classicremastered.minecraft.level.tile.Block;

public final class Pathfinder {

    public static final class Node {
        public final int x, y, z;
        public final Node parent;

        public Node(int x, int y, int z, Node parent) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.parent = parent;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Node))
                return false;
            Node n = (Node) o;
            return n.x == x && n.y == y && n.z == z;
        }

        @Override
        public int hashCode() {
            return (x * 73856093) ^ (y * 19349663) ^ (z * 83492791);
        }
    }

    /** Simple BFS pathfinder. */
    public static List<Node> findPath(Level level, int sx, int sy, int sz, int tx, int ty, int tz, int maxDist) {
        Queue<Node> open = new ArrayDeque<>();
        Set<Node> visited = new HashSet<>();

        Node start = new Node(sx, sy, sz, null);
        open.add(start);
        visited.add(start);

        while (!open.isEmpty() && visited.size() < maxDist) {
            Node cur = open.poll();

            // Reached target
            if (cur.x == tx && cur.y == ty && cur.z == tz) {
                List<Node> path = new ArrayList<>();
                for (Node n = cur; n != null; n = n.parent)
                    path.add(0, n);
                return path;
            }

            for (int[] d : NEIGHBORS) {
                int nx = cur.x + d[0];
                int ny = cur.y + d[1];
                int nz = cur.z + d[2];

                // allow start node always; enforce walkability on others
                if (!(cur.parent == null) && !isWalkable(level, nx, ny, nz))
                    continue;

                Node n = new Node(nx, ny, nz, cur);
                if (visited.add(n)) {
                    open.add(n);
                }
            }
        }
        return null;
    }

    // allow step-up and step-down
    private static final int[][] NEIGHBORS = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 }, { 1, 1, 0 },
            { -1, 1, 0 }, { 0, 1, 1 }, { 0, 1, -1 }, { 1, -1, 0 }, { -1, -1, 0 }, { 0, -1, 1 }, { 0, -1, -1 } };

    /** Check if mob can stand at (x,y,z). */
    private static boolean isWalkable(Level level, int x, int y, int z) {
        if (!level.isInBounds(x, y, z))
            return false;

        // Must stand on something solid
        int below = level.getTile(x, y - 1, z);
        if (below <= 0)
            return false;
        Block b = Block.blocks[below];
        if (b == null || !b.isSolid())
            return false;

        // Feet position must be air
        int atFeet = level.getTile(x, y, z);
        if (atFeet != 0)
            return false;

        // Head must also be air
        int atHead = level.getTile(x, y + 1, z);
        if (atHead != 0)
            return false;

        // Reject liquids
        if (b.getLiquidType() != net.classicremastered.minecraft.level.liquid.LiquidType.NOT_LIQUID)
            return false;

        return true;
    }

}
