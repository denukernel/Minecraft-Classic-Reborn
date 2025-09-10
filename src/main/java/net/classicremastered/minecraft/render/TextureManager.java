package net.classicremastered.minecraft.render;

import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.*;

import net.classicremastered.minecraft.GameSettings;
import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.render.texture.TextureFX;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipFile;

public class TextureManager
{
    public TextureManager(GameSettings settings)
    {
        this.settings = settings;

        minecraftFolder = Minecraft.mcDir;
        texturesFolder = new File(minecraftFolder, "texturepacks");

        if(!texturesFolder.exists())
        {
            texturesFolder.mkdir();
        }
    }

    public HashMap<String, Integer> textures = new HashMap<String, Integer>();
    public HashMap<Integer, BufferedImage> textureImages = new HashMap<Integer, BufferedImage>();
    public IntBuffer idBuffer = BufferUtils.createIntBuffer(1);
    public ByteBuffer textureBuffer = BufferUtils.createByteBuffer(262144);
    public List<TextureFX> animations = new ArrayList<TextureFX>();
    public GameSettings settings;

    public HashMap<String, Integer> externalTexturePacks = new HashMap<String, Integer>();

    public File minecraftFolder;
    public File texturesFolder;

    public int previousMipmapMode;

    public int load(String file)
    {
        if(textures.get(file) != null)
        {
            return textures.get(file);
        } else if(externalTexturePacks.get(file) != null) {
            return externalTexturePacks.get(file);
        } else {
            try {
                idBuffer.clear();

                GL11.glGenTextures(idBuffer);

                int textureID = idBuffer.get(0);

                if(file.endsWith(".png"))
                {
                    if(file.startsWith("##"))
                    {
                        load(load1(ImageIO.read(TextureManager.class.getResourceAsStream(file.substring(2)))), textureID);
                    } else {
                        load(ImageIO.read(TextureManager.class.getResourceAsStream(file)), textureID);
                    }

                    textures.put(file, textureID);
                } else if(file.endsWith(".zip")) {
                    ZipFile zip = new ZipFile(new File(minecraftFolder, "texturepacks/" + file));

                    String terrainPNG = "terrain.png";

                    if(zip.getEntry(terrainPNG.startsWith("/") ? terrainPNG.substring(1, terrainPNG.length()) : terrainPNG) != null)
                    {
                        load(ImageIO.read(zip.getInputStream(zip.getEntry(terrainPNG.startsWith("/") ? terrainPNG.substring(1, terrainPNG.length()) : terrainPNG))), textureID);
                    } else {
                        load(ImageIO.read(TextureManager.class.getResourceAsStream(terrainPNG)), textureID);
                    }

                    zip.close();

                    externalTexturePacks.put(file, textureID);
                }

                return textureID;
            } catch (IOException e) {
                throw new RuntimeException("!!", e);
            }
        }
    }

    public static BufferedImage load1(BufferedImage image)
    {
        int charWidth = image.getWidth() / 16;
        BufferedImage image1 = new BufferedImage(16, image.getHeight() * charWidth, 2);
        Graphics graphics = image1.getGraphics();

        for(int i = 0; i < charWidth; i++)
        {
            graphics.drawImage(image, -i << 4, i * image.getHeight(), null);
        }

        graphics.dispose();

        return image1;
    }

    public int load(BufferedImage image)
    {
        idBuffer.clear();

        GL11.glGenTextures(idBuffer);

        int textureID = idBuffer.get(0);

        load(image, textureID);

        textureImages.put(textureID, image);

        return textureID;
    }

    public void load(BufferedImage image, int textureID)
    {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureID);
        if(settings.smoothing > 0)
        {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST_MIPMAP_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 4);

