package net.classicremastered.toolkit.modeleditor;

import java.util.*;

/** Lossless snapshots and separately named duplicates of model trees. */
final class ModelCopies {
    private ModelCopies() {
    }

    static EditableModel copy(EditableModel source) {
        EditableModel result = new EditableModel();
        result.name = source.name;
        result.atlasW = source.atlasW;
        result.atlasH = source.atlasH;
        result.setRotationAnglesCode = source.setRotationAnglesCode;
        for (EditablePart part : source.parts)
            result.parts.add(copyPart(part));
        return result;
    }

    static EditablePart copyPart(EditablePart p) {
        EditablePart c = new EditablePart(p.name);
        c.u = p.u;
        c.v = p.v;
        c.x = p.x;
        c.y = p.y;
        c.z = p.z;
        c.w = p.w;
        c.h = p.h;
        c.d = p.d;
        c.inflate = p.inflate;
        c.posX = p.posX;
        c.posY = p.posY;
        c.posZ = p.posZ;
        c.pitch = p.pitch;
        c.yaw = p.yaw;
        c.roll = p.roll;
        c.mirror = p.mirror;
        c.animPitch = p.animPitch;
        c.animYaw = p.animYaw;
        c.animRoll = p.animRoll;
        for (EditablePart child : p.children)
            c.children.add(copyPart(child));
        return c;
    }

    static EditablePart duplicate(EditablePart part, Collection<EditablePart> existing) {
        Set<String> names = new HashSet<>();
        for (EditablePart other : existing)
            names.add(other.name);
        EditablePart result = copyPart(part);
        rename(result, names);
        return result;
    }

    private static void rename(EditablePart part, Set<String> names) {
        String base = (part.name == null ? "part" : part.name) + "_copy";
        String name = base;
        for (int suffix = 2; !names.add(name); suffix++)
            name = base + suffix;
        part.name = name;
        for (EditablePart child : part.children)
            rename(child, names);
    }
}
