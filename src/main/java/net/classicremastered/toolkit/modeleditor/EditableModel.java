package net.classicremastered.toolkit.modeleditor;

import java.util.ArrayList;
import java.util.List;

public final class EditableModel {
    public String name = "unnamed_model";
    public int atlasW = 64, atlasH = 32;
    public final List<EditablePart> parts = new ArrayList<>();

    // optional global animation method body
    public String setRotationAnglesCode = null;
}

