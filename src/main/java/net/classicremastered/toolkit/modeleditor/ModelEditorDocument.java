package net.classicremastered.toolkit.modeleditor;

import java.io.File;
import java.io.IOException;

import java.io.*;

/** Tracks the current document separately from file dialogs and Swing widgets. */
final class ModelEditorDocument {
    record State(File file, String savedState) {}
    private volatile File file;
    private volatile String savedState;
    private final java.util.Map<File, String> savedFiles = new java.util.HashMap<>();

    File file() { return file; }
    State snapshot() { return new State(file, savedState); }
    void restore(State state) {
        file = state.file();
        savedState = savedFiles.getOrDefault(file, state.savedState());
    }
    void reset() { file = null; savedState = null; }
    void remember(File file, EditableModel model) throws IOException {
        String state = ModelIO.toJson(model);
        this.file = file.getAbsoluteFile();
        savedState = state;
        savedFiles.put(this.file, state);
    }
    void save(File file, EditableModel model) throws IOException {
        ModelIO.save(file, model);
        remember(file, model);
    }
    boolean isModified(EditableModel model) {
        try { return !ModelIO.toJson(model).equals(savedState); }
        catch (IOException e) { return true; }
    }
}
