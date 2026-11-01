package net.classicremastered.toolkit.modeleditor;

import org.lwjgl.opengl.GL11;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;


import net.classicremastered.minecraft.model.*;

import javax.swing.*;
import java.awt.*;

/** Model Editor Files responsibilities. */
final class ModelEditorFiles {
    private final ModelEditorApp editor;

    ModelEditorFiles(ModelEditorApp editor) { this.editor = editor; }

    File getDefaultDir() {
        String appdata = System.getenv("APPDATA");
        if (appdata == null)
            appdata = System.getProperty("user.home");
        File dir = new File(appdata, ".mcraft/client/modelexport");
        if (!dir.exists())
            dir.mkdirs();
        return dir;
    }

    File showSaveDialog(String title, String defaultName, String ext) {
        Frame owner = new Frame(); // no UI shown
        FileDialog fd = new FileDialog(owner, title, FileDialog.SAVE);
        fd.setDirectory(editor.getDefaultDir().getAbsolutePath());
        fd.setFile(ModelEditorApp.ensureExt(defaultName, ext));
        fd.setVisible(true);
        owner.dispose();
        editor.restoreFocusAfterDialog();

        if (fd.getFile() == null)
            return null;
        File chosen = new File(fd.getDirectory(), fd.getFile());
        chosen = new File(chosen.getParentFile(), ModelEditorApp.ensureExt(chosen.getName(), ext));
        return chosen;
    }

    File showOpenDialog(String title, String ext) {
        Frame owner = new Frame();
        FileDialog fd = new FileDialog(owner, title, FileDialog.LOAD);
        fd.setDirectory(editor.getDefaultDir().getAbsolutePath());
        fd.setFile("*" + ext);
        fd.setVisible(true);
        owner.dispose();
        editor.restoreFocusAfterDialog();

        if (fd.getFile() == null)
            return null;
        return new File(fd.getDirectory(), fd.getFile());
    }

    void doLoad() {
        editor.requestLoad();
    }

    void doImportJava() {
        editor.requestImportJava();
    }

    void requestSave() {
        ModelEditorApp.onEDT(editor::doSave);
    }

    void requestLoad() {
        ModelEditorApp.onEDT(() -> {
            File in = editor.showOpenDialog("Load Model (.mmdl)", ".mmdl");
            if (in == null) return;
            Thread worker = new Thread(() -> ModelEditorApp.safe(() -> {
                EditableModel loaded = ModelIO.load(in);
                SwingUtilities.invokeLater(() -> ModelEditorApp.safe(() -> {
                    if (!editor.confirmDiscard()) return;
                    editor.pushHistory();
                    editor.model = loaded;
                    editor.document.remember(in, loaded);
                    editor.selected = loaded.parts.isEmpty() ? null : loaded.parts.get(0);
                    editor.refreshPartListSelect(editor.selected);
                    editor.syncModelFieldsToUI();
                    if (editor.animText != null) editor.animText.setText(loaded.setRotationAnglesCode == null ? "" : loaded.setRotationAnglesCode);
                    editor.updateModeTitle();
                }));
            }), "LoadWorker");
            worker.setDaemon(true);
            worker.start();
        });
    }

    void requestExportJava() {
        ModelEditorApp.onEDT(() -> {
            String base = (editor.model.name == null || editor.model.name.isEmpty()) ? "MyNewMobModel" : editor.model.name;
            File dir = editor.getDefaultDir();
            File out = ModelEditorApp.uniqueIfExists(new File(dir, base + ".java"));
            new Thread(() -> ModelEditorApp.safe(() -> {
                if (editor.animText != null)
                    editor.model.setRotationAnglesCode = editor.animText.getText();
                JavaExporter.export("net.classicremastered.minecraft.model", out.getName().replace(".java", ""), editor.model);
                System.out.println("[export] " + out.getAbsolutePath());
            }), "ExportJavaWorker").start();
        });
    }

