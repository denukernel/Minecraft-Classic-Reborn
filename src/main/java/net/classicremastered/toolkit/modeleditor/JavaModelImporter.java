package net.classicremastered.toolkit.modeleditor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Import Classic 0.30-style Java model sources, including simple inheritance.
 */
public final class JavaModelImporter {

    // ---- Regex ----
    private static final Pattern CLASS_NAME = Pattern.compile("\\bclass\\s+([A-Za-z0-9_]+)\\b");
    private static final Pattern EXTENDS_NAME = Pattern
            .compile("\\bclass\\s+[A-Za-z0-9_]+\\s+extends\\s+([A-Za-z0-9_]+)\\b");

    // new ModelPart(u,v) OR new ModelPart(u,v,atlasW,atlasH)
    private static final Pattern NEW_PART = Pattern
            .compile("\\b([A-Za-z0-9_]+)\\s*=\\s*new\\s+ModelPart\\s*\\(([^,]+),([^,\\)]+)(?:,([^,]+),([^\\)]+))?\\)");

    private static final Pattern SET_BOUNDS = Pattern
            .compile("([A-Za-z0-9_]+)\\.setBounds\\(([^,]+),([^,]+),([^,]+),([^,]+),([^,]+),([^,]+)(?:,([^\\)]+))?\\)");

    private static final Pattern SET_POS = Pattern
            .compile("([A-Za-z0-9_]+)\\.(?:setPosition|setPos)\\(([^,]+),([^,]+),([^\\)]+)\\)");

    private static final Pattern MIRROR_ASSIGN = Pattern.compile("([A-Za-z0-9_]+)\\.mirror\\s*=\\s*(true|false)");

    private static final Pattern ROT_NUMERIC = Pattern
            .compile("([A-Za-z0-9_]+)\\.(pitch|yaw|roll|xRot|yRot|zRot)\\s*=\\s*([-+]?\\d+(?:\\.\\d+)?)");

    private static final Pattern ROT_EXPR = Pattern
            .compile("([A-Za-z0-9_]+)\\.(pitch|yaw|roll|xRot|yRot|zRot)\\s*=\\s*([^;]+);");

    private static final Pattern ADD_CHILD = Pattern
            .compile("([A-Za-z0-9_]+)\\.addChild\\((?:this\\.)?([A-Za-z0-9_]+)\\)");

    private JavaModelImporter() {
    }

    // ---- Public API ----
    public static EditableModel load(File javaFile) throws IOException {
        String src = readAll(javaFile);

        EditableModel model = new EditableModel();
        model.name = guessClassName(src);
        model.atlasW = 64;
        model.atlasH = 32;

        Map<String, EditablePart> parts = new LinkedHashMap<>();
        Map<String, String> parentOf = new LinkedHashMap<>();

        // --- inheritance ---
        String base = guessExtends(src);
        if (base != null) {
            File baseFile = new File(javaFile.getParentFile(), base + ".java");
            if (baseFile.isFile()) {
                EditableModel baseModel = load(baseFile);
                for (EditablePart bp : baseModel.parts)
                    model.parts.add(deepCopy(bp));
                collectParentLinks(baseModel.parts, null, parentOf);
                model.atlasW = baseModel.atlasW;
                model.atlasH = baseModel.atlasH;
                model.setRotationAnglesCode = baseModel.setRotationAnglesCode;
            }
        }

        // --- parse parts ---
        forEach(src, NEW_PART, m -> {
            String name = m.group(1).trim();
            int u = (int) numSafe(m.group(2));
            int v = (int) numSafe(m.group(3));
            EditablePart p = new EditablePart(name);
            p.u = u;
            p.v = v;
            parts.put(name, p);
        });

        forEach(src, SET_BOUNDS, m -> {
            EditablePart p = parts.get(m.group(1));
            if (p == null)
                return;
            p.x = (int) numSafe(m.group(2));
            p.y = (int) numSafe(m.group(3));
            p.z = (int) numSafe(m.group(4));
            p.w = (int) numSafe(m.group(5));
            p.h = (int) numSafe(m.group(6));
            p.d = (int) numSafe(m.group(7));
            if (m.group(8) != null)
                p.inflate = (float) evalExpr(m.group(8));
        });

        forEach(src, SET_POS, m -> {
            EditablePart p = parts.get(m.group(1));
            if (p == null)
                return;
            p.posX = (float) numSafe(m.group(2));
            p.posY = (float) numSafe(m.group(3));
            p.posZ = (float) numSafe(m.group(4));
        });

        forEach(src, MIRROR_ASSIGN, m -> {
            EditablePart p = parts.get(m.group(1));
            if (p != null)
                p.mirror = Boolean.parseBoolean(m.group(2));
        });

        forEach(src, ROT_NUMERIC, m -> {
            EditablePart p = parts.get(m.group(1));
            if (p == null)
                return;
            setRotField(m.group(2), p, (float) numSafe(m.group(3)));
        });

        forEach(src, ROT_EXPR, m -> {
            EditablePart p = parts.get(m.group(1));
            if (p == null)
                return;
            Float v = evalPiExpr(m.group(3));
            if (v != null)
                setRotField(m.group(2), p, v);
        });

        // --- parse setRotationAngles for animations ---
        Pattern anim = Pattern.compile("setRotationAngles\\s*\\([^)]*\\)\\s*\\{([\\s\\S]*?)\\}");
        Matcher am = anim.matcher(src);
        if (am.find()) {
            model.setRotationAnglesCode = am.group(1).trim();
        }

        forEach(src, ADD_CHILD, m -> parentOf.put(m.group(2), m.group(1)));

        // build hierarchy
        model.parts.clear();
        for (Map.Entry<String, EditablePart> e : parts.entrySet()) {
            if (!parentOf.containsKey(e.getKey()))
                model.parts.add(e.getValue());
        }
        for (Map.Entry<String, String> e : parentOf.entrySet()) {
            EditablePart ch = parts.get(e.getKey());
            EditablePart par = parts.get(e.getValue());
            if (ch != null && par != null)
                par.children.add(ch);
        }

        return model;
    }

