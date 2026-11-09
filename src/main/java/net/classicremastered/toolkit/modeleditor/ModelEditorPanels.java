package net.classicremastered.toolkit.modeleditor;

import net.classicremastered.minecraft.model.*;

import java.awt.*;

import javax.swing.*;

/** Builds the viewport and properties cards without owning application state. */
final class ModelEditorPanels {
    private ModelEditorPanels() {}

    static void build(
            ModelEditorApp editor,
            Color bgDark,
            Color cardDark,
            Color textLight,
            Color borderDark,
            Color activeSelection) {
        // ----- CARD 1: VIEW_3D -----
        JPanel view3D = new JPanel(new BorderLayout());
        view3D.setBackground(bgDark);

        editor.glCanvas = new Canvas();
        editor.glCanvas.setPreferredSize(new Dimension(820, 720));

        // UV viewer shows current atlas and UV rects
        editor.uvPanel =
                new UVViewerPanel(
                        editor,
                        () -> editor.textureImage,
                        () -> editor.model,
                        () -> editor.selected);
        editor.uvPanel.setPreferredSize(new Dimension(820, 260));

        JPanel uvContainer = new JPanel(new BorderLayout());
        JPanel uvToolbar = new JPanel(new BorderLayout());
        uvToolbar.add(
                new JLabel("  UV: left click selects, right drag pans, wheel zooms"),
                BorderLayout.CENTER);
        JButton fitUv = editor.createButton("Fit UV");
        fitUv.addActionListener(e -> editor.uvPanel.fitToView());
        uvToolbar.add(fitUv, BorderLayout.EAST);
        uvContainer.add(uvToolbar, BorderLayout.NORTH);
        uvContainer.add(editor.uvPanel, BorderLayout.CENTER);
        JSplitPane centerSplit =
                new JSplitPane(JSplitPane.VERTICAL_SPLIT, editor.glCanvas, uvContainer);
        centerSplit.setResizeWeight(0.72);
        centerSplit.setDividerLocation(450);
        centerSplit.setDividerSize(6);
        centerSplit.setBackground(bgDark);
        centerSplit.setBorder(BorderFactory.createEmptyBorder());
        view3D.add(centerSplit, BorderLayout.CENTER);

        // 3D Mode Right Sidebar (Parts tree + Preview Controls)
        JPanel compactSide = new JPanel();
        compactSide.setBackground(bgDark);
        compactSide.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        compactSide.setLayout(new BoxLayout(compactSide, BoxLayout.Y_AXIS));
        compactSide.setPreferredSize(new Dimension(240, 720));

        JPanel cpPartsBox = editor.titledBox("Parts");
        cpPartsBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 420));
        editor.partList = new JList<>(editor.partListModel);
        editor.partList.setVisibleRowCount(12);
        editor.partList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        editor.partList.setCellRenderer(
                (lst, value, idx, isSel, hasFocus) -> {
                    JLabel lbl = new JLabel(value == null ? "(null)" : value.name);
                    lbl.setOpaque(true);
                    lbl.setBackground(isSel ? new Color(0x3B, 0x82, 0xF6) : new Color(0x131317));
                    lbl.setForeground(isSel ? Color.WHITE : new Color(0xE4, 0xE4, 0xE7));
                    lbl.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
                    return lbl;
                });
        JScrollPane cpSp = new JScrollPane(editor.partList);
        cpSp.setBorder(BorderFactory.createLineBorder(new Color(0x2E2E35)));
        cpPartsBox.add(cpSp);

        JButton cpBtnAddRoot = editor.createButton("Add Root");
        JButton cpBtnAddChild = editor.createButton("Add Child");
        JButton cpBtnDuplicate = editor.createButton("Duplicate");
        JButton cpBtnDelete = editor.createButton("Delete");

        cpBtnAddRoot.addActionListener(
                e -> {
                    editor.pushHistory();
                    EditablePart np = editor.makeNewPart();
                    editor.model.parts.add(np);
                    editor.selected = np;
                    editor.refreshPartListSelect(np);
                });
        cpBtnAddChild.addActionListener(
                e -> {
                    editor.pushHistory();
                    EditablePart np = editor.makeNewPart();
                    if (editor.selected != null) editor.selected.children.add(np);
                    else editor.model.parts.add(np);
                    editor.selected = np;
                    editor.refreshPartListSelect(np);
                });
        cpBtnDelete.addActionListener(
                e -> {
                    if (editor.selected == null) return;
                    editor.pushHistory();
                    editor.deleteSelected();
                    editor.refreshPartListSelect(editor.selected);
                });
        cpBtnDuplicate.addActionListener(
                e -> {
                    if (editor.selected == null) return;
                    editor.pushHistory();
                    EditablePart cp = editor.copyPart(editor.selected);
                    EditablePart parent = editor.parentOf(editor.selected);
                    if (parent != null) parent.children.add(cp);
                    else editor.model.parts.add(cp);
                    editor.selected = cp;
                    editor.refreshPartListSelect(cp);
                });

        JPanel cpPartBtns = new JPanel(new GridLayout(2, 2, 6, 6));
        cpPartBtns.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        cpPartBtns.setPreferredSize(new Dimension(220, 70));
        cpPartBtns.add(cpBtnAddRoot);
        cpPartBtns.add(cpBtnAddChild);
        cpPartBtns.add(cpBtnDuplicate);
        cpPartBtns.add(cpBtnDelete);
        cpPartsBox.add(Box.createVerticalStrut(6));
        cpPartsBox.add(cpPartBtns);
        compactSide.add(cpPartsBox);

        compactSide.add(Box.createVerticalStrut(8));
        JPanel cpPreviewBox = editor.titledBox("Preview Controls");
        cpPreviewBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        editor.chkPreviewAnim = new JCheckBox("Preview Walk/Idle");
        editor.chkAggressive = new JCheckBox("Preview Aggressive");
        cpPreviewBox.add(editor.chkPreviewAnim);
        cpPreviewBox.add(Box.createVerticalStrut(4));
        cpPreviewBox.add(editor.chkAggressive);
        compactSide.add(cpPreviewBox);

        view3D.add(compactSide, BorderLayout.EAST);

        // ----- CARD 2: VIEW_SETTINGS -----
        JPanel viewSettings = new JPanel(new GridLayout(1, 2, 12, 0));
        viewSettings.setBackground(bgDark);
        viewSettings.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Column 1 (General Settings, Hierarchy, Files)
        JPanel col1 = new JPanel();
        col1.setBackground(bgDark);
        col1.setLayout(new BoxLayout(col1, BoxLayout.Y_AXIS));

        JPanel settingsModelBox = editor.titledBox("Model Settings");
        editor.modelName = new JTextField(editor.model.name, 16);
        editor.atlasW = editor.spinnerInt(editor.model.atlasW, 1, 4096, 1);
        editor.atlasH = editor.spinnerInt(editor.model.atlasH, 1, 4096, 1);
        settingsModelBox.add(editor.row("Name:", editor.modelName));
        settingsModelBox.add(editor.row("Atlas W:", editor.atlasW));
        settingsModelBox.add(editor.row("Atlas H:", editor.atlasH));
        JButton btnApplyAtlas = editor.createButton("Apply Atlas");
        btnApplyAtlas.addActionListener(
                e -> {
                    try {
                        editor.atlasW.commitEdit();
                        editor.atlasH.commitEdit();
                    } catch (java.text.ParseException ignore) {
                    }
                    editor.pushHistory();
                    editor.model.name = editor.modelName.getText().trim();
                    editor.model.atlasW = ((Number) editor.atlasW.getValue()).intValue();
                    editor.model.atlasH = ((Number) editor.atlasH.getValue()).intValue();
                    editor.uvPanel.repaint();
                });
        settingsModelBox.add(Box.createVerticalStrut(6));
        settingsModelBox.add(btnApplyAtlas);
        col1.add(settingsModelBox);
        col1.add(Box.createVerticalStrut(8));

        JPanel settingsPartsBox = editor.titledBox("Model Parts");
        editor.settingsPartList = new JList<>(editor.partListModel);
        editor.settingsPartList.setVisibleRowCount(8);
        editor.settingsPartList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        editor.settingsPartList.setCellRenderer(
                (lst, value, idx, isSel, hasFocus) -> {
                    JLabel lbl = new JLabel(value == null ? "(null)" : value.name);
                    lbl.setOpaque(true);
                    lbl.setBackground(isSel ? new Color(0x3B, 0x82, 0xF6) : new Color(0x131317));
                    lbl.setForeground(isSel ? Color.WHITE : new Color(0xE4, 0xE4, 0xE7));
                    lbl.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
                    return lbl;
                });
        JScrollPane settingsPartScroll = new JScrollPane(editor.settingsPartList);
        settingsPartScroll.setBorder(BorderFactory.createLineBorder(new Color(0x2E2E35)));
        settingsPartsBox.add(settingsPartScroll);

        JButton settingsBtnAddRoot = editor.createButton("Add Root");
        JButton settingsBtnAddChild = editor.createButton("Add Child");
        JButton settingsBtnDuplicate = editor.createButton("Duplicate");
        JButton settingsBtnDelete = editor.createButton("Delete");

        settingsBtnAddRoot.addActionListener(
                e -> {
                    editor.pushHistory();
                    EditablePart np = editor.makeNewPart();
                    editor.model.parts.add(np);
                    editor.selected = np;
                    editor.refreshPartListSelect(np);
                });
        settingsBtnAddChild.addActionListener(
                e -> {
                    editor.pushHistory();
                    EditablePart np = editor.makeNewPart();
                    if (editor.selected != null) editor.selected.children.add(np);
                    else editor.model.parts.add(np);
                    editor.selected = np;
                    editor.refreshPartListSelect(np);
                });
        settingsBtnDelete.addActionListener(
                e -> {
                    if (editor.selected == null) return;
                    editor.pushHistory();
                    editor.deleteSelected();
                    editor.refreshPartListSelect(editor.selected);
                });
        settingsBtnDuplicate.addActionListener(
                e -> {
                    if (editor.selected == null) return;
                    editor.pushHistory();
                    EditablePart cp = editor.copyPart(editor.selected);
                    EditablePart parent = editor.parentOf(editor.selected);
                    if (parent != null) parent.children.add(cp);
                    else editor.model.parts.add(cp);
                    editor.selected = cp;
                    editor.refreshPartListSelect(cp);
                });

        JPanel settingsPartBtns = new JPanel(new GridLayout(2, 2, 6, 6));
        settingsPartBtns.add(settingsBtnAddRoot);
        settingsPartBtns.add(settingsBtnAddChild);
        settingsPartBtns.add(settingsBtnDuplicate);
        settingsPartBtns.add(settingsBtnDelete);
        settingsPartsBox.add(Box.createVerticalStrut(6));
        settingsPartsBox.add(settingsPartBtns);
        col1.add(settingsPartsBox);
        col1.add(Box.createVerticalStrut(8));

        JPanel ioBox = editor.titledBox("File & Textures");
        JButton saveBtn = editor.createButton("Save (Ctrl+S)");
        JButton saveAsBtn = editor.createButton("Save As...");
        saveAsBtn.addActionListener(e -> editor.saveAs());
        saveAsBtn.setToolTipText("Choose a different model file (Ctrl+Shift+S)");
        JButton loadBtn = editor.createButton("Load (.mmdl)");
        JButton exportJavaBtn = editor.createButton("Export Java");
        JButton exportUVBtn = editor.createButton("Export UV Template");
        JButton importJavaBtn = editor.createButton("Import .java");
        editor.textureLabel = new JLabel(editor.textureName);
        JButton importTexBtn = editor.createButton("Import Texture");
        JButton clearTexBtn = editor.createButton("Clear Texture");
        JButton newBtn = editor.createButton("New Project");
        JButton undoBtn = editor.createButton("Undo (Ctrl+Z)");
        JButton redoBtn = editor.createButton("Redo (Ctrl+Y)");

        importTexBtn.addActionListener(e -> editor.doImportTexture());
        clearTexBtn.addActionListener(e -> editor.clearTexture());
        newBtn.addActionListener(e -> editor.newProject());
        undoBtn.addActionListener(e -> editor.undoAction());
        redoBtn.addActionListener(e -> editor.redoAction());

        JPanel texRow = new JPanel(new BorderLayout(6, 0));
        texRow.add(new JLabel("Texture:"), BorderLayout.WEST);
        texRow.add(editor.textureLabel, BorderLayout.CENTER);
        JPanel texBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        texBtns.add(importTexBtn);
        texBtns.add(clearTexBtn);
        texRow.add(texBtns, BorderLayout.EAST);
        ioBox.add(texRow);

        saveBtn.addActionListener(e -> editor.doSave());
        loadBtn.addActionListener(e -> editor.doLoad());
        exportJavaBtn.addActionListener(e -> editor.doExportJava());
        exportUVBtn.addActionListener(e -> editor.doExportTemplate());
        importJavaBtn.addActionListener(e -> editor.doImportJava());

        JPanel ioGrid = new JPanel(new GridLayout(2, 3, 6, 6));
        ioGrid.add(saveBtn);
        ioGrid.add(loadBtn);
        ioGrid.add(importJavaBtn);
        ioGrid.add(exportJavaBtn);
        ioGrid.add(exportUVBtn);
        ioGrid.add(saveAsBtn);
        ioBox.add(Box.createVerticalStrut(6));
        ioBox.add(ioGrid);

        JPanel ioGrid2 = new JPanel(new GridLayout(1, 3, 6, 6));
        ioGrid2.add(newBtn);
        ioGrid2.add(undoBtn);
        ioGrid2.add(redoBtn);
        ioBox.add(Box.createVerticalStrut(6));
        ioBox.add(ioGrid2);
        col1.add(ioBox);

        viewSettings.add(col1);

        // Column 2 (Selected Part Details & Animations)
        JPanel col2 = new JPanel();
        col2.setBackground(bgDark);
        col2.setLayout(new BoxLayout(col2, BoxLayout.Y_AXIS));

        JPanel propBox = editor.titledBox("Selected Part Properties");
        editor.partName = new JTextField(editor.selected != null ? editor.selected.name : "", 14);
        editor.mirror = new JCheckBox("Mirror");
        editor.u = editor.spinnerInt(0, 0, 4096, 1);
        editor.v = editor.spinnerInt(0, 0, 4096, 1);
        editor.x = editor.spinnerInt(0, -128, 128, 1);
        editor.y = editor.spinnerInt(0, -128, 128, 1);
        editor.z = editor.spinnerInt(0, -128, 128, 1);
        editor.w = editor.spinnerInt(4, 1, 256, 1);
        editor.h = editor.spinnerInt(4, 1, 256, 1);
        editor.d = editor.spinnerInt(4, 1, 256, 1);
        editor.posX = editor.spinnerFloat(0, -128, 128, 0.1);
        editor.posY = editor.spinnerFloat(0, -128, 128, 0.1);
        editor.posZ = editor.spinnerFloat(0, -128, 128, 0.1);
        editor.pitchSp = editor.spinnerFloat(0, -Math.PI * 2, Math.PI * 2, Math.toRadians(5));
        editor.yawSp = editor.spinnerFloat(0, -Math.PI * 2, Math.PI * 2, Math.toRadians(5));
        editor.rollSp = editor.spinnerFloat(0, -Math.PI * 2, Math.PI * 2, Math.toRadians(5));
        editor.inflate = editor.spinnerFloat(0, 0, 32, 0.1);

        propBox.add(editor.row("Name:", editor.partName));
        propBox.add(editor.row("Mirror:", editor.mirror));
        propBox.add(editor.row("UV U:", editor.u));
        propBox.add(editor.row("UV V:", editor.v));
        propBox.add(editor.row("Box X:", editor.x));
        propBox.add(editor.row("Box Y:", editor.y));
        propBox.add(editor.row("Box Z:", editor.z));
        propBox.add(editor.row("Width:", editor.w));
        propBox.add(editor.row("Height:", editor.h));
        propBox.add(editor.row("Depth:", editor.d));
        propBox.add(editor.row("Pivot X:", editor.posX));
        propBox.add(editor.row("Pivot Y:", editor.posY));
        propBox.add(editor.row("Pivot Z:", editor.posZ));
        propBox.add(editor.row("Pitch (rad):", editor.pitchSp));
        propBox.add(editor.row("Yaw (rad):", editor.yawSp));
        propBox.add(editor.row("Roll (rad):", editor.rollSp));
        propBox.add(editor.row("Inflate:", editor.inflate));

        JButton apply = editor.createButton("Apply Changes");
        apply.addActionListener(
                e -> {
                    editor.pushHistory();
                    editor.applyUIToSelected();
                    editor.uvPanel.repaint();
                });
        propBox.add(Box.createVerticalStrut(6));
        propBox.add(apply);
        col2.add(propBox);
        col2.add(Box.createVerticalStrut(8));

        JPanel animBox = editor.titledBox("Animations (setRotationAngles)");
        editor.animText = new JTextArea(8, 20);
        editor.animText.setLineWrap(true);
        editor.animText.setWrapStyleWord(true);
        editor.animText.setText(
                editor.model.setRotationAnglesCode == null
                        ? ""
                        : editor.model.setRotationAnglesCode);
        JScrollPane animScroll = new JScrollPane(editor.animText);
        animScroll.setBorder(BorderFactory.createLineBorder(new Color(0x2E2E35)));
        animBox.add(animScroll);

        JPanel settingsPreviewPanel = new JPanel(new GridLayout(1, 2, 6, 0));
        JCheckBox settingsChkPreviewAnim = new JCheckBox("Preview Walk/Idle");
        JCheckBox settingsChkAggressive = new JCheckBox("Preview Aggressive");
        settingsPreviewPanel.add(settingsChkPreviewAnim);
        settingsPreviewPanel.add(settingsChkAggressive);
        animBox.add(Box.createVerticalStrut(6));
        animBox.add(settingsPreviewPanel);

        // Sync preview checkboxes
        editor.chkPreviewAnim.addActionListener(
                e -> settingsChkPreviewAnim.setSelected(editor.chkPreviewAnim.isSelected()));
        settingsChkPreviewAnim.addActionListener(
                e -> editor.chkPreviewAnim.setSelected(settingsChkPreviewAnim.isSelected()));
        editor.chkAggressive.addActionListener(
                e -> settingsChkAggressive.setSelected(editor.chkAggressive.isSelected()));
        settingsChkAggressive.addActionListener(
                e -> editor.chkAggressive.setSelected(settingsChkAggressive.isSelected()));

        JButton applyAnimBtn = editor.createButton("Apply Animations");
        applyAnimBtn.addActionListener(
                e -> {
                    editor.pushHistory();
                    editor.model.setRotationAnglesCode = editor.animText.getText();
                    System.out.println(
                            "[anim] Updated setRotationAnglesCode ("
                                    + (editor.model.setRotationAnglesCode == null
                                            ? 0
                                            : editor.model.setRotationAnglesCode.length())
                                    + " chars)");
                });
        animBox.add(Box.createVerticalStrut(6));
        animBox.add(applyAnimBtn);
        col2.add(animBox);

        viewSettings.add(col2);

        JScrollPane scrollSettings = new JScrollPane(viewSettings);
        scrollSettings.setBorder(BorderFactory.createEmptyBorder());
        scrollSettings.getVerticalScrollBar().setUnitIncrement(16);
        view3D.remove(compactSide);
        JTabbedPane properties = new JTabbedPane();
        properties.addTab("Parts", new JScrollPane(compactSide));
        properties.addTab("Model and files", new JScrollPane(col1));
        properties.addTab("Properties and animation", new JScrollPane(col2));
        properties.setPreferredSize(new Dimension(360, 700));
        JSplitPane workspace = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, view3D, properties);
        workspace.setResizeWeight(1.0);
        editor.mainCardContainer.add(workspace, "3D");
    }
}
