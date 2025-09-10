package net.classicremastered.toolkit.modeleditor;

import java.io.*;

public final class JavaExporter {
    private JavaExporter() {
    }

    public static void export(String pkg, String className, EditableModel em) throws IOException {
        String appdata = System.getenv("APPDATA");
        if (appdata == null)
            appdata = System.getProperty("user.home");

        File dir = new File(appdata, ".mcraft/client/modelexport");
        if (!dir.exists())
            dir.mkdirs();

        File outFile = new File(dir, className + ".java");

        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(outFile)))) {
            if (pkg != null && !pkg.isEmpty())
                pw.printf("package %s;%n%n", pkg);
            pw.println("import net.classicremastered.minecraft.model.*;");
            pw.println();
            pw.printf("public final class %s extends Model {%n%n", className);

            // declare fields
            for (EditablePart p : em.parts)
                declareFields(pw, p, "    ");
            pw.println();

            // constructor
            pw.printf("    public %s() {%n", className);
            pw.printf("        int prevW = TexturedQuad.ATLAS_W, prevH = TexturedQuad.ATLAS_H;%n");
            pw.printf("        TexturedQuad.ATLAS_W = %d; TexturedQuad.ATLAS_H = %d;%n", em.atlasW, em.atlasH);
            for (EditablePart p : em.parts)
                emitPartCtor(pw, p, "        ", null);
            for (EditablePart p : em.parts)
                emitAttachChildren(pw, p, "        ");
            pw.printf("        TexturedQuad.ATLAS_W = prevW; TexturedQuad.ATLAS_H = prevH;%n");
            pw.println("    }");
            pw.println();

            // render
            pw.println("    @Override");
            pw.println(
                    "    public void render(float a, float b, float c, float headYaw, float headPitch, float scale) {");
            for (EditablePart p : em.parts)
                pw.printf("        this.%s.render(scale);%n", sanitize(p.name));
            pw.println("    }");
            pw.println();

            // optional animation
            if (em.setRotationAnglesCode != null && !em.setRotationAnglesCode.isEmpty()) {
                pw.println("    @Override");
                pw.println(
                        "    public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scale) {");
                pw.println("        " + em.setRotationAnglesCode.replace("\n", "\n        "));
                pw.println("    }");
            }

            pw.println("}");
        }

        System.out.println("Exported model to: " + outFile.getAbsolutePath());
    }

    private static void declareFields(PrintWriter pw, EditablePart p, String ind) {
        pw.printf("%spublic ModelPart %s;%n", ind, sanitize(p.name));
        for (EditablePart c : p.children)
            declareFields(pw, c, ind);
    }

    private static void emitPartCtor(PrintWriter pw, EditablePart p, String ind, String parent) {
        String n = sanitize(p.name);
        pw.printf("%s%s = new ModelPart(%d, %d);%n", ind, n, p.u, p.v);
        if (p.mirror)
            pw.printf("%s%s.mirror = true;%n", ind, n);
        pw.printf("%s%s.setBounds(%s, %s, %s, %d, %d, %d, %s);%n", ind, n, f(p.x), f(p.y), f(p.z), p.w, p.h, p.d,
                f(p.inflate));
        pw.printf("%s%s.setPosition(%s, %s, %s);%n", ind, n, f(p.posX), f(p.posY), f(p.posZ));
        if (nz(p.pitch))
            pw.printf("%s%s.pitch = %s;%n", ind, n, f(p.pitch));
        if (nz(p.yaw))
            pw.printf("%s%s.yaw   = %s;%n", ind, n, f(p.yaw));
        if (nz(p.roll))
            pw.printf("%s%s.roll  = %s;%n", ind, n, f(p.roll));
        for (EditablePart c : p.children)
            emitPartCtor(pw, c, ind, n);
    }

    private static void emitAttachChildren(PrintWriter pw, EditablePart p, String ind) {
        String n = sanitize(p.name);
        for (EditablePart c : p.children) {
            pw.printf("%s%s.addChild(%s);%n", ind, n, sanitize(c.name));
            emitAttachChildren(pw, c, ind);
        }
    }

    private static boolean nz(float v) {
        return Math.abs(v) > 1e-6;
    }

    private static String f(float v) {
        return (v == (int) v) ? Integer.toString((int) v) : Float.toString(v);
    }

    private static String sanitize(String s) {
        String t = s.trim().replaceAll("[^a-zA-Z0-9_]", "_");
        if (t.isEmpty() || Character.isDigit(t.charAt(0)))
            t = "_" + t;
        return t;
    }
}
