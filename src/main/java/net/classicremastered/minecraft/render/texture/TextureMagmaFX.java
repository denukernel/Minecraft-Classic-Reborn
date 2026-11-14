package net.classicremastered.minecraft.render.texture;

import net.classicremastered.minecraft.level.tile.Block;

import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

public final class TextureMagmaFX extends TextureFX {
    private static final int SIZE = 16;
    private static final int FRAME_TICKS = 8;
    private final int[] pixels;
    private int tick;

    public TextureMagmaFX() throws IOException {
        super(Block.MAGMA.textureId);
        try (InputStream input =
                TextureMagmaFX.class.getResourceAsStream("/textures/block/magma.png")) {
            if (input == null) throw new IOException("Missing magma block texture");
            var image = ImageIO.read(input);
            pixels = image.getRGB(0, 0, SIZE, image.getHeight(), null, 0, SIZE);
        }
    }

    @Override
    public void animate() {
        int frameSize = SIZE * SIZE;
        int start = tick / FRAME_TICKS * frameSize;
        int next = (start + frameSize) % pixels.length;
        int blend = tick % FRAME_TICKS;
        for (int i = 0; i < frameSize; i++) {
            int from = pixels[start + i];
            int to = pixels[next + i];
            int r = interpolate(from >> 16, to >> 16, blend);
            int g = interpolate(from >> 8, to >> 8, blend);
            int b = interpolate(from, to, blend);
            if (anaglyph) {
                int gray = (r * 30 + g * 59 + b * 11) / 100;
                g = (r * 30 + g * 70) / 100;
                b = (r * 30 + b * 70) / 100;
                r = gray;
            }
            textureData[i * 4] = (byte) r;
            textureData[i * 4 + 1] = (byte) g;
            textureData[i * 4 + 2] = (byte) b;
            textureData[i * 4 + 3] = (byte) 255;
        }
        tick = (tick + 1) % (pixels.length / frameSize * FRAME_TICKS);
    }

    private static int interpolate(int from, int to, int blend) {
        return ((from & 255) * (FRAME_TICKS - blend) + (to & 255) * blend) / FRAME_TICKS;
    }
}
