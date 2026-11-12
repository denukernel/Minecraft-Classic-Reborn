package net.classicremastered.toolkit.modeleditor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** UTF-8 model documents with atomic replacement and complete animation state. */
public final class ModelIO {
    private ModelIO() {
    }

    public static void save(File out, EditableModel model) throws IOException {
        String json = toJson(model);
        Path target = out.toPath().toAbsolutePath();
        Path temporary = Files.createTempFile(target.getParent(), ".model-", ".tmp");
        try {
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static String toJson(EditableModel model) throws IOException {
        if (model.atlasW <= 0 || model.atlasH <= 0)
            throw new IOException("Atlas dimensions must be positive");
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("name", model.name);
        root.put("atlasW", model.atlasW);
        root.put("atlasH", model.atlasH);
        root.put("setRotationAngles", model.setRotationAnglesCode);
        List<Object> parts = new ArrayList<>();
        for (EditablePart part : model.parts)
            parts.add(writePart(part));
        root.put("parts", parts);
        return ModelJson.write(root) + "\n";
    }

    private static Map<String, Object> writePart(EditablePart p) throws IOException {
        if (p.w < 0 || p.h < 0 || p.d < 0)
            throw new IOException("Part dimensions cannot be negative");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", p.name);
        result.put("u", p.u);
        result.put("v", p.v);
        result.put("x", p.x);
        result.put("y", p.y);
        result.put("z", p.z);
        result.put("w", p.w);
        result.put("h", p.h);
        result.put("d", p.d);
        result.put("inflate", p.inflate);
        result.put("posX", p.posX);
        result.put("posY", p.posY);
        result.put("posZ", p.posZ);
        result.put("pitch", p.pitch);
        result.put("yaw", p.yaw);
        result.put("roll", p.roll);
        result.put("mirror", p.mirror);
        result.put("animPitch", p.animPitch);
        result.put("animYaw", p.animYaw);
        result.put("animRoll", p.animRoll);
        List<Object> children = new ArrayList<>();
        for (EditablePart child : p.children)
            children.add(writePart(child));
        result.put("children", children);
        return result;
    }

    public static EditableModel load(File file) throws IOException {
        try {
            return parse(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IOException("Cannot load " + file.getName() + ": " + e.getMessage(), e);
        }
    }

    public static EditableModel load(InputStream input) throws IOException {
        return parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
    }

    private static EditableModel parse(String json) throws IOException {
        Map<?, ?> root = object(ModelJson.parse(json));
            EditableModel model = new EditableModel();
            model.name = string(root, "name", model.name);
            model.atlasW = integer(root, "atlasW", model.atlasW);
            model.atlasH = integer(root, "atlasH", model.atlasH);
            if (model.atlasW <= 0 || model.atlasH <= 0)
                throw new IOException("Atlas dimensions must be positive");
            model.setRotationAnglesCode = string(root, "setRotationAngles", null);
            for (Object part : list(root, "parts"))
                model.parts.add(readPart(object(part)));
        return model;
    }

    private static EditablePart readPart(Map<?, ?> data) throws IOException {
        EditablePart p = new EditablePart(string(data, "name", "part"));
        p.u = integer(data, "u", 0);
        p.v = integer(data, "v", 0);
        p.x = number(data, "x", 0);
        p.y = number(data, "y", 0);
        p.z = number(data, "z", 0);
        p.w = integer(data, "w", 1);
        p.h = integer(data, "h", 1);
        p.d = integer(data, "d", 1);
        if (p.w < 0 || p.h < 0 || p.d < 0)
            throw new IOException("Part dimensions cannot be negative");
        p.inflate = number(data, "inflate", 0);
        p.posX = number(data, "posX", 0);
        p.posY = number(data, "posY", 0);
        p.posZ = number(data, "posZ", 0);
        p.pitch = number(data, "pitch", 0);
        p.yaw = number(data, "yaw", 0);
        p.roll = number(data, "roll", 0);
        if (data.containsKey("mirror")) {
            if (!(data.get("mirror") instanceof Boolean))
                throw new IOException("mirror must be true or false");
            p.mirror = (Boolean) data.get("mirror");
        }
        p.animPitch = string(data, "animPitch", null);
        p.animYaw = string(data, "animYaw", null);
        p.animRoll = string(data, "animRoll", null);
        for (Object child : list(data, "children"))
            p.children.add(readPart(object(child)));
        return p;
    }

    private static Map<?, ?> object(Object value) throws IOException {
        if (!(value instanceof Map<?, ?> map))
            throw new IOException("Expected a JSON object");
        return map;
    }

    private static List<?> list(Map<?, ?> data, String key) throws IOException {
        if (!data.containsKey(key))
            return Collections.emptyList();
        if (!(data.get(key) instanceof List<?> list))
            throw new IOException(key + " must be an array");
        return list;
    }

    private static String string(Map<?, ?> data, String key, String fallback) throws IOException {
        Object value = data.get(key);
        if (value == null)
            return fallback;
        if (!(value instanceof String))
            throw new IOException(key + " must be a string");
        return (String) value;
    }

    private static float number(Map<?, ?> data, String key, float fallback) throws IOException {
        if (!data.containsKey(key))
            return fallback;
        if (!(data.get(key) instanceof Number n) || !Float.isFinite(n.floatValue()))
            throw new IOException(key + " must be finite");
        return n.floatValue();
    }

    private static int integer(Map<?, ?> data, String key, int fallback) throws IOException {
        if (!data.containsKey(key))
            return fallback;
        if (!(data.get(key) instanceof Number n) || n.doubleValue() != Math.rint(n.doubleValue())
                || n.doubleValue() < Integer.MIN_VALUE || n.doubleValue() > Integer.MAX_VALUE)
            throw new IOException(key + " must be an integer");
        return n.intValue();
    }
}
