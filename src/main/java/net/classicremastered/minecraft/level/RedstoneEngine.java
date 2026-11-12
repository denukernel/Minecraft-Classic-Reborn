package net.classicremastered.minecraft.level;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.infinite.SimpleChunk;
import net.classicremastered.minecraft.level.tile.*;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.nbt.*;

import java.util.*;

/** World-owned circuit state. Power propagation and actuator updates run on the game tick. */
public final class RedstoneEngine {
    public record Pos(int x, int y, int z) {
        public Pos step(int direction) {
            int[] d = DIRECTIONS[direction];
            return new Pos(x + d[0], y + d[1], z + d[2]);
        }
    }

    public static final int[][] DIRECTIONS = {
        {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}
    };

    public static final class State {
        public int block, facing = 3, power, delay = 1, release, due, pending = -1;
        public int mount = 1;
        public int chestPartner = -1;
        public transient int chestUsers;
        public transient float previousLid, lid;
        public boolean on, extended, lootInitialized;
        public int[] items = new int[27], counts = new int[27];
        public final net.classicremastered.minecraft.level.itemstack.PackedTurret[] crates =
                new net.classicremastered.minecraft.level.itemstack.PackedTurret[27];

        public State(int block) {
            this.block = block;
            if (block == Block.REDSTONE_TORCH.id
                    || block == Block.TORCH.id
                    || block == Block.STONE_BUTTON.id) facing = 1;
        }
    }

    private final Level level;
    private final Map<Pos, State> states = new HashMap<>();
    private final Set<SimpleChunk> scanned = Collections.newSetFromMap(new WeakHashMap<>());
    private boolean finiteScanned;
    private boolean movingBlocks;
    private final Map<Pos, Motion> moving = new HashMap<>();
    private final List<Motion> retractingHeads = new ArrayList<>();

    public record Motion(Pos from, Pos to, int block, int facing, boolean sticky, int started) {}

    public boolean isMoving(int x, int y, int z, int block) {
        if (moving.isEmpty()) return false;
        Motion motion = moving.get(new Pos(x, y, z));
        return motion != null && motion.block == block;
    }

    public List<Motion> motions() {
        if (moving.isEmpty() && retractingHeads.isEmpty()) return List.of();
        List<Motion> result = new ArrayList<>(moving.values());
        result.addAll(retractingHeads);
        return result;
    }

    private void expireMotions() {
        List<Pos> finished = new ArrayList<>();
        moving.entrySet()
                .removeIf(
                        entry -> {
                            if (level.tickCount - entry.getValue().started < 2
                                    && tile(entry.getKey()) == entry.getValue().block) return false;
                            finished.add(entry.getKey());
                            return true;
                        });
        retractingHeads.removeIf(motion -> level.tickCount - motion.started >= 2);
        for (Pos pos : finished) level.onBlockChanged(pos.x, pos.y, pos.z);
    }

    public RedstoneEngine(Level level) {
        this.level = level;
    }

    private boolean tracked(int id) {
        return id > 0
                && (Block.blocks[id] instanceof CircuitBlock
                        || id == Block.CHEST.id
                        || id == Block.FURNACE.id
                        || id == Block.SANDSTONE_STAIRS.id);
    }

    private boolean loaded(Pos p) {
        if (!level.isInBounds(p.x, p.y, p.z)) return false;
        if (level instanceof LevelInfiniteTerrain t)
            return t.chunks().isChunkLoaded(p.x >> 4, p.z >> 4);
        if (level instanceof LevelInfiniteFlat f)
            return f.chunks().isChunkLoaded(p.x >> 4, p.z >> 4);
        return true;
    }

    private int tile(Pos p) {
        return loaded(p) ? level.getTile(p.x, p.y, p.z) : 0;
    }

    private CircuitBlock.Kind kind(State s) {
        return Block.blocks[s.block] instanceof CircuitBlock b ? b.kind : null;
    }

    public State state(int x, int y, int z) {
        Pos p = new Pos(x, y, z);
        int id = level.getTile(x, y, z);
        State state = states.get(p);
        if (state == null || state.block != id) {
            state = new State(id);
            if (tracked(id)) states.put(p, state);
        }
        if (id == Block.CHEST.id && !state.lootInitialized) {
            state.lootInitialized = true;
            net.classicremastered.minecraft.level.structure.DesertPyramid.fillLoot(level, p, state);
        }
        return state;
    }

