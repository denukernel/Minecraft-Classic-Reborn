package net.classicremastered.minecraft.render.texture;

public class TextureFX {
    public TextureFX(int textureID) {
        this.textureId = textureID;
    }

    // 16×16 RGBA = 16 * 16 * 4 = 1024
    public byte[] textureData = new byte[1024];

    // Which cell in terrain.png (or particles.png, etc.) this animation is bound to
    public int textureId;

    // Whether to apply anaglyph adjustments (red/blue shift for 3D mode)
    public boolean anaglyph = false;

    // Called each tick to update `textureData`
    public void animate() {
    }
}
