package net.classicremastered.toolkit.modeleditor;


import net.classicremastered.minecraft.model.*;


/** Model Editor Parts responsibilities. */
final class ModelEditorParts {
    private final ModelEditorApp editor;

    ModelEditorParts(ModelEditorApp editor) { this.editor = editor; }

    EditablePart makeNewPart() {
        java.util.Set<String> existing = new java.util.HashSet<>();
        for (EditablePart p : ModelEditorApp.getAllParts(editor.model)) {
            existing.add(p.name);
        }
        
        String name = "new_part";
        if (existing.contains(name)) {
            int suffix = 2;
            while (existing.contains("new_part" + suffix)) {
                suffix++;
            }
            name = "new_part" + suffix;
        }
        
        EditablePart np = new EditablePart(name);
        np.w = np.h = np.d = 4;
        return np;
    }

    EditablePart copyPart(EditablePart p) {
        return ModelCopies.duplicate(p, ModelEditorApp.getAllParts(editor.model));
    }

    String safeCopyName(String original) {
        String base = original;
        if (base.endsWith("_copy")) {
            int idx = base.lastIndexOf("_copy");
            base = base.substring(0, idx);
        }
        int count = 1;
        String newName = base + "_copy";
        java.util.Set<String> existing = new java.util.HashSet<>();
        for (EditablePart p : ModelEditorApp.getAllParts(editor.model))
            existing.add(p.name);
        while (existing.contains(newName))
            newName = base + "_copy" + (count++ == 1 ? "" : "_" + count);
        return newName;
    }

    EditablePart parentOf(EditablePart target) {
        for (EditablePart r : editor.model.parts) {
            EditablePart p = editor.parentSearch(r, target);
            if (p != null)
                return p;
        }
        return null;
    }

    EditablePart parentSearch(EditablePart cur, EditablePart target) {
        for (EditablePart c : cur.children) {
            if (c == target)
                return cur;
            EditablePart d = editor.parentSearch(c, target);
            if (d != null)
                return d;
        }
        return null;
    }

    void deleteSelected() {
        synchronized (editor.modelLock) {
            if (editor.selected == null)
                return;
            if (editor.model.parts.remove(editor.selected)) {
                editor.selected = editor.model.parts.isEmpty() ? null : editor.model.parts.get(0);
                return;
            }
            for (EditablePart root : editor.model.parts) {
                if (editor.deleteRecursive(root, editor.selected)) {
                    editor.selected = root;
                    return;
                }
            }
        }
    }

    boolean deleteRecursive(EditablePart parent, EditablePart target) {
        if (parent.children.remove(target))
            return true;
        for (EditablePart c : parent.children)
            if (editor.deleteRecursive(c, target))
                return true;
        return false;
    }
}
