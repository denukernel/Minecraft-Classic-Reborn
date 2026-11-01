package net.classicremastered.toolkit.modeleditor;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;


import net.classicremastered.minecraft.model.*;


/** Model Editor Viewport responsibilities. */
final class ModelEditorViewport {
    private final ModelEditorApp editor;
    private final java.util.Map<EditablePart, CachedPart> meshes = new java.util.IdentityHashMap<>();

    private record Geometry(int u, int v, float x, float y, float z, int w, int h, int d,
                            float inflate, boolean mirror, int atlasW, int atlasH) {}
    private record CachedPart(Geometry geometry, ModelPart part) {}

    private void deleteMesh(CachedPart mesh) {
        if (mesh.part.hasList) GL11.glDeleteLists(mesh.part.list, 1);
    }

    private void drawPart(EditablePart source, float scale) {
        Geometry geometry = new Geometry(source.u, source.v, source.x, source.y, source.z,
            source.w, source.h, source.d, source.inflate, source.mirror, editor.model.atlasW, editor.model.atlasH);
        CachedPart mesh = meshes.get(source);
        if (mesh == null || !mesh.geometry.equals(geometry)) {
            if (mesh != null) deleteMesh(mesh);
            ModelPart part = new ModelPart(source.u, source.v);
            part.mirror = source.mirror;
            part.setBounds(source.x, source.y, source.z, source.w, source.h, source.d, source.inflate);
            mesh = new CachedPart(geometry, part);
            meshes.put(source, mesh);
        }
        mesh.part.render(scale);
    }

    void dispose() {
        for (CachedPart mesh : meshes.values()) deleteMesh(mesh);
        meshes.clear();
    }

    ModelEditorViewport(ModelEditorApp editor) { this.editor = editor; }

