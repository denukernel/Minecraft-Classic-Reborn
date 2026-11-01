package net.classicremastered.toolkit.modeleditor;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import java.awt.image.BufferedImage;
import java.io.File;


import net.classicremastered.minecraft.model.*;

import javax.swing.*;
import java.awt.*;

public final class ModelEditorApp {

    final ModelEditorDocument document = new ModelEditorDocument();
    volatile boolean running = true;
    void saveAs() { modelEditorFiles.saveAs(); }
    boolean confirmDiscard() { return modelEditorFiles.confirmDiscard(); }

    private final ModelEditorUi modelEditorUi = new ModelEditorUi(this);
    private final ModelEditorInput modelEditorInput = new ModelEditorInput(this);
    private final ModelEditorViewport modelEditorViewport = new ModelEditorViewport(this);
    private final ModelEditorFiles modelEditorFiles = new ModelEditorFiles(this);
    private final ModelEditorHistory modelEditorHistory = new ModelEditorHistory(this);
    private final ModelEditorParts modelEditorParts = new ModelEditorParts(this);
    private final ModelEditorWatcher modelEditorWatcher = new ModelEditorWatcher(this);

    public static void main(String[] args) throws Exception {
        net.classicremastered.toolkit.debugmodel.LWJGLNatives.useMCraftClientNatives();
        new ModelEditorApp().run();
    }

    EditableModel model = new EditableModel();
    EditablePart selected = null;

    // camera
    float yaw = 30, pitch = 15, dist = 7;
    float targetX = 0.0f, targetY = 1.2f, targetZ = 0.0f;
    volatile boolean triggerPicking = false;
    int pickingX = 0, pickingY = 0;
    // Suppress UI-driven mutations while restoring a snapshot

    // Debounce duplicate to avoid multiple triggers (mouse repeat/double-fire)
    long lastDuplicateAt = 0L;
    static final long DUP_DEBOUNCE_MS = 160;

    // --- UI bits ---
    JFrame frame;
    Canvas glCanvas;
    DefaultListModel<EditablePart> partListModel = new DefaultListModel<>();
    JList<EditablePart> partList;
    JList<EditablePart> settingsPartList;
    CardLayout mainCardLayout;
    JPanel mainCardContainer;
    JButton btn3DMode;
    JButton btnSettingsMode;
    // Updated only on EDT; read by the render loop
    volatile boolean typingInSwing = false;

    // part fields
    JTextField partName;
    JCheckBox mirror;
    JSpinner u, v, x, y, z, w, h, d, posX, posY, posZ, pitchSp, yawSp, rollSp, inflate;

    // atlas fields
    JSpinner atlasW, atlasH;
    JTextField modelName;

    // Preview checkboxes
    JCheckBox chkPreviewAnim;
    JCheckBox chkAggressive;

    // guard to avoid feedback loops when pushing model→UI
    volatile boolean updatingUI = false;
 // ---- Hot reload watcher ----
    java.nio.file.WatchService watchService;
    Thread watcherThread;
    volatile boolean watcherRunning = false;

    // --- texture state ---
    int glTextureId = 0;
    int texW = 0, texH = 0;
    String textureName = "(none)";
    // Edit-mode gate: true = edit in GL canvas, false = typing in Swing
    volatile boolean canvasEdit = true;
    volatile boolean inFocusTransition = false;
    long lastUiUpdate = 0L;

    // --- path helpers ---
    File getDefaultDir() { return modelEditorFiles.getDefaultDir(); }

    static String ensureExt(String name, String ext) {
        return name.toLowerCase().endsWith(ext) ? name : (name + ext);
    }

    static File uniqueIfExists(File f) {
        if (!f.exists())
            return f;
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        String base = (dot >= 0 ? name.substring(0, dot) : name);
        String ext = (dot >= 0 ? name.substring(dot) : "");
        int n = 1;
        File g;
        do {
            g = new File(f.getParentFile(), base + "_" + (n++) + ext);
        } while (g.exists());
        return g;
    }

    void upd() { modelEditorUi.upd(); }

    // native file dialogs
    File showSaveDialog(String title, String defaultName, String ext) { return modelEditorFiles.showSaveDialog(title, defaultName, ext); }

