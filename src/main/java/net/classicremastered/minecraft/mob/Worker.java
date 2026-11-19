package net.classicremastered.minecraft.mob;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.entity.DroppedBlock;
import net.classicremastered.minecraft.entity.DroppedItem;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.itemstack.Equipment;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.level.itemstack.ItemRegistry;
import net.classicremastered.minecraft.level.itemstack.PackedTurret;
import net.classicremastered.minecraft.level.itemstack.TurretItems;
import net.classicremastered.minecraft.level.tile.BuilderDoorBlock;
import net.classicremastered.minecraft.player.DeathMessages;
import net.classicremastered.minecraft.player.Inventory;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.nbt.CompoundTag;
import net.classicremastered.nbt.ListTag;

public abstract class Worker extends HumanoidMob implements Companion {
    private static final long serialVersionUID = 1L;
    public static final int PRICE = 30;
    private String owner = "";
    private final Inventory pack = new Inventory();
    private transient String activity = "Ready";
    private transient Mob lastHostileAttacker;
    private transient int lastHostileHit;

    protected Worker(Level level, float x, float y, float z, String role) {
        super(level, x, y, z);
        ItemRegistry.bootstrap();
        customName = role;
        textureName = "/char.png";
        allowAlpha = true;
        helmet = armor = legacyArmor = false;
        equipmentRolled = persistent = true;
        setSize(.6f, 1.8f);
        heightOffset = 1.62f;
        restoreHealth(20);
        equipment[0] = Equipment.GOLDEN_HELMET + 256;
        pack.resizeGrid(0);
        if (level != null && level.player instanceof Player player)
            assignOwner(player);
        setPos(x, y, z);
    }

    @Override
    public final Mob mob() {
        return this;
    }

    @Override
    public final Inventory pack() {
        return pack;
    }

    @Override
    public final void assignOwner(Player player) {
        owner = CrossbowTurret.playerName(player);
    }

    @Override
    public final boolean canControl(Player player) {
        return Companion.canControl(this, owner, player);
    }

    public final boolean worksWith(Worker worker) {
        return worker.level == level && worker.health > 0 && !worker.removed && owner.equals(worker.owner);
    }

    boolean canBeEscortedBy(Defender defender) {
        return level == defender.level && !removed && health > 0 && owner.equals(defender.owner);
    }

    @Override
    public final String activity() {
        return activity == null ? "Ready" : activity;
    }

    public final void setActivity(String activity) {
        this.activity = activity;
    }

    public final int roomFor(int raw) {
        int total = 0;
        for (int slot = 0; slot < pack.totalSize(); slot++) {
            int count = pack.getCount(slot);
            if (count == 0 || pack.getId(slot) == raw)
                total += Item.stackLimit(raw) - count;
        }
        return total;
    }

    @Override
    public void move(float dx, float dy, float dz) {
        if (health > 0 && !isInKnockback() && dx * dx + dz * dz > .0001f)
            BuilderDoorBlock.openFor(this, dx, dz);
        super.move(dx, dy, dz);
    }

    Mob recentHostileAttacker() {
        return lastHostileAttacker != null
                        && lastHostileAttacker.level == level
                        && tickCount - lastHostileHit <= 100
                        && Defender.isWorkerEnemy(lastHostileAttacker)
                ? lastHostileAttacker
                : null;
    }

    @Override
    public void hurt(Entity source, int damage) {
        int previousHealth = health;
        super.hurt(source, damage);
        if (health > 0
                && health < previousHealth
                && DeathMessages.resolveAttacker(source) instanceof Mob attacker
                && attacker.level == level
                && Defender.isWorkerEnemy(attacker)) {
            lastHostileAttacker = attacker;
            lastHostileHit = tickCount;
        }
    }

    @Override
    public void die(Entity killer) {
        if (dead || removed)
            return;
        if (level != null && !level.isNetworkMode() && level.doMobLoot) {
            for (int i = 0; i < pack.totalSize(); i++) {
                int id = pack.getId(i), count = pack.getCount(i);
                if (id <= 0 || count <= 0)
                    continue;
                level.addEntity(id < 256
                        ? new DroppedBlock(level, x, y, z, id, count)
                        : new DroppedItem(level, x, y, z, id - 256, count).withArmorWear(pack.armorWear(i))
                                .withCrate(pack.crate(i)));
            }
        }
        for (int i = 0; i < pack.totalSize(); i++)
            pack.setIdCount(i, -1, 0);
        super.die(killer);
    }

    @Override
    public void savePersistence(CompoundTag tag) {
        super.savePersistence(tag);
        tag.putString("WorkerOwner", owner);
        ListTag stacks = new ListTag("WorkerPack", (byte) 10);
        for (int slot = 0; slot < pack.totalSize(); slot++) {
            if (pack.getCount(slot) <= 0)
                continue;
            CompoundTag stack = new CompoundTag(null);
            stack.putInt("Slot", slot);
            stack.putInt("Id", pack.getId(slot));
            stack.putInt("Count", pack.getCount(slot));
            stack.putInt("Wear", pack.armorWear(slot));
            if (pack.crate(slot) != null)
                stack.put(pack.crate(slot).save());
            stacks.add(stack);
        }
        tag.put(stacks);
    }

    @Override
    public void loadPersistence(CompoundTag tag) {
        super.loadPersistence(tag);
        if (tag.hasKey("WorkerOwner"))
            owner = tag.getString("WorkerOwner");
        for (int slot = 0; slot < pack.totalSize(); slot++)
            pack.setIdCount(slot, -1, 0);
        for (var entry : tag.getList("WorkerPack").list) {
            if (!(entry instanceof CompoundTag stack))
                continue;
            int slot = stack.getInt("Slot"), raw = stack.getInt("Id");
            if (slot < 0 || slot >= pack.totalSize() || raw <= 0)
                continue;
            pack.setIdCount(slot, raw, stack.getInt("Count"));
            pack.setArmorWear(slot, stack.getInt("Wear"));
            if (pack.getId(slot) == TurretItems.PACKED + 256)
                pack.setCrate(slot, PackedTurret.load(stack));
        }
        persistent = true;
    }
}