    void render() {
        if (editor.glCanvas.getWidth() <= 0 || editor.glCanvas.getHeight() <= 0) return;
        java.util.Set<EditablePart> current = new java.util.HashSet<>(ModelEditorApp.getAllParts(editor.model));
        meshes.entrySet().removeIf(entry -> {
            if (current.contains(entry.getKey())) return false;
            deleteMesh(entry.getValue());
            return true;
        });
        GL11.glViewport(0, 0, editor.glCanvas.getWidth(), editor.glCanvas.getHeight());
        GL11.glClearColor(0.07f, 0.08f, 0.10f, 1f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GLU.gluPerspective(60, editor.glCanvas.getWidth() / (float) editor.glCanvas.getHeight(), 0.05f, 200f);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();

        float ex = (float) (Math.cos(Math.toRadians(editor.yaw)) * Math.cos(Math.toRadians(editor.pitch)));
        float ey = (float) (Math.sin(Math.toRadians(editor.pitch)));
        float ez = (float) (Math.sin(Math.toRadians(editor.yaw)) * Math.cos(Math.toRadians(editor.pitch)));
        GLU.gluLookAt(ex * editor.dist + editor.targetX, ey * editor.dist + editor.targetY, ez * editor.dist + editor.targetZ, editor.targetX, editor.targetY, editor.targetZ, 0, 1, 0);

        Runnable job;
        while ((job = editor.glTasks.poll()) != null) {
            try {
                job.run();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        editor.drawGrid(14, 1.0f);

        final float s = 0.0625f;

        // ---- snapshot under lock ----
        java.util.List<EditablePart> partsSnapshot;
        EditablePart selSnapshot;
        int atlasW, atlasH;
        synchronized (editor.modelLock) {
            partsSnapshot = new java.util.ArrayList<>(editor.model.parts);
            selSnapshot = editor.selected;
            atlasW = editor.model.atlasW;
            atlasH = editor.model.atlasH;
        }

        TexturedQuad.ATLAS_W = atlasW;
        TexturedQuad.ATLAS_H = atlasH;
        if (editor.triggerPicking) {
            GL11.glClearColor(0, 0, 0, 1f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            GLU.gluPerspective(60, editor.glCanvas.getWidth() / (float) editor.glCanvas.getHeight(), 0.05f, 200f);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glLoadIdentity();

            GLU.gluLookAt(ex * editor.dist + editor.targetX, ey * editor.dist + editor.targetY, ez * editor.dist + editor.targetZ, editor.targetX, editor.targetY, editor.targetZ, 0, 1, 0);

            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glPushMatrix();
            GL11.glTranslatef(0, 24 * 0.0625f, 0);
            GL11.glScalef(1, -1, 1);
            GL11.glFrontFace(GL11.GL_CW);

            java.util.List<EditablePart> allParts = ModelEditorApp.getAllParts(editor.model);
            for (EditablePart p : partsSnapshot) {
                editor.renderPartForPicking(p, 0.0625f, allParts);
            }

            GL11.glFrontFace(GL11.GL_CCW);
            GL11.glPopMatrix();

            java.nio.ByteBuffer pixel = org.lwjgl.BufferUtils.createByteBuffer(4);
            GL11.glReadPixels(editor.pickingX, editor.pickingY, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
            int r = pixel.get(0) & 0xFF;
            if (r > 0 && r <= allParts.size()) {
                final EditablePart hit = allParts.get(r - 1);
                ModelEditorApp.onEDT(() -> {
                    editor.selected = hit;
                    editor.refreshPartListSelect(hit);
                });
            }
            editor.triggerPicking = false;
            GL11.glClearColor(0.07f, 0.08f, 0.10f, 1f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        }

        GL11.glViewport(0, 0, editor.glCanvas.getWidth(), editor.glCanvas.getHeight());
        TexturedQuad.ATLAS_W = atlasW;
        TexturedQuad.ATLAS_H = atlasH;

        // ---- draw ----
        GL11.glPushMatrix();
        if (editor.chkAggressive != null && editor.chkAggressive.isSelected()) {
            float var10 = 0.02f;
            java.util.Random rnd = new java.util.Random();
            GL11.glTranslatef((float) rnd.nextGaussian() * var10, 0.0f, (float) rnd.nextGaussian() * var10);
        }
        GL11.glTranslatef(0, 24 * s, 0);
        GL11.glScalef(1, -1, 1);
        GL11.glFrontFace(GL11.GL_CW);

        if (editor.glTextureId != 0) {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, editor.glTextureId);
        } else {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
        }

        float time = (float) (System.currentTimeMillis() / 50.0);
        for (EditablePart p : partsSnapshot) {
            editor.renderPart(p, s, selSnapshot, time);
        }

        GL11.glFrontFace(GL11.GL_CCW);
        GL11.glPopMatrix();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    void renderPart(EditablePart ep, float scale, EditablePart sel, float time) {
        GL11.glPushMatrix();

        float px = ep.posX;
        float py = ep.posY;
        float pz = ep.posZ;

        float p = ep.pitch;
        float y = ep.yaw;
        float r = ep.roll;

        boolean preview = editor.chkPreviewAnim != null && editor.chkPreviewAnim.isSelected();
        boolean aggressive = editor.chkAggressive != null && editor.chkAggressive.isSelected();

        String name = ep.name.toLowerCase();

        if (preview) {
            if (name.contains("head")) {
                y += (float) Math.sin(time * 0.08f) * 0.15f;
                p += (float) Math.cos(time * 0.05f) * 0.08f;
            } else if (name.contains("rightleg") || name.contains("right_leg")) {
                p += (float) Math.cos(time * 0.12f) * 0.4f;
            } else if (name.contains("leftleg") || name.contains("left_leg")) {
                p += (float) Math.cos(time * 0.12f + Math.PI) * 0.4f;
            } else if (name.contains("rightarm") || name.contains("right_arm")) {
                p += (float) Math.cos(time * 0.12f + Math.PI) * 0.3f;
                r += (float) Math.cos(time * 0.09f) * 0.05f + 0.05f;
            } else if (name.contains("leftarm") || name.contains("left_arm")) {
                p += (float) Math.cos(time * 0.12f) * 0.3f;
                r -= (float) Math.cos(time * 0.09f) * 0.05f + 0.05f;
            }
        }

        if (aggressive) {
            if (name.contains("headwear")) {
                py += 5.0f;
            } else if (name.contains("head")) {
                py -= 5.0f;
            } else if (name.contains("body")) {
                p += 0.08f;
            }
        }

        GL11.glTranslatef(px * scale, py * scale, pz * scale);
        if (r != 0)
            GL11.glRotatef((float) Math.toDegrees(r), 0, 0, 1);
        if (y != 0)
            GL11.glRotatef((float) Math.toDegrees(y), 0, 1, 0);
        if (p != 0)
            GL11.glRotatef((float) Math.toDegrees(p), 1, 0, 0);


        if (ep == sel)
            GL11.glColor3f(1, 1, 0.75f);
        else
            GL11.glColor3f(1, 1, 1);
        drawPart(ep, scale);

        for (EditablePart c : ep.children)
            editor.renderPart(c, scale, sel, time);
        GL11.glPopMatrix();
    }

    void renderPartForPicking(EditablePart ep, float scale, java.util.List<EditablePart> allParts) {
        GL11.glPushMatrix();
        float px = ep.posX;
        float py = ep.posY;
        float pz = ep.posZ;

        float p = ep.pitch;
        float y = ep.yaw;
        float r = ep.roll;

        GL11.glTranslatef(px * scale, py * scale, pz * scale);
        if (r != 0)
            GL11.glRotatef((float) Math.toDegrees(r), 0, 0, 1);
        if (y != 0)
            GL11.glRotatef((float) Math.toDegrees(y), 0, 1, 0);
        if (p != 0)
            GL11.glRotatef((float) Math.toDegrees(p), 1, 0, 0);

        int idx = allParts.indexOf(ep);
        float colorValue = (idx + 1) / 255.0f;
        GL11.glColor3f(colorValue, 0, 0);

        drawPart(ep, scale);

        for (EditablePart c : ep.children)
            editor.renderPartForPicking(c, scale, allParts);
        GL11.glPopMatrix();
    }

    void drawGrid(int half, float step) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor3f(0.18f, 0.20f, 0.24f);
        GL11.glBegin(GL11.GL_LINES);
        for (int i = -half; i <= half; i++) {
            GL11.glVertex3f(i * step, 0, -half * step);
            GL11.glVertex3f(i * step, 0, half * step);
            GL11.glVertex3f(-half * step, 0, i * step);
            GL11.glVertex3f(half * step, 0, i * step);
        }
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    void initGL() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glClearDepth(1.0);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glCullFace(GL11.GL_BACK);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glViewport(0, 0, editor.glCanvas.getWidth(), editor.glCanvas.getHeight());
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GLU.gluPerspective(60, editor.glCanvas.getWidth() / (float) editor.glCanvas.getHeight(), 0.05f, 200f);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glCullFace(GL11.GL_BACK);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_REPLACE);
        GL11.glShadeModel(GL11.GL_FLAT);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    int uploadTexture(java.awt.image.BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int[] argb = new int[w * h];
        img.getRGB(0, 0, w, h, argb, 0, w);

        java.nio.ByteBuffer buf = org.lwjgl.BufferUtils.createByteBuffer(w * h * 4);
        for (int p : argb) {
            buf.put((byte) ((p >> 16) & 0xFF)); // R
            buf.put((byte) ((p >> 8) & 0xFF)); // G
            buf.put((byte) (p & 0xFF)); // B
            buf.put((byte) ((p >> 24) & 0xFF)); // A
        }
        buf.flip();

        int id = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        return id;
    }
}
