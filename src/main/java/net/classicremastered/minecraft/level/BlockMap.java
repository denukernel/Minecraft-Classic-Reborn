package net.classicremastered.minecraft.level;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.render.Frustrum;
import net.classicremastered.minecraft.render.TextureManager;

public class BlockMap implements Serializable {

    public static final long serialVersionUID = 0L;
    private int width;
    private int depth;
    private int height;
    private BlockMap$Slot slot = new BlockMap$Slot(this, (SyntheticClass) null);
    private BlockMap$Slot slot2 = new BlockMap$Slot(this, (SyntheticClass) null);
    public List[] entityGrid;
    public List<Entity> all = new ArrayList<>();
    private List<Entity> tmp = new ArrayList<>();

    // New: infinite toggle
    public boolean infiniteMode = false;

    public BlockMap(int w, int d, int h) {
        this.width = w / 16;
        this.depth = d / 16;
        this.height = h / 16;
        if (this.width == 0)
            this.width = 1;
        if (this.depth == 0)
            this.depth = 1;
        if (this.height == 0)
            this.height = 1;

        this.entityGrid = new ArrayList[this.width * this.depth * this.height];
        for (int x = 0; x < this.width; ++x) {
            for (int y = 0; y < this.depth; ++y) {
                for (int z = 0; z < this.height; ++z) {
                    this.entityGrid[(z * this.depth + y) * this.width + x] = new ArrayList<>();
                }
            }
        }
    }

    public void insert(Entity e) {
        this.all.add(e);
        if (infiniteMode) {
            this.entityGrid[0].add(e);
        } else {
            this.slot.init(e.x, e.y, e.z).add(e);
        }
        e.xOld = e.x;
        e.yOld = e.y;
        e.zOld = e.z;
        e.blockMap = this;
    }

    public void remove(Entity e) {
        if (infiniteMode) {
            this.entityGrid[0].remove(e);
        } else {
            this.slot.init(e.xOld, e.yOld, e.zOld).remove(e);
        }
        this.all.remove(e);
    }

    public void moved(Entity e) {
        if (infiniteMode)
            return; // all in one bucket
        BlockMap$Slot a = this.slot.init(e.xOld, e.yOld, e.zOld);
        BlockMap$Slot b = this.slot2.init(e.x, e.y, e.z);
        if (!a.equals(b)) {
            a.remove(e);
            b.add(e);
            e.xOld = e.x;
            e.yOld = e.y;
            e.zOld = e.z;
        }
    }

    public List<Entity> getEntities(Entity except, float x0, float y0, float z0, float x1, float y1, float z1) {
        this.tmp.clear();
        return getEntities(except, x0, y0, z0, x1, y1, z1, this.tmp);
    }

    public List<Entity> getEntities(Entity except, float x0, float y0, float z0, float x1, float y1, float z1,
            List<Entity> out) {
        if (infiniteMode) {
            for (Entity e : this.all) {
                if (e != except && e.intersects(x0, y0, z0, x1, y1, z1)) {
                    out.add(e);
                }
            }
            return out;
        }

        BlockMap$Slot a = this.slot.init(x0, y0, z0);
        BlockMap$Slot b = this.slot2.init(x1, y1, z1);

        for (int xs = BlockMap$Slot.getXSlot(a) - 1; xs <= BlockMap$Slot.getXSlot(b) + 1; ++xs) {
            for (int ys = BlockMap$Slot.getYSlot(a) - 1; ys <= BlockMap$Slot.getYSlot(b) + 1; ++ys) {
                for (int zs = BlockMap$Slot.getZSlot(a) - 1; zs <= BlockMap$Slot.getZSlot(b) + 1; ++zs) {
                    if (xs >= 0 && ys >= 0 && zs >= 0 && xs < this.width && ys < this.depth && zs < this.height) {
                        List<Entity> cell = this.entityGrid[(zs * this.depth + ys) * this.width + xs];
                        for (Entity e : cell) {
                            if (e != except && e.intersects(x0, y0, z0, x1, y1, z1)) {
                                out.add(e);
                            }
                        }
                    }
                }
            }
        }
        return out;
    }

    public void removeAllNonCreativeModeEntities() {
        if (infiniteMode) {
            for (int i = 0; i < all.size(); i++) {
                if (!all.get(i).isCreativeModeAllowed()) {
                    all.remove(i--);
                    this.entityGrid[0].remove(i);
                }
            }
            return;
        }
        for (List<Entity> cell : this.entityGrid) {
            cell.removeIf(e -> !e.isCreativeModeAllowed());
        }
    }

    public void clear() {
        for (List<Entity> cell : this.entityGrid) {
            cell.clear();
        }
        this.all.clear();
    }

    public List<Entity> getEntities(Entity except, AABB box) {
        this.tmp.clear();
        return getEntities(except, box.x0, box.y0, box.z0, box.x1, box.y1, box.z1, this.tmp);
    }

    public List<Entity> getEntities(Entity except, AABB box, List<Entity> out) {
        return getEntities(except, box.x0, box.y0, box.z0, box.x1, box.y1, box.z1, out);
    }

    public void tickAll() {
        for (int i = 0; i < this.all.size(); ++i) {
            Entity e = this.all.get(i);
            e.tick();
            if (e.removed) {
                this.all.remove(i--);
                if (infiniteMode) {
                    this.entityGrid[0].remove(e);
                } else {
                    this.slot.init(e.xOld, e.yOld, e.zOld).remove(e);
                }
            } else if (!infiniteMode) {
                int sx = (int) (e.xOld / 16.0F);
                int sy = (int) (e.yOld / 16.0F);
                int sz = (int) (e.zOld / 16.0F);
                int sx2 = (int) (e.x / 16.0F);
                int sy2 = (int) (e.y / 16.0F);
                int sz2 = (int) (e.z / 16.0F);
                if (sx != sx2 || sy != sy2 || sz != sz2) {
                    moved(e);
                }
            }
        }
    }

    public void render(Vec3D cam, Frustrum fr, TextureManager tex, float partial) {
        if (infiniteMode) {
            for (Entity e : this.all) {
                if (!e.removed && e.shouldRender(cam)) {
                    AABB bb = e.bb;
                    if (bb != null && fr.isBoxInFrustrum(bb.x0, bb.y0, bb.z0, bb.x1, bb.y1, bb.z1)) {
                        e.render(tex, partial);
                    }
                }
            }
            return;
        }

        // === finite path ===
        for (int x = 0; x < this.width; ++x) {
            float x0 = (float) ((x << 4) - 2);
            float x1 = (float) ((x + 1 << 4) + 2);
            for (int y = 0; y < this.depth; ++y) {
                float y0 = (float) ((y << 4) - 2);
                float y1 = (float) ((y + 1 << 4) + 2);
                for (int z = 0; z < this.height; ++z) {
                    List<Entity> cell = this.entityGrid[(z * this.depth + y) * this.width + x];
                    if (!cell.isEmpty()) {
                        float z0 = (float) ((z << 4) - 2);
                        float z1 = (float) ((z + 1 << 4) + 2);
                        if (fr.isBoxInFrustrum(x0, y0, z0, x1, y1, z1)) {
                            for (Entity e : cell) {
                                if (e.shouldRender(cam)) {
                                    e.render(tex, partial);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // synthetic helpers
    static int getWidth(BlockMap m) {
        return m.width;
    }

    static int getDepth(BlockMap m) {
        return m.depth;
    }

    static int getHeight(BlockMap m) {
        return m.height;
    }
}
