package net.classicremastered.minecraft;

import net.classicremastered.minecraft.gui.GuiCrafting;
import net.classicremastered.minecraft.gui.InventoryScreen;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.liquid.LiquidType;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.particle.Particle;
import net.classicremastered.minecraft.particle.ParticleManager;
import net.classicremastered.minecraft.phys.AABB;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.*;
import net.classicremastered.minecraft.render.Renderer;
import net.classicremastered.minecraft.util.Screenshot;
import net.classicremastered.util.MathHelper;

import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import java.awt.*;
import java.nio.FloatBuffer;

/** Renders one client frame, including picking, world passes, held items, and GUI. */
final class ClientFrameRenderer {
    private static final FloatBuffer PARTICLE_VIEW = BufferUtils.createFloatBuffer(16);

    private ClientFrameRenderer() {}

    static int crackTexture(float progress) {
        return 240 + Math.min(9, Math.max(0, (int) (progress * 10.0F)));
    }

    static void render(Minecraft mc) {
        Minecraft.checkGLError("Pre render");
        GL11.glEnable(3553);
        boolean fabulous =
                mc.transparency.prepare(
                        mc.settings,
                        mc.level == null ? 0 : mc.width,
                        mc.level == null ? 0 : mc.height);
        if (!mc.online) {
            float partial = mc.worldRenderPartial();
            if (!mc.isLocalWorldPaused()) mc.gamemode.applyCracks(partial);
            if (mc.level != null) {
                mc.level.setRenderPartial(partial);
                mc.level.updateDayNightColorsSmooth();
            }
            Renderer r = mc.renderer;
            if (mc.renderer.displayActive && !Display.isActive()) {
                r.minecraft.pause();
            }

            r.displayActive = Display.isActive();
            int uiW;
            int uiH;
            int mouseDY;
            int mouseDX;
            if (r.minecraft.hasMouse && !r.minecraft.updateParsecMouseLook()) {
                mouseDX = 0;
                mouseDY = 0;
                if (r.minecraft.levelLoaded) {
                    if (r.minecraft.canvas != null) {
                        Point canvasLoc;
                        uiH =
                                (canvasLoc = r.minecraft.canvas.getLocationOnScreen()).x
                                        + r.minecraft.width / 2;
                        uiW = canvasLoc.y + r.minecraft.height / 2;
                        Point pointer;
                        mouseDX = (pointer = MouseInfo.getPointerInfo().getLocation()).x - uiH;
                        mouseDY = -(pointer.y - uiW);
                        r.minecraft.robot.mouseMove(uiH, uiW);
                    } else {
                        Mouse.setCursorPosition(r.minecraft.width / 2, r.minecraft.height / 2);
                    }
                } else {
                    mouseDX = Mouse.getDX();
                    mouseDY = Mouse.getDY();
                }

                byte invert = 1;
                if (r.minecraft.settings.invertMouse) invert = -1;
                r.minecraft.player.turn((float) mouseDX, (float) (mouseDY * invert));
            }

            if (!r.minecraft.online) {
                mouseDX = r.minecraft.width * 240 / r.minecraft.height;
                mouseDY = r.minecraft.height * 240 / r.minecraft.height;
                int mouseUiX = Mouse.getX() * mouseDX / r.minecraft.width;
                uiH = mouseDY - Mouse.getY() * mouseDY / r.minecraft.height - 1;
                if (r.minecraft.level != null) {
                    float renderPartial = partial;
                    Renderer r2 = r;
                    ClientTargetPicker.pick(r, partial);
                    Player player;

                    long cameraFrameNanos = System.nanoTime();
                    int stereoEye = 0;
                    while (true) {
                        if (stereoEye >= 2) {
                            GL11.glColorMask(true, true, true, false);
                            break;
                        }
                        if (r2.minecraft.settings.anaglyph) {
                            if (stereoEye == 0) GL11.glColorMask(false, true, true, false);
                            else GL11.glColorMask(true, false, false, false);
                        }

                        Player camPlayer = r2.minecraft.player;
                        Level camLevel = r2.minecraft.level;
                        LevelRenderer lr = r2.minecraft.levelRenderer;
                        ParticleManager pm = r2.minecraft.particleManager;
                        GL11.glViewport(0, 0, r2.minecraft.width, r2.minecraft.height);
                        Level levelRef = r2.minecraft.level;
                        player = r2.minecraft.player;
                        float invViewDist = 1.0F / (float) (4 - r2.minecraft.settings.viewDistance);
                        invViewDist = 1.0F - (float) Math.pow((double) invViewDist, 0.25D);
                        float skyR = (float) (levelRef.skyColor >> 16 & 255) / 255.0F;
                        float skyG = (float) (levelRef.skyColor >> 8 & 255) / 255.0F;
                        float skyB = (float) (levelRef.skyColor & 255) / 255.0F;
                        r2.fogRed = (float) (levelRef.fogColor >> 16 & 255) / 255.0F;
                        r2.fogGreen = (float) (levelRef.fogColor >> 8 & 255) / 255.0F;
                        r2.fogBlue = (float) (levelRef.fogColor & 255) / 255.0F;
                        r2.fogRed += (skyR - r2.fogRed) * invViewDist;
                        r2.fogGreen += (skyG - r2.fogGreen) * invViewDist;
                        r2.fogBlue += (skyB - r2.fogBlue) * invViewDist;
                        r2.fogRed *= r2.fogColorMultiplier;
                        r2.fogGreen *= r2.fogColorMultiplier;
                        r2.fogBlue *= r2.fogColorMultiplier;

                        Block feetBlock;
                        if ((feetBlock =
                                                Block.blocks[
                                                        levelRef.getTile(
                                                                (int) player.x,
                                                                (int) (player.y + 0.12F),
                                                                (int) player.z)])
                                        != null
                                && feetBlock.getLiquidType() != LiquidType.NOT_LIQUID) {
                            LiquidType lt;
                            if ((lt = feetBlock.getLiquidType()) == LiquidType.WATER) {
                                r2.fogRed = 0.02F;
                                r2.fogGreen = 0.02F;
                                r2.fogBlue = 0.2F;
                            } else if (lt == LiquidType.LAVA) {
                                r2.fogRed = 0.6F;
                                r2.fogGreen = 0.1F;
                                r2.fogBlue = 0.0F;
                            }
                        }

                        if (r2.minecraft.settings.anaglyph) {
                            float rGray =
                                    (r2.fogRed * 30.0F + r2.fogGreen * 59.0F + r2.fogBlue * 11.0F)
                                            / 100.0F;
                            float gGray = (r2.fogRed * 30.0F + r2.fogGreen * 70.0F) / 100.0F;
                            float bGray = (r2.fogRed * 30.0F + r2.fogBlue * 70.0F) / 100.0F;
                            r2.fogRed = rGray;
                            r2.fogGreen = gGray;
                            r2.fogBlue = bGray;
                        }

                        GL11.glClearColor(r2.fogRed, r2.fogGreen, r2.fogBlue, 0.0F);
                        GL11.glClear(16640);
                        r2.fogColorMultiplier = 1.0F;
                        GL11.glEnable(2884);
                        r2.fogEnd = (float) (512 >> (r2.minecraft.settings.viewDistance << 1));
                        GL11.glMatrixMode(5889);
                        GL11.glLoadIdentity();
                        float eyeShift = 0.07F;
                        if (r2.minecraft.settings.anaglyph) {
                            GL11.glTranslatef(
                                    (float) (-((stereoEye << 1) - 1)) * eyeShift, 0.0F, 0.0F);
                        }

                        camPlayer = r2.minecraft.player;
                        float fov = 70.0F;
                        if (camPlayer.health <= 0) {
                            float deathTimeInterp = (float) camPlayer.deathTime + renderPartial;
                            fov /= (1.0F - 500.0F / (deathTimeInterp + 500.0F)) * 2.0F + 1.0F;
                        }
                        if (camPlayer != null && camPlayer.inventory != null) {
                            int selId = camPlayer.inventory.getSelected();
                            if (selId >= 256
                                    && (selId - 256) == 19
                                    && org.lwjgl.input.Mouse.isButtonDown(1)) {
                                fov /= 2.0F;
                            }
                        }

                        GLU.gluPerspective(
                                fov,
                                (float) r2.minecraft.width / (float) r2.minecraft.height,
                                0.05F,
                                r2.fogEnd);
                        GL11.glMatrixMode(5888);
                        GL11.glLoadIdentity();
                        if (r2.minecraft.settings.anaglyph) {
                            GL11.glTranslatef((float) ((stereoEye << 1) - 1) * 0.1F, 0.0F, 0.0F);
                        }

                        r2.hurtEffect(renderPartial);
                        if (r2.minecraft.settings.viewBobbing) {
                            r2.applyBobbing(renderPartial);
                        }

                        camPlayer = r2.minecraft.player;
                        if (mc.cameraMode == 0) GL11.glTranslatef(0.0F, 0.0F, -0.1F);
                        // Replace vanilla camera placement with mode-aware setup
                        mc.renderer.setupCamera(renderPartial, cameraFrameNanos);

                        Frustrum fr = FrustrumImpl.update();
                        // sky: stars + sun/moon background pass (rendered before terrain chunks so
                        // blocks occlude them)
                        if (mc.skyRenderer != null) {
                            if (feetBlock == null
                                    || feetBlock.getLiquidType() == LiquidType.NOT_LIQUID)
                                mc.skyRenderer.renderGradient(camPlayer, renderPartial, r2.fogEnd);
                            mc.skyRenderer.renderStars(mc.player, renderPartial);
                            mc.skyRenderer.renderSunMoon(mc.player, renderPartial);
                        }

                        // Common path: this dispatches to your LevelRenderer logic.
                        // For finite: uses chunkCache. For infinite: uses your SimpleChunkManager
                        // path.
                        lr.sortChunks(camPlayer, 0, renderPartial);
                        if (!fabulous) lr.renderOpaqueGlass(camPlayer, renderPartial);

                        r2.updateFog();
                        GL11.glEnable(2912);
                        ShapeRenderer shape;
                        int f;
                        int zTile;
                        int yTile;
                        int xTile;
                        if (!camPlayer.isSpectator()
                                && camLevel.isSolid(camPlayer.x, camPlayer.y, camPlayer.z, 0.1F)) {
                            // Integer camera cell
                            final int cx = (int) camPlayer.x;
                            final int cy = (int) camPlayer.y;
                            final int cz = (int) camPlayer.z;

                            // Render the “inside block” dark overlay around the camera
                            for (int nx = cx - 1; nx <= cx + 1; nx++) {
                                for (int ny = cy - 1; ny <= cy + 1; ny++) {
                                    for (int nz = cz - 1; nz <= cz + 1; nz++) {
                                        int id = lr.level.getTile(nx, ny, nz);
                                        if (id <= 0) continue;

                                        Block b = Block.blocks[id];
                                        if (b == null || !b.isSolid()) continue;

                                        // Darken interior a bit
                                        GL11.glColor4f(0.2F, 0.2F, 0.2F, 1.0F);

                                        // First pass: render inside faces with depth EQUAL
                                        GL11.glDepthFunc(513); // GL_EQUAL
                                        ShapeRenderer sr = ShapeRenderer.instance;
                                        sr.begin();
                                        for (int face = 0; face < 6; face++) {
                                            b.renderInside(sr, nx, ny, nz, face);
                                        }
                                        sr.end();

                                        // Second pass: cull swap for proper interior look
                                        GL11.glCullFace(1028); // GL_FRONT
                                        sr.begin();
                                        for (int face = 0; face < 6; face++) {
                                            b.renderInside(sr, nx, ny, nz, face);
                                        }
                                        sr.end();
                                        GL11.glCullFace(1029); // GL_BACK

                                        // Restore default depth func
                                        GL11.glDepthFunc(515); // GL_LEQUAL
                                    }
                                }
                            }
                        }

                        r2.setLighting(true);
                        Vec3D camVec = r2.getPlayerVector(renderPartial);
                        lr.level.blockMap.render(camVec, fr, lr.textureManager, renderPartial);
                        r2.setLighting(false);
                        r2.updateFog();
                        if (!fabulous) renderParticles(pm, renderPartial);
                        GL11.glDisable(3553);
                        r2.updateFog();
                        GL11.glDisable(3553);
                        GL11.glDepthMask(false);
                        GL11.glDepthMask(true);
                        GL11.glEnable(3553);

                        LevelRenderer lrRef = lr;
                        // clouds (infinite sheet)
                        if (mc.skyRenderer != null) {
                            float cloudR = (float) ((levelRef.cloudColor >> 16) & 255) / 255f;
                            float cloudG = (float) ((levelRef.cloudColor >> 8) & 255) / 255f;
                            float cloudB = (float) ((levelRef.cloudColor) & 255) / 255f;
                            mc.skyRenderer.renderClouds(renderPartial, cloudR, cloudG, cloudB);
                        }

                        GL11.glEnable(3553);
                        r2.updateFog();

                        if (mc.selected != null && (!mc.hudHidden || lr.cracks > 0.0F)) {
                            GL11.glDisable(3008);
                            MovingObjectPosition pick = r2.minecraft.selected;
                            LevelRenderer lrPick = lr;
                            ShapeRenderer sr1 = ShapeRenderer.instance;
                            GL11.glEnable(3042);
                            GL11.glEnable(3008);
                            GL11.glBlendFunc(770, 1);
                            GL11.glColor4f(
                                    1,
                                    1,
                                    1,
                                    (MathHelper.sin((float) System.currentTimeMillis() / 100.0F)
                                                            * 0.2F
                                                    + 0.4F)
                                            * 0.5F);
                            if (lr.cracks > 0.0F) {
                                GL11.glBlendFunc(774, 768);
                                int terrainTex =
                                        lr.textureManager.load(
                                                net.classicremastered.minecraft.render
                                                        .BlockTextureAtlas.RESOURCE);
                                GL11.glBindTexture(3553, terrainTex);
                                GL11.glColor4f(1, 1, 1, 0.5F);
                                GL11.glPushMatrix();
                                int pickId = lr.level.getTile(pick.x, pick.y, pick.z);
                                Block pickBlock = pickId > 0 ? Block.blocks[pickId] : null;
                                if (pickBlock == null) pickBlock = Block.STONE;
                                float cx = (pickBlock.x1 + pickBlock.x2) / 2.0F;
                                float cy = (pickBlock.y1 + pickBlock.y2) / 2.0F;
                                float cz = (pickBlock.z1 + pickBlock.z2) / 2.0F;
                                GL11.glTranslatef(pick.x + cx, pick.y + cy, pick.z + cz);
                                GL11.glScalef(1.01F, 1.01F, 1.01F);
                                GL11.glTranslatef(-(pick.x + cx), -(pick.y + cy), -(pick.z + cz));
                                sr1.begin();
                                sr1.noColor();
                                GL11.glDepthMask(false);
                                for (int face = 0; face < 6; ++face)
                                    pickBlock.renderSide(
                                            sr1,
                                            pick.x,
                                            pick.y,
                                            pick.z,
                                            face,
                                            crackTexture(lrPick.cracks));
                                sr1.end();
                                GL11.glDepthMask(true);
                                GL11.glPopMatrix();
                            }
                            GL11.glDisable(3042);
                            GL11.glDisable(3008);

                            GL11.glEnable(3042);
                            GL11.glBlendFunc(770, 771);
                            GL11.glColor4f(0, 0, 0, 0.4F);
                            GL11.glLineWidth(2.0F);
                            GL11.glDisable(3553);
                            GL11.glDepthMask(false);
                            float eps = 0.002F;
                            int id = lr.level.getTile(pick.x, pick.y, pick.z);
                            if (id > 0 && !mc.hudHidden) {
                                AABB bb =
                                        Block.blocks[id]
                                                .getSelectionBox(
                                                        r.minecraft.level, pick.x, pick.y, pick.z)
                                                .grow(eps, eps, eps);
                                GL11.glBegin(3);
                                GL11.glVertex3f(bb.x0, bb.y0, bb.z0);
                                GL11.glVertex3f(bb.x1, bb.y0, bb.z0);
                                GL11.glVertex3f(bb.x1, bb.y0, bb.z1);
                                GL11.glVertex3f(bb.x0, bb.y0, bb.z1);
                                GL11.glVertex3f(bb.x0, bb.y0, bb.z0);
                                GL11.glEnd();
                                GL11.glBegin(3);
                                GL11.glVertex3f(bb.x0, bb.y1, bb.z0);
                                GL11.glVertex3f(bb.x1, bb.y1, bb.z0);
                                GL11.glVertex3f(bb.x1, bb.y1, bb.z1);
                                GL11.glVertex3f(bb.x0, bb.y1, bb.z1);
                                GL11.glVertex3f(bb.x0, bb.y1, bb.z0);
                                GL11.glEnd();
                                GL11.glBegin(1);
                                GL11.glVertex3f(bb.x0, bb.y0, bb.z0);
                                GL11.glVertex3f(bb.x0, bb.y1, bb.z0);
                                GL11.glVertex3f(bb.x1, bb.y0, bb.z0);
                                GL11.glVertex3f(bb.x1, bb.y1, bb.z0);
                                GL11.glVertex3f(bb.x1, bb.y0, bb.z1);
                                GL11.glVertex3f(bb.x1, bb.y1, bb.z1);
                                GL11.glVertex3f(bb.x0, bb.y0, bb.z1);
                                GL11.glVertex3f(bb.x0, bb.y1, bb.z1);
                                GL11.glEnd();
                            }
                            GL11.glDepthMask(true);
                            GL11.glEnable(3553);
                            GL11.glDisable(3042);
                            GL11.glEnable(3008);
                        }

                        r2.updateFog();
                        GL11.glEnable(GL11.GL_TEXTURE_2D);
                        if (fabulous && mc.transparency.begin(mc.settings)) {
                            try {
                                lr.renderFabulousTerrain(camPlayer);
                                renderParticles(pm, renderPartial);
                            } finally {
                                mc.transparency.composite();
                            }
                        } else {
                            if (fabulous) {
                                lr.renderOpaqueGlass(camPlayer, renderPartial);
                                renderParticles(pm, renderPartial);
                            }
                            GL11.glBlendFunc(770, 771);
                            GL11.glEnable(3042);
                            if (lr.waterReady) {
                                GL11.glBindTexture(3553, lr.textureManager.load("/water.png"));
                                GL11.glCallList(lr.listId + 1);
                            }
                            GL11.glColorMask(false, false, false, false);
                            xTile = lr.sortChunks(camPlayer, 1, renderPartial);
                            GL11.glColorMask(true, true, true, true);
                            if (r2.minecraft.settings.anaglyph) {
                                if (stereoEye == 0) GL11.glColorMask(false, true, true, false);
                                else GL11.glColorMask(true, false, false, false);
                            }
                            if (xTile > 0) GL11.glCallLists(lr.buffer);
                            GL11.glDepthMask(true);
                            GL11.glDisable(3042);
                        }
                        GL11.glDisable(2912);

                        if (mc.debugHitboxes) {
                            EntityHitboxRenderer.render(mc, camVec, fr, renderPartial);
                        }
                        ClientWeatherRenderer.render(r2, renderPartial);
                        net.classicremastered.minecraft.render.DeveloperPowerRenderer.render(
                                mc, renderPartial);

                        if (r2.entity != null) {
                            r2.entity.renderHover(r2.minecraft.textureManager, renderPartial);
                        }

                        ClientFirstPersonRenderer.render(r2, renderPartial, stereoEye);
                        if (!r2.minecraft.settings.anaglyph) break;
                        ++stereoEye;
                    }
                    mc.renderer.renderUnderwaterOverlay(partial);
                    if (!mc.hudHidden) {
                        boolean hideHotbar =
                                mc.currentScreen instanceof InventoryScreen
                                        || mc.currentScreen instanceof GuiCrafting;
                        mc.hud.render(partial, hideHotbar, mouseUiX, uiH);
                    } else if (mc.currentScreen != null) {
                        r.enableGuiMode();
                    }

                } else {
                    GL11.glViewport(0, 0, r.minecraft.width, r.minecraft.height);
                    GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
                    GL11.glClear(16640);
                    GL11.glMatrixMode(5889);
                    GL11.glLoadIdentity();
                    GL11.glMatrixMode(5888);
                    GL11.glLoadIdentity();
                    r.enableGuiMode();
                }

                if (r.minecraft.currentScreen != null) {
                    r.minecraft.currentScreen.render(mouseUiX, uiH);
                }

                // Take pending screenshot AFTER rendering, BEFORE the swap
                if (mc.pendingScreenshot) {
                    Screenshot.take(Minecraft.mcDir);
                    mc.pendingScreenshot = false;
                }

                Thread.yield();
                Display.update();
            }
        }
    }

    private static void renderParticles(ParticleManager manager, float partial) {
        if (manager.particles[0].isEmpty() && manager.particles[1].isEmpty()) return;
        PARTICLE_VIEW.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, PARTICLE_VIEW);
        Vec3D right = new Vec3D(PARTICLE_VIEW.get(0), PARTICLE_VIEW.get(4), PARTICLE_VIEW.get(8));
        Vec3D up = new Vec3D(PARTICLE_VIEW.get(1), PARTICLE_VIEW.get(5), PARTICLE_VIEW.get(9));
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
        try {
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            for (int pass = 0; pass < 2; pass++) {
                var particles = manager.particles[pass];
                if (particles.isEmpty()) continue;
                String texture = pass == 0 ? "/particles.png" : BlockTextureAtlas.RESOURCE;
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, manager.textureManager.load(texture));
                ShapeRenderer shape = ShapeRenderer.instance;
                shape.begin();
                for (Object particle : particles) {
                    ((Particle) particle).render(shape, partial, right, up);
                }
                shape.end();
            }
        } finally {
            GL11.glPopAttrib();
        }
    }
}
