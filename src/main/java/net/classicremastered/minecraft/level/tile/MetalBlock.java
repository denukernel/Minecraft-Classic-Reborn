package net.classicremastered.minecraft.level.tile;

public final class MetalBlock extends Block {
    private final int topTexture;
    private final int bottomTexture;

    public MetalBlock(int id, int sideTexture, int topTexture, int bottomTexture) {
        super(id, sideTexture, "Metalic Block");
        this.topTexture = topTexture;
        this.bottomTexture = bottomTexture;
    }

    @Override
    protected int getTextureId(int face) {
        return switch (face) {
            case 0 -> bottomTexture;
            case 1 -> topTexture;
            default -> textureId;
        };
    }
}
