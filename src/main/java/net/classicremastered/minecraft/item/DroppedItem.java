// File: src/com/mojang/minecraft/item/DroppedItem.java
package net.classicremastered.minecraft.item;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.render.ShapeRenderer;
import net.classicremastered.minecraft.render.TextureManager;
import net.classicremastered.util.MathHelper;

import org.lwjgl.opengl.GL11;

public class DroppedItem extends Entity {
    public int itemId;
    public int life = 6000; // ~5 mins

    public DroppedItem(Level level, float x, float y, float z, int itemId) {
        super(level);
        this.itemId = itemId;
        setPos(x, y, z);
        this.bb = new AABB(x - 0.25F, y - 0.25F, z - 0.25F, x + 0.25F, y + 0.25F, z + 0.25F);
        setSize(0.25F, 0.25F);
    }

    @Override
    public void tick() {
        super.tick();
        if (!onGround) yd -= 0.04F;
        move(xd, yd, zd);
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

        if (p.inventory.addResource(idToAdd)) { // fixed: add remapped ID
            if (this.level != null) {
                float pitch = 1.0F + (MathHelper.random.nextFloat() - 0.5F) * 0.2F;
                this.level.playSound("random/pop", this.x, this.y, this.z, 0.2F, pitch);
            }
            remove();
        }
    }

    @Override
    public void render(TextureManager tm, float partial) {
        int lookupId = this.itemId;

        // fixed: render from Item.items even if <256 (drop visuals use original ID)
        net.classicremastered.minecraft.level.itemstack.Item it =
            net.classicremastered.minecraft.level.itemstack.Item.items[lookupId];
        if (it == null) return;

        GL11.glPushMatrix();
        GL11.glTranslatef(xo + (x - xo) * partial,
                          yo + (y - yo) * partial + 0.1F,
                          zo + (z - zo) * partial);
        GL11.glRotatef((System.currentTimeMillis() / 10) % 360, 0, 1, 0);
        GL11.glScalef(0.03F, 0.03F, 0.1F);
        GL11.glDisable(GL11.GL_CULL_FACE);
        it.renderIcon(tm, ShapeRenderer.instance);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glPopMatrix();
    }

    @Override
    public boolean isPickable() { return false; }
    @Override
    public boolean isPushable() { return true; }
}
