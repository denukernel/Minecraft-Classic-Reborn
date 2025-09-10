package net.classicremastered.toolkit.modeleditor;

import java.util.ArrayList;
import java.util.List;

public final class EditablePart {
    public String name = "part";
    public int u = 0, v = 0;
    public float x, y, z;
    public int w = 1, h = 1, d = 1;
    public float inflate = 0.0f;
    public float posX, posY, posZ;
    public float pitch, yaw, roll;   // static radians
    public boolean mirror = false;
    public final List<EditablePart> children = new ArrayList<>();

    // --- NEW animation expressions (optional, may be null) ---
    public String animPitch, animYaw, animRoll;

    public EditablePart() {}
    public EditablePart(String name) { this.name = name; }
}
