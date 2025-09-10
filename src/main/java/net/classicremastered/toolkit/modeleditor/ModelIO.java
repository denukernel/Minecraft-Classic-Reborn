package net.classicremastered.toolkit.modeleditor;

import java.io.*;
import java.util.*;

public final class ModelIO {
    private ModelIO() {}

    // tiny JSON writer (enough for our fields)


    private static void writePart(PrintWriter pw, EditablePart p, String indent) {
        pw.print(indent + "{");
        pw.printf("\n%s  \"name\":\"%s\",", indent, escape(p.name));
        pw.printf("\n%s  \"u\":%d, \"v\":%d,", indent, p.u, p.v);
        pw.printf("\n%s  \"x\":%s, \"y\":%s, \"z\":%s, \"w\":%d, \"h\":%d, \"d\":%d, \"inflate\":%s,",
                indent, f(p.x), f(p.y), f(p.z), p.w, p.h, p.d, f(p.inflate));
        pw.printf("\n%s  \"posX\":%s, \"posY\":%s, \"posZ\":%s,", indent, f(p.posX), f(p.posY), f(p.posZ));
        pw.printf("\n%s  \"pitch\":%s, \"yaw\":%s, \"roll\":%s,", indent, f(p.pitch), f(p.yaw), f(p.roll));
        pw.printf("\n%s  \"mirror\":%s,", indent, p.mirror ? "true":"false");
        pw.printf("\n%s  \"children\":[", indent);
        for (int i=0;i<p.children.size();i++) {
            writePart(pw, p.children.get(i), indent + "    ");
            if (i < p.children.size()-1) pw.println(",");
        }
        pw.printf("\n%s  ]\n%s}", indent, indent);
    }

    private static String f(float v){ return ((v== (int)v)? Integer.toString((int)v) : Float.toString(v)); }
    private static String escape(String s){ return s.replace("\\","\\\\").replace("\"","\\\""); }

 // --- ModelIO.java ---
    public static void save(File out, EditableModel m) throws IOException {
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(out)))) {
            pw.println("{");
            pw.printf("  \"name\":\"%s\",\n", escape(m.name));
            pw.printf("  \"atlasW\":%d,\n", m.atlasW);
            pw.printf("  \"atlasH\":%d,\n", m.atlasH);

            // --- NEW: write animation code if present ---
            if (m.setRotationAnglesCode != null) {
                String animEsc = escape(m.setRotationAnglesCode).replace("\n", "\\n");
                pw.printf("  \"setRotationAngles\":\"%s\",\n", animEsc);
            }

            pw.println("  \"parts\":[");
            for (int i=0;i<m.parts.size();i++) {
                writePart(pw, m.parts.get(i), "    ");
                if (i < m.parts.size()-1) pw.println(",");
            }
            pw.println("\n  ]");
            pw.println("}");
        }
    }

    public static EditableModel load(File in) throws IOException {
        String json = new String(readAllBytes(in), "UTF-8");
        Token t = new Token(json);
        t.expect('{'); EditableModel m = new EditableModel();
        while (!t.peek('}')) {
            String key = t.readString(); t.expect(':');
            if (key.equals("name")) m.name = t.readString();
            else if (key.equals("atlasW")) m.atlasW = (int)t.readNumber();
            else if (key.equals("atlasH")) m.atlasH = (int)t.readNumber();
            else if (key.equals("setRotationAngles")) {
                String code = t.readString().replace("\\n", "\n");
                m.setRotationAnglesCode = code;
            }
            else if (key.equals("parts")) {
                t.expect('[');
                while (!t.peek(']')) {
                    m.parts.add(readPart(t));
                    if (t.peek(',')) t.next();
                }
                t.expect(']');
            }
            if (t.peek(',')) t.next();
        }
        t.expect('}');
        return m;
    }


    private static EditablePart readPart(Token t) {
        EditablePart p = new EditablePart();
        t.expect('{');
        while (!t.peek('}')) {
            String k = t.readString(); t.expect(':');
            switch (k) {
                case "name": p.name = t.readString(); break;
                case "u": p.u = (int)t.readNumber(); break;
                case "v": p.v = (int)t.readNumber(); break;
                case "x": p.x = t.readNumber(); break;
                case "y": p.y = t.readNumber(); break;
                case "z": p.z = t.readNumber(); break;
                case "w": p.w = (int)t.readNumber(); break;
                case "h": p.h = (int)t.readNumber(); break;
                case "d": p.d = (int)t.readNumber(); break;
                case "inflate": p.inflate = t.readNumber(); break;
                case "posX": p.posX = t.readNumber(); break;
                case "posY": p.posY = t.readNumber(); break;
                case "posZ": p.posZ = t.readNumber(); break;
                case "pitch": p.pitch = t.readNumber(); break;
                case "yaw": p.yaw = t.readNumber(); break;
                case "roll": p.roll = t.readNumber(); break;
                case "mirror": p.mirror = t.readBoolean(); break;
                case "children":
                    t.expect('[');
                    while (!t.peek(']')) {
                        p.children.add(readPart(t));
                        if (t.peek(',')) t.next();
                    }
                    t.expect(']');
                    break;
                default: throw new RuntimeException("Unknown key: " + k);
            }
            if (t.peek(',')) t.next();
        }
        t.expect('}');
        return p;
    }

    private static byte[] readAllBytes(File f) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[8192]; int n;
            while ((n=in.read(buf))!=-1) out.write(buf,0,n);
            return out.toByteArray();
        }
    }

    // tiny tokenizer for our JSON subset
    private static final class Token {
        final String s; int i=0;
        Token(String s){ this.s=s; skip(); }
        void skip(){ while (i<s.length()) { char c=s.charAt(i); if (c<=' ') i++; else break; } }
        boolean peek(char c){ skip(); return i<s.length() && s.charAt(i)==c; }
        void expect(char c){ skip(); if (s.charAt(i)!=c) throw new RuntimeException("Expected "+c+" at "+i); i++; skip(); }
        void next(){ i++; skip(); }
        String readString(){ expect('"'); int start=i; while (s.charAt(i)!='"'){ if (s.charAt(i)=='\\') i++; i++; } String out=s.substring(start,i); i++; skip(); return out.replace("\\\"","\"").replace("\\\\","\\"); }
        float readNumber(){ skip(); int start=i; while (i<s.length() && "-+.0123456789".indexOf(s.charAt(i))>=0) i++; float v=Float.parseFloat(s.substring(start,i)); skip(); return v; }
        boolean readBoolean(){ skip(); if (s.startsWith("true",i)){ i+=4; } else if (s.startsWith("false",i)){ i+=5; } else throw new RuntimeException("bool at "+i); skip(); return s.charAt(i-1)=='e'; }
    }
}
