package net.classicremastered.toolkit.modeleditor;

import net.classicremastered.minecraft.model.*;

import java.awt.*;

import javax.swing.*;

/** Model Editor Ui responsibilities. */
final class ModelEditorUi {
    private final ModelEditorApp editor;

    ModelEditorUi(ModelEditorApp editor) {
        this.editor = editor;
    }

    void buildUI() {
        Color bgDark = new Color(0x18181B); // dark gray-black for frame/panel backgrounds
        Color cardDark = new Color(0x202025); // slightly lighter slate card background
        Color textLight = new Color(0xE4E4E7); // clean zinc light white text
        Color borderDark = new Color(0x2E2E35); // outline borders
        Color activeSelection = new Color(0x3B82F6); // modern active blue

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        UIManager.put("Panel.background", cardDark);
        UIManager.put("Label.foreground", textLight);
        UIManager.put("CheckBox.foreground", textLight);
        UIManager.put("CheckBox.background", cardDark);
        UIManager.put("List.background", new Color(0x131317));
        UIManager.put("List.foreground", textLight);
        UIManager.put("List.selectionBackground", activeSelection);
        UIManager.put("List.selectionForeground", Color.WHITE);
        UIManager.put("TextField.background", new Color(0x131317));
        UIManager.put("TextField.foreground", textLight);
        UIManager.put("TextField.caretColor", textLight);
        UIManager.put("TextField.border", BorderFactory.createLineBorder(borderDark));
        UIManager.put("TextArea.background", new Color(0x131317));
        UIManager.put("TextArea.foreground", textLight);
        UIManager.put("TextArea.caretColor", textLight);
        UIManager.put("TextArea.border", BorderFactory.createLineBorder(borderDark));
        UIManager.put("Spinner.background", new Color(0x131317));
        UIManager.put("Spinner.foreground", textLight);
        UIManager.put("Spinner.border", BorderFactory.createLineBorder(borderDark));
        UIManager.put("Button.background", new Color(0x31313E));
        UIManager.put("Button.foreground", textLight);
        UIManager.put(
                "Button.border",
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(borderDark),
                        BorderFactory.createEmptyBorder(4, 10, 4, 10)));

