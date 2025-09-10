package net.classicremastered.minecraft.render.texture;

public final class TextureFireFX extends TextureFX {
    private float[] current = new float[320]; // 16×20
    private float[] last    = new float[320];

    public TextureFireFX(int atlasIndex) {
        super(atlasIndex); // e.g., Block.FIRE.textureId
    }

    @Override
    public void animate() {
        // Fire simulation update
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 20; y++) {
                int idx = x + y * 16;
                float accum = this.current[x + ((y + 1) % 20) * 16] * 18.0F;
                int count = 18;

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = 0; dy <= 1; dy++) {
                        int xx = x + dx;
                        int yy = y + dy;
                        if (xx >= 0 && xx < 16 && yy >= 0 && yy < 20) {
                            accum += this.current[xx + yy * 16];
                        }
                        count++;
                    }
                }
                this.last[idx] = accum / (count * 1.06F);

                if (y == 19) {
                    this.last[idx] = (float)(Math.random() * Math.random() * Math.random() * 4.0D
                                             + Math.random() * 0.1D + 0.2D);
                }
            }
        }

        float[] tmp = this.last;
        this.last = this.current;
        this.current = tmp;

        // Convert to RGBA bytes
        for (int i = 0; i < 256; i++) {
            float val = this.current[i] * 1.8F;
            if (val > 1.0F) val = 1.0F;
            if (val < 0.0F) val = 0.0F;

            int r = (int)(val * 155.0F + 100.0F);
            int g = (int)(val * val * 255.0F);
            int b = (int)(Math.pow(val, 10) * 255.0F);
            int a = (val < 0.5F) ? 0 : 255;

            if (this.anaglyph) {
                int rr = (r * 30 + g * 59 + b * 11) / 100;
                int gg = (r * 30 + g * 70) / 100;
                int bb = (r * 30 + b * 70) / 100;
                r = rr; g = gg; b = bb;
            }

            this.textureData[i * 4 + 0] = (byte) r;
            this.textureData[i * 4 + 1] = (byte) g;
            this.textureData[i * 4 + 2] = (byte) b;
            this.textureData[i * 4 + 3] = (byte) a;
        }
    }
}