    File showOpenDialog(String title, String ext) { return modelEditorFiles.showOpenDialog(title, ext); }

    // --- keep model box consistent and safe on the EDT ---
    void syncModelFieldsToUI() { modelEditorUi.syncModelFieldsToUI(); }

    // Tracks whether a Swing text input/spinner currently has focus
    void installFocusTracking() { modelEditorInput.installFocusTracking(); }

    // Recognize Swing components that should block LWJGL key input
    boolean isEditorComponent(Object obj) { return modelEditorInput.isEditorComponent(obj); }

    // True while the mouse cursor is over the GL canvas (updated on EDT)
    volatile boolean canvasHover = false;
    void startHotReloadWatcher() { modelEditorWatcher.startHotReloadWatcher(); }

    void stopHotReloadWatcher() { modelEditorWatcher.stopHotReloadWatcher(); }
    void reloadTextureAsync() { modelEditorWatcher.reloadTextureAsync(); }

    void reloadModelAsync(String fname) { modelEditorWatcher.reloadModelAsync(fname); }

    public void run() throws Exception {
        // Initialize default model
        model.name = "new_model";
        model.atlasW = 64;
        model.atlasH = 32;
        EditablePart head = new EditablePart("head");
        head.u = 0;
        head.v = 0;
        head.x = -4;
        head.y = -8;
        head.z = -4;
        head.w = 8;
        head.h = 8;
        head.d = 8;
        selected = head;
        model.parts.add(head);

        // --- Build UI safely on Swing EDT ---
        SwingUtilities.invokeAndWait(() -> {
            buildUI();
            installHoverTracking();
            installFocusHandlers();
            installFocusTracking();
            installGlobalHotkeys();
            refreshPartListSelect(head);
            syncModelFieldsToUI();
            frame.setVisible(true);
            startHotReloadWatcher();
        });

        // Ensure AWT Canvas peer exists before GL
        SwingUtilities.invokeAndWait(() -> {
            glCanvas.addNotify();
        });

        // --- Start OpenGL render thread ---
        Thread glThread = new Thread(() -> {
            try {
                Display.setParent(glCanvas);
                Display.setDisplayMode(new DisplayMode(glCanvas.getWidth(), glCanvas.getHeight()));
                Display.setTitle("Model Editor (Ctrl+S save, Ctrl+L load, Ctrl+E export)");
                Display.create();
                Keyboard.create();
                Mouse.create();

                initGL();

                // --- Main render loop ---
                while (running && !Display.isCloseRequested()) {
                    try {
                        if (!Display.isVisible()) {
                            Thread.sleep(50);
                            continue;
                        }

                        input();
                        render();
                        Display.update();

                        Display.sync(60);

                    } catch (Throwable loopErr) {
                        loopErr.printStackTrace();
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
            } finally {
                stopHotReloadWatcher();
                try {
                    modelEditorViewport.dispose();
                    if (glTextureId != 0)
                        GL11.glDeleteTextures(glTextureId);
                    if (Display.isCreated())
                        Display.destroy();
                } catch (Throwable ignored) {
                }

                SwingUtilities.invokeLater(() -> {
                    if (frame != null)
                        frame.dispose();
                });
            }
        }, "GLThread");

        glThread.setDaemon(false);
        glThread.start();
    }

    boolean restoring = false; // suppress listeners while restoring snapshots
    // ---------------- UI ----------------
    // UV viewer + CPU copy of the atlas
    UVViewerPanel uvPanel;
    BufferedImage textureImage = null;

    void doLoad() { modelEditorFiles.doLoad(); }

    // at top with other fields
    JTextArea animText;

    void doImportJava() { modelEditorFiles.doImportJava(); }

    static final int HISTORY_LIMIT = 100;

    static final class Snapshot {
        final EditableModel model;
        final int[] selPath;
        final ModelEditorDocument.State document;

        Snapshot(EditableModel m, int[] p, ModelEditorDocument.State document) {
            this.model = m;
            this.selPath = p;
            this.document = document;
        }
    }

    final java.util.Deque<Snapshot> undoStack = new java.util.ArrayDeque<>();
    final java.util.Deque<Snapshot> redoStack = new java.util.ArrayDeque<>();
    long lastHistPush = 0L; // throttle for spinner edits
    boolean inKeyEdit = false; // batch key-held edits into one snapshot
    // ---- Deep copy / snapshot helpers ----

    EditableModel copyModel(EditableModel m) { return modelEditorHistory.copyModel(m); }

    Snapshot takeSnapshot() { return modelEditorHistory.takeSnapshot(); }

    void restoreSnapshot(Snapshot s) { modelEditorHistory.restoreSnapshot(s); }

    void pushHistory() { modelEditorHistory.pushHistory(); }

    void pushHistoryThrottled() { modelEditorHistory.pushHistoryThrottled(); }

    boolean skipOneEditFrame = false;

    void undoAction() { modelEditorHistory.undoAction(); }

    void redoAction() { modelEditorHistory.redoAction(); }

    // selection path by indices (rootIndex, childIndex, ...)
    int[] indexPathOf(EditablePart target) { return modelEditorHistory.indexPathOf(target); }

    java.util.List<Integer> findPath(EditablePart cur, EditablePart target) { return modelEditorHistory.findPath(cur, target); }

    EditablePart resolveByIndexPath(int[] path) { return modelEditorHistory.resolveByIndexPath(path); }

    void bindIntSpinner(JSpinner s, java.util.function.IntConsumer apply) { modelEditorUi.bindIntSpinner(s, apply); }

    void bindFloatSpinner(JSpinner s, java.util.function.DoubleConsumer apply) { modelEditorUi.bindFloatSpinner(s, apply); }

    // Give focus back to the Swing side panel (first editable field)
    // Only put focus somewhere if nothing already has a text editor focus
    void focusSidePanel() { modelEditorInput.focusSidePanel(); }
    void restoreFocusAfterDialog() { modelEditorInput.restoreFocusAfterDialog(); }

    // Enable/disable list keyboard navigation so it won't steal arrow keys while
    // typing
    void setListInteractive(boolean on) { modelEditorInput.setListInteractive(on); }

    // ---------------- UI ----------------
    void buildUI() { modelEditorUi.buildUI(); }

    // helper UI builders
    JPanel titledBox(String title) { return modelEditorUi.titledBox(title); }

    JButton createButton(String text) { return modelEditorUi.createButton(text); }

    JPanel row(String label, JComponent comp) { return modelEditorUi.row(label, comp); }

    JSpinner spinnerInt(int val, int min, int max, int step) { return modelEditorUi.spinnerInt(val, min, max, step); }

    JSpinner spinnerFloat(double val, double min, double max, double step) { return modelEditorUi.spinnerFloat(val, min, max, step); }

    // synchronize all access to model/selection across threads
    final Object modelLock = new Object();

    void refreshPartListSelect(EditablePart toSelect) { modelEditorUi.refreshPartListSelect(toSelect); }

    void addPartTreeToList(EditablePart p) { modelEditorUi.addPartTreeToList(p); }

    /** READ-ONLY: push model → UI (no writes to selected.*) */
    void loadSelectedToUI() { modelEditorUi.loadSelectedToUI(); }

    void applyUIToSelected() { modelEditorUi.applyUIToSelected(); }

    EditablePart makeNewPart() { return modelEditorParts.makeNewPart(); }

    EditablePart copyPart(EditablePart p) { return modelEditorParts.copyPart(p); }
    String safeCopyName(String original) { return modelEditorParts.safeCopyName(original); }

    static java.util.List<EditablePart> getAllParts(EditableModel m) {
        java.util.List<EditablePart> all = new java.util.ArrayList<>();
        for (EditablePart p : m.parts)
            collect(all, p);
        return all;
    }

    static void collect(java.util.List<EditablePart> list, EditablePart p) {
        list.add(p);
        for (EditablePart c : p.children)
            collect(list, c);
    }

    EditablePart parentOf(EditablePart target) { return modelEditorParts.parentOf(target); }

    EditablePart parentSearch(EditablePart cur, EditablePart target) { return modelEditorParts.parentSearch(cur, target); }

    // ---------------- GL ----------------

    void initGL() { modelEditorViewport.initGL(); }

    // True if a Swing text component/spinner editor currently has focus
    boolean hasTypingFocus() { return modelEditorInput.hasTypingFocus(); }
    // Call once after buildUI() shows the frame (or at end of buildUI()).

    void input() { modelEditorInput.input(); }

    void switchMode(boolean toCanvas) { modelEditorInput.switchMode(toCanvas); }

    void enterCanvasEditMode() { modelEditorInput.enterCanvasEditMode(); }

    void enterSwingTypingMode() { modelEditorInput.enterSwingTypingMode(); }

    // Disable and enable LWJGL keyboard grabbing
    boolean keyboardActive = true;

    void disableKeyboard() { modelEditorInput.disableKeyboard(); }

    void enableKeyboard() { modelEditorInput.enableKeyboard(); }

    // Run a task on EDT
    static void onEDT(Runnable r) {
        if (SwingUtilities.isEventDispatchThread())
            r.run();
        else
            SwingUtilities.invokeLater(r);
    }

    // Save: choose file on EDT, then write on a worker thread
    void requestSave() { modelEditorFiles.requestSave(); }

    void requestLoad() { modelEditorFiles.requestLoad(); }

    void requestExportJava() { modelEditorFiles.requestExportJava(); }

    void requestExportTemplate() { modelEditorFiles.requestExportTemplate(); }

    void requestImportJava() { modelEditorFiles.requestImportJava(); }

    // Call once after buildUI() shows the frame
    void installHoverTracking() { modelEditorInput.installHoverTracking(); }

// Call once after buildUI() shows the frame
    // Call once after buildUI() shows the frame (or at end of buildUI()).
    // Control when the GL canvas actually steals keyboard focus
    // Control focus for the GL canvas: any click inside → canvas gets focus
    // Any click inside the canvas → start editing in GL immediately
    void installFocusHandlers() { modelEditorInput.installFocusHandlers(); }

    // Update window title to reflect current mode
    void updateModeTitle() { modelEditorInput.updateModeTitle(); }

    void installGlobalHotkeys() { modelEditorInput.installGlobalHotkeys(); }

    // Suppress UI-driven mutations while restoring a snapshot

    void newProject() { modelEditorFiles.newProject(); }

    void doSave() { modelEditorFiles.doSave(); }

    void doExportJava() { modelEditorFiles.doExportJava(); }

    void doExportTemplate() { modelEditorFiles.doExportTemplate(); }

    String texturePath = null; // full path to texture file
    JLabel textureLabel; // label in UI // --- Texture import / clear ---

    void doImportTexture() { modelEditorFiles.doImportTexture(); }

    void clearTexture() { modelEditorFiles.clearTexture(); }

    int uploadTexture(java.awt.image.BufferedImage img) { return modelEditorViewport.uploadTexture(img); }
    // ------------------------------------------------------------
    // UV viewer panel: checkerboard + atlas + UV nets with selection
    // Mouse: wheel=zoom, right-drag=pan, left-click=select by UV
    // ------------------------------------------------------------

    void deleteSelected() { modelEditorParts.deleteSelected(); }

    boolean deleteRecursive(EditablePart parent, EditablePart target) { return modelEditorParts.deleteRecursive(parent, target); }

    // ------------------------------------------------------------
    // UV viewer panel: checkerboard + atlas + per-face UVs (BB-like)
    // ------------------------------------------------------------

    void render() { modelEditorViewport.render(); }

    final java.util.concurrent.ConcurrentLinkedQueue<Runnable> glTasks = new java.util.concurrent.ConcurrentLinkedQueue<>();

    void renderPart(EditablePart ep, float scale, EditablePart sel, float time) { modelEditorViewport.renderPart(ep, scale, sel, time); }

    void renderPartForPicking(EditablePart ep, float scale, java.util.List<EditablePart> allParts) { modelEditorViewport.renderPartForPicking(ep, scale, allParts); }

    static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    void drawGrid(int half, float step) { modelEditorViewport.drawGrid(half, step); }

    static void safe(IORun r) {
        try {
            r.run();
        } catch (Exception e) {
            e.printStackTrace();
            if (!GraphicsEnvironment.isHeadless()) onEDT(() -> JOptionPane.showMessageDialog(null,
                e.getMessage(), "Model Editor Error", JOptionPane.ERROR_MESSAGE));
        }
    }

    interface IORun {
        void run() throws Exception;
    }
}