    // ---- utils ----

    private static String guessExtends(String src) {
        Matcher m = EXTENDS_NAME.matcher(src);
        return m.find() ? m.group(1) : null;
    }

    private static void collectParentLinks(List<EditablePart> roots, String parent, Map<String, String> map) {
        for (EditablePart p : roots) {
            if (parent != null)
                map.put(p.name, parent);
            collectParentLinks(p.children, p.name, map);
        }
    }

    private static EditablePart deepCopy(EditablePart src) {
        EditablePart d = new EditablePart(src.name);
        d.u = src.u;
        d.v = src.v;
        d.x = src.x;
        d.y = src.y;
        d.z = src.z;
        d.w = src.w;
        d.h = src.h;
        d.d = src.d;
        d.posX = src.posX;
        d.posY = src.posY;
        d.posZ = src.posZ;
        d.pitch = src.pitch;
        d.yaw = src.yaw;
        d.roll = src.roll;
        d.inflate = src.inflate;
        d.mirror = src.mirror;
        for (EditablePart c : src.children)
            d.children.add(deepCopy(c));
        return d;
    }

    private static void setRotField(String field, EditablePart p, float v) {
        switch (field) {
        case "pitch":
        case "xRot":
            p.pitch = v;
            break;
        case "yaw":
        case "yRot":
            p.yaw = v;
            break;
        case "roll":
        case "zRot":
            p.roll = v;
            break;
        }
    }

    private static String readAll(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static double numSafe(String s) {
        try {
            return Double.parseDouble(s.replaceAll("[fF]$", "").trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static double evalExpr(String expr) {
        try {
            return Double.parseDouble(expr.replaceAll("[^0-9+\\-*/.]", ""));
        } catch (Exception e) {
            return 0.0;
        }
    }

    /** Evaluate Math.PI style expressions. */
    private static Float evalPiExpr(String expr) {
        String e = expr.replaceAll("\\s+", "");
        if (e.contains("PI")) {
            if (e.equals("Math.PI") || e.equals("PI"))
                return (float) Math.PI;
            if (e.equals("-Math.PI") || e.equals("-PI"))
                return (float) -Math.PI;
            if (e.matches("[-0-9.]+\\*Math\\.PI(/[-0-9.]+)?")) {
                String[] parts = e.split("\\*Math\\.PI");
                double mul = Double.parseDouble(parts[0]);
                double div = 1.0;
                if (parts.length > 1 && parts[1].startsWith("/"))
                    div = Double.parseDouble(parts[1].substring(1));
                return (float) (mul * Math.PI / div);
            }
        }
        try {
            return Float.parseFloat(e.replaceAll("[fF]$", ""));
        } catch (Exception ex) {
            return null;
        }
    }

    private static String guessClassName(String src) {
        Matcher m = CLASS_NAME.matcher(src);
        return m.find() ? m.group(1) : "ImportedModel";
    }

    // mini foreach
    private interface Hit {
        void on(Matcher m) throws IOException;
    }

    private static void forEach(String text, Pattern p, Hit hit) {
        Matcher m = p.matcher(text);
        while (m.find())
            try {
                hit.on(m);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
    }
}
