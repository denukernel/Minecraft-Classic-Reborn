package net.classicremastered.minecraft.player;

import java.io.Serializable;

import net.classicremastered.minecraft.CreativeInventoryBlocks;
import net.classicremastered.minecraft.level.tile.Block;

public class Inventory implements Serializable {

    public static final long serialVersionUID = 0L;
    public static final int POP_TIME_DURATION = 5;

    // --- Hotbar (0..8) — Classic 0.30 compatible ---
    public static final int HOTBAR_SIZE = 9;
    private static final int PACKED_TURRET_ID =
            net.classicremastered.minecraft.level.itemstack.TurretItems.PACKED + 256;
    public int[] slots = new int[HOTBAR_SIZE];
    private java.util.Map<Integer, net.classicremastered.minecraft.level.itemstack.PackedTurret>
            crates;

    public net.classicremastered.minecraft.level.itemstack.PackedTurret crate(int slot) {
        return crates == null || getId(slot) != PACKED_TURRET_ID || getCount(slot) != 1
                ? null
                : crates.get(slot);
    }

    public void setCrate(
            int slot, net.classicremastered.minecraft.level.itemstack.PackedTurret crate) {
        if (crate == null) {
            if (crates != null) crates.remove(slot);
        } else {
            setIdCount(slot, PACKED_TURRET_ID, 1);
            if (crates == null) crates = new java.util.HashMap<>();
            crates.put(slot, crate);
        }
    }

    public boolean addCrate(net.classicremastered.minecraft.level.itemstack.PackedTurret crate) {
        for (int slot = 0; slot < totalSize(); slot++) {
            if (getCount(slot) <= 0 || getId(slot) <= 0) {
                setCrate(slot, crate);
                return true;
            }
        }
        return false;
    }

    public int[] count = new int[HOTBAR_SIZE];
    public int[] popTime = new int[HOTBAR_SIZE];
    public int selected = 0;

    // --- Extra storage grid used by the Inventory GUI (not by HUD/gameplay) ---
    private static final int DEFAULT_GRID = 27; // 3 rows × 9 columns
    private int[] gridSlots = new int[DEFAULT_GRID];
    private int[] gridCount = new int[DEFAULT_GRID];
    private int[] gridPopTime = new int[DEFAULT_GRID];

    public Inventory() {
        this(HOTBAR_SIZE);
    }

    private static final int DEFAULT_SIZE = HOTBAR_SIZE;

    public Inventory(int size) {
        // Initialize hotbar (always 9)
        for (int i = 0; i < HOTBAR_SIZE; ++i) {
            this.slots[i] = -1;
            this.count[i] = 0;
            this.popTime[i] = 0;
        }
        // Initialize storage grid
        for (int i = 0; i < gridSlots.length; ++i) {
            gridSlots[i] = -1;
            gridCount[i] = 0;
            gridPopTime[i] = 0;
        }
    }

    // ----------------- Classic hotbar API (unchanged) -----------------

    public int getSelected() {
        return this.slots[this.selected];
    }