    public void changed(int x, int y, int z) {
        if (movingBlocks) return;
        Pos p = new Pos(x, y, z);
        State old = states.get(p);
        int id = level.getTile(x, y, z);
        if (old != null && old.block != id) {
            states.remove(p);
            if (old.extended) {
                Pos head = p.step(old.facing);
                if (tile(head) == Block.PISTON_HEAD.id)
                    level.setTileNoUpdate(head.x, head.y, head.z, 0);
            }
            if (old.block == Block.CHEST.id) {
                ChestController.removed(level, p, old);
                for (int slot = 0; slot < 27; slot++)
                    drop(p, old.items[slot], old.counts[slot], old.crates[slot]);
            }
        }
        if (tracked(id)) state(x, y, z);
    }

    private void drop(
            Pos p,
            int id,
            int count,
            net.classicremastered.minecraft.level.itemstack.PackedTurret crate) {
        if (id <= 0 || count <= 0 || level.isNetworkMode()) return;
        if (id >= 256)
            level.addEntity(
                    new net.classicremastered.minecraft.entity.DroppedItem(
                                    level, p.x + .5f, p.y + .5f, p.z + .5f, id - 256, count)
                            .withCrate(crate));
        else
            level.addEntity(
                    new net.classicremastered.minecraft.entity.DroppedBlock(
                            level, p.x + .5f, p.y + .5f, p.z + .5f, id, count));
    }

    private void scan() {
        Collection<SimpleChunk> chunks = null;
        if (level instanceof LevelInfiniteTerrain t) chunks = t.chunks().getAllChunks().values();
        else if (level instanceof LevelInfiniteFlat f) chunks = f.chunks().getAllChunks().values();
        if (chunks != null) {
            for (SimpleChunk c : chunks)
                if (c.loaded && c.blocks != null && scanned.add(c))
                    for (int y = 0; y < c.height; y++)
                        for (int z = 0; z < 16; z++)
                            for (int x = 0; x < 16; x++)
                                if (tracked(c.blocks[SimpleChunk.idx(x, y, z, c.height)] & 255))
                                    state(c.cx * 16 + x, y, c.cz * 16 + z);
        } else if (!finiteScanned) {
            finiteScanned = true;
            for (int y = 0; y < level.depth; y++)
                for (int z = 0; z < level.height; z++)
                    for (int x = 0; x < level.width; x++)
                        if (tracked(level.blocks[(y * level.height + z) * level.width + x] & 255))
                            state(x, y, z);
        }
    }

    public List<Pos> chestPositions() {
        scan();
        List<Pos> chests = new ArrayList<>();
        for (var entry : states.entrySet()) {
            if (entry.getValue().block == Block.CHEST.id
                    && loaded(entry.getKey())
                    && tile(entry.getKey()) == Block.CHEST.id) chests.add(entry.getKey());
        }
        chests.sort(
                Comparator.comparingInt(Pos::x).thenComparingInt(Pos::y).thenComparingInt(Pos::z));
        return chests;
    }

