package net.classicremastered.minecraft.level;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.entity.Arrow;
import net.classicremastered.minecraft.entity.HeldBlockEntity;
import net.classicremastered.minecraft.mob.CrossbowTurret;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.Frustrum;
import net.classicremastered.minecraft.render.TextureManager;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

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
        if (this.width == 0) this.width = 1;
        if (this.depth == 0) this.depth = 1;
        if (this.height == 0) this.height = 1;

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
        synchronized (this.all) {
            if (this.all.contains(e))
                return;
            e.resetInterpolation();
            this.all.add(e);
        }
        if (infiniteMode) {
            synchronized (this.entityGrid[0]) {
                this.entityGrid[0].add(e);
            }
        } else {
            this.slot.init(e.x, e.y, e.z).add(e);
        }
        e.xOld = e.x;
        e.yOld = e.y;
        e.zOld = e.z;
        e.blockMap = this;
    }

    public void remove(Entity e) {
        e.ejectPassengers();
        if (e.riding != null)
            e.unmount();
        if (infiniteMode) {
            synchronized (this.entityGrid[0]) {
                this.entityGrid[0].remove(e);
            }
        } else {
            this.slot.init(e.xOld, e.yOld, e.zOld).remove(e);
        }
        synchronized (this.all) {
            this.all.remove(e);
        }
    }

    public void moved(Entity e) {
        if (infiniteMode) return; // all in one bucket
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

    public List<Entity> getEntities(
            Entity except, float x0, float y0, float z0, float x1, float y1, float z1) {
        this.tmp.clear();
        return getEntities(except, x0, y0, z0, x1, y1, z1, this.tmp);
    }

    public List<Entity> getEntities(
            Entity except,
            float x0,
            float y0,
            float z0,
            float x1,
            float y1,
            float z1,
            List<Entity> out) {
        if (infiniteMode) {
            Entity[] entities;
            synchronized (this.all) {
                entities = this.all.toArray(new Entity[0]);
            }
            for (Entity e : entities) {
                if (e != null && e != except && e.intersects(x0, y0, z0, x1, y1, z1)) {
                    out.add(e);
                }
            }
            return out;
        }

        BlockMap$Slot a = this.slot.init(x0, y0, z0);
        BlockMap$Slot b = this.slot2.init(x1, y1, z1);

        for (int xs = BlockMap$Slot.getXSlot(a) - 1; xs <= BlockMap$Slot.getXSlot(b) + 1; ++xs) {
            for (int ys = BlockMap$Slot.getYSlot(a) - 1;
                    ys <= BlockMap$Slot.getYSlot(b) + 1;
                    ++ys) {
                for (int zs = BlockMap$Slot.getZSlot(a) - 1;
                        zs <= BlockMap$Slot.getZSlot(b) + 1;
                        ++zs) {
                    if (xs >= 0
                            && ys >= 0
                            && zs >= 0
                            && xs < this.width
                            && ys < this.depth
                            && zs < this.height) {
                        List<Entity> cell =
                                this.entityGrid[(zs * this.depth + ys) * this.width + xs];
                        Entity[] entities;
                        synchronized (cell) {
                            entities = cell.toArray(new Entity[0]);
                        }
                        for (Entity e : entities) {
                            if (e != null && e != except && e.intersects(x0, y0, z0, x1, y1, z1)) {
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
            synchronized (this.all) {
                for (int i = 0; i < all.size(); i++) {
                    Entity e = all.get(i);
                    if (!e.isCreativeModeAllowed()) {
                        all.remove(i--);
                        synchronized (this.entityGrid[0]) {
                            this.entityGrid[0].remove(e);
                        }
                    }
                }
            }
            return;
        }
        for (List<Entity> cell : this.entityGrid) {
            synchronized (cell) {
                cell.removeIf(e -> !e.isCreativeModeAllowed());
            }
        }
    }

    public void clear() {
        for (List<Entity> cell : this.entityGrid) {
            synchronized (cell) {
                cell.clear();
            }
        }
        synchronized (this.all) {
            this.all.clear();
        }
    }

    public List<Entity> getEntities(Entity except, AABB box) {
        this.tmp.clear();
        return getEntities(except, box.x0, box.y0, box.z0, box.x1, box.y1, box.z1, this.tmp);
    }

    public List<Entity> getEntities(Entity except, AABB box, List<Entity> out) {
        return getEntities(except, box.x0, box.y0, box.z0, box.x1, box.y1, box.z1, out);
    }

    public void tickAll() {
        Entity[] entities;
        synchronized (this.all) {
            entities = this.all.toArray(new Entity[0]);
        }
        List<Mob> mountedMobs = null;
        for (Entity entity : entities) {
            if (entity instanceof Mob mob
                    && !(mob instanceof Player)
                    && mob.riding != null
                    && !(mob.riding instanceof CrossbowTurret)) {
                if (mountedMobs == null) mountedMobs = new ArrayList<>();
                mountedMobs.add(mob);
                continue;
            }
            if (entity != null && !(entity instanceof CrossbowTurret)
                    && !(entity instanceof Arrow arrow && arrow.isTurretProjectile()))
                tickEntity(entity);
        }
        // Aim and fire from the mount's final position, regardless of insertion order.
        if (mountedMobs != null) for (Mob mob : mountedMobs) tickEntity(mob);
        for (Entity entity : entities) {
            if (entity != null && !(entity.riding instanceof CrossbowTurret))
                syncPassenger(entity);
        }
        // Aim and resolve flight against this tick's final mob positions.
        for (Entity entity : entities)
            if (entity instanceof CrossbowTurret)
                tickEntity(entity);
        for (Entity entity : entities)
            if (entity != null && entity.riding instanceof CrossbowTurret)
                syncPassenger(entity);
        for (Entity entity : entities)
            if (entity instanceof Arrow arrow && arrow.isTurretProjectile())
                tickEntity(entity);
    }

    private void syncPassenger(Entity entity) {
        if (!entity.removed && entity.riding != null) {
            entity.syncRidingPosition();
            moved(entity);
        }
    }

    private void tickEntity(Entity entity) {
        if (!entity.removed && entity.level != null && !entity.level.isEntityActive(entity)) {
            entity.resetInterpolation();
            return;
        }
        if (!entity.removed
                && !net.classicremastered.minecraft.level.itemstack.GunEntityDrag.tick(entity))
            entity.tick();
        if (entity.removed) {
            remove(entity);
        } else if (!infiniteMode && ((int) (entity.xOld / 16f) != (int) (entity.x / 16f)
                || (int) (entity.yOld / 16f) != (int) (entity.y / 16f)
                || (int) (entity.zOld / 16f) != (int) (entity.z / 16f))) {
            moved(entity);
        }
    }

    public void render(Vec3D cam, Frustrum fr, TextureManager tex, float partial) {
        // Render all HeldBlockEntity first, bypassing frustum check and infiniteMode
        // check
        Entity[] entities;
        synchronized (this.all) {
            entities = this.all.toArray(new Entity[0]);
        }
        for (Entity e : entities) {
            if (e instanceof HeldBlockEntity && !e.removed) {
                e.render(tex, partial);
            }
        }

        // Draw gravity gun and physics gun beams for grabbed mobs
        for (Entity e : entities) {
            if (e instanceof Player) {
                Player p = (Player) e;
                net.classicremastered.minecraft.render.HeldItemRenderer.renderWorld(p, partial);
                int selId = p.inventory.getSelected();
                if (selId >= 256 && (selId - 256) == 11) {
                    Entity grabbed =
                            net.classicremastered.minecraft.level.itemstack.GravityGunItem
                                    .getGrabbedEntity(p);
                    if (grabbed != null && !grabbed.removed) {
                        net.classicremastered.minecraft.render.GunBeamRenderer.render(
                                p, grabbed, partial, true);
                    }
                }
                if (selId >= 256 && (selId - 256) == 10) {
                    Entity grabbed =
                            net.classicremastered.minecraft.level.itemstack.PhysicsGunItem
                                    .getGrabbedEntity(p);
                    if (grabbed != null && !grabbed.removed) {
                        net.classicremastered.minecraft.render.GunBeamRenderer.render(
                                p, grabbed, partial, false);
                    }
                }
                net.classicremastered.minecraft.render.GrappleRopeRenderer.render(p, partial);
            }
        }

        if (infiniteMode) {
            for (Entity e : entities) {
                if (e != null
                        && !e.removed
                        && !(e instanceof HeldBlockEntity)
                        && e.shouldRender(cam)) {
                    AABB bb = e.bb;
                    if (bb != null
                            && fr.isBoxInFrustrum(bb.x0, bb.y0, bb.z0, bb.x1, bb.y1, bb.z1)) {
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
                            Entity[] cellEntities;
                            synchronized (cell) {
                                cellEntities = cell.toArray(new Entity[0]);
                            }
                            for (Entity e : cellEntities) {
                                if (e != null
                                        && !e.removed
                                        && !(e instanceof HeldBlockEntity)
                                        && e.shouldRender(cam)) {
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