            if(settings.anisotropic > 0)
            {
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, 16);
            }
        } else {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        }

        //GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);

        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = new int[width * height];
        byte[] color = new byte[width * height << 2];

        image.getRGB(0, 0, width, height, pixels, 0, width);

        for(int pixel = 0; pixel < pixels.length; pixel++)
        {
            int alpha = pixels[pixel] >>> 24;
            int red = pixels[pixel] >> 16 & 0xFF;
            int green = pixels[pixel] >> 8 & 0xFF;
            int blue = pixels[pixel] & 0xFF;

            if(settings.anaglyph)
            {
                int rgba3D = (red * 30 + green * 59 + blue * 11) / 100;

                green = (red * 30 + green * 70) / 100;
                blue = (red * 30 + blue * 70) / 100;
                red = rgba3D;
            }

            color[pixel << 2] = (byte)red;
            color[(pixel << 2) + 1] = (byte)green;
            color[(pixel << 2) + 2] = (byte)blue;
            color[(pixel << 2) + 3] = (byte)alpha;
        }

        textureBuffer.clear();
        textureBuffer.put(color);
        textureBuffer.position(0).limit(color.length);

        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, textureBuffer);

        if(settings.smoothing > 0)
        {
            if(settings.smoothing == 1)
            {
                ContextCapabilities capabilities = GLContext.getCapabilities();

                if(capabilities.OpenGL30)
                {
                    if(previousMipmapMode != settings.smoothing)
                    {
                        System.out.println("Using OpenGL 3.0 for mipmap generation.");
                    }

                    GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
                } else if(capabilities.GL_EXT_framebuffer_object) {
                    if(previousMipmapMode != settings.smoothing)
                    {
                        System.out.println("Using GL_EXT_framebuffer_object extension for mipmap generation.");
                    }

                    EXTFramebufferObject.glGenerateMipmapEXT(GL11.GL_TEXTURE_2D);
                } else if(capabilities.OpenGL14) {
                    if(previousMipmapMode != settings.smoothing)
                    {
                        System.out.println("Using OpenGL 1.4 for mipmap generation.");
                    }

                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_GENERATE_MIPMAP, GL11.GL_TRUE);
                }
            } else if(settings.smoothing == 2) {
                if(previousMipmapMode != settings.smoothing)
                {
                    System.out.println("Using custom system for mipmap generation.");
                }

                generateMipMaps(textureBuffer, width, height, false);
            }
        }

        previousMipmapMode = settings.smoothing;
    }
    // TextureManager.java
 // TextureManager.java
    public void applyBlockTilesToTerrainAtlas() {
        int terrainTex = load("/terrain.png");
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, terrainTex);

        // blit a BufferedImage (or a sub-rect) into a terrain.png cell
        java.util.function.BiConsumer<Integer, BufferedImage> putImg = (index, img) -> {
            if (img == null) return;
            if (img.getWidth() != 16 || img.getHeight() != 16) {
                BufferedImage s = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                Graphics g = s.getGraphics(); g.drawImage(img, 0, 0, 16, 16, null); g.dispose();
                img = s;
            }
            int x = (index % 16) * 16;
            int y = (index / 16) * 16;
            int[] px = new int[16 * 16];
            byte[] rgba = new byte[16 * 16 * 4];
            img.getRGB(0, 0, 16, 16, px, 0, 16);
            for (int i = 0; i < px.length; i++) {
                int a = px[i] >>> 24, r = (px[i] >> 16) & 255, g = (px[i] >> 8) & 255, b = px[i] & 255;
                int o = i << 2;
                rgba[o] = (byte) r; rgba[o + 1] = (byte) g; rgba[o + 2] = (byte) b; rgba[o + 3] = (byte) a;
            }
            ByteBuffer buf = BufferUtils.createByteBuffer(rgba.length);
            buf.put(rgba).flip();
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, x, y, 16, 16, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        };

        // load a 16×16 from classpath
        java.util.function.BiConsumer<Integer, String> put = (index, path) -> {
            try { putImg.accept(index, ImageIO.read(TextureManager.class.getResourceAsStream(path))); }
            catch (Exception ignored) {}
        };

        // ---- vanilla/classic tiles (existing) ----
        put.accept(1,  "/terrain/blocks/stone.png");
        put.accept(2,  "/terrain/blocks/dirt.png");
        put.accept(0,  "/terrain/blocks/grass_top.png");
        put.accept(3,  "/terrain/blocks/grass_side.png");
        put.accept(4,  "/terrain/blocks/planks.png");
        put.accept(5,  "/terrain/blocks/smooth_stone2.png");
        put.accept(6,  "/terrain/blocks/smooth_stone1.png");
        put.accept(7,  "/terrain/blocks/bricks.png");
        put.accept(8,  "/terrain/blocks/tnt_side.png");
        put.accept(9,  "/terrain/blocks/tnt_up.png");
        put.accept(10, "/terrain/blocks/tnt_bottom.png");
        put.accept(12, "/terrain/blocks/rose.png");
        put.accept(13, "/terrain/blocks/dandelion.png");
        put.accept(52, "/terrain/blocks/web.png");
        put.accept(18, "/terrain/blocks/sand.png");
        put.accept(19, "/terrain/blocks/gravel.png");
        put.accept(16, "/terrain/blocks/old_stone.png");
        put.accept(36, "/terrain/blocks/mossy_cobblestone.png");
        put.accept(32, "/terrain/blocks/gold_ore.png");
        put.accept(33, "/terrain/blocks/iron_ore.png");
        put.accept(34, "/terrain/blocks/coal_ore.png");
        put.accept(21, "/terrain/blocks/wood_top.png");
        put.accept(20, "/terrain/blocks/wood.png");
        put.accept(22, "/terrain/blocks/leaves.png");
        put.accept(35, "/terrain/blocks/bookshelv.png");
        put.accept(49, "/terrain/blocks/glass.png");
        put.accept(37, "/terrain/blocks/obsidian.png");
        put.accept(48, "/terrain/blocks/sponge.png");
        put.accept(40, "/terrain/blocks/gold_top.png");
        put.accept(41, "/terrain/blocks/gold_side.png");
        put.accept(42, "/terrain/blocks/gold_bottom.png");
        put.accept(43, "/terrain/blocks/iron.png");
        put.accept(44, "/terrain/blocks/iron_side.png");
        put.accept(39, "/terrain/blocks/iron_bottom.png");
        put.accept(15, "/terrain/blocks/saplings.png");
        put.accept(208, "/terrain/blocks/tile_7_34.png"); // added
        put.accept(209, "/terrain/blocks/tile_7_35.png"); // added
        put.accept(210, "/terrain/blocks/tile_7_36.png"); // added
        put.accept(211, "/terrain/blocks/tile_7_37.png"); // added
        put.accept(212, "/terrain/blocks/jukebox.png");   // added

        // ---- NEW BLOCKS (your /terrain/blocks/new_blocks/*) ----
        // Reserve a clean range 176..189 for custom tiles to avoid collisions. // added
        // Pistons
        put.accept(176, "/terrain/blocks/new_blocks/piston_top_normal.png"); // added
        put.accept(177, "/terrain/blocks/new_blocks/piston_bottom.png");     // added
        put.accept(178, "/terrain/blocks/new_blocks/piston_side.png");       // added

        // Cactus
        put.accept(179, "/terrain/blocks/new_blocks/cactus_side.png");       // added
        put.accept(180, "/terrain/blocks/new_blocks/cactus_top.png");        // added

        // Furnace
        put.accept(181, "/terrain/blocks/new_blocks/furnace_top.png");       // added
        put.accept(182, "/terrain/blocks/new_blocks/furnace_front_off.png"); // added
        put.accept(183, "/terrain/blocks/new_blocks/furnace_front_on.png");  // added
        put.accept(184, "/terrain/blocks/new_blocks/furnace_side.png");      // added

        // Beacon / Diamond / Pumpkin
        put.accept(185, "/terrain/blocks/new_blocks/beacon.png");            // added
        put.accept(186, "/terrain/blocks/new_blocks/diamond_block.png");     // added
        put.accept(187, "/terrain/blocks/new_blocks/diamond_ore.png");       // added
        put.accept(188, "/terrain/blocks/new_blocks/pumpkin_side.png");      // added
        put.accept(189, "/terrain/blocks/new_blocks/pumpkin_face_off.png");  // added

        // ---- optional: wool strip 64..79 ----
        try {
            BufferedImage strip = ImageIO.read(TextureManager.class.getResourceAsStream("/terrain/blocks/wooltextures.png"));
            if (strip != null) {
                for (int i = 0; i < 16; i++) {
                    BufferedImage sub = strip.getSubimage(i * (strip.getWidth()/16), 0, strip.getWidth()/16, strip.getHeight());
                    putImg.accept(64 + i, sub);
                }
            }
        } catch (Exception ignored) {}
    }



    public void generateMipMaps(ByteBuffer data, int width, int height, boolean test)
    {
        ByteBuffer mipData = data;

        for (int level = test ? 0 : 1; level <= 4; level++)
        {
            int parWidth = width >> level - 1;
            int mipWidth = width >> level;
            int mipHeight = height >> level;

            if(mipWidth <= 0 || mipHeight <= 0)
            {
                break;
            }

            ByteBuffer mipData1 = BufferUtils.createByteBuffer(data.capacity());

            mipData1.clear();

            for (int mipX = 0; mipX < mipWidth; mipX++)
            {
                for (int mipY = 0; mipY < mipHeight; mipY++)
                {
                    int p1 = mipData.getInt((mipX * 2 + 0 + (mipY * 2 + 0) * parWidth) * 4);
                    int p2 = mipData.getInt((mipX * 2 + 1 + (mipY * 2 + 0) * parWidth) * 4);
                    int p3 = mipData.getInt((mipX * 2 + 1 + (mipY * 2 + 1) * parWidth) * 4);
                    int p4 = mipData.getInt((mipX * 2 + 0 + (mipY * 2 + 1) * parWidth) * 4);

                    int pixel = b(b(p1, p2), b(p3, p4));

                    mipData1.putInt((mipX + mipY * mipWidth) * 4, pixel);
                }
            }

            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, level, GL11.GL_RGBA, mipWidth, mipHeight, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, mipData1);
            GL11.glAlphaFunc(GL11.GL_GEQUAL, 0.1F * level); // Create transparency for each level.

            mipData = mipData1;
        }
    }

    private int b(int c1, int c2)
    {
        int a1 = (c1 & 0xFF000000) >> 24 & 0xFF;
        int a2 = (c2 & 0xFF000000) >> 24 & 0xFF;

        int ax = (a1 + a2) / 2;
        if (ax > 255) {
            ax = 255;
        }
        if (a1 + a2 <= 0)
        {
            a1 = 1;
            a2 = 1;
            ax = 0;
        }

        int r1 = (c1 >> 16 & 0xFF) * a1;
        int g1 = (c1 >> 8 & 0xFF) * a1;
        int b1 = (c1 & 0xFF) * a1;

        int r2 = (c2 >> 16 & 0xFF) * a2;
        int g2 = (c2 >> 8 & 0xFF) * a2;
        int b2 = (c2 & 0xFF) * a2;

        int rx = (r1 + r2) / (a1 + a2);
        int gx = (g1 + g2) / (a1 + a2);
        int bx = (b1 + b2) / (a1 + a2);
        return ax << 24 | rx << 16 | gx << 8 | bx;
    }

    public void registerAnimation(TextureFX FX)
    {
        animations.add(FX);

        FX.animate();
    }
}