    public void tick() {
        if (level.isNetworkMode()) return;
        expireMotions();
        scan();
        ChestController.tick(level);
        states.entrySet()
                .removeIf(e -> loaded(e.getKey()) && tile(e.getKey()) != e.getValue().block);
        List<Map.Entry<Pos, State>> active = new ArrayList<>();
        for (var e : new ArrayList<>(states.entrySet())) {
            Pos pos = e.getKey();
            State state = e.getValue();
            if (!loaded(pos)) continue;
            if (Block.blocks[state.block] instanceof CircuitBlock block && block.needsSupport()) {
                int face =
                        block.needsFloorSupport()
                                ? 1
                                : block.kind == CircuitBlock.Kind.LEVER
                                        ? state.mount
                                        : state.facing;
                Pos support = pos.step(face ^ 1);
                if ((loaded(support) || !level.isInBounds(support.x, support.y, support.z))
                        && !block.canPlaceOnFace(level, pos.x, pos.y, pos.z, face)
                        && level.setTile(pos.x, pos.y, pos.z, 0)) {
                    block.onBreak(level, pos.x, pos.y, pos.z);
                    continue;
                }
            }
            active.add(e);
        }
        Map<Pos, Integer> previous = new HashMap<>();
        for (var e : active) {
            Pos p = e.getKey();
            State s = e.getValue();
            previous.put(p, s.power);
            CircuitBlock.Kind k = kind(s);
            if (k == CircuitBlock.Kind.BUTTON && s.release <= level.tickCount) s.on = false;
            if (k == CircuitBlock.Kind.PLATE)
                s.on =
                        !level.findEntities(
                                        null,
                                        new AABB(
                                                p.x + .1f, p.y, p.z + .1f, p.x + .9f, p.y + .3f,
                                                p.z + .9f))
                                .isEmpty();
            if (k == CircuitBlock.Kind.SOURCE) s.power = 15;
            else if (k == CircuitBlock.Kind.LEVER
                    || k == CircuitBlock.Kind.BUTTON
                    || k == CircuitBlock.Kind.PLATE) s.power = s.on ? 15 : 0;
        }
        wires(active);
        Map<State, Integer> updates = new HashMap<>();
        for (var e : active) {
            Pos p = e.getKey();
            State s = e.getValue();
            CircuitBlock.Kind k = kind(s);
            int wanted = -1;
            if (k == CircuitBlock.Kind.TORCH)
                wanted = input(p.step(s.facing ^ 1), p, true) > 0 ? 0 : 15;
            if (k == CircuitBlock.Kind.REPEATER || k == CircuitBlock.Kind.COMPARATOR) {
                int back = directOrSolid(p.step(s.facing ^ 1), p, true);
                if (k == CircuitBlock.Kind.REPEATER) wanted = back > 0 ? 15 : 0;
                else {
                    int left = s.facing < 4 ? 4 : 2, right = left ^ 1;
                    int side =
                            Math.max(
                                    directOrSolid(p.step(left), p, true),
                                    directOrSolid(p.step(right), p, true));
                    wanted = s.on ? Math.max(0, back - side) : back >= side ? back : 0;
                }
            }
            if (wanted >= 0) {
                if (wanted == s.power) s.pending = -1;
                else if (s.pending != wanted) {
                    s.pending = wanted;
                    s.due = level.tickCount + 2 * (k == CircuitBlock.Kind.REPEATER ? s.delay : 1);
                } else if (level.tickCount >= s.due) {
                    updates.put(s, wanted);
                    s.pending = -1;
                }
            }
        }
        updates.forEach((state, power) -> state.power = power);
        if (!updates.isEmpty()) wires(active);
        Set<Pos> tnt = new HashSet<>();
        for (var e : active) {
            Pos p = e.getKey();
            State s = e.getValue();
            if (states.get(p) != s || tile(p) != s.block) continue;
            CircuitBlock.Kind k = kind(s);
            if (k == CircuitBlock.Kind.PISTON || k == CircuitBlock.Kind.STICKY_PISTON)
                movePiston(p, s, input(p, null, true) > 0);
            if (k == CircuitBlock.Kind.LAMP || k == CircuitBlock.Kind.LIT_LAMP) {
                int id =
                        input(p, null, true) > 0
                                ? Block.LIT_REDSTONE_LAMP.id
                                : Block.REDSTONE_LAMP.id;
                if (tile(p) != id) level.setTile(p.x, p.y, p.z, id);
            }
            if (s.power > 0)
                for (int d = 0; d < 6; d++) {
                    Pos near = p.step(d);
                    tnt.add(near);
                    for (int other = 0; other < 6; other++) tnt.add(near.step(other));
                }
            if (previous.get(p) != s.power) level.onBlockChanged(p.x, p.y, p.z);
        }
        for (Pos p : tnt)
            if (tile(p) == Block.TNT.id && input(p, null, true) > 0)
                TNTBlock.ignite(level, p.x, p.y, p.z);
    }

    private void wires(List<Map.Entry<Pos, State>> active) {
        ArrayDeque<Pos> queue = new ArrayDeque<>();
        for (var e : active)
            if (kind(e.getValue()) == CircuitBlock.Kind.WIRE) e.getValue().power = 0;
        for (var e : active)
            if (kind(e.getValue()) == CircuitBlock.Kind.WIRE) {
                int power = input(e.getKey(), null, false);
                if (power > 0) {
                    e.getValue().power = power;
                    queue.add(e.getKey());
                }
            }
        while (!queue.isEmpty()) {
            Pos p = queue.remove();
            int power = states.get(p).power - 1;
            if (power <= 0) continue;
            for (int direction = 2; direction < 6; direction++) {
                Pos neighbor = wireNeighbor(p, direction);
                if (neighbor == null) continue;
                State state = state(neighbor.x, neighbor.y, neighbor.z);
                if (state.power < power) {
                    state.power = power;
                    queue.add(neighbor);
                }
            }
        }
    }