    void requestExportTemplate() {
        ModelEditorApp.onEDT(() -> {
            new Thread(() -> ModelEditorApp.safe(() -> {
                String appdata = System.getenv("APPDATA");
                if (appdata == null)
                    appdata = System.getProperty("user.home");
                File dir = new File(appdata, ".mcraft/client/modelexport");
                if (!dir.exists())
                    dir.mkdirs();
                String base = editor.model.name == null || editor.model.name.isEmpty() ? "model" : editor.model.name;
                File out = new File(dir, base + "-template.png");
                // heavy work off EDT
                TextureTemplateExporter.exportTemplatePNG(out, editor.model, 255, 48);
                System.out.println("[uv template] " + out.getAbsolutePath());
            }), "ExportTemplateWorker").start();
        });
    }

    void requestImportJava() {
        ModelEditorApp.onEDT(() -> {
            File in = editor.showOpenDialog("Open Classic Java Model (.java)", ".java");
            if (in == null) return;
            Thread worker = new Thread(() -> ModelEditorApp.safe(() -> {
                EditableModel imported = JavaModelImporter.load(in);
                SwingUtilities.invokeLater(() -> {
                    if (!editor.confirmDiscard()) return;
                    editor.pushHistory();
                    editor.document.reset();
                    editor.model = imported;
                    editor.selected = imported.parts.isEmpty() ? null : imported.parts.get(0);
                    editor.refreshPartListSelect(editor.selected);
                    editor.syncModelFieldsToUI();
                    if (editor.animText != null) editor.animText.setText(imported.setRotationAnglesCode == null ? "" : imported.setRotationAnglesCode);
                    editor.updateModeTitle();
                });
            }), "ImportModelWorker");
            worker.setDaemon(true);
            worker.start();
        });
    }

    void newProject() {
        if (!editor.confirmDiscard()) return;
        editor.pushHistory();
        editor.document.reset();
        EditablePart head = new EditablePart("head");
        head.u = 0;
        head.v = 0;
        head.x = -4;
        head.y = -8;
        head.z = -4;
        head.w = 8;
        head.h = 8;
        head.d = 8;

        synchronized (editor.modelLock) {
            editor.model = new EditableModel();
            editor.model.name = "new_model";
            editor.model.atlasW = 64;
            editor.model.atlasH = 64;
            editor.model.parts.add(head);
            editor.model.setRotationAnglesCode = null; // clear animations on new project
            editor.selected = head;
        }

        editor.yaw = 30;
        editor.pitch = 15;
        editor.dist = 7;
        editor.targetX = 0.0f;
        editor.targetY = 1.2f;
        editor.targetZ = 0.0f;
        editor.clearTexture();

        editor.refreshPartListSelect(head);
        editor.syncModelFieldsToUI();
        if (editor.animText != null)
            editor.animText.setText("");
        if (editor.uvPanel != null)
            editor.uvPanel.repaint();
    }

    void doSave() {
        saveDocument(false);
    }

    void doExportJava() {
        String base = (editor.model.name == null || editor.model.name.isEmpty()) ? "MyNewMobModel" : editor.model.name;
        File dir = editor.getDefaultDir();
        File out = ModelEditorApp.uniqueIfExists(new File(dir, base + ".java"));
        ModelEditorApp.safe(() -> {
            if (editor.animText != null)
                editor.model.setRotationAnglesCode = editor.animText.getText();
            JavaExporter.export("net.classicremastered.minecraft.model", out.getName().replace(".java", ""), editor.model);
            System.out.println("[export] " + out.getAbsolutePath());
        });
    }

