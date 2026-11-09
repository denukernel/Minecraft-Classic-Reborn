package net.classicremastered.minecraft.render.texture;

public final class TextureFireFX extends TextureFX {
    private static final int[] TONGUES = {5,8,12,15,10,6,8,12,16,12,8,5,9,14,10,6};
    private int frame;
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

        frame++;
        // Convert to RGBA bytes
        for (int i = 0; i < 256; i++) {
            float val = this.current[i] * 1.8F;
            if (val > 1.0F) val = 1.0F;
            if (val < 0.0F) val = 0.0F;

            int r = (int)(val * 155.0F + 100.0F);
            int g = (int)(val * val * 255.0F);
            int b = (int)(Math.pow(val, 10) * 255.0F);
            int x = i % 16, y = 15 - i / 16;
            int drift = (int) Math.round(1.4 * Math.sin(frame * .16 + y * .2));
            int tip = TONGUES[Math.floorMod(x + drift, 16)];
            tip += (int) Math.round(1.5 * Math.sin(frame * .21 + x * .8));
            int a = y < tip && (y < 3 || val > .25f || y < tip - 1) ? 255 : 0;
            if (y > 3) b /= 3;

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
