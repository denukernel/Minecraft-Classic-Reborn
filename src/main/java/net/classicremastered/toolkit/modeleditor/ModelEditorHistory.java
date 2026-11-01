package net.classicremastered.toolkit.modeleditor;


import net.classicremastered.minecraft.model.*;


/** Model Editor History responsibilities. */
final class ModelEditorHistory {
    private final ModelEditorApp editor;

    ModelEditorHistory(ModelEditorApp editor) { this.editor = editor; }

    EditableModel copyModel(EditableModel m) {
        return ModelCopies.copy(m);
    }

    ModelEditorApp.Snapshot takeSnapshot() {
        return new ModelEditorApp.Snapshot(editor.copyModel(editor.model), editor.indexPathOf(editor.selected), editor.document.snapshot());
    }

    void restoreSnapshot(ModelEditorApp.Snapshot s) {
        editor.restoring = true;
        try {
            editor.model = s.model;
            editor.document.restore(s.document);
            editor.updateModeTitle();
            editor.selected = editor.resolveByIndexPath(s.selPath);
            editor.refreshPartListSelect(editor.selected);
            editor.syncModelFieldsToUI();
            if (editor.animText != null) editor.animText.setText(editor.model.setRotationAnglesCode == null
                ? "" : editor.model.setRotationAnglesCode);
            if (editor.uvPanel != null)
                editor.uvPanel.repaint();
            // reset any in-progress edit gesture
            editor.inKeyEdit = false;
        } finally {
            editor.restoring = false;
        }
    }

    void pushHistory() {
        if (editor.restoring)
            return; // <- critical
        editor.undoStack.push(editor.takeSnapshot());
        while (editor.undoStack.size() > ModelEditorApp.HISTORY_LIMIT)
            editor.undoStack.removeLast();
        editor.redoStack.clear();
        editor.lastHistPush = System.currentTimeMillis();
    }

    void pushHistoryThrottled() {
        if (editor.restoring)
            return; // <- critical
        long now = System.currentTimeMillis();
        if (now - editor.lastHistPush >= 250)
            editor.pushHistory();
    }

    void undoAction() {
        if (editor.undoStack.isEmpty())
            return;
        editor.redoStack.push(editor.takeSnapshot());
        editor.restoreSnapshot(editor.undoStack.pop());
    }

    void redoAction() {
        if (editor.redoStack.isEmpty())
            return;
        editor.undoStack.push(editor.takeSnapshot());
        editor.restoreSnapshot(editor.redoStack.pop());
    }

    int[] indexPathOf(EditablePart target) {
        if (target == null)
            return new int[0];
        for (int i = 0; i < editor.model.parts.size(); i++) {
            EditablePart r = editor.model.parts.get(i);
            if (r == target)
                return new int[] { i };
            java.util.List<Integer> sub = editor.findPath(r, target);
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

    java.util.List<Integer> findPath(EditablePart cur, EditablePart target) {
        for (int i = 0; i < cur.children.size(); i++) {
            EditablePart ch = cur.children.get(i);
            if (ch == target) {
                java.util.ArrayList<Integer> l = new java.util.ArrayList<>();
                l.add(i);
                return l;
            }
            java.util.List<Integer> deeper = editor.findPath(ch, target);
            if (deeper != null) {
                java.util.ArrayList<Integer> l = new java.util.ArrayList<>();
                l.add(i);
                l.addAll(deeper);
                return l;
            }
        }
        return null;
    }

    EditablePart resolveByIndexPath(int[] path) {
        if (path == null || path.length == 0)
            return editor.model.parts.isEmpty() ? null : editor.model.parts.get(0);
        if (path[0] < 0 || path[0] >= editor.model.parts.size())
            return null;
        EditablePart cur = editor.model.parts.get(path[0]);
        for (int i = 1; i < path.length; i++) {
            int idx = path[i];
            if (idx < 0 || idx >= cur.children.size())
                break;
            cur = cur.children.get(idx);
        }
        return cur;
    }
}