    void doExportTemplate() {
        ModelEditorApp.safe(() -> {
            String appdata = System.getenv("APPDATA");
            if (appdata == null)
                appdata = System.getProperty("user.home");
            File dir = new File(appdata, ".mcraft/client/modelexport");
            if (!dir.exists())
                dir.mkdirs();
            String base = editor.model.name == null || editor.model.name.isEmpty() ? "model" : editor.model.name;
            File out = new File(dir, base + "-template.png");
            // faceAlpha=255 (opaque faces), checkerAlpha=48 (semi-transparent)
            TextureTemplateExporter.exportTemplatePNG(out, editor.model, 255, 48);
            System.out.println("[uv template] " + out.getAbsolutePath());
        });
    }

    void doImportTexture() {
        File in = editor.showOpenDialog("Import Texture (.png/.jpg)", ".png");
        editor.restoreFocusAfterDialog();
        if (in == null)
            return;

        try {
            if (!in.isFile())
                throw new IOException("Not a file: " + in);

            javax.imageio.ImageIO.setUseCache(false);
            BufferedImage raw = javax.imageio.ImageIO.read(in);
            if (raw == null)
                throw new IOException("Unsupported image format: " + in.getName());

            BufferedImage img = new BufferedImage(raw.getWidth(), raw.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = img.createGraphics();
                try { graphics.drawImage(raw, 0, 0, null); } finally { graphics.dispose(); }

            // CPU copy for viewer + UI label
            editor.texturePath = in.getAbsolutePath();
            editor.textureName = in.getName();
            editor.textureImage = img;
            if (editor.textureLabel != null)
                editor.textureLabel.setText(editor.textureName);
            if (editor.uvPanel != null)
                editor.uvPanel.setTexture(img);

            // Upload on GL thread
            final BufferedImage imgForGL = img;
            editor.glTasks.add(() -> {
                int newTex = editor.uploadTexture(imgForGL);
                if (editor.glTextureId != 0)
                    GL11.glDeleteTextures(editor.glTextureId);
                editor.glTextureId = newTex;

                editor.model.atlasW = imgForGL.getWidth();
                editor.model.atlasH = imgForGL.getHeight();
                SwingUtilities.invokeLater(() -> {
                    editor.atlasW.setValue(editor.model.atlasW);
                    editor.atlasH.setValue(editor.model.atlasH);
                    editor.uvPanel.repaint();
                });

                System.out.println("[texture] OK " + editor.texturePath + " (" + editor.model.atlasW + "x" + editor.model.atlasH + ")");
            });

        } catch (Throwable t) {
            t.printStackTrace();
            JOptionPane.showMessageDialog(editor.frame, "Failed to load texture:\n" + t, "Texture Import Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    void clearTexture() {
        editor.glTasks.add(() -> {
            if (editor.glTextureId != 0) GL11.glDeleteTextures(editor.glTextureId);
            editor.glTextureId = 0;
        });
        editor.texturePath = null;
        editor.textureImage = null;
        editor.textureName = "(none)";
        if (editor.textureLabel != null) editor.textureLabel.setText(editor.textureName);
        if (editor.uvPanel != null) editor.uvPanel.setTexture(null);
    }
    void saveAs() { saveDocument(true); }

    private void saveDocument(boolean saveAs) {
        File out = saveAs ? null : editor.document.file();
        if (out == null) out = editor.showSaveDialog("Save Model (.mmdl)", editor.model.name == null ? "model" : editor.model.name, ".mmdl");
        if (out == null) return;
        final File destination = out;
        ModelEditorApp.safe(() -> {
            if (editor.animText != null) editor.model.setRotationAnglesCode = editor.animText.getText();
            EditableModel snapshot;
            synchronized (editor.modelLock) { snapshot = ModelCopies.copy(editor.model); }
            editor.document.save(destination, snapshot);
            editor.updateModeTitle();
        });
    }

    boolean confirmDiscard() {
        EditableModel snapshot = ModelCopies.copy(editor.model);
        if (editor.animText != null) snapshot.setRotationAnglesCode = editor.animText.getText();
        return !editor.document.isModified(snapshot) || JOptionPane.showConfirmDialog(editor.frame,
            "Discard unsaved model changes?", "Unsaved Changes", JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;
    }
}
