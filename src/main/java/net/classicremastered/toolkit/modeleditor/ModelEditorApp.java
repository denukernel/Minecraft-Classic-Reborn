package net.classicremastered.toolkit.modeleditor;

import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import net.classicremastered.minecraft.model.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public final class ModelEditorApp {

    public static void main(String[] args) throws Exception {
        net.classicremastered.toolkit.debugmodel.LWJGLNatives.useMCraftClientNatives();
        new ModelEditorApp().run();
    }

    private EditableModel model = new EditableModel();
    private EditablePart selected = null;

    // camera
    private float yaw = 30, pitch = 15, dist = 7;
    // Suppress UI-driven mutations while restoring a snapshot

    // Debounce duplicate to avoid multiple triggers (mouse repeat/double-fire)
    private long lastDuplicateAt = 0L;
    private static final long DUP_DEBOUNCE_MS = 160;

    // --- UI bits ---
    private JFrame frame;
    private Canvas glCanvas;
    private DefaultListModel<EditablePart> partListModel = new DefaultListModel<>();
    private JList<EditablePart> partList;
    // Updated only on EDT; read by the render loop
    private volatile boolean typingInSwing = false;

    // part fields
    private JTextField partName;
    private JCheckBox mirror;
    private JSpinner u, v, x, y, z, w, h, d, posX, posY, posZ, pitchSp, yawSp, rollSp, inflate;

    // atlas fields
    private JSpinner atlasW, atlasH;
    private JTextField modelName;

    // guard to avoid feedback loops when pushing model→UI
    private volatile boolean updatingUI = false;

    // --- texture state ---
    private int glTextureId = 0;
    private int texW = 0, texH = 0;
    private String textureName = "(none)";
    // Edit-mode gate: true = edit in GL canvas, false = typing in Swing
    private volatile boolean canvasEdit = true;

    // --- path helpers ---
    private File getDefaultDir() {
        String appdata = System.getenv("APPDATA");
        if (appdata == null)
            appdata = System.getProperty("user.home");
        File dir = new File(appdata, ".mcraft/client/modelexport");
        if (!dir.exists())
            dir.mkdirs();
        return dir;
    }

    private static String ensureExt(String name, String ext) {
        return name.toLowerCase().endsWith(ext) ? name : (name + ext);
    }

    private static File uniqueIfExists(File f) {
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

    private void upd() {
        if (selected == null || updatingUI)
            return;
        pushHistoryThrottled();
        selected.name = partName.getText().trim();
        partList.repaint();
        uvPanel.repaint();
    }

    // native file dialogs
    private File showSaveDialog(String title, String defaultName, String ext) {
        Frame owner = new Frame(); // no UI shown
        FileDialog fd = new FileDialog(owner, title, FileDialog.SAVE);
        fd.setDirectory(getDefaultDir().getAbsolutePath());
        fd.setFile(ensureExt(defaultName, ext));
        fd.setVisible(true);
        owner.dispose();
        if (fd.getFile() == null)
            return null;
        File chosen = new File(fd.getDirectory(), fd.getFile());
        chosen = new File(chosen.getParentFile(), ensureExt(chosen.getName(), ext));
        return uniqueIfExists(chosen);
    }

    private File showOpenDialog(String title, String ext) {
        Frame owner = new Frame();
        FileDialog fd = new FileDialog(owner, title, FileDialog.LOAD);
        fd.setDirectory(getDefaultDir().getAbsolutePath());
        fd.setFile("*" + ext);
        fd.setVisible(true);
        owner.dispose();
        if (fd.getFile() == null)
            return null;
        return new File(fd.getDirectory(), fd.getFile());
    }

    // --- keep model box consistent and safe on the EDT ---
    private void syncModelFieldsToUI() {
        SwingUtilities.invokeLater(() -> {
            updatingUI = true;
            try {
                modelName.setText(model.name == null ? "" : model.name);
                int aw = (model.atlasW > 0 ? model.atlasW : 64);
                int ah = (model.atlasH > 0 ? model.atlasH : 32);
                atlasW.setValue(aw);
                atlasH.setValue(ah);
            } finally {
                updatingUI = false;
            }
        });
    }

    // Tracks whether a Swing text input/spinner currently has focus
    private void installFocusTracking() {
        java.awt.KeyboardFocusManager kfm = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager();
        kfm.addPropertyChangeListener("focusOwner", evt -> {
            Object obj = evt.getNewValue();
            boolean editing = isEditorComponent(obj);
            typingInSwing = editing;
            if (editing)
                enterSwingTypingMode();
        });
    }

    // Recognize Swing components that should block LWJGL key input
    private boolean isEditorComponent(Object obj) {
        if (!(obj instanceof java.awt.Component))
            return false;

        // Text field or text area
        if (obj instanceof javax.swing.text.JTextComponent)
            return true;

        // Spinner or its inner text field
        if (obj instanceof javax.swing.JSpinner)
            return true;
        if (obj instanceof javax.swing.JFormattedTextField)
            return true;

        // Editable combo box
        if (obj instanceof javax.swing.JComboBox && ((javax.swing.JComboBox<?>) obj).isEditable())
            return true;

        return false;
    }

    // True while the mouse cursor is over the GL canvas (updated on EDT)
    private volatile boolean canvasHover = false;

    public void run() throws Exception {
        // starter: atlas size like Classic
        model.name = "new_model";
        model.atlasW = 64;
        model.atlasH = 32;
        // start with one cube (a head)
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

        // --- Build UI first (EDT) ---
        SwingUtilities.invokeAndWait(this::buildUI);
        SwingUtilities.invokeLater(this::installHoverTracking);
        SwingUtilities.invokeLater(this::installFocusHandlers);
        SwingUtilities.invokeLater(this::installFocusTracking); // <<< required
        SwingUtilities.invokeLater(this::installGlobalTabToggle);
        SwingUtilities.invokeLater(this::installGlobalHotkeys);

        // Create LWJGL inside AWT Canvas
        Display.setParent(glCanvas);
        Display.setDisplayMode(new DisplayMode(glCanvas.getWidth(), glCanvas.getHeight()));
        Display.setTitle("Model Editor (N new, Del delete, S save, L load, E export, F6 template, J import .java)");
        Display.create();
        Keyboard.create();
        Mouse.create();

        initGL();

        while (!Display.isCloseRequested()) {
            input();
            render();
            Display.update();
            Display.sync(60);
        }
        // cleanup
        if (glTextureId != 0)
            GL11.glDeleteTextures(glTextureId);
        Display.destroy();
        frame.dispose();
    }

    private boolean restoring = false; // suppress listeners while restoring snapshots
    // ---------------- UI ----------------
    // UV viewer + CPU copy of the atlas
    private UVViewerPanel uvPanel;
    private BufferedImage textureImage = null;

    private void doLoad() {
        File in = showOpenDialog("Load Model (.mmdl)", ".mmdl");
        if (in == null)
            return;
        safe(() -> {
            pushHistory();
            model = ModelIO.load(in);
            selected = model.parts.isEmpty() ? null : model.parts.get(0);
            refreshPartListSelect(selected);
            syncModelFieldsToUI();
            if (animText != null)
                animText.setText(model.setRotationAnglesCode == null ? "" : model.setRotationAnglesCode);
            System.out.println("[load] " + in.getAbsolutePath());
        });
    }

    // at top with other fields
    private JTextArea animText;

    private void doImportJava() {
        File in = showOpenDialog("Open Classic Java Model (.java)", ".java");
        if (in == null)
            return;
        safe(() -> {
            pushHistory();
            EditableModel imported = JavaModelImporter.load(in);
            this.model = imported;
            this.selected = model.parts.isEmpty() ? null : model.parts.get(0);
            refreshPartListSelect(selected);
            syncModelFieldsToUI();
            if (animText != null)
                animText.setText(model.setRotationAnglesCode == null ? "" : model.setRotationAnglesCode);
            System.out.println("[import .java] " + in.getAbsolutePath() + " → " + model.name);
        });
    }

    private static final int HISTORY_LIMIT = 100;

    private static final class Snapshot {
        final EditableModel model;
        final int[] selPath;

        Snapshot(EditableModel m, int[] p) {
            this.model = m;
            this.selPath = p;
        }
    }

    private final java.util.Deque<Snapshot> undoStack = new java.util.ArrayDeque<>();
    private final java.util.Deque<Snapshot> redoStack = new java.util.ArrayDeque<>();
    private long lastHistPush = 0L; // throttle for spinner edits
    private boolean inKeyEdit = false; // batch key-held edits into one snapshot
    // ---- Deep copy / snapshot helpers ----

    private EditableModel copyModel(EditableModel m) {
        EditableModel c = new EditableModel();
        c.name = m.name;
        c.atlasW = m.atlasW;
        c.atlasH = m.atlasH;
        for (EditablePart p : m.parts)
            c.parts.add(copyPart(p));
        return c;
    }

    private Snapshot takeSnapshot() {
        return new Snapshot(copyModel(model), indexPathOf(selected));
    }

    private void restoreSnapshot(Snapshot s) {
        restoring = true;
        try {
            this.model = s.model;
            this.selected = resolveByIndexPath(s.selPath);
            refreshPartListSelect(selected);
            syncModelFieldsToUI();
            if (uvPanel != null)
                uvPanel.repaint();
            // reset any in-progress edit gesture
            inKeyEdit = false;
        } finally {
            restoring = false;
        }
    }

    private void pushHistory() {
        if (restoring)
            return; // <- critical
        undoStack.push(takeSnapshot());
        while (undoStack.size() > HISTORY_LIMIT)
            undoStack.removeLast();
        redoStack.clear();
        lastHistPush = System.currentTimeMillis();
    }

    private void pushHistoryThrottled() {
        if (restoring)
            return; // <- critical
        long now = System.currentTimeMillis();
        if (now - lastHistPush >= 250)
            pushHistory();
    }

    private boolean skipOneEditFrame = false;

    private void undoAction() {
        if (undoStack.isEmpty())
            return;
        redoStack.push(takeSnapshot());
        restoreSnapshot(undoStack.pop());
    }

    private void redoAction() {
        if (redoStack.isEmpty())
            return;
        undoStack.push(takeSnapshot());
        restoreSnapshot(redoStack.pop());
    }

    // selection path by indices (rootIndex, childIndex, ...)
    private int[] indexPathOf(EditablePart target) {
        if (target == null)
            return new int[0];
        for (int i = 0; i < model.parts.size(); i++) {
            EditablePart r = model.parts.get(i);
            if (r == target)
                return new int[] { i };
            java.util.List<Integer> sub = findPath(r, target);
            if (sub != null) {
                int[] out = new int[sub.size() + 1];
                out[0] = i;
                for (int k = 0; k < sub.size(); k++)
                    out[k + 1] = sub.get(k);
                return out;
            }
        }
        return new int[0];
    }

    private java.util.List<Integer> findPath(EditablePart cur, EditablePart target) {
        for (int i = 0; i < cur.children.size(); i++) {
            EditablePart ch = cur.children.get(i);
            if (ch == target) {
                java.util.ArrayList<Integer> l = new java.util.ArrayList<>();
                l.add(i);
                return l;
            }
            java.util.List<Integer> deeper = findPath(ch, target);
            if (deeper != null) {
                java.util.ArrayList<Integer> l = new java.util.ArrayList<>();
                l.add(i);
                l.addAll(deeper);
                return l;
            }
        }
        return null;
    }

    private EditablePart resolveByIndexPath(int[] path) {
        if (path == null || path.length == 0)
            return model.parts.isEmpty() ? null : model.parts.get(0);
        if (path[0] < 0 || path[0] >= model.parts.size())
            return null;
        EditablePart cur = model.parts.get(path[0]);
        for (int i = 1; i < path.length; i++) {
            int idx = path[i];
            if (idx < 0 || idx >= cur.children.size())
                break;
            cur = cur.children.get(idx);
        }
        return cur;
    }

    private void bindIntSpinner(JSpinner s, java.util.function.IntConsumer apply) {
        s.addChangeListener(e -> {
            if (restoring || updatingUI || selected == null)
                return;
            pushHistoryThrottled();
            apply.accept(((Number) s.getValue()).intValue());
            partList.repaint();
            if (uvPanel != null)
                uvPanel.repaint();
        });
    }

    private void bindFloatSpinner(JSpinner s, java.util.function.DoubleConsumer apply) {
        s.addChangeListener(e -> {
            if (restoring || updatingUI || selected == null)
                return;
            pushHistoryThrottled();
            apply.accept(((Number) s.getValue()).doubleValue());
            partList.repaint();
            if (uvPanel != null)
                uvPanel.repaint();
        });
    }

    // Give focus back to the Swing side panel (first editable field)
    // Only put focus somewhere if nothing already has a text editor focus
    private void focusSidePanel() {
        SwingUtilities.invokeLater(() -> {
            Component fo = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (isEditorComponent(fo))
                return; // don't steal focus from what you just clicked

            // Prefer model-level editors first
            if (modelName != null && modelName.isShowing()) {
                modelName.requestFocusInWindow();
                modelName.selectAll();
                return;
            }
            if (atlasW != null && atlasW.isShowing()) {
                ((JSpinner.DefaultEditor) atlasW.getEditor()).getTextField().requestFocusInWindow();
                ((JSpinner.DefaultEditor) atlasW.getEditor()).getTextField().selectAll();
                return;
            }
            if (atlasH != null && atlasH.isShowing()) {
                ((JSpinner.DefaultEditor) atlasH.getEditor()).getTextField().requestFocusInWindow();
                ((JSpinner.DefaultEditor) atlasH.getEditor()).getTextField().selectAll();
                return;
            }
            // Fallback to partName
            if (partName != null && partName.isShowing()) {
                partName.requestFocusInWindow();
                partName.selectAll();
            }
        });
    }

    // Enable/disable list keyboard navigation so it won't steal arrow keys while
    // typing
    private void setListInteractive(boolean on) {
        if (partList != null) {
            partList.setFocusable(on); // blocks key navigation when false
            // optional: also kill some common bindings when disabled
            if (!on) {
                InputMap im = partList.getInputMap(JComponent.WHEN_FOCUSED);
                im.put(KeyStroke.getKeyStroke("UP"), "none");
                im.put(KeyStroke.getKeyStroke("DOWN"), "none");
                im.put(KeyStroke.getKeyStroke("PAGE_UP"), "none");
                im.put(KeyStroke.getKeyStroke("PAGE_DOWN"), "none");
                im.put(KeyStroke.getKeyStroke("HOME"), "none");
                im.put(KeyStroke.getKeyStroke("END"), "none");
            }
        }
    }

    // ---------------- UI ----------------
    private void buildUI() {
        frame = new JFrame("Classic Model Editor");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        // ----- CENTER: GL canvas + UV viewer (Blockbench-style) -----
        glCanvas = new Canvas();
        glCanvas.setPreferredSize(new Dimension(820, 720));

        // UV viewer shows current atlas and UV rects
        uvPanel = new UVViewerPanel(() -> textureImage, () -> model, () -> selected);
        uvPanel.setPreferredSize(new Dimension(820, 260));

        JSplitPane centerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, glCanvas, uvPanel);
        centerSplit.setResizeWeight(1.0);
        centerSplit.setDividerSize(6);
        frame.add(centerSplit, BorderLayout.CENTER);

        // ----- RIGHT: side panel -----
        JPanel side = new JPanel();
        side.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));

        // Model section
        JPanel modelBox = titledBox("Model");
        modelName = new JTextField(model.name, 16);
        atlasW = spinnerInt(model.atlasW, 1, 4096, 1);
        atlasH = spinnerInt(model.atlasH, 1, 4096, 1);
        modelBox.add(row("Name:", modelName));
        modelBox.add(row("Atlas W:", atlasW));
        modelBox.add(row("Atlas H:", atlasH));
        JButton btnApplyAtlas = new JButton("Apply Atlas");
        btnApplyAtlas.addActionListener(e -> {
            try {
                atlasW.commitEdit();
                atlasH.commitEdit();
            } catch (java.text.ParseException ignore) {
            }
            pushHistory();
            model.name = modelName.getText().trim();
            model.atlasW = ((Number) atlasW.getValue()).intValue();
            model.atlasH = ((Number) atlasH.getValue()).intValue();
            uvPanel.repaint();
        });
        modelBox.add(btnApplyAtlas);
        side.add(modelBox);
        side.add(Box.createVerticalStrut(8));

        // Parts section
        JPanel partsBox = titledBox("Parts");
        partList = new JList<>(partListModel);
        partList.setVisibleRowCount(10);
        partList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        partList.setCellRenderer((lst, value, idx, isSel, hasFocus) -> {
            JLabel lbl = new JLabel(value == null ? "(null)" : value.name);
            lbl.setOpaque(true);
            lbl.setBackground(isSel ? new Color(0x2d, 0x6a, 0xa3) : Color.WHITE);
            lbl.setForeground(isSel ? Color.WHITE : Color.DARK_GRAY);
            lbl.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            return lbl;
        });
        JScrollPane sp = new JScrollPane(partList);
        partsBox.add(sp);

        JButton btnAddRoot = new JButton("Add Root Cube");
        JButton btnAddChild = new JButton("Add Child Cube");
        JButton btnDelete = new JButton("Delete");
        JButton btnDuplicate = new JButton("Duplicate");

        btnAddRoot.addActionListener(e -> {
            pushHistory();
            EditablePart np = makeNewPart();
            model.parts.add(np);
            selected = np;
            refreshPartListSelect(np);
        });
        btnAddChild.addActionListener(e -> {
            pushHistory();
            EditablePart np = makeNewPart();
            if (selected != null)
                selected.children.add(np);
            else
                model.parts.add(np);
            selected = np;
            refreshPartListSelect(np);
        });
        btnDelete.addActionListener(e -> {
            if (selected == null)
                return;
            pushHistory();
            deleteSelected();
            refreshPartListSelect(selected);
        });
        btnDuplicate.addActionListener(e -> {
            if (selected == null)
                return;
            pushHistory();
            EditablePart cp = copyPart(selected);
            EditablePart parent = parentOf(selected);
            if (parent != null)
                parent.children.add(cp);
            else
                model.parts.add(cp);
            selected = cp;
            refreshPartListSelect(cp);
        });

        JPanel partBtns = new JPanel(new GridLayout(1, 4, 6, 0));
        partBtns.add(btnAddRoot);
        partBtns.add(btnAddChild);
        partBtns.add(btnDuplicate);
        partBtns.add(btnDelete);
        partsBox.add(Box.createVerticalStrut(6));
        partsBox.add(partBtns);
        side.add(partsBox);
        side.add(Box.createVerticalStrut(8));

        // Selected Part Properties
        JPanel propBox = titledBox("Selected Part");
        partName = new JTextField(selected != null ? selected.name : "", 14);
        mirror = new JCheckBox("Mirror");
        u = spinnerInt(0, 0, 4096, 1);
        v = spinnerInt(0, 0, 4096, 1);
        x = spinnerInt(0, -128, 128, 1);
        y = spinnerInt(0, -128, 128, 1);
        z = spinnerInt(0, -128, 128, 1);
        w = spinnerInt(4, 1, 256, 1);
        h = spinnerInt(4, 1, 256, 1);
        d = spinnerInt(4, 1, 256, 1);
        posX = spinnerFloat(0, -128, 128, 0.1);
        posY = spinnerFloat(0, -128, 128, 0.1);
        posZ = spinnerFloat(0, -128, 128, 0.1);
        pitchSp = spinnerFloat(0, -Math.PI * 2, Math.PI * 2, Math.toRadians(5));
        yawSp = spinnerFloat(0, -Math.PI * 2, Math.PI * 2, Math.toRadians(5));
        rollSp = spinnerFloat(0, -Math.PI * 2, Math.PI * 2, Math.toRadians(5));
        inflate = spinnerFloat(0, 0, 32, 0.1);

        propBox.add(row("Name:", partName));
        propBox.add(row("Mirror:", mirror));
        propBox.add(row("UV U:", u));
        propBox.add(row("UV V:", v));
        propBox.add(row("Box X:", x));
        propBox.add(row("Box Y:", y));
        propBox.add(row("Box Z:", z));
        propBox.add(row("Width:", w));
        propBox.add(row("Height:", h));
        propBox.add(row("Depth:", d));
        propBox.add(row("Pivot X:", posX));
        propBox.add(row("Pivot Y:", posY));
        propBox.add(row("Pivot Z:", posZ));
        propBox.add(row("Pitch (rad):", pitchSp));
        propBox.add(row("Yaw (rad):", yawSp));
        propBox.add(row("Roll (rad):", rollSp));
        propBox.add(row("Inflate:", inflate));

        JButton apply = new JButton("Apply Changes");
        apply.addActionListener(e -> {
            pushHistory();
            applyUIToSelected();
            uvPanel.repaint();
        });
        propBox.add(apply);

        side.add(propBox);
        side.add(Box.createVerticalStrut(8));

        // File / Texture ops
        JPanel io = titledBox("File");
        JButton saveBtn = new JButton("Save (.mmdl)");
        JButton loadBtn = new JButton("Load (.mmdl)");
        JButton exportJavaBtn = new JButton("Export Java");
        JButton exportUVBtn = new JButton("Export UV Template");
        JButton importJavaBtn = new JButton("Import .java");
        textureLabel = new JLabel(textureName);
        JButton importTexBtn = new JButton("Import Texture");
        JButton clearTexBtn = new JButton("Clear Texture");

        JButton newBtn = new JButton("New Project");
        JButton undoBtn = new JButton("Undo (Ctrl+Z)");
        JButton redoBtn = new JButton("Redo (Ctrl+X)");

        importTexBtn.addActionListener(e -> doImportTexture());
        clearTexBtn.addActionListener(e -> clearTexture());
        newBtn.addActionListener(e -> newProject());
        undoBtn.addActionListener(e -> undoAction());
        redoBtn.addActionListener(e -> redoAction());

        JPanel texRow = new JPanel(new BorderLayout(6, 0));
        texRow.add(new JLabel("Texture:"), BorderLayout.WEST);
        texRow.add(textureLabel, BorderLayout.CENTER);
        JPanel texBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        texBtns.add(importTexBtn);
        texBtns.add(clearTexBtn);
        texRow.add(texBtns, BorderLayout.EAST);
        io.add(texRow);

        saveBtn.addActionListener(e -> doSave());
        loadBtn.addActionListener(e -> doLoad());
        exportJavaBtn.addActionListener(e -> doExportJava());
        exportUVBtn.addActionListener(e -> doExportTemplate());
        importJavaBtn.addActionListener(e -> doImportJava());

        JPanel ioGrid = new JPanel(new GridLayout(2, 3, 6, 6));
        ioGrid.add(saveBtn);
        ioGrid.add(loadBtn);
        ioGrid.add(importJavaBtn);
        ioGrid.add(exportJavaBtn);
        ioGrid.add(exportUVBtn);
        ioGrid.add(new JLabel());
        io.add(ioGrid);

        JPanel ioGrid2 = new JPanel(new GridLayout(1, 3, 6, 6));
        ioGrid2.add(newBtn);
        ioGrid2.add(undoBtn);
        ioGrid2.add(redoBtn);
        io.add(Box.createVerticalStrut(6));
        io.add(ioGrid2);

        side.add(io);

        // --- NEW: Animations editor (setRotationAngles) ---
        side.add(Box.createVerticalStrut(8));
        JPanel animBox = titledBox("Animations (setRotationAngles)");
        animText = new JTextArea(8, 20);
        animText.setLineWrap(true);
        animText.setWrapStyleWord(true);
        animText.setText(model.setRotationAnglesCode == null ? "" : model.setRotationAnglesCode);
        JScrollPane animScroll = new JScrollPane(animText);
        animBox.add(animScroll);
        JButton applyAnimBtn = new JButton("Apply Animations");
        applyAnimBtn.addActionListener(e -> {
            pushHistory();
            model.setRotationAnglesCode = animText.getText();
            System.out.println("[anim] Updated setRotationAnglesCode ("
                    + (model.setRotationAnglesCode == null ? 0 : model.setRotationAnglesCode.length()) + " chars)");
        });
        animBox.add(applyAnimBtn);
        side.add(animBox);

        frame.add(side, BorderLayout.EAST);

        // selection → selected
        partList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                EditablePart sel = partList.getSelectedValue();
                if (sel != null) {
                    selected = sel;
                    loadSelectedToUI();
                    uvPanel.setSelected(sel);
                }
            }
        });

        // initial list fill
        refreshPartListSelect(selected);

        // Commit spinner text on focus loss
        for (JSpinner s : new JSpinner[] { u, v, x, y, z, w, h, d, posX, posY, posZ, pitchSp, yawSp, rollSp, inflate,
                atlasW, atlasH }) {
            ((JSpinner.DefaultEditor) s.getEditor()).getTextField()
                    .setFocusLostBehavior(JFormattedTextField.COMMIT_OR_REVERT);
        }

        // Live binding
        bindIntSpinner(u, v -> {
            if (selected != null)
                selected.u = v;
            uvPanel.repaint();
        });
        bindIntSpinner(v, v -> {
            if (selected != null)
                selected.v = v;
            uvPanel.repaint();
        });
        bindIntSpinner(x, v -> {
            if (selected != null)
                selected.x = v;
        });
        bindIntSpinner(y, v -> {
            if (selected != null)
                selected.y = v;
        });
        bindIntSpinner(z, v -> {
            if (selected != null)
                selected.z = v;
        });
        bindIntSpinner(w, v -> {
            if (selected != null)
                selected.w = v;
            uvPanel.repaint();
        });
        bindIntSpinner(h, v -> {
            if (selected != null)
                selected.h = v;
            uvPanel.repaint();
        });
        bindIntSpinner(d, v -> {
            if (selected != null)
                selected.d = v;
            uvPanel.repaint();
        });

        bindFloatSpinner(posX, d -> {
            if (selected != null)
                selected.posX = (float) d;
        });
        bindFloatSpinner(posY, d -> {
            if (selected != null)
                selected.posY = (float) d;
        });
        bindFloatSpinner(posZ, d -> {
            if (selected != null)
                selected.posZ = (float) d;
        });
        bindFloatSpinner(pitchSp, d -> {
            if (selected != null)
                selected.pitch = (float) d;
        });
        bindFloatSpinner(yawSp, d -> {
            if (selected != null)
                selected.yaw = (float) d;
        });
        bindFloatSpinner(rollSp, d -> {
            if (selected != null)
                selected.roll = (float) d;
        });
        bindFloatSpinner(inflate, d -> {
            if (selected != null)
                selected.inflate = (float) d;
        });

        mirror.addActionListener(e -> {
            if (restoring || selected == null || updatingUI)
                return;
            pushHistory();
            selected.mirror = mirror.isSelected();
            uvPanel.repaint();
        });

        partName.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void apply() {
                if (restoring || selected == null || updatingUI)
                    return;
                pushHistoryThrottled();
                selected.name = partName.getText().trim();
                partList.repaint();
                uvPanel.repaint();
            }

            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                apply();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                apply();
            }

            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                apply();
            }
        });

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // helper UI builders
    private JPanel titledBox(String title) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createTitledBorder(title));
        return p;
    }

    private JPanel row(String label, JComponent comp) {
        JPanel r = new JPanel(new BorderLayout(6, 0));
        r.add(new JLabel(label), BorderLayout.WEST);
        r.add(comp, BorderLayout.CENTER);
        r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        return r;
    }

    private JSpinner spinnerInt(int val, int min, int max, int step) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(val, min, max, step));
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        // force integer display
        JSpinner.NumberEditor ed = new JSpinner.NumberEditor(s, "0");
        s.setEditor(ed);
        return s;
    }

    private JSpinner spinnerFloat(double val, double min, double max, double step) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(val, min, max, step));
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        JSpinner.NumberEditor ed = new JSpinner.NumberEditor(s, "0.###");
        s.setEditor(ed);
        return s;
    }

    // synchronize all access to model/selection across threads
    private final Object modelLock = new Object();

    private void refreshPartListSelect(EditablePart toSelect) {
        partListModel.clear();
        java.util.List<EditablePart> partsSnapshot;
        EditablePart selSnapshot;
        synchronized (modelLock) {
            partsSnapshot = new java.util.ArrayList<>(model.parts);
            selSnapshot = toSelect;
        }

        for (EditablePart p : partsSnapshot)
            addPartTreeToList(p);

        if (selSnapshot != null) {
            partList.setSelectedValue(selSnapshot, true);
        }
        loadSelectedToUI();
        if (uvPanel != null)
            uvPanel.setSelected(selSnapshot);
    }

    private void addPartTreeToList(EditablePart p) {
        partListModel.addElement(p);
        for (EditablePart c : p.children)
            addPartTreeToList(c);
    }

    /** READ-ONLY: push model → UI (no writes to selected.*) */
    private void loadSelectedToUI() {
        boolean has = selected != null;
        for (JComponent c : new JComponent[] { partName, mirror, u, v, x, y, z, w, h, d, posX, posY, posZ, pitchSp,
                yawSp, rollSp, inflate })
            c.setEnabled(has);

        if (!has) {
            partName.setText("");
            return;
        }

        updatingUI = true;
        try {
            partName.setText(selected.name);
            mirror.setSelected(selected.mirror);

            // ints — cast in case EditablePart stores floats
            u.setValue(Integer.valueOf(selected.u));
            v.setValue(Integer.valueOf(selected.v));
            x.setValue(Integer.valueOf((int) selected.x));
            y.setValue(Integer.valueOf((int) selected.y));
            z.setValue(Integer.valueOf((int) selected.z));
            w.setValue(Integer.valueOf((int) selected.w));
            h.setValue(Integer.valueOf((int) selected.h));
            d.setValue(Integer.valueOf((int) selected.d));

            // floats
            posX.setValue(Double.valueOf(selected.posX));
            posY.setValue(Double.valueOf(selected.posY));
            posZ.setValue(Double.valueOf(selected.posZ));
            pitchSp.setValue(Double.valueOf(selected.pitch));
            yawSp.setValue(Double.valueOf(selected.yaw));
            rollSp.setValue(Double.valueOf(selected.roll));
            inflate.setValue(Double.valueOf(selected.inflate));
        } finally {
            updatingUI = false;
        }
    }

    private void applyUIToSelected() {
        if (selected == null)
            return;

        selected.name = partName.getText().trim().isEmpty() ? selected.name : partName.getText().trim();
        selected.mirror = mirror.isSelected();

        // read integers safely
        selected.u = ((Number) u.getValue()).intValue();
        selected.v = ((Number) v.getValue()).intValue();
        selected.x = ((Number) x.getValue()).intValue();
        selected.y = ((Number) y.getValue()).intValue();
        selected.z = ((Number) z.getValue()).intValue();
        selected.w = ((Number) w.getValue()).intValue();
        selected.h = ((Number) h.getValue()).intValue();
        selected.d = ((Number) d.getValue()).intValue();

        // read floats safely
        selected.posX = ((Number) posX.getValue()).floatValue();
        selected.posY = ((Number) posY.getValue()).floatValue();
        selected.posZ = ((Number) posZ.getValue()).floatValue();

        selected.pitch = ((Number) pitchSp.getValue()).floatValue();
        selected.yaw = ((Number) yawSp.getValue()).floatValue();
        selected.roll = ((Number) rollSp.getValue()).floatValue();
        selected.inflate = ((Number) inflate.getValue()).floatValue();

        partList.repaint();
    }

    private EditablePart makeNewPart() {
        EditablePart np = new EditablePart("part" + (int) (Math.random() * 1000));
        np.w = np.h = np.d = 4;
        return np;
    }

    private EditablePart copyPart(EditablePart p) {
        EditablePart c = new EditablePart(p.name + "_copy");
        c.u = p.u;
        c.v = p.v;
        c.x = p.x;
        c.y = p.y;
        c.z = p.z;
        c.w = p.w;
        c.h = p.h;
        c.d = p.d;
        c.posX = p.posX;
        c.posY = p.posY;
        c.posZ = p.posZ;
        c.pitch = p.pitch;
        c.yaw = p.yaw;
        c.roll = p.roll;
        c.inflate = p.inflate;
        c.mirror = p.mirror;
        for (EditablePart ch : p.children)
            c.children.add(copyPart(ch));
        return c;
    }

    private EditablePart parentOf(EditablePart target) {
        for (EditablePart r : model.parts) {
            EditablePart p = parentSearch(r, target);
            if (p != null)
                return p;
        }
        return null;
    }

    private EditablePart parentSearch(EditablePart cur, EditablePart target) {
        for (EditablePart c : cur.children) {
            if (c == target)
                return cur;
            EditablePart d = parentSearch(c, target);
            if (d != null)
                return d;
        }
        return null;
    }

    // ---------------- GL ----------------

    private void initGL() {
        GL11.glViewport(0, 0, glCanvas.getWidth(), glCanvas.getHeight());
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GLU.gluPerspective(60, glCanvas.getWidth() / (float) glCanvas.getHeight(), 0.05f, 200f);
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

    // True if a Swing text component/spinner editor currently has focus
    private boolean hasTypingFocus() {
        java.awt.Component c = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (c == null)
            return false;

        // Text field or area
        if (c instanceof javax.swing.text.JTextComponent)
            return true;

        // Spinner editor's JTextField
        if (c instanceof JSpinner)
            return true;
        if (c.getParent() != null && c.getParent().getParent() instanceof JSpinner)
            return true;

        return false;
    }
    // Call once after buildUI() shows the frame (or at end of buildUI()).

    private void input() {
        // Always allow ESC to exit
        if (Keyboard.isKeyDown(Keyboard.KEY_ESCAPE))
            System.exit(0);

        // --- Handle global LWJGL keys that must work in BOTH modes ---
        while (Keyboard.next()) {
            if (!Keyboard.getEventKeyState())
                continue;
            int k = Keyboard.getEventKey();

            if (k == Keyboard.KEY_TAB) {
                if (canvasEdit) {
                    enterSwingTypingMode();
                } else {
                    enterCanvasEditMode();
                }
                updateModeTitle();
                continue; // skip further handling of this event
            }

            if (!canvasEdit)
                continue; // ignore all other LWJGL keys while in Swing typing mode

            // --- Edge-triggered hotkeys while in canvas edit mode ---
            boolean ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
            boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);

            switch (k) {
            case Keyboard.KEY_Z:
                if (ctrl) {
                    undoAction();
                }
                break;
            case Keyboard.KEY_X:
            case Keyboard.KEY_Y:
                if (ctrl) {
                    redoAction();
                }
                break;
            case Keyboard.KEY_N:
                if (ctrl && shift)
                    newProject();
                break;
            case Keyboard.KEY_DELETE:
                if (selected != null) {
                    pushHistory();
                    deleteSelected();
                    refreshPartListSelect(selected);
                }
                break;

            // Duplicated hotkeys for when canvas has focus
            case Keyboard.KEY_S:
                if (ctrl) {
                    requestSave();
                }
                break;
            case Keyboard.KEY_L:
                if (ctrl) {
                    requestLoad();
                }
                break;
            case Keyboard.KEY_E:
                if (ctrl) {
                    requestExportJava();
                }
                break;
            case Keyboard.KEY_F6: {
                requestExportTemplate();
            }
                break;
            case Keyboard.KEY_J:
                if (ctrl) {
                    requestImportJava();
                }
                break;
            }
        }

        if (!canvasEdit)
            return; // stop here if not in canvas mode

        final boolean anyMouseDown = Mouse.isButtonDown(0) || Mouse.isButtonDown(1) || Mouse.isButtonDown(2);
        final boolean allow3DInput = canvasHover || anyMouseDown;

        if (!allow3DInput)
            return;

        // Camera orbit/zoom
        if (Mouse.isButtonDown(0)) {
            yaw += Mouse.getDX() * 0.4f;
            pitch -= Mouse.getDY() * 0.4f;
            pitch = clamp(pitch, -89, 89);
        }
        int wheel = Mouse.getDWheel();
        if (wheel != 0) {
            dist -= wheel * 0.01f;
            dist = clamp(dist, 2, 25);
        }

        if (selected == null)
            return;

        // Continuous edit keys (unchanged)
        boolean ctrlDown = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        boolean editingKeys = !ctrlDown && (Keyboard.isKeyDown(Keyboard.KEY_I) || Keyboard.isKeyDown(Keyboard.KEY_K)
                || Keyboard.isKeyDown(Keyboard.KEY_J) || Keyboard.isKeyDown(Keyboard.KEY_L)
                || Keyboard.isKeyDown(Keyboard.KEY_U) || Keyboard.isKeyDown(Keyboard.KEY_O)
                || Keyboard.isKeyDown(Keyboard.KEY_T) || Keyboard.isKeyDown(Keyboard.KEY_G)
                || Keyboard.isKeyDown(Keyboard.KEY_F) || Keyboard.isKeyDown(Keyboard.KEY_H)
                || Keyboard.isKeyDown(Keyboard.KEY_R) || Keyboard.isKeyDown(Keyboard.KEY_Y)
                || Keyboard.isKeyDown(Keyboard.KEY_LEFT) || Keyboard.isKeyDown(Keyboard.KEY_RIGHT)
                || Keyboard.isKeyDown(Keyboard.KEY_UP) || Keyboard.isKeyDown(Keyboard.KEY_DOWN)
                || Keyboard.isKeyDown(Keyboard.KEY_PRIOR) || Keyboard.isKeyDown(Keyboard.KEY_NEXT)
                || Keyboard.isKeyDown(Keyboard.KEY_NUMPAD8) || Keyboard.isKeyDown(Keyboard.KEY_NUMPAD2)
                || Keyboard.isKeyDown(Keyboard.KEY_NUMPAD6) || Keyboard.isKeyDown(Keyboard.KEY_NUMPAD4)
                || Keyboard.isKeyDown(Keyboard.KEY_NUMPAD9) || Keyboard.isKeyDown(Keyboard.KEY_NUMPAD7)
                || Keyboard.isKeyDown(Keyboard.KEY_ADD) || Keyboard.isKeyDown(Keyboard.KEY_SUBTRACT)
                || Keyboard.isKeyDown(Keyboard.KEY_M));

        if (editingKeys && !inKeyEdit) {
            pushHistory();
            inKeyEdit = true;
        }
        if (!editingKeys)
            inKeyEdit = false;

        // (your existing continuous transforms go here)
    }

    // ---- Mode switches ----
    // ---- Mode switches ----
    private void enterCanvasEditMode() {
        canvasEdit = true;
        typingInSwing = false;
        setListInteractive(true); // re-enable list navigation
        if (!glCanvas.isFocusable())
            glCanvas.setFocusable(true);
        if (!glCanvas.isFocusOwner())
            glCanvas.requestFocusInWindow();
        canvasHover = true;
        updateModeTitle();
    }

    private void enterSwingTypingMode() {
        canvasEdit = false;
        typingInSwing = true;
        setListInteractive(false); // prevent list from eating arrow keys
        if (glCanvas.isFocusable())
            glCanvas.setFocusable(false);
        focusSidePanel(); // won't override what you just clicked
        updateModeTitle();
    }

    // Run a task on EDT
    private static void onEDT(Runnable r) {
        if (SwingUtilities.isEventDispatchThread())
            r.run();
        else
            SwingUtilities.invokeLater(r);
    }

    // Save: choose file on EDT, then write on a worker thread
    private void requestSave() {
        onEDT(() -> {
            File out = showSaveDialog("Save Model (.mmdl)", model.name == null ? "model" : model.name, ".mmdl");
            if (out == null)
                return;
            new Thread(() -> safe(() -> {
                if (animText != null)
                    model.setRotationAnglesCode = animText.getText();
                ModelIO.save(out, model);
                System.out.println("[save] " + out.getAbsolutePath());
            }), "SaveWorker").start();
        });
    }

    private void requestLoad() {
        onEDT(() -> {
            File in = showOpenDialog("Load Model (.mmdl)", ".mmdl");
            if (in == null)
                return;
            new Thread(() -> safe(() -> {
                EditableModel loaded = ModelIO.load(in);
                SwingUtilities.invokeLater(() -> {
                    pushHistory();
                    model = loaded;
                    selected = model.parts.isEmpty() ? null : model.parts.get(0);
                    refreshPartListSelect(selected);
                    syncModelFieldsToUI();
                    if (animText != null)
                        animText.setText(model.setRotationAnglesCode == null ? "" : model.setRotationAnglesCode);
                    System.out.println("[load] " + in.getAbsolutePath());
                });
            }), "LoadWorker").start();
        });
    }

    private void requestExportJava() {
        onEDT(() -> {
            String base = (model.name == null || model.name.isEmpty()) ? "MyNewMobModel" : model.name;
            File dir = getDefaultDir();
            File out = uniqueIfExists(new File(dir, base + ".java"));
            new Thread(() -> safe(() -> {
                if (animText != null)
                    model.setRotationAnglesCode = animText.getText();
                JavaExporter.export("net.classicremastered.minecraft.model", out.getName().replace(".java", ""), model);
                System.out.println("[export] " + out.getAbsolutePath());
            }), "ExportJavaWorker").start();
        });
    }

    private void requestExportTemplate() {
        onEDT(() -> {
            new Thread(() -> safe(() -> {
                String appdata = System.getenv("APPDATA");
                if (appdata == null)
                    appdata = System.getProperty("user.home");
                File dir = new File(appdata, ".mcraft/client/modelexport");
                if (!dir.exists())
                    dir.mkdirs();
                String base = model.name == null || model.name.isEmpty() ? "model" : model.name;
                File out = new File(dir, base + "-template.png");
                // heavy work off EDT
                TextureTemplateExporter.exportTemplatePNG(out, model, 255, 48);
                System.out.println("[uv template] " + out.getAbsolutePath());
            }), "ExportTemplateWorker").start();
        });
    }

    private void requestImportJava() {
        onEDT(() -> {
            File in = showOpenDialog("Open Classic Java Model (.java)", ".java");
            if (in == null)
                return;
            new Thread(() -> safe(() -> {
                EditableModel imported = JavaModelImporter.load(in);
                SwingUtilities.invokeLater(() -> {
                    pushHistory();
                    model = imported;
                    selected = model.parts.isEmpty() ? null : model.parts.get(0);
                    refreshPartListSelect(selected);
                    syncModelFieldsToUI();
                    if (animText != null)
                        animText.setText(model.setRotationAnglesCode == null ? "" : model.setRotationAnglesCode);
                    System.out.println("[import .java] " + in.getAbsolutePath() + " → " + model.name);
                });
            }), "ImportJavaWorker").start();
        });
    }

    // Call once after buildUI() shows the frame
    private void installHoverTracking() {
        glCanvas.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                canvasHover = true;
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                canvasHover = false;
            }

            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                canvasHover = true;
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                /* leave canvasHover; exit will clear */ }
        });
        glCanvas.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                canvasHover = true;
            }

            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                canvasHover = true;
            }
        });
    }