        editor.frame = new JFrame("Classic Model Editor");
        editor.frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        editor.frame.addWindowListener(
                new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosing(java.awt.event.WindowEvent event) {
                        if (editor.confirmDiscard()) editor.running = false;
                    }
                });
        editor.frame.setLayout(new BorderLayout());
        editor.frame.getContentPane().setBackground(bgDark);

        // ----- HEADER SELECTOR BAR -----
        JPanel topBar = new JPanel(new BorderLayout(12, 0));
        topBar.setBackground(new Color(0x18181B));
        topBar.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

        JLabel titleLabel = new JLabel("Classic Model Editor");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLabel.setForeground(Color.WHITE);
        topBar.add(titleLabel, BorderLayout.WEST);

        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        modePanel.setBackground(new Color(0x18181B));
        editor.btn3DMode = editor.createButton("Viewport input");
        editor.btnSettingsMode = editor.createButton("Property input");
        editor.btn3DMode.setPreferredSize(new Dimension(150, 28));
        editor.btnSettingsMode.setPreferredSize(new Dimension(170, 28));

        editor.btn3DMode.addActionListener(e -> editor.switchMode(true));
        editor.btnSettingsMode.addActionListener(e -> editor.switchMode(false));

        modePanel.add(editor.btn3DMode);
        modePanel.add(editor.btnSettingsMode);
        JComboBox<String> presets = new JComboBox<>(ModelPresets.NAMES);
        JButton loadPreset = editor.createButton("Load preset");
        loadPreset.addActionListener(
                e ->
                        ModelEditorApp.safe(
                                () -> {
                                    if (!editor.confirmDiscard()) return;
                                    editor.pushHistory();
                                    editor.model =
                                            ModelPresets.create((String) presets.getSelectedItem());
                                    editor.document.reset();
                                    editor.animText.setText("");
                                    editor.selected =
                                            editor.model.parts.isEmpty()
                                                    ? null
                                                    : editor.model.parts.get(0);
                                    editor.refreshPartListSelect(editor.selected);
                                    editor.syncModelFieldsToUI();
                                    String resource =
                                            "/mob/"
                                                    + presets.getSelectedItem()
                                                            .toString()
                                                            .toLowerCase(java.util.Locale.ROOT)
                                                    + ".png";
                                    try (java.io.InputStream input =
                                            ModelEditorUi.class.getResourceAsStream(resource)) {
                                        editor.clearTexture();
                                        if (input != null) {
                                            java.awt.image.BufferedImage image =
                                                    javax.imageio.ImageIO.read(input);
                                            editor.textureImage = image;
                                            editor.model.atlasW = image.getWidth();
                                            editor.model.atlasH = image.getHeight();
                                            editor.textureName = resource;
                                            editor.textureLabel.setText(resource);
                                            editor.uvPanel.setTexture(image);
                                            editor.glTasks.add(
                                                    () ->
                                                            editor.glTextureId =
                                                                    editor.uploadTexture(image));
                                        }
                                    }
                                    editor.yaw = 30;
                                    editor.pitch = 15;
                                    editor.dist = 7;
                                    editor.targetX = 0;
                                    editor.targetY = 1.2f;
                                    editor.targetZ = 0;
                                    editor.syncModelFieldsToUI();
                                }));
        JButton frameModel = editor.createButton("Frame model");
        frameModel.addActionListener(
                e -> {
                    editor.yaw = 30;
                    editor.pitch = 15;
                    editor.dist = 7;
                    editor.targetX = 0;
                    editor.targetY = 1.2f;
                    editor.targetZ = 0;
                });
        modePanel.add(presets);
        modePanel.add(loadPreset);
        modePanel.add(frameModel);
        topBar.add(modePanel, BorderLayout.CENTER);
        editor.frame.add(topBar, BorderLayout.NORTH);

        // ----- MAIN CARD CONTAINER -----
        editor.mainCardLayout = new CardLayout();
        editor.mainCardContainer = new JPanel(editor.mainCardLayout);
        editor.mainCardContainer.setBackground(bgDark);

        ModelEditorPanels.build(editor, bgDark, cardDark, textLight, borderDark, activeSelection);

        editor.frame.add(editor.mainCardContainer, BorderLayout.CENTER);

        // Synchronize list selections
        editor.settingsPartList.addListSelectionListener(
                e -> {
                    if (!e.getValueIsAdjusting()) {
                        EditablePart sel = editor.settingsPartList.getSelectedValue();
                        if (sel != null && sel != editor.selected) {
                            editor.selected = sel;
                            editor.partList.setSelectedValue(sel, true);
                            editor.loadSelectedToUI();
                        }
                    }
                });
        editor.partList.addListSelectionListener(
                e -> {
                    if (!e.getValueIsAdjusting()) {
                        EditablePart sel = editor.partList.getSelectedValue();
                        if (sel != null) {
                            editor.selected = sel;
                            editor.settingsPartList.setSelectedValue(sel, true);
                            editor.loadSelectedToUI();
                            editor.uvPanel.setSelected(sel);
                        }
                    }
                });

        // Set initial mode
        editor.switchMode(true);

        // Commit spinner text on focus loss
        for (JSpinner s :
                new JSpinner[] {
                    editor.u,
                    editor.v,
                    editor.x,
                    editor.y,
                    editor.z,
                    editor.w,
                    editor.h,
                    editor.d,
                    editor.posX,
                    editor.posY,
                    editor.posZ,
                    editor.pitchSp,
                    editor.yawSp,
                    editor.rollSp,
                    editor.inflate,
                    editor.atlasW,
                    editor.atlasH
                }) {
            ((JSpinner.DefaultEditor) s.getEditor())
                    .getTextField()
                    .setFocusLostBehavior(JFormattedTextField.COMMIT_OR_REVERT);
        }

        // Live binding
        editor.bindIntSpinner(
                editor.u,
                v -> {
                    if (editor.selected != null) editor.selected.u = v;
                    editor.uvPanel.repaint();
                });
        editor.bindIntSpinner(
                editor.v,
                v -> {
                    if (editor.selected != null) editor.selected.v = v;
                    editor.uvPanel.repaint();
                });
        editor.bindIntSpinner(
                editor.x,
                v -> {
                    if (editor.selected != null) editor.selected.x = v;
                });
        editor.bindIntSpinner(
                editor.y,
                v -> {
                    if (editor.selected != null) editor.selected.y = v;
                });
        editor.bindIntSpinner(
                editor.z,
                v -> {
                    if (editor.selected != null) editor.selected.z = v;
                });
        editor.bindIntSpinner(
                editor.w,
                v -> {
                    if (editor.selected != null) editor.selected.w = v;
                    editor.uvPanel.repaint();
                });
        editor.bindIntSpinner(
                editor.h,
                v -> {
                    if (editor.selected != null) editor.selected.h = v;
                    editor.uvPanel.repaint();
                });
        editor.bindIntSpinner(
                editor.d,
                v -> {
                    if (editor.selected != null) editor.selected.d = v;
                    editor.uvPanel.repaint();
                });

        editor.bindFloatSpinner(
                editor.posX,
                d -> {
                    if (editor.selected != null) editor.selected.posX = (float) d;
                });
        editor.bindFloatSpinner(
                editor.posY,
                d -> {
                    if (editor.selected != null) editor.selected.posY = (float) d;
                });
        editor.bindFloatSpinner(
                editor.posZ,
                d -> {
                    if (editor.selected != null) editor.selected.posZ = (float) d;
                });
        editor.bindFloatSpinner(
                editor.pitchSp,
                d -> {
                    if (editor.selected != null) editor.selected.pitch = (float) d;
                });
        editor.bindFloatSpinner(
                editor.yawSp,
                d -> {
                    if (editor.selected != null) editor.selected.yaw = (float) d;
                });
        editor.bindFloatSpinner(
                editor.rollSp,
                d -> {
                    if (editor.selected != null) editor.selected.roll = (float) d;
                });
        editor.bindFloatSpinner(
                editor.inflate,
                d -> {
                    if (editor.selected != null) editor.selected.inflate = (float) d;
                });

        editor.mirror.addActionListener(
                e -> {
                    if (editor.restoring || editor.selected == null || editor.updatingUI) return;
                    editor.pushHistory();
                    editor.selected.mirror = editor.mirror.isSelected();
                    editor.uvPanel.repaint();
                });

        editor.partName
                .getDocument()
                .addDocumentListener(
                        new javax.swing.event.DocumentListener() {
                            private void apply() {
                                if (editor.restoring
                                        || editor.selected == null
                                        || editor.updatingUI) return;
                                editor.pushHistoryThrottled();
                                editor.selected.name = editor.partName.getText().trim();
                                editor.partList.repaint();
                                editor.settingsPartList.repaint();
                                editor.uvPanel.repaint();
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

        editor.frame.pack();
        editor.frame.setLocationRelativeTo(null);
    }

    JPanel titledBox(String title) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(new Color(0x202025));
        p.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createTitledBorder(
                                BorderFactory.createLineBorder(new Color(0x2E2E35), 1),
                                title,
                                javax.swing.border.TitledBorder.LEFT,
                                javax.swing.border.TitledBorder.TOP,
                                new Font("Segoe UI", Font.BOLD, 12),
                                new Color(0x94A3B8)),
                        BorderFactory.createEmptyBorder(6, 6, 6, 6)));
        return p;
    }

    JButton createButton(String text) {
        JButton btn =
                new JButton(text) {
                    @Override
                    protected void paintComponent(Graphics g) {
                        g.setColor(getBackground());
                        g.fillRect(0, 0, getWidth(), getHeight());
                        super.paintComponent(g);
                    }
                };
        btn.setBackground(new Color(0x31313E));
        btn.setForeground(new Color(0xE4E4E7));
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(0x2E2E35), 1),
                        BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        btn.addMouseListener(
                new java.awt.event.MouseAdapter() {
                    @Override
                    public void mouseEntered(java.awt.event.MouseEvent e) {
                        btn.setBackground(new Color(0x454555));
                    }

                    @Override
                    public void mouseExited(java.awt.event.MouseEvent e) {
                        btn.setBackground(new Color(0x31313E));
                    }

                    @Override
                    public void mousePressed(java.awt.event.MouseEvent e) {
                        btn.setBackground(new Color(0x252530));
                    }

                    @Override
                    public void mouseReleased(java.awt.event.MouseEvent e) {
                        if (btn.getBounds().contains(e.getPoint())) {
                            btn.setBackground(new Color(0x454555));
                        } else {
                            btn.setBackground(new Color(0x31313E));
                        }
                    }
                });
        return btn;
    }

    JPanel row(String label, JComponent comp) {
        JPanel r = new JPanel(new BorderLayout(6, 0));
        r.add(new JLabel(label), BorderLayout.WEST);
        r.add(comp, BorderLayout.CENTER);
        r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        return r;
    }

    JSpinner spinnerInt(int val, int min, int max, int step) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(val, min, max, step));
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        // force integer display
        JSpinner.NumberEditor ed = new JSpinner.NumberEditor(s, "0");
        s.setEditor(ed);
        return s;
    }

    JSpinner spinnerFloat(double val, double min, double max, double step) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(val, min, max, step));
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        JSpinner.NumberEditor ed = new JSpinner.NumberEditor(s, "0.###");
        s.setEditor(ed);
        return s;
    }

    void bindIntSpinner(JSpinner s, java.util.function.IntConsumer apply) {
        s.addChangeListener(
                e -> {
                    if (editor.restoring || editor.updatingUI || editor.selected == null) return;
                    editor.pushHistoryThrottled();
                    apply.accept(((Number) s.getValue()).intValue());
                    editor.partList.repaint();
                    if (editor.uvPanel != null) editor.uvPanel.repaint();
                });
    }

    void bindFloatSpinner(JSpinner s, java.util.function.DoubleConsumer apply) {
        s.addChangeListener(
                e -> {
                    if (editor.restoring || editor.updatingUI || editor.selected == null) return;
                    editor.pushHistoryThrottled();
                    apply.accept(((Number) s.getValue()).doubleValue());
                    editor.partList.repaint();
                    if (editor.uvPanel != null) editor.uvPanel.repaint();
                });
    }

    void syncModelFieldsToUI() {
        if (editor.modelName == null) return;
        SwingUtilities.invokeLater(
                () -> {
                    editor.updatingUI = true;
                    try {
                        editor.modelName.setText(
                                editor.model.name == null ? "" : editor.model.name);
                        int aw = (editor.model.atlasW > 0 ? editor.model.atlasW : 64);
                        int ah = (editor.model.atlasH > 0 ? editor.model.atlasH : 32);
                        editor.atlasW.setValue(aw);
                        editor.atlasH.setValue(ah);
                    } finally {
                        editor.updatingUI = false;
                    }
                });
    }

    void upd() {
        if (editor.selected == null || editor.updatingUI) return;
        editor.pushHistoryThrottled();
        editor.selected.name = editor.partName.getText().trim();
        editor.partList.repaint();
        editor.uvPanel.repaint();
    }

    void refreshPartListSelect(EditablePart toSelect) {
        if (editor.partList == null) {
            editor.selected = toSelect;
            return;
        }
        editor.partListModel.clear();
        java.util.List<EditablePart> partsSnapshot;
        EditablePart selSnapshot;
        synchronized (editor.modelLock) {
            partsSnapshot = new java.util.ArrayList<>(editor.model.parts);
            selSnapshot = toSelect;
        }

        for (EditablePart p : partsSnapshot) editor.addPartTreeToList(p);

        if (selSnapshot != null) {
            editor.partList.setSelectedValue(selSnapshot, true);
            if (editor.settingsPartList != null) {
                editor.settingsPartList.setSelectedValue(selSnapshot, true);
            }
        }
        editor.loadSelectedToUI();
        if (editor.uvPanel != null) editor.uvPanel.setSelected(selSnapshot);
    }

    void addPartTreeToList(EditablePart p) {
        editor.partListModel.addElement(p);
        for (EditablePart c : p.children) editor.addPartTreeToList(c);
    }

    void loadSelectedToUI() {
        boolean has = editor.selected != null;
        for (JComponent c :
                new JComponent[] {
                    editor.partName,
                    editor.mirror,
                    editor.u,
                    editor.v,
                    editor.x,
                    editor.y,
                    editor.z,
                    editor.w,
                    editor.h,
                    editor.d,
                    editor.posX,
                    editor.posY,
                    editor.posZ,
                    editor.pitchSp,
                    editor.yawSp,
                    editor.rollSp,
                    editor.inflate
                }) c.setEnabled(has);

        if (!has) {
            editor.partName.setText("");
            return;
        }

        editor.updatingUI = true;
        try {
            editor.partName.setText(editor.selected.name);
            editor.mirror.setSelected(editor.selected.mirror);

            // ints — cast in case EditablePart stores floats
            editor.u.setValue(Integer.valueOf(editor.selected.u));
            editor.v.setValue(Integer.valueOf(editor.selected.v));
            editor.x.setValue(Integer.valueOf((int) editor.selected.x));
            editor.y.setValue(Integer.valueOf((int) editor.selected.y));
            editor.z.setValue(Integer.valueOf((int) editor.selected.z));
            editor.w.setValue(Integer.valueOf((int) editor.selected.w));
            editor.h.setValue(Integer.valueOf((int) editor.selected.h));
            editor.d.setValue(Integer.valueOf((int) editor.selected.d));

            // floats
            editor.posX.setValue(Double.valueOf(editor.selected.posX));
            editor.posY.setValue(Double.valueOf(editor.selected.posY));
            editor.posZ.setValue(Double.valueOf(editor.selected.posZ));
            editor.pitchSp.setValue(Double.valueOf(editor.selected.pitch));
            editor.yawSp.setValue(Double.valueOf(editor.selected.yaw));
            editor.rollSp.setValue(Double.valueOf(editor.selected.roll));
            editor.inflate.setValue(Double.valueOf(editor.selected.inflate));
        } finally {
            editor.updatingUI = false;
        }
    }

    void applyUIToSelected() {
        if (editor.selected == null) return;

        editor.selected.name =
                editor.partName.getText().trim().isEmpty()
                        ? editor.selected.name
                        : editor.partName.getText().trim();
        editor.selected.mirror = editor.mirror.isSelected();

        // read integers safely
        editor.selected.u = ((Number) editor.u.getValue()).intValue();
        editor.selected.v = ((Number) editor.v.getValue()).intValue();
        editor.selected.x = ((Number) editor.x.getValue()).intValue();
        editor.selected.y = ((Number) editor.y.getValue()).intValue();
        editor.selected.z = ((Number) editor.z.getValue()).intValue();
        editor.selected.w = ((Number) editor.w.getValue()).intValue();
        editor.selected.h = ((Number) editor.h.getValue()).intValue();
        editor.selected.d = ((Number) editor.d.getValue()).intValue();

        // read floats safely
        editor.selected.posX = ((Number) editor.posX.getValue()).floatValue();
        editor.selected.posY = ((Number) editor.posY.getValue()).floatValue();
        editor.selected.posZ = ((Number) editor.posZ.getValue()).floatValue();

        editor.selected.pitch = ((Number) editor.pitchSp.getValue()).floatValue();
        editor.selected.yaw = ((Number) editor.yawSp.getValue()).floatValue();
        editor.selected.roll = ((Number) editor.rollSp.getValue()).floatValue();
        editor.selected.inflate = ((Number) editor.inflate.getValue()).floatValue();

        editor.partList.repaint();
    }
}