    private Pos wireNeighbor(Pos pos, int direction) {
        Pos side = pos.step(direction);
        if (tile(side) == Block.REDSTONE_WIRE.id) return side;
        if (solid(side)) {
            Pos above = side.step(1);
            if (!solid(pos.step(1)) && tile(above) == Block.REDSTONE_WIRE.id) return above;
        } else {
            Pos below = side.step(0);
            if (tile(below) == Block.REDSTONE_WIRE.id) return below;
        }
        return null;
    }

    public int wireConnections(Pos pos) {
        int connections = 0;
        for (int direction = 2; direction < 6; direction++) {
            Pos side = pos.step(direction);
            Pos wire = wireNeighbor(pos, direction);
            if (wire != null) {
                connections |= 1 << direction;
                if (wire.y > pos.y) connections |= 1 << (direction + 4);
            } else if (Block.blocks[tile(side)] instanceof CircuitBlock block) {
                switch (block.kind) {
                    case COMPARATOR -> connections |= 1 << direction;
                    case REPEATER -> {
                        int facing = state(side.x, side.y, side.z).facing;
                        if ((direction & ~1) == (facing & ~1)) connections |= 1 << direction;
                    }
                    case TORCH, LEVER, BUTTON, PLATE, SOURCE -> connections |= 1 << direction;
                    default -> {}
                }
            }
        }
        return connections;
    }

    private boolean solid(Pos p) {
        int id = tile(p);
        return id > 0 && Block.blocks[id] != null && Block.blocks[id].isSolid();
    }

    private int direct(Pos source, Pos target, boolean wires) {
        State s = states.get(source);
        if (s == null || !loaded(source)) return 0;
        CircuitBlock.Kind kind = kind(s);
        if (kind == CircuitBlock.Kind.WIRE) return wires ? s.power : 0;
        if (kind == CircuitBlock.Kind.TORCH && source.step(s.facing ^ 1).equals(target)) return 0;
        if ((kind == CircuitBlock.Kind.REPEATER || kind == CircuitBlock.Kind.COMPARATOR)
                && !source.step(s.facing).equals(target)) return 0;
        if (s.block == Block.CHEST.id) {
            return ChestController.signal(level, source);
        }
        return s.power;
    }

    private int directOrSolid(Pos source, Pos target, boolean wires) {
        int power = direct(source, target, wires);
        State sourceState = states.get(source);
        if (solid(source) && (sourceState == null || kind(sourceState) != CircuitBlock.Kind.HEAD))
            for (int d = 0; d < 6; d++) {
                Pos n = source.step(d);
                if (!n.equals(target)) power = Math.max(power, direct(n, source, wires));
            }
        return power;
    }

    private int input(Pos target, Pos excluded, boolean wires) {
        int power = 0;
        for (int d = 0; d < 6; d++) {
            Pos n = target.step(d);
            if (!n.equals(excluded)) power = Math.max(power, directOrSolid(n, target, wires));
        }
        return power;
    }

    private boolean movable(Pos p) {
        int id = tile(p);
        if (!loaded(p)
                || id == Block.BEDROCK.id
                || id == Block.OBSIDIAN.id
                || id == Block.CHEST.id
                || id == Block.PISTON_HEAD.id) return false;
        if (id == 0) return true;
        State state = states.get(p);
        if (state != null && state.extended) return false;
        return Block.blocks[id] != null
                && Block.blocks[id].getLiquidType()
                        == net.classicremastered.minecraft.level.liquid.LiquidType.NOT_LIQUID;
    }

    private boolean commitStroke(Map<Pos, Integer> blocks, Map<Pos, State> replacements) {
        Map<Pos, Integer> before = new LinkedHashMap<>();
        for (var entry : blocks.entrySet()) {
            Pos pos = entry.getKey();
            if (!loaded(pos)
                    || level instanceof LevelInfiniteFlat && !level.creativeMode && pos.y == 0)
                return false;
            before.put(pos, tile(pos));
        }
        movingBlocks = true;
        try {
            for (var entry : blocks.entrySet()) {
                Pos pos = entry.getKey();
                if (before.get(pos).equals(entry.getValue())) continue;
                if (!level.setTileNoUpdate(pos.x, pos.y, pos.z, entry.getValue())
                        || tile(pos) != entry.getValue()) {
                    for (var original : before.entrySet()) {
                        Pos cell = original.getKey();
                        if (tile(cell) != original.getValue())
                            level.setTileNoUpdate(cell.x, cell.y, cell.z, original.getValue());
                    }
                    return false;
                }
            }
            for (Pos pos : blocks.keySet()) {
                states.remove(pos);
                moving.remove(pos);
                State replacement = replacements.get(pos);
                if (replacement != null) states.put(pos, replacement);
            }
            return true;
        } finally {
            movingBlocks = false;
        }
    }