// Call once after buildUI() shows the frame
    // Call once after buildUI() shows the frame (or at end of buildUI()).
    // Control when the GL canvas actually steals keyboard focus
    // Control focus for the GL canvas: any click inside → canvas gets focus
    // Any click inside the canvas → start editing in GL immediately
    private void installFocusHandlers() {
        glCanvas.setFocusable(true);
        glCanvas.setFocusTraversalKeysEnabled(false);

        glCanvas.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                enterCanvasEditMode();
            }
        });
    }

    private void installGlobalTabToggle() {
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(ev -> {
            if (ev.getID() == java.awt.event.KeyEvent.KEY_PRESSED
                    && ev.getKeyCode() == java.awt.event.KeyEvent.VK_TAB) {

                if (canvasEdit) {
                    enterSwingTypingMode();
                } else {
                    enterCanvasEditMode();
                }
                updateModeTitle();
                ev.consume();
                return true;
            }
            return false;
        });
    }

    // Update window title to reflect current mode
    private void updateModeTitle() {
        SwingUtilities.invokeLater(() -> {
            if (frame != null) {
                frame.setTitle("Classic Model Editor — Mode: " + (canvasEdit ? "Canvas" : "Swing (Typing)")
                        + "  [TAB to toggle]");
            }
        });
    }

    private void installGlobalHotkeys() {
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(ev -> {
            if (ev.getID() != java.awt.event.KeyEvent.KEY_PRESSED)
                return false;

            final boolean ctrl = ev.isControlDown() || ev.isMetaDown(); // meta for mac
            switch (ev.getKeyCode()) {
            case java.awt.event.KeyEvent.VK_S:
                if (ctrl) {
                    requestSave();
                    ev.consume();
                    return true;
                }
                break;
            case java.awt.event.KeyEvent.VK_L:
                if (ctrl) {
                    requestLoad();
                    ev.consume();
                    return true;
                }
                break;
            case java.awt.event.KeyEvent.VK_E:
                if (ctrl) {
                    requestExportJava();
                    ev.consume();
                    return true;
                }
                break;
            case java.awt.event.KeyEvent.VK_F6: {
                requestExportTemplate();
                ev.consume();
                return true;
            }
            case java.awt.event.KeyEvent.VK_J:
                if (ctrl) {
                    requestImportJava();
                    ev.consume();
                    return true;
                }
                break;
            case java.awt.event.KeyEvent.VK_Z:
                if (ctrl) {
                    undoAction();
                    ev.consume();
                    return true;
                }
                break;
            case java.awt.event.KeyEvent.VK_X:
            case java.awt.event.KeyEvent.VK_Y:
                if (ctrl) {
                    redoAction();
                    ev.consume();
                    return true;
                }
                break;
            }
            return false;
        });
    }

    // Suppress UI-driven mutations while restoring a snapshot

    private void newProject() {
        pushHistory();
        EditablePart head = new EditablePart("head");
        head.u = 0;
        head.v = 0;
        head.x = -4;
        head.y = -8;
        head.z = -4;
        head.w = 8;
        head.h = 8;
        head.d = 8;

        synchronized (modelLock) {
            this.model = new EditableModel();
            model.name = "new_model";
            model.atlasW = 64;
            model.atlasH = 64;
            model.parts.add(head);
            model.setRotationAnglesCode = null; // clear animations on new project
            selected = head;
        }

        yaw = 30;
        pitch = 15;
        dist = 7;
        clearTexture();

        refreshPartListSelect(head);
        syncModelFieldsToUI();
        if (animText != null)
            animText.setText("");
        if (uvPanel != null)
            uvPanel.repaint();
    }

    private void doSave() {
        File out = showSaveDialog("Save Model (.mmdl)", model.name == null ? "model" : model.name, ".mmdl");
        if (out != null)
            safe(() -> {
                if (animText != null)
                    model.setRotationAnglesCode = animText.getText();
                ModelIO.save(out, model);
                System.out.println("[save] " + out.getAbsolutePath());
            });
    }

    private void doExportJava() {
        String base = (model.name == null || model.name.isEmpty()) ? "MyNewMobModel" : model.name;
        File dir = getDefaultDir();
        File out = uniqueIfExists(new File(dir, base + ".java"));
        safe(() -> {
            if (animText != null)
                model.setRotationAnglesCode = animText.getText();
            JavaExporter.export("net.classicremastered.minecraft.model", out.getName().replace(".java", ""), model);
            System.out.println("[export] " + out.getAbsolutePath());
        });
    }

    private void doExportTemplate() {
        safe(() -> {
            String appdata = System.getenv("APPDATA");
            if (appdata == null)
                appdata = System.getProperty("user.home");
            File dir = new File(appdata, ".mcraft/client/modelexport");
            if (!dir.exists())
                dir.mkdirs();
            String base = model.name == null || model.name.isEmpty() ? "model" : model.name;
            File out = new File(dir, base + "-template.png");
            // faceAlpha=255 (opaque faces), checkerAlpha=48 (semi-transparent)
            TextureTemplateExporter.exportTemplatePNG(out, model, 255, 48);
            System.out.println("[uv template] " + out.getAbsolutePath());
        });
    }

    private String texturePath = null; // full path to texture file
    private JLabel textureLabel; // label in UI // --- Texture import / clear ---

    private void doImportTexture() {
        File in = showOpenDialog("Import Texture (.png/.jpg)", ".png");
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
            img.getGraphics().drawImage(raw, 0, 0, null);

            // CPU copy for viewer + UI label
            this.texturePath = in.getAbsolutePath();
            this.textureName = in.getName();
            this.textureImage = img;
            if (textureLabel != null)
                textureLabel.setText(textureName);
            if (uvPanel != null)
                uvPanel.setTexture(img);

            // Upload on GL thread
            final BufferedImage imgForGL = img;
            glTasks.add(() -> {
                int newTex = uploadTexture(imgForGL);
                if (glTextureId != 0)
                    GL11.glDeleteTextures(glTextureId);
                glTextureId = newTex;

                model.atlasW = imgForGL.getWidth();
                model.atlasH = imgForGL.getHeight();
                SwingUtilities.invokeLater(() -> {
                    atlasW.setValue(model.atlasW);
                    atlasH.setValue(model.atlasH);
                    uvPanel.repaint();
                });

                System.out.println("[texture] OK " + texturePath + " (" + model.atlasW + "x" + model.atlasH + ")");
            });

        } catch (Throwable t) {
            t.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Failed to load texture:\n" + t, "Texture Import Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void clearTexture() {
        if (glTextureId != 0) {
            GL11.glDeleteTextures(glTextureId);
            glTextureId = 0;
        }
        textureImage = null;
        textureName = "(none)";
        if (textureLabel != null)
            textureLabel.setText(textureName);
        if (uvPanel != null)
            uvPanel.setTexture(null);
        System.out.println("[texture] Cleared");
    }

    private int uploadTexture(java.awt.image.BufferedImage img) {
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
    // ------------------------------------------------------------
    // UV viewer panel: checkerboard + atlas + UV nets with selection
    // Mouse: wheel=zoom, right-drag=pan, left-click=select by UV
    // ------------------------------------------------------------

    private void deleteSelected() {
        synchronized (modelLock) {
            if (selected == null)
                return;
            if (model.parts.remove(selected)) {
                selected = model.parts.isEmpty() ? null : model.parts.get(0);
                return;
            }
            for (EditablePart root : model.parts) {
                if (deleteRecursive(root, selected)) {
                    selected = root;
                    return;
                }
            }
        }
    }

    private boolean deleteRecursive(EditablePart parent, EditablePart target) {
        if (parent.children.remove(target))
            return true;
        for (EditablePart c : parent.children)
            if (deleteRecursive(c, target))
                return true;
        return false;
    }

    // ------------------------------------------------------------
    // UV viewer panel: checkerboard + atlas + per-face UVs (BB-like)
    // ------------------------------------------------------------
    private final class UVViewerPanel extends JPanel {
        private final java.util.function.Supplier<BufferedImage> texSupplier;
        private final java.util.function.Supplier<EditableModel> modelSupplier;
        private final java.util.function.Supplier<EditablePart> selSupplier;

        private double zoom = 1.0;
        private double panX = 0, panY = 0;
        private Point lastDrag = null;

        // local Rect (only for this panel)
        private final class Rect {
            final int x, y, w, h;

            Rect(int x, int y, int w, int h) {
                this.x = x;
                this.y = y;
                this.w = w;
                this.h = h;
            }
        }

        UVViewerPanel(java.util.function.Supplier<BufferedImage> texSupplier,
                java.util.function.Supplier<EditableModel> modelSupplier,
                java.util.function.Supplier<EditablePart> selSupplier) {
            this.texSupplier = texSupplier;
            this.modelSupplier = modelSupplier;
            this.selSupplier = selSupplier;

            setBackground(new Color(0x1c1f26));

            addMouseWheelListener(e -> {
                double old = zoom;
                zoom *= (e.getPreciseWheelRotation() < 0 ? 1.1 : 0.9);
                zoom = Math.max(0.1, Math.min(16, zoom));
                Point p = e.getPoint();
                panX = p.x - (p.x - panX) * (zoom / old);
                panY = p.y - (p.y - panY) * (zoom / old);
                repaint();
            });
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e))
                        lastDrag = e.getPoint();
                    if (SwingUtilities.isLeftMouseButton(e))
                        pickPartAt(e.getPoint());
                }

                @Override
                public void mouseReleased(java.awt.event.MouseEvent e) {
                    lastDrag = null;
                }
            });
            addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override
                public void mouseDragged(java.awt.event.MouseEvent e) {
                    if (lastDrag != null) {
                        panX += e.getX() - lastDrag.x;
                        panY += e.getY() - lastDrag.y;
                        lastDrag = e.getPoint();
                        repaint();
                    }
                }
            });
        }

        void setTexture(BufferedImage img) {
            repaint();
        }

        void setSelected(EditablePart p) {
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            BufferedImage tex = texSupplier.get();
            EditableModel m = modelSupplier.get();
            if (m == null)
                return;

            int aw = (tex != null ? tex.getWidth() : Math.max(1, m.atlasW));
            int ah = (tex != null ? tex.getHeight() : Math.max(1, m.atlasH));

            drawChecker(g);

            g.translate(panX, panY);
            g.scale(zoom, zoom);

            if (tex != null)
                g.drawImage(tex, 0, 0, null);
            else {
                g.setColor(new Color(0x2a3240));
                g.fillRect(0, 0, aw, ah);
                g.setColor(new Color(0x44506a));
                g.drawRect(0, 0, aw - 1, ah - 1);
            }

            // all parts UVs
            for (EditablePart p : m.parts)
                drawPartFaces(g, p, false);

            // highlight selected
            EditablePart sel = selSupplier.get();
            if (sel != null) {
                g.setColor(new Color(0x5aa0ff));
                g.setStroke(new BasicStroke(2.0f / (float) zoom));
                for (Rect r : faceRects(sel))
                    g.drawRect(r.x, r.y, r.w, r.h);
                Rect nameAt = faceRects(sel)[4]; // -Z
                g.drawString(sel.name, nameAt.x + 4, nameAt.y + 12);
            }
        }

        private void drawChecker(Graphics2D g) {
            final int cell = 16;
            for (int y = 0; y < getHeight(); y += cell)
                for (int x = 0; x < getWidth(); x += cell) {
                    boolean a = ((x ^ y) & cell) == 0;
                    g.setColor(a ? new Color(0x2b2f36) : new Color(0x242932));
                    g.fillRect(x, y, cell, cell);
                }
        }

        // Classic cross layout, per-face rects (Blockbench-like)
        private Rect[] faceRects(EditablePart p) {
            int u = p.u, v = p.v, w = p.w, h = p.h, d = p.d;
            Rect negY = new Rect(u + d, v, w, d); // bottom
            Rect posY = new Rect(u + d + w, v, w, d); // top
            Rect negX = new Rect(u, v + d, d, h);
            Rect posX = new Rect(u + d + w, v + d, d, h);
            Rect negZ = new Rect(u + d, v + d, w, h); // front
            Rect posZ = new Rect(u + d + w + d, v + d, w, h); // back
            return new Rect[] { negY, posY, negX, posX, negZ, posZ };
        }

        private void drawPartFaces(Graphics2D g, EditablePart p, boolean highlight) {
            Color fill = pastelFromName(p.name, highlight ? 140 : 90);
            Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

            for (Rect r : faceRects(p)) {
                if (r.w <= 0 || r.h <= 0)
                    continue;
                g.setColor(fill);
                g.fillRect(r.x, r.y, r.w, r.h);
                if (highlight) {
                    g.setColor(new Color(0x5aa0ff));
                    g.setStroke(new BasicStroke(2.0f / (float) zoom));
                    g.drawRect(r.x, r.y, r.w, r.h);
                }
            }
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);

            for (EditablePart c : p.children)
                drawPartFaces(g, c, false);
        }

        private Color pastelFromName(String name, int alpha) {
            int h = (name == null ? 0 : name.hashCode());
            float hue = ((h >>> 1) & 0xFFFF) / 65535f;
            float sat = 0.30f, val = 1.00f;
            int rgb = java.awt.Color.HSBtoRGB(hue, sat, val);
            return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, Math.max(0, Math.min(255, alpha)));
        }

        private void pickPartAt(Point screenPt) {
            EditableModel m = modelSupplier.get();
            if (m == null)
                return;
            double tx = (screenPt.x - panX) / zoom;
            double ty = (screenPt.y - panY) / zoom;

            EditablePart hit = pickInList(m.parts, tx, ty);
            if (hit != null) {
                selected = hit;
                refreshPartListSelect(hit);
                repaint();
            }
        }

        private EditablePart pickInList(java.util.List<EditablePart> list, double x, double y) {
            for (EditablePart p : list) {
                EditablePart child = pickInList(p.children, x, y);
                if (child != null)
                    return child;
                for (Rect r : faceRects(p))
                    if (x >= r.x && y >= r.y && x < r.x + r.w && y < r.y + r.h)
                        return p;
            }
            return null;
        }
    }

    private void render() {
        GL11.glViewport(0, 0, glCanvas.getWidth(), glCanvas.getHeight());
        GL11.glClearColor(0.07f, 0.08f, 0.10f, 1f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GLU.gluPerspective(60, glCanvas.getWidth() / (float) glCanvas.getHeight(), 0.05f, 200f);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();

        float ex = (float) (Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
        float ey = (float) (Math.sin(Math.toRadians(pitch)));
        float ez = (float) (Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
        GLU.gluLookAt(ex * dist, ey * dist, ez * dist, 0, 1.2f, 0, 0, 1, 0);

        Runnable job;
        while ((job = glTasks.poll()) != null) {
            try {
                job.run();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        drawGrid(14, 1.0f);

        final float s = 0.0625f;

        // ---- snapshot under lock ----
        java.util.List<EditablePart> partsSnapshot;
        EditablePart selSnapshot;
        int atlasW, atlasH;
        synchronized (modelLock) {
            partsSnapshot = new java.util.ArrayList<>(model.parts);
            selSnapshot = selected;
            atlasW = model.atlasW;
            atlasH = model.atlasH;
        }
        TexturedQuad.ATLAS_W = atlasW;
        TexturedQuad.ATLAS_H = atlasH;

        // ---- draw ----
        GL11.glPushMatrix();
        GL11.glTranslatef(0, 24 * s, 0);
        GL11.glScalef(1, -1, 1);
        GL11.glFrontFace(GL11.GL_CW);

        if (glTextureId != 0) {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, glTextureId);
        } else {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
        }

        for (EditablePart p : partsSnapshot) {
            renderPart(p, s, selSnapshot);
        }

        GL11.glFrontFace(GL11.GL_CCW);
        GL11.glPopMatrix();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private final java.util.concurrent.ConcurrentLinkedQueue<Runnable> glTasks = new java.util.concurrent.ConcurrentLinkedQueue<>();

    private void renderPart(EditablePart ep, float scale, EditablePart sel) {
        GL11.glPushMatrix();
        GL11.glTranslatef(ep.posX * scale, ep.posY * scale, ep.posZ * scale);
        if (ep.roll != 0)
            GL11.glRotatef((float) Math.toDegrees(ep.roll), 0, 0, 1);
        if (ep.yaw != 0)
            GL11.glRotatef((float) Math.toDegrees(ep.yaw), 0, 1, 0);
        if (ep.pitch != 0)
            GL11.glRotatef((float) Math.toDegrees(ep.pitch), 1, 0, 0);

        ModelPart part = new ModelPart(ep.u, ep.v);
        part.mirror = ep.mirror;
        part.setBounds(ep.x, ep.y, ep.z, ep.w, ep.h, ep.d, ep.inflate);

        if (ep == sel)
            GL11.glColor3f(1, 1, 0.75f);
        else
            GL11.glColor3f(1, 1, 1);
        part.render(scale);

        for (EditablePart c : ep.children)
            renderPart(c, scale, sel);
        GL11.glPopMatrix();
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private void drawGrid(int half, float step) {
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

    private static void safe(IORun r) {
        try {
            r.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private interface IORun {
        void run() throws Exception;
    }
}
