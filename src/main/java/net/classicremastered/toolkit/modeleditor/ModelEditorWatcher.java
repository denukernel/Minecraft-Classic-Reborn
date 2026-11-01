package net.classicremastered.toolkit.modeleditor;

import org.lwjgl.opengl.GL11;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;


import net.classicremastered.minecraft.model.*;

import javax.swing.*;
import java.awt.*;

/** Model Editor Watcher responsibilities. */
final class ModelEditorWatcher {
    private final ModelEditorApp editor;

    ModelEditorWatcher(ModelEditorApp editor) { this.editor = editor; }

    void startHotReloadWatcher() {
        try {
            java.nio.file.Path dir = editor.getDefaultDir().toPath();
            editor.watchService = dir.getFileSystem().newWatchService();
            dir.register(editor.watchService,
                    java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY,
                    java.nio.file.StandardWatchEventKinds.ENTRY_CREATE);

            editor.watcherRunning = true;
            editor.watcherThread = new Thread(() -> {
                System.out.println("[watcher] Hot reload active on " + dir);
                while (editor.watcherRunning) {
                    java.nio.file.WatchKey key;
                    try {
                        key = editor.watchService.take();
                    } catch (InterruptedException | java.nio.file.ClosedWatchServiceException e) {
                        break;
                    }
                    for (java.nio.file.WatchEvent<?> ev : key.pollEvents()) {
                        java.nio.file.WatchEvent.Kind<?> kind = ev.kind();
                        if (kind == java.nio.file.StandardWatchEventKinds.OVERFLOW)
                            continue;

                        String fname = ev.context().toString().toLowerCase();
                        if (fname.endsWith(".png") || fname.endsWith(".jpg")) {
                            if (editor.texturePath != null && fname.equalsIgnoreCase(new java.io.File(editor.texturePath).getName())) {
                                System.out.println("[hot-reload] Texture changed → reloading...");
                                editor.reloadTextureAsync();
                            }
                        } else if (fname.endsWith(".mmdl")) {
                            if (editor.document.file() != null && fname.equalsIgnoreCase(editor.document.file().getName())) {
                                System.out.println("[hot-reload] Model changed → reloading...");
                                editor.reloadModelAsync(fname);
                            }
                        }
                    }
                    key.reset();
                }
            }, "HotReloadWatcher");
            editor.watcherThread.setDaemon(true);
            editor.watcherThread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void stopHotReloadWatcher() {
        editor.watcherRunning = false;
        try {
            if (editor.watchService != null)
                editor.watchService.close();
        } catch (Exception ignored) {}
    }

    void reloadTextureAsync() {
        new Thread(() -> {
            try {
                BufferedImage raw = ImageIO.read(new File(editor.texturePath));
                if (raw == null) return;
                BufferedImage img = new BufferedImage(raw.getWidth(), raw.getHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = img.createGraphics();
                try { graphics.drawImage(raw, 0, 0, null); } finally { graphics.dispose(); }
                editor.textureImage = img;
                editor.glTasks.add(() -> {
                    int newTex = editor.uploadTexture(img);
                    if (editor.glTextureId != 0)
                        GL11.glDeleteTextures(editor.glTextureId);
                    editor.glTextureId = newTex;
                    System.out.println("[hot-reload] Texture refreshed OK");
                });
                SwingUtilities.invokeLater(() -> {
                    if (editor.uvPanel != null) editor.uvPanel.setTexture(img);
                    if (editor.textureLabel != null) editor.textureLabel.setText(editor.textureName);
                });
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }, "ReloadTexture").start();
    }

    void reloadModelAsync(String fname) {
        File current = editor.document.file();
        if (current == null || !current.getName().equalsIgnoreCase(fname)) return;
        Thread worker = new Thread(() -> ModelEditorApp.safe(() -> {
            EditableModel loaded = ModelIO.load(current);
            SwingUtilities.invokeLater(() -> ModelEditorApp.safe(() -> {
                EditableModel snapshot = ModelCopies.copy(editor.model);
                if (editor.animText != null) snapshot.setRotationAnglesCode = editor.animText.getText();
                if (!current.equals(editor.document.file()) || editor.document.isModified(snapshot)) return;
                editor.pushHistory();
                editor.model = loaded;
                editor.document.remember(current, loaded);
                editor.selected = loaded.parts.isEmpty() ? null : loaded.parts.get(0);
                editor.refreshPartListSelect(editor.selected);
                editor.syncModelFieldsToUI();
                if (editor.animText != null) editor.animText.setText(loaded.setRotationAnglesCode == null ? "" : loaded.setRotationAnglesCode);
            }));
        }), "ReloadModel");
        worker.setDaemon(true);
        worker.start();
    }
}
