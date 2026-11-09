package net.classicremastered.toolkit.modeleditor;

import net.classicremastered.minecraft.model.*;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.*;

import javax.swing.*;

/** Model Editor Input responsibilities. */
final class ModelEditorInput {
    private final ModelEditorApp editor;

    ModelEditorInput(ModelEditorApp editor) {
        this.editor = editor;
    }

    void input() {
        if (!editor.keyboardActive || !Keyboard.isCreated()) {
            return; // skip polling when typing in Swing
        }

        try {
            // Always allow ESC to exit
            if (Keyboard.isKeyDown(Keyboard.KEY_ESCAPE)) {
                System.exit(0);
            }

            // --- Handle global LWJGL keys that must work in BOTH modes ---
            while (Keyboard.isCreated() && Keyboard.next()) {
                if (!Keyboard.getEventKeyState()) continue;

                int k = Keyboard.getEventKey();

                // Ignore all other keys when in Swing typing mode
                if (!editor.canvasEdit) continue;

                boolean ctrl =
                        Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)
                                || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
                boolean shift =
                        Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                                || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);

                switch (k) {
                    case Keyboard.KEY_Z:
                        if (ctrl) {
                            if (shift) editor.redoAction();
                            else editor.undoAction();
                        }
                        break;
                    case Keyboard.KEY_Y:
                        if (ctrl) editor.redoAction();
                        break;
                    case Keyboard.KEY_N:
                        if (ctrl && shift) editor.newProject();
                        break;
                    case Keyboard.KEY_DELETE:
                        if (editor.selected != null) {
                            editor.pushHistory();
                            editor.deleteSelected();
                            editor.refreshPartListSelect(editor.selected);
                        }
                        break;
                    case Keyboard.KEY_S:
                        if (ctrl) editor.requestSave();
                        break;
                    case Keyboard.KEY_L:
                        if (ctrl) editor.requestLoad();
                        break;
                    case Keyboard.KEY_E:
                        if (ctrl) editor.requestExportJava();
                        break;
                    case Keyboard.KEY_F6:
                        editor.requestExportTemplate();
                        break;
                    case Keyboard.KEY_J:
                        if (ctrl) editor.requestImportJava();
                        break;
                }
            }
        } catch (IllegalStateException ignored) {
            // Happens if keyboard was destroyed mid-frame
            return;
        }

        // Skip all camera/movement input if not in canvas mode
        if (!editor.canvasEdit) return;

        // --- Camera orbit and zoom ---
        final boolean anyMouseDown =
                Mouse.isButtonDown(0) || Mouse.isButtonDown(1) || Mouse.isButtonDown(2);
        final boolean allow3DInput = editor.canvasHover || anyMouseDown;

        // Process mouse events for right-click picking
        while (Mouse.isCreated() && Mouse.next()) {
            if (Mouse.getEventButton() == 1 && Mouse.getEventButtonState()) {
                editor.pickingX = Mouse.getEventX();
                editor.pickingY = Mouse.getEventY();
                editor.triggerPicking = true;
            }
        }

        if (allow3DInput) {
            if (Mouse.isButtonDown(0)) {
                editor.yaw += Mouse.getDX() * 0.4f;
                editor.pitch -= Mouse.getDY() * 0.4f;
                editor.pitch = ModelEditorApp.clamp(editor.pitch, -89, 89);
            }

            int wheel = Mouse.getDWheel();
            if (wheel != 0) {
                editor.dist -= wheel * 0.01f;
                editor.dist = ModelEditorApp.clamp(editor.dist, 2, 25);
            }
        }

        // Arrow keys to pan/translate the camera
        boolean camW = Keyboard.isKeyDown(Keyboard.KEY_UP);
        boolean camS = Keyboard.isKeyDown(Keyboard.KEY_DOWN);
        boolean camA = Keyboard.isKeyDown(Keyboard.KEY_LEFT);
        boolean camD = Keyboard.isKeyDown(Keyboard.KEY_RIGHT);

        if (camW || camS || camA || camD) {
            float camSpeed =
                    Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                                    || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)
                            ? 0.25f
                            : 0.08f;
            double rad = Math.toRadians(editor.yaw);
            float cosY = (float) Math.cos(rad);
            float sinY = (float) Math.sin(rad);

            float fX = -cosY;
            float fZ = -sinY;
            float rX = sinY;
            float rZ = -cosY;

            if (camW) {
                editor.targetX += fX * camSpeed;
                editor.targetZ += fZ * camSpeed;
            }
            if (camS) {
                editor.targetX -= fX * camSpeed;
                editor.targetZ -= fZ * camSpeed;
            }
            if (camA) {
                editor.targetX -= rX * camSpeed;
                editor.targetZ -= rZ * camSpeed;
            }
            if (camD) {
                editor.targetX += rX * camSpeed;
                editor.targetZ += rZ * camSpeed;
            }
        }

        if (editor.selected == null) return;

        // Continuous edit keys
        boolean ctrlDown =
                Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)
                        || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        boolean shiftDown =
                Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);

        boolean movingW = Keyboard.isKeyDown(Keyboard.KEY_W);
        boolean movingS = Keyboard.isKeyDown(Keyboard.KEY_S);
        boolean movingA = Keyboard.isKeyDown(Keyboard.KEY_A);
        boolean movingD = Keyboard.isKeyDown(Keyboard.KEY_D);
        boolean movingQ = Keyboard.isKeyDown(Keyboard.KEY_Q);
        boolean movingE = Keyboard.isKeyDown(Keyboard.KEY_E);

        boolean editingKeys =
                !ctrlDown && (movingW || movingS || movingA || movingD || movingQ || movingE);

        if (editingKeys) {
            if (!editor.inKeyEdit) {
                editor.pushHistory();
                editor.inKeyEdit = true;
            }

            float delta = shiftDown ? 0.60f : 0.30f;

            // Camera-yaw-relative translation
            double rad = Math.toRadians(editor.yaw);
            float cosY = (float) Math.cos(rad);
            float sinY = (float) Math.sin(rad);

            float moveX = 0;
            float moveZ = 0;

            if (movingW) {
                moveX += -cosY * delta;
                moveZ += -sinY * delta;
            }
            if (movingS) {
                moveX += cosY * delta;
                moveZ += sinY * delta;
            }
            if (movingA) {
                moveX += -sinY * delta;
                moveZ += cosY * delta;
            }
            if (movingD) {
                moveX += sinY * delta;
                moveZ += -cosY * delta;
            }

            editor.selected.posX += moveX;
            editor.selected.posZ += moveZ;

            if (movingQ) editor.selected.posY += delta; // Down
            if (movingE) editor.selected.posY -= delta; // Up

            long now = System.currentTimeMillis();
            if (now - editor.lastUiUpdate > 66) {
                editor.lastUiUpdate = now;
                ModelEditorApp.onEDT(editor::loadSelectedToUI);
            }
        } else {
            editor.inKeyEdit = false;
        }
    }

    void focusSidePanel() {
        SwingUtilities.invokeLater(
                () -> {
                    Component fo =
                            KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                    if (editor.isEditorComponent(fo))
                        return; // don't steal focus from what you just clicked

                    // Prefer model-level editors first
                    if (editor.modelName != null && editor.modelName.isShowing()) {
                        editor.modelName.requestFocusInWindow();
                        editor.modelName.selectAll();
                        return;
                    }
                    if (editor.atlasW != null && editor.atlasW.isShowing()) {
                        ((JSpinner.DefaultEditor) editor.atlasW.getEditor())
                                .getTextField()
                                .requestFocusInWindow();
                        ((JSpinner.DefaultEditor) editor.atlasW.getEditor())
                                .getTextField()
                                .selectAll();
                        return;
                    }
                    if (editor.atlasH != null && editor.atlasH.isShowing()) {
                        ((JSpinner.DefaultEditor) editor.atlasH.getEditor())
                                .getTextField()
                                .requestFocusInWindow();
                        ((JSpinner.DefaultEditor) editor.atlasH.getEditor())
                                .getTextField()
                                .selectAll();
                        return;
                    }
                    // Fallback to partName
                    if (editor.partName != null && editor.partName.isShowing()) {
                        editor.partName.requestFocusInWindow();
                        editor.partName.selectAll();
                    }
                });
    }

    void restoreFocusAfterDialog() {
        new javax.swing.Timer(
                150,
                e -> {
                    try {
                        if (editor.frame != null) {
                            editor.frame.toFront();
                            editor.frame.requestFocus();
                        }
                        if (editor.glCanvas != null && editor.canvasEdit) {
                            editor.glCanvas.requestFocusInWindow();
                        }
                    } catch (Throwable ignored) {
                    }
                }) {
            {
                setRepeats(false);
                start();
            }
        };
    }

    void setListInteractive(boolean on) {
        if (editor.partList != null) {
            editor.partList.setFocusable(on); // blocks key navigation when false
            // optional: also kill some common bindings when disabled
            if (!on) {
                InputMap im = editor.partList.getInputMap(JComponent.WHEN_FOCUSED);
                im.put(KeyStroke.getKeyStroke("UP"), "none");
                im.put(KeyStroke.getKeyStroke("DOWN"), "none");
                im.put(KeyStroke.getKeyStroke("PAGE_UP"), "none");
                im.put(KeyStroke.getKeyStroke("PAGE_DOWN"), "none");
                im.put(KeyStroke.getKeyStroke("HOME"), "none");
                im.put(KeyStroke.getKeyStroke("END"), "none");
            }
        }
    }

    void installFocusTracking() {
        java.awt.KeyboardFocusManager kfm =
                java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager();
        kfm.addPropertyChangeListener(
                "focusOwner",
                evt -> {
                    if (editor.inFocusTransition) return;
                    Object obj = evt.getNewValue();
                    boolean editing = editor.isEditorComponent(obj);
                    editor.typingInSwing = editing;
                    if (editing) editor.enterSwingTypingMode();
                });
    }

    boolean isEditorComponent(Object obj) {
        if (!(obj instanceof java.awt.Component)) return false;

        // Text field or text area
        if (obj instanceof javax.swing.text.JTextComponent) return true;

        // Spinner or its inner text field
        if (obj instanceof javax.swing.JSpinner) return true;
        if (obj instanceof javax.swing.JFormattedTextField) return true;

        // Editable combo box
        if (obj instanceof javax.swing.JComboBox && ((javax.swing.JComboBox<?>) obj).isEditable())
            return true;

        return false;
    }

    boolean hasTypingFocus() {
        java.awt.Component c =
                java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (c == null) return false;

        // Text field or area
        if (c instanceof javax.swing.text.JTextComponent) return true;

        // Spinner editor's JTextField
        if (c instanceof JSpinner) return true;
        if (c.getParent() != null && c.getParent().getParent() instanceof JSpinner) return true;

        return false;
    }

    void switchMode(boolean toCanvas) {
        ModelEditorApp.onEDT(
                () -> {
                    if (toCanvas) {
                        if (editor.mainCardLayout != null && editor.mainCardContainer != null) {
                            editor.mainCardLayout.show(editor.mainCardContainer, "3D");
                        }
                        editor.enterCanvasEditMode();
                        if (editor.btn3DMode != null) {
                            editor.btn3DMode.setBackground(new Color(0x3B82F6));
                            editor.btn3DMode.setForeground(Color.WHITE);
                        }
                        if (editor.btnSettingsMode != null) {
                            editor.btnSettingsMode.setBackground(new Color(0x31313E));
                            editor.btnSettingsMode.setForeground(new Color(0xE4E4E7));
                        }
                    } else {
                        if (editor.mainCardLayout != null && editor.mainCardContainer != null) {
                            editor.mainCardLayout.show(editor.mainCardContainer, "3D");
                        }
                        editor.enterSwingTypingMode();
                        if (editor.btnSettingsMode != null) {
                            editor.btnSettingsMode.setBackground(new Color(0x3B82F6));
                            editor.btnSettingsMode.setForeground(Color.WHITE);
                        }
                        if (editor.btn3DMode != null) {
                            editor.btn3DMode.setBackground(new Color(0x31313E));
                            editor.btn3DMode.setForeground(new Color(0xE4E4E7));
                        }
                    }
                });
    }

    void enterCanvasEditMode() {
        editor.inFocusTransition = true;
        editor.canvasEdit = true;
        editor.typingInSwing = false;
        editor.setListInteractive(true); // re-enable list navigation
        editor.enableKeyboard(); // reattach LWJGL keyboard
        if (!editor.glCanvas.isFocusOwner()) editor.glCanvas.requestFocusInWindow();
        editor.canvasHover = true;
        editor.updateModeTitle();
        SwingUtilities.invokeLater(
                () -> {
                    editor.inFocusTransition = false;
                });
    }

    void enterSwingTypingMode() {
        editor.inFocusTransition = true;
        editor.canvasEdit = false;
        editor.typingInSwing = true;
        editor.setListInteractive(false); // prevent list from eating arrow keys
        editor.disableKeyboard(); // release LWJGL keyboard grab
        editor.focusSidePanel(); // focus text field
        editor.updateModeTitle();
        SwingUtilities.invokeLater(
                () -> {
                    editor.inFocusTransition = false;
                });
    }

    void disableKeyboard() {
        editor.keyboardActive = false;
        System.out.println("[keyboard] paused for Swing input");
    }

    void enableKeyboard() {
        editor.keyboardActive = true;
        try {
            if (Keyboard.isCreated()) {
                while (Keyboard.next()) {
                    // drain queue
                }
            }
        } catch (Throwable ignored) {
        }
        System.out.println("[keyboard] resumed for Canvas input");
    }

    void installHoverTracking() {
        editor.glCanvas.addMouseListener(
                new java.awt.event.MouseAdapter() {
                    @Override
                    public void mouseEntered(java.awt.event.MouseEvent e) {
                        editor.canvasHover = true;
                    }

                    @Override
                    public void mouseExited(java.awt.event.MouseEvent e) {
                        editor.canvasHover = false;
                    }

                    @Override
                    public void mousePressed(java.awt.event.MouseEvent e) {
                        editor.canvasHover = true;
                    }

                    @Override
                    public void mouseReleased(java.awt.event.MouseEvent e) {
                        /* leave canvasHover; exit will clear */ }
                });
        editor.glCanvas.addMouseMotionListener(
                new java.awt.event.MouseMotionAdapter() {
                    @Override
                    public void mouseMoved(java.awt.event.MouseEvent e) {
                        editor.canvasHover = true;
                    }

                    @Override
                    public void mouseDragged(java.awt.event.MouseEvent e) {
                        editor.canvasHover = true;
                    }
                });
    }

    void installFocusHandlers() {
        editor.glCanvas.setFocusable(true);
        editor.glCanvas.setFocusTraversalKeysEnabled(false);

        editor.glCanvas.addMouseListener(
                new java.awt.event.MouseAdapter() {
                    @Override
                    public void mousePressed(java.awt.event.MouseEvent e) {
                        editor.enterCanvasEditMode();
                    }
                });
    }

    void updateModeTitle() {
        SwingUtilities.invokeLater(
                () -> {
                    if (editor.frame != null) {
                        editor.frame.setTitle(
                                "Classic Model Editor - "
                                        + (editor.document.file() == null
                                                ? "Unsaved model"
                                                : editor.document.file().getName())
                                        + " - "
                                        + (editor.canvasEdit ? "Canvas" : "Properties"));
                    }
                });
    }

    void installGlobalHotkeys() {
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(
                        ev -> {
                            if (editor.frame == null || !editor.frame.isActive()) return false;
                            if (ev.getID() != java.awt.event.KeyEvent.KEY_PRESSED) return false;

                            if (editor.isEditorComponent(ev.getComponent())
                                    && (ev.getKeyCode() == java.awt.event.KeyEvent.VK_Z
                                            || ev.getKeyCode() == java.awt.event.KeyEvent.VK_Y))
                                return false;

                            final boolean ctrl =
                                    ev.isControlDown() || ev.isMetaDown(); // meta for mac
                            switch (ev.getKeyCode()) {
                                case java.awt.event.KeyEvent.VK_S:
                                    if (ctrl) {
                                        if (ev.isShiftDown()) editor.saveAs();
                                        else editor.requestSave();
                                        ev.consume();
                                        return true;
                                    }
                                    break;
                                case java.awt.event.KeyEvent.VK_L:
                                    if (ctrl) {
                                        editor.requestLoad();
                                        ev.consume();
                                        return true;
                                    }
                                    break;
                                case java.awt.event.KeyEvent.VK_E:
                                    if (ctrl) {
                                        editor.requestExportJava();
                                        ev.consume();
                                        return true;
                                    }
                                    break;
                                case java.awt.event.KeyEvent.VK_F6:
                                    {
                                        editor.requestExportTemplate();
                                        ev.consume();
                                        return true;
                                    }
                                case java.awt.event.KeyEvent.VK_J:
                                    if (ctrl) {
                                        editor.requestImportJava();
                                        ev.consume();
                                        return true;
                                    }
                                    break;
                                case java.awt.event.KeyEvent.VK_Z:
                                    if (ctrl) {
                                        if (ev.isShiftDown()) editor.redoAction();
                                        else editor.undoAction();
                                        ev.consume();
                                        return true;
                                    }
                                    break;
                                case java.awt.event.KeyEvent.VK_Y:
                                    if (ctrl) {
                                        editor.redoAction();
                                        ev.consume();
                                        return true;
                                    }
                                    break;
                                case java.awt.event.KeyEvent.VK_ESCAPE:
                                    if (!editor.canvasEdit) {
                                        editor.switchMode(true);
                                        ev.consume();
                                        return true;
                                    }
                                    break;
                            }
                            return false;
                        });
    }
}
