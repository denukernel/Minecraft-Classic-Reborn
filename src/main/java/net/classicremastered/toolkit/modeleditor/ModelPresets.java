package net.classicremastered.toolkit.modeleditor;

import net.classicremastered.minecraft.model.*;

import java.lang.reflect.Field;
import java.util.*;

/** Copies the current game models, including child transforms and UV origins. */
public final class ModelPresets {
    public static final String[] NAMES = {
        "Zombie", "Skeleton", "Creeper", "Pig", "Sheep", "Spider", "Enderman"
    };

    private ModelPresets() {}

    public static EditableModel create(String name) throws ReflectiveOperationException {
        Model source = new ModelManager().getModel(name.toLowerCase(Locale.ROOT));
        if (source == null) throw new IllegalArgumentException("Unknown preset " + name);
        source.setRotationAngles(0, 0, 0, 0, 0, .0625f);
        EditableModel result = new EditableModel();
        result.name = name + "Model";
        Map<ModelPart, String> names = new LinkedHashMap<>();
        for (Class<?> type = source.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() != ModelPart.class) continue;
                field.setAccessible(true);
                ModelPart part = (ModelPart) field.get(source);
                if (part != null) names.putIfAbsent(part, field.getName());
            }
        }
        Set<ModelPart> children = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ModelPart part : names.keySet()) children.addAll(children(part));
        for (ModelPart part : names.keySet())
            if (!children.contains(part)) result.parts.add(copy(part, names));
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<ModelPart> children(ModelPart part) throws ReflectiveOperationException {
        Field field = ModelPart.class.getDeclaredField("children");
        field.setAccessible(true);
        return (List<ModelPart>) field.get(part);
    }

    private static EditablePart copy(ModelPart part, Map<ModelPart, String> names)
            throws ReflectiveOperationException {
        EditablePart result = new EditablePart(names.getOrDefault(part, "part"));
        for (String name : new String[] {"u", "v"}) {
            Field field = ModelPart.class.getDeclaredField(name);
            field.setAccessible(true);
            if (name.equals("u")) result.u = field.getInt(part);
            else result.v = field.getInt(part);
        }
        float minX = Float.MAX_VALUE,
                minY = minX,
                minZ = minX,
                maxX = -minX,
                maxY = -minX,
                maxZ = -minX;
        if (part.vertices != null)
            for (Vertex vertex : part.vertices) {
                minX = Math.min(minX, vertex.vector.x);
                minY = Math.min(minY, vertex.vector.y);
                minZ = Math.min(minZ, vertex.vector.z);
                maxX = Math.max(maxX, vertex.vector.x);
                maxY = Math.max(maxY, vertex.vector.y);
                maxZ = Math.max(maxZ, vertex.vector.z);
            }
        if (part.vertices != null) {
            result.w = textureSpan(part.quads[4], true, TexturedQuad.ATLAS_W);
            result.h = textureSpan(part.quads[4], false, TexturedQuad.ATLAS_H);
            result.d = textureSpan(part.quads[0], true, TexturedQuad.ATLAS_W);
            result.inflate = (maxX - minX - result.w) / 2;
            result.x = minX + result.inflate;
            result.y = minY + result.inflate;
            result.z = minZ + result.inflate;
        }
        result.posX = part.x;
        result.posY = part.y;
        result.posZ = part.z;
        result.pitch = part.pitch;
        result.yaw = part.yaw;
        result.roll = part.roll;
        result.mirror = part.mirror;
        for (ModelPart child : children(part)) result.children.add(copy(child, names));
        return result;
    }

    private static int textureSpan(TexturedQuad quad, boolean horizontal, int atlasSize) {
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (Vertex vertex : quad.vertices) {
            float value = horizontal ? vertex.u : vertex.v;
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return Math.round((max - min) * atlasSize + .2f);
    }
}
