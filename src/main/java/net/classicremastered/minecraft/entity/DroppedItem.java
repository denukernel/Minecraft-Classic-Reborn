// File: src/com/mojang/minecraft/item/DroppedItem.java
package net.classicremastered.minecraft.entity;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.render.TextureManager;
import net.classicremastered.util.MathHelper;

import org.lwjgl.opengl.GL11;

public class DroppedItem extends Entity {
    private static final long serialVersionUID = -4202125431351869001L;
    public int itemId;
    public int life = 6000; // ~5 mins
    public int count = 1;
    public net.classicremastered.minecraft.level.itemstack.PackedTurret crate;

    public DroppedItem withCrate(
            net.classicremastered.minecraft.level.itemstack.PackedTurret crate) {
        this.crate = crate;
        return this;
    }

    public DroppedItem(Level level, float x, float y, float z, int itemId) {
        this(level, x, y, z, itemId, 1);
    }

    public DroppedItem(Level level, float x, float y, float z, int itemId, int count) {
        super(level);
        this.itemId = itemId;
        this.count = count;
        setSize(.25f, .25f);
        heightOffset = bbHeight / 2;
        setPos(x, y, z);
        xo = x;
        yo = y;
        zo = z;
        makeStepSound = false;
    }

    @Override
    public void tick() {
        if (removed) return;
        Item item = itemId >= 0 && itemId < Item.items.length ? Item.items[itemId] : null;
        boolean fireResistant = item != null && item.isFireResistantDrop();
        if (fireDestroysStack(fireResistant)) return;
        heightOffset = bbHeight / 2;
        super.tick();
        yd -= 0.04F;
        move(xd, yd, zd);
        if (fireDestroysStack(fireResistant) || cactusDamagesStack()) return;
        yd *= 0.98F; xd *= 0.98F; zd *= 0.98F;
        if (onGround) { xd *= 0.7F; zd *= 0.7F; yd *= -0.5F; }
        if (--life <= 0) remove();
    }

    @Override
    public void playerTouch(Entity who) {
        if (removed) return;
        if (!(who instanceof net.classicremastered.minecraft.player.Player)) return;
        if (life > 5990) return;

        net.classicremastered.minecraft.player.Player p =
            (net.classicremastered.minecraft.player.Player) who;

        int idToAdd = this.itemId;

        // fixed: if an Item exists at this ID, remap it into item space (offset 256+)
        net.classicremastered.minecraft.level.itemstack.Item it =
            net.classicremastered.minecraft.level.itemstack.Item.items[itemId];
        if (it != null) {
            idToAdd = 256 + itemId; // fixed: prevents collision with Block IDs
        }

        int added = 0;
        for (int i = 0; i < (crate == null ? this.count : 1); i++) {
            if (crate != null ? p.inventory.addCrate(crate) : p.inventory.addResource(idToAdd)) {
                added++;
            } else {
                break;
            }
        }

        if (added > 0) {
            if (this.level != null) {
                float pitch = 1.0F + (MathHelper.random.nextFloat() - 0.5F) * 0.2F;
                this.level.playSound("random/pop", this.x, this.y, this.z, 0.2F, pitch);
                this.level.addEntity(new TakeEntityAnim(this.level, this, p));
            }
            this.count = crate == null ? this.count - added : 0;
            if (this.count <= 0) {
                remove();
            }
        }
    }

    @Override
    public void render(TextureManager tm, float partial) {
        int lookupId = this.itemId;

        // fixed: render from Item.items even if <256 (drop visuals use original ID)
        net.classicremastered.minecraft.level.itemstack.Item it =
            net.classicremastered.minecraft.level.itemstack.Item.items[lookupId];
        if (it == null) return;

        float age = 6000 - life + partial;
        float hover = MathHelper.sin(age / 10) * .1f + .1f;
        GL11.glPushMatrix();
        GL11.glTranslatef(xo + (x - xo) * partial,
                          yo + (y - yo) * partial + .1875f + hover,
                          zo + (z - zo) * partial);
        GL11.glRotatef(age * 3, 0, 1, 0);
        if (net.classicremastered.minecraft.model.GunModel.isGun(lookupId)) {
            GL11.glScalef(.6f,.6f,.6f);
            net.classicremastered.minecraft.model.GunModel.render(lookupId, getRenderBrightness(partial));
            GL11.glPopMatrix();
            return;
        }
        GL11.glScalef(.5f,.5f,.5f);
        net.classicremastered.minecraft.render.ItemMeshRenderer.renderItem(tm,it,getRenderBrightness(partial));
        GL11.glPopMatrix();
    }

    @Override
    public boolean isPickable() { return false; }
    @Override
    public boolean isPushable() { return true; }
}