    private Motion motion(Pos from, Pos to, int block, State piston) {
        return new Motion(
                from,
                to,
                block,
                piston.facing,
                kind(piston) == CircuitBlock.Kind.STICKY_PISTON,
                level.tickCount);
    }

    private void movePiston(Pos p, State s, boolean powered) {
        if (powered == s.extended) return;
        Pos head = p.step(s.facing);
        Map<Pos, Integer> blocks = new LinkedHashMap<>();
        Map<Pos, State> replacements = new HashMap<>();
        List<Motion> animations = new ArrayList<>();
        if (powered) {
            List<Pos> line = new ArrayList<>();
            Pos cursor = head;
            while (tile(cursor) != 0) {
                if (line.size() == 12 || !movable(cursor)) return;
                line.add(cursor);
                cursor = cursor.step(s.facing);
            }
            if (!loaded(cursor)) return;
            int[] movement = DIRECTIONS[s.facing];
            AABB sweep =
                    new AABB(
                            Math.min(head.x, cursor.x),
                            Math.min(head.y, cursor.y),
                            Math.min(head.z, cursor.z),
                            Math.max(head.x, cursor.x) + 1,
                            Math.max(head.y, cursor.y) + 1,
                            Math.max(head.z, cursor.z) + 1);
            List<Entity> pushed = new ArrayList<>();
            for (Object value : level.findEntities(null, sweep)) {
                Entity entity = (Entity) value;
                if (entity.removed) continue;
                if (!level.getCubes(entity.bb.cloneMove(movement[0], movement[1], movement[2]))
                        .isEmpty()) return;
                pushed.add(entity);
            }
            for (Pos source : line) {
                Pos target = source.step(s.facing);
                int id = tile(source);
                blocks.put(target, id);
                State saved = states.get(source);
                if (saved != null) replacements.put(target, saved);
                animations.add(motion(source, target, id, s));
            }
            blocks.put(head, Block.PISTON_HEAD.id);
            State headState = new State(Block.PISTON_HEAD.id);
            headState.facing = s.facing;
            headState.on = kind(s) == CircuitBlock.Kind.STICKY_PISTON;
            replacements.put(head, headState);
            animations.add(motion(p, head, Block.PISTON_HEAD.id, s));
            if (!commitStroke(blocks, replacements)) return;
            for (Entity entity : pushed)
                entity.setPos(
                        entity.x + movement[0], entity.y + movement[1], entity.z + movement[2]);
        } else {
            boolean hasHead = tile(head) == Block.PISTON_HEAD.id;
            if (hasHead) blocks.put(head, 0);
            Pos source = head.step(s.facing);
            if (kind(s) == CircuitBlock.Kind.STICKY_PISTON
                    && (hasHead || tile(head) == 0)
                    && tile(source) != 0
                    && movable(source)) {
                blocks.put(source, 0);
                blocks.put(head, tile(source));
                State saved = states.get(source);
                if (saved != null) replacements.put(head, saved);
                animations.add(motion(source, head, tile(source), s));
            }
            if (!commitStroke(blocks, replacements)) {
                if (!hasHead || !commitStroke(Map.of(head, 0), Map.of())) return;
                blocks = Map.of(head, 0);
                animations.clear();
            }
            if (hasHead) retractingHeads.add(motion(head, p, Block.PISTON_HEAD.id, s));
        }
        s.extended = powered;
        for (Motion animation : animations) moving.put(animation.to, animation);
        for (var entry : blocks.entrySet()) {
            Pos cell = entry.getKey();
            level.onBlockChanged(cell.x, cell.y, cell.z);
            level.updateNeighborsAt(cell.x, cell.y, cell.z, entry.getValue());
        }
        level.onBlockChanged(p.x, p.y, p.z);
        level.playSound(
                powered ? "tile/piston/out" : "tile/piston/in",
                p.x + .5f,
                p.y + .5f,
                p.z + .5f,
                .7f,
                1);
    }