    /** Returns the item/block id in a given hotbar slot, or -1. */
    public int getItem(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= this.slots.length) return -1;
        return this.slots[slotIndex];
    }

    // helper
    public boolean isBlock(int id) {
        return id >= 0 && id < 256;
    }

    public boolean isItem(int id) {
        return id >= 256 && id < 512;
    }

    /** True if the hotbar or inventory grid contains a stack of this item. */
    public boolean hasResource(int id) {
        for (int slot = 0; slot < totalSize(); slot++) {
            if (getId(slot) == id && getCount(slot) > 0) return true;
        }
        return false;
    }

    /** Remove from the currently selected slot (used for block placement). */
    public boolean removeSelected(int n) {
        if (selected < 0 || selected >= slots.length) return false;
        if (slots[selected] < 0) return false;
        if (count[selected] <= 0) return false;

        setCrate(selected, null);
        count[selected] -= n;
        if (count[selected] <= 0) {
            count[selected] = 0;
            slots[selected] = -1;
        }
        return true;
    }

    private int getSlot(int id) {
        for (int i = 0; i < this.slots.length; ++i) {
            if (id == this.slots[i]) return i;
        }
        return -1;
    }

    public void grabTexture(int id, boolean allowCheat) {
        int s;
        if ((s = this.getSlot(id)) >= 0) {
            this.selected = s;
        } else {
            if (allowCheat
                    && id > 0
                    && CreativeInventoryBlocks.allowedBlocks.contains(Block.blocks[id])) {
                this.replaceSlot(Block.blocks[id]);
            }
        }
    }

    public void swapPaint(int dir) {
        if (dir > 0) dir = 1;
        if (dir < 0) dir = -1;
        for (this.selected -= dir; this.selected < 0; this.selected += this.slots.length) {}
        while (this.selected >= this.slots.length) this.selected -= this.slots.length;
    }

    public void replaceSlot(int id) {
        if (id >= 0) this.replaceSlot((Block) CreativeInventoryBlocks.allowedBlocks.get(id));
    }

    public void replaceSlot(Block b) {
        if (b != null) {
            int s;
            if ((s = this.getSlot(b.id)) >= 0) {
                var movedCrate = crate(this.selected);
                setCrate(s, null);
                this.slots[s] = this.slots[this.selected];
                if (movedCrate != null) setCrate(s, movedCrate);
            }
            setCrate(this.selected, null);
            this.slots[this.selected] = b.id;
        }
    }

    public void replaceSlotWithId(int id) {
        if (id >= 0) {
            int s;
            if ((s = this.getSlot(id)) >= 0) {
                var movedCrate = crate(this.selected);
                setCrate(s, null);
                this.slots[s] = this.slots[this.selected];
                if (movedCrate != null) setCrate(s, movedCrate);
                this.count[s] = this.count[this.selected];
            }
            setCrate(this.selected, null);
            this.slots[this.selected] = id;
            this.count[this.selected] = 1;
        }
    }

    // Inventory.java
    public boolean addResource(int id) {
        if (id < 0 || id == PACKED_TURRET_ID) return false;

        // --- normalize empties so we don't "see" ghost ids ---
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            if (count[i] <= 0) {
                slots[i] = -1;
                count[i] = 0;
            }
        }
        for (int i = 0; i < gridSlots.length; i++) {
            if (gridCount[i] <= 0) {
                gridSlots[i] = -1;
                gridCount[i] = 0;
            }
        }

        final int CAP = net.classicremastered.minecraft.level.itemstack.Item.stackLimit(id);

        // --- pass 1: merge into existing stacks (hotbar first, then grid) ---
        for (int s = 0; s < HOTBAR_SIZE; s++) {
            if (slots[s] == id && count[s] < CAP) {
                count[s]++;
                popTime[s] = POP_TIME_DURATION;
                return true;
            }
        }
        // grid merge
        for (int g = 0; g < gridSlots.length; g++) {
            if (gridSlots[g] == id && gridCount[g] < CAP) {
                gridCount[g]++;
                gridPopTime[g] = POP_TIME_DURATION;
                return true;
            }
        }

        // --- pass 2: first empty hotbar slot ---
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            if (slots[i] < 0 || count[i] <= 0) {
                slots[i] = id;
                count[i] = 1;
                popTime[i] = POP_TIME_DURATION;
                return true;
            }
        }

        // --- pass 3: first empty grid slot ---
        for (int g = 0; g < gridSlots.length; g++) {
            if (gridSlots[g] < 0 || gridCount[g] <= 0) {
                gridSlots[g] = id;
                gridCount[g] = 1;
                gridPopTime[g] = POP_TIME_DURATION;
                return true;
            }
        }

        return false; // inventory full
    }

    public void tick() {
        for (int i = 0; i < this.popTime.length; ++i) if (this.popTime[i] > 0) --this.popTime[i];
        for (int i = 0; i < this.gridPopTime.length; ++i)
            if (this.gridPopTime[i] > 0) --this.gridPopTime[i];
    }

    public boolean removeResource(int id) {
        if (id == PACKED_TURRET_ID) return false;
        int s = this.getSlot(id);
        if (s >= 0) {
            if (this.count[s] <= 0) {
                this.slots[s] = -1;
                return false;
            }
            --this.count[s];
            if (this.count[s] <= 0) {
                this.count[s] = 0;
                this.slots[s] = -1;
            }
            return true;
        }
        // Also check grid (optional)
        int g = getGridSlot(id);
        if (g >= 0) {
            if (this.gridCount[g] <= 0) {
                this.gridSlots[g] = -1;
                return false;
            }
            --this.gridCount[g];
            if (this.gridCount[g] <= 0) {
                this.gridCount[g] = 0;
                this.gridSlots[g] = -1;
            }
            return true;
        }
        return false;
    }

    // ----------------- New: global GUI API (hotbar + grid) -----------------

    /** Total GUI-visible slots = 9 hotbar + grid size. */
    public int totalSize() {
        return HOTBAR_SIZE + gridSlots.length;
    }

    /** Return id at global index (0..totalSize-1). */
    public int getId(int globalSlot) {
        if (globalSlot < 0) return -1;
        if (globalSlot < HOTBAR_SIZE) return slots[globalSlot];
        int g = globalSlot - HOTBAR_SIZE;
        if (g < 0 || g >= gridSlots.length) return -1;
        return gridSlots[g];
    }

    /** Return count at global index. */
    public int getCount(int globalSlot) {
        if (globalSlot < 0) return 0;
        if (globalSlot < HOTBAR_SIZE) return count[globalSlot];
        int g = globalSlot - HOTBAR_SIZE;
        if (g < 0 || g >= gridSlots.length) return 0;
        return gridCount[g];
    }

    /** Set a stack, retaining an oversized remainder from a legacy slot when reduced. */
    public void setIdCount(int globalSlot, int id, int cnt) {
        setCrate(globalSlot, null);
        int limit = net.classicremastered.minecraft.level.itemstack.Item.stackLimit(id);
        if (getId(globalSlot) == id) limit = Math.max(limit, getCount(globalSlot));
        setLoadedStack(globalSlot, id, Math.min(limit, cnt));
    }

    public void setLoadedStack(int globalSlot, int id, int cnt) {
        setCrate(globalSlot, null);
        cnt = id > 0 ? Math.max(0, Math.min(99, cnt)) : 0;
        if (cnt == 0) id = -1;
        if (globalSlot < 0) return;
        if (globalSlot < HOTBAR_SIZE) {
            slots[globalSlot] = id;
            count[globalSlot] = cnt;
            popTime[globalSlot] = (cnt > 0) ? POP_TIME_DURATION : 0;
            return;
        }
        int g = globalSlot - HOTBAR_SIZE;
        if (g < 0 || g >= gridSlots.length) return;
        gridSlots[g] = id;
        gridCount[g] = cnt;
        gridPopTime[g] = (cnt > 0) ? POP_TIME_DURATION : 0;
    }

    public static void splitOversized(
            int size,
            java.util.function.IntUnaryOperator ids,
            java.util.function.IntUnaryOperator counts,
            StackWriter writer) {
        int free = 0;
        for (int slot = 0; slot < size; slot++) {
            int id = ids.applyAsInt(slot);
            if (id <= 0) continue;
            int limit = net.classicremastered.minecraft.level.itemstack.Item.stackLimit(id);
            int count = counts.applyAsInt(slot);
            while (count > limit) {
                while (free < size && counts.applyAsInt(free) > 0) free++;
                if (free == size) return;
                int moved = Math.min(limit, count - limit);
                writer.set(free++, id, moved);
                count -= moved;
                writer.set(slot, id, count);
            }
        }
    }

    @FunctionalInterface
    public interface StackWriter {
        void set(int slot, int id, int count);
    }

    /** Increment one item at global index (creates stack if empty). */
    public void addOneTo(int globalSlot, int id) {
        if (globalSlot < 0) return;
        if (globalSlot < HOTBAR_SIZE) {
            if (slots[globalSlot] <= 0) slots[globalSlot] = id;
            if (count[globalSlot]
                    < net.classicremastered.minecraft.level.itemstack.Item.stackLimit(
                            slots[globalSlot])) count[globalSlot]++;
            popTime[globalSlot] = POP_TIME_DURATION;
            return;
        }
        int g = globalSlot - HOTBAR_SIZE;
        if (g < 0 || g >= gridSlots.length) return;
        if (gridSlots[g] <= 0) gridSlots[g] = id;
        if (gridCount[g] < net.classicremastered.minecraft.level.itemstack.Item.stackLimit(id))
            gridCount[g]++;
        gridPopTime[g] = POP_TIME_DURATION;
    }

    /** True if global index points to hotbar (0..8). */
    public boolean isHotbarIndex(int globalSlot) {
        return globalSlot >= 0 && globalSlot < HOTBAR_SIZE;
    }

    /** Resize grid (e.g., to 27/36). Keeps stacks. */
    public void resizeGrid(int newSize) {
        if (newSize < 0) newSize = 0;
        if (newSize == gridSlots.length) return;

        int[] ns = new int[newSize];
        int[] nc = new int[newSize];
        int[] np = new int[newSize];
        for (int i = 0; i < newSize; i++) {
            if (i < gridSlots.length) {
                ns[i] = gridSlots[i];
                nc[i] = gridCount[i];
                np[i] = gridPopTime[i];
            } else {
                ns[i] = -1;
                nc[i] = 0;
                np[i] = 0;
            }
        }
        if (crates != null) {
            int retainedSlots = HOTBAR_SIZE + newSize;
            crates.keySet().removeIf(slot -> slot >= retainedSlots);
        }
        gridSlots = ns;
        gridCount = nc;
        gridPopTime = np;
    }

    // ----------------- private grid helpers -----------------
    private int getGridSlot(int id) {
        for (int i = 0; i < gridSlots.length; ++i) if (gridSlots[i] == id) return i;
        return -1;
    }

    private boolean addResourceToGrid(int id) {
        int g = getGridSlot(id);
        if (g >= 0
                && gridCount[g]
                        < net.classicremastered.minecraft.level.itemstack.Item.stackLimit(id)) {
            gridSlots[g] = id;
            gridCount[g]++;
            gridPopTime[g] = POP_TIME_DURATION;
            return true;
        }
        for (int i = 0; i < gridSlots.length; ++i) {
            if (gridSlots[i] < 0) {
                gridSlots[i] = id;
                gridCount[i] = 1;
                gridPopTime[i] = POP_TIME_DURATION;
                return true;
            }
        }
        return false;
    }
}