    public void save(CompoundTag root) {
        scan();
        ListTag list = new ListTag("CircuitStates", (byte) 10);
        for (var e : states.entrySet()) {
            Pos p = e.getKey();
            State s = e.getValue();
            CompoundTag tag = new CompoundTag(null);
            tag.putInt("X", p.x);
            tag.putInt("Y", p.y);
            tag.putInt("Z", p.z);
            tag.putInt("Block", s.block);
            tag.putInt("Facing", s.facing);
            if (Block.blocks[s.block] instanceof CircuitBlock circuit && circuit.needsSupport()) {
                tag.putInt("Mount", circuit.kind == CircuitBlock.Kind.LEVER ? s.mount : s.facing);
            }
            tag.putInt("ChestPartner", s.chestPartner);
            tag.putInt("Power", s.power);
            tag.putInt("Delay", s.delay);
            tag.putInt("Release", Math.max(0, s.release - level.tickCount));
            tag.putInt("Due", Math.max(0, s.due - level.tickCount));
            tag.putInt("Pending", s.pending);
            tag.putBoolean("On", s.on);
            tag.putBoolean("Extended", s.extended);
            tag.putBoolean("LootInitialized", s.lootInitialized);
            ListTag inventory = new ListTag("Inventory", (byte) 10);
            for (int i = 0; i < 27; i++)
                if (s.counts[i] > 0) {
                    CompoundTag slot = new CompoundTag(null);
                    slot.putInt("Slot", i);
                    slot.putInt("Id", s.items[i]);
                    slot.putInt("Count", s.counts[i]);
                    if (s.crates[i] != null) slot.put(s.crates[i].save());
                    inventory.add(slot);
                }
            tag.put(inventory);
            list.add(tag);
        }
        root.put(list);
    }

    public void load(CompoundTag root) {
        if (!root.hasKey("CircuitStates")) return;
        ListTag list = root.getList("CircuitStates");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = (CompoundTag) list.get(i);
            Pos p = new Pos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
            int block = tag.getInt("Block");
            if (!tracked(block)
                    || !level.isInBounds(p.x, p.y, p.z)
                    || level.getTile(p.x, p.y, p.z) != block) continue;
            State s = new State(block);
            CircuitBlock.Kind kind = kind(s);
            if (kind == CircuitBlock.Kind.BUTTON
                    || kind == CircuitBlock.Kind.TORCH
                    || kind == CircuitBlock.Kind.NORMAL_TORCH) {
                if (tag.hasKey("Mount")) s.facing = Math.max(0, Math.min(5, tag.getInt("Mount")));
            } else if (tag.hasKey("Facing")) {
                s.facing = Math.max(0, Math.min(5, tag.getInt("Facing")));
            }
            if (kind == CircuitBlock.Kind.LEVER && tag.hasKey("Mount")) {
                s.mount = Math.max(0, Math.min(5, tag.getInt("Mount")));
            }
            s.chestPartner = tag.hasKey("ChestPartner") ? tag.getInt("ChestPartner") : -1;
            if (s.chestPartner < 2 || s.chestPartner > 5) s.chestPartner = -1;
            s.power = Math.max(0, Math.min(15, tag.getInt("Power")));
            s.delay = Math.max(1, Math.min(4, tag.getInt("Delay")));
            s.release = level.tickCount + tag.getInt("Release");
            s.due = level.tickCount + tag.getInt("Due");
            s.pending = tag.getInt("Pending");
            s.on = tag.getBoolean("On");
            if (block == Block.PISTON_HEAD.id) {
                Pos base = p.step(s.facing ^ 1);
                s.on = level.getTile(base.x, base.y, base.z) == Block.STICKY_PISTON.id;
            }
            s.extended = tag.getBoolean("Extended");
            s.lootInitialized = tag.getBoolean("LootInitialized");
            ListTag inventory = tag.getList("Inventory");
            for (int n = 0; n < inventory.size(); n++) {
                CompoundTag item = (CompoundTag) inventory.get(n);
                int slot = item.getInt("Slot");
                if (slot >= 0 && slot < 27) {
                    s.items[slot] = item.getInt("Id");
                    if (s.items[slot]
                            == net.classicremastered.minecraft.level.itemstack.TurretItems.PACKED
                                    + 256) {
                        s.crates[slot] =
                                net.classicremastered.minecraft.level.itemstack.PackedTurret.load(
                                        item);
                    }
                    s.counts[slot] = Math.max(0, Math.min(99, item.getInt("Count")));
                }
            }
            net.classicremastered.minecraft.player.Inventory.splitOversized(
                    27,
                    slot -> s.items[slot],
                    slot -> s.counts[slot],
                    (slot, id, count) -> {
                        s.items[slot] = id;
                        s.counts[slot] = count;
                    });
            states.put(p, s);
        }
    }
}
