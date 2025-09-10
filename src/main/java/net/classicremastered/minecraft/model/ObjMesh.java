package net.classicremastered.minecraft.model;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Minimal OBJ(+MTL) loader for Classic 0.30.
 * - Triangulates faces (fan)
 * - Reads v/vt/vn/usemtl
 * - Picks first map_Kd from .mtl (optional)
 * - Bakes SM64-style Z-up geometry into Minecraft Y-up:
 *      Ymc = Zobj ; Zmc = -Yobj
 */
public final class ObjMesh {

    // ===== Data =====
    public static final class Vertex {
        public float x, y, z;      // position
        public float nx, ny, nz;   // normal (unit)
        public float u, v;         // UV [0..1]
    }
    public static final class Tri { public int a, b, c; }
    public static final class Data {
        public final ArrayList<Vertex> verts = new ArrayList<>();
        public final ArrayList<Tri> tris = new ArrayList<>();
        public String materialName = "";
        public String textureFile  = null; // from MTL map_Kd (if any)
    }

    // ===== Public API =====
    public static Data load(InputStream objStream, InputStream mtlStream) throws IOException {
        var vs  = new ArrayList<float[]>();  // v
        var vts = new ArrayList<float[]>();  // vt
        var vns = new ArrayList<float[]>();  // vn
        var map = new HashMap<String, Integer>();
        var out = new Data();

        if (mtlStream != null) out.textureFile = parseMtlForFirstMapKd(mtlStream);

        try (var br = new BufferedReader(new InputStreamReader(objStream))) {
            String usemtl = "";
            for (String line; (line = br.readLine()) != null; ) {
                line = line.trim();
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                String[] t = line.split("\\s+");
                switch (t[0]) {
                    case "v":
                        vs.add(new float[]{ f(t,1), f(t,2), f(t,3) });
                        break;
                    case "vt":
                        // flip V to match usual GL texture coords
                        vts.add(new float[]{ f(t,1), 1f - (t.length>2 ? f(t,2) : 0f) });
                        break;
                    case "vn":
                        vns.add(new float[]{ f(t,1), f(t,2), f(t,3) });
                        break;
                    case "usemtl":
                        usemtl = (t.length>1 ? t[1] : "");
                        break;
                    case "f": {
                        // triangulate n-gon as a fan
                        int[] idx = new int[t.length - 1];
                        for (int i=1;i<t.length;i++) idx[i-1] = indexFor(t[i], vs, vts, vns, map, out);
                        for (int i=1;i+1<idx.length;i++) addTri(out, idx[0], idx[i], idx[i+1]);
                    } break;
                }
            }
            out.materialName = usemtl;
        }

        // === Orientation fix: Z-up OBJ -> Y-up Minecraft ===
        // Ymc = Zobj ; Zmc = -Yobj  (positions AND normals)
        for (Vertex v : out.verts) {
            float oy = v.y, oz = v.z;
            v.y = oz;
            v.z = -oy;

            float ony = v.ny, onz = v.nz;
            v.ny = onz;
            v.nz = -ony;
        }

        // If OBJ had no normals, compute them
        boolean anyNormal = out.verts.stream().anyMatch(v -> (v.nx != 0f || v.ny != 0f || v.nz != 0f));
        if (!anyNormal) recalcNormals(out);

        // Normalize invalid UVs into [0..1]
        for (Vertex v : out.verts) {
            v.u = wrap01(v.u);
            v.v = wrap01(v.v);
        }
        return out;
    }

    // ===== Helpers =====
    private static void addTri(Data d, int a, int b, int c) {
        Tri t = new Tri(); t.a=a; t.b=b; t.c=c; d.tris.add(t);
    }

    private static int indexFor(String tok,
                                ArrayList<float[]> vs,
                                ArrayList<float[]> vts,
                                ArrayList<float[]> vns,
                                HashMap<String,Integer> map,
                                Data out) {
        Integer got = map.get(tok);
        if (got != null) return got;

        String[] sp = tok.split("/");
        int vi = objIndex(sp[0], vs.size());
        float[] p  = vs.get(vi);
        float[] vt = (sp.length>1 && !sp[1].isEmpty()) ? vts.get(objIndex(sp[1], vts.size())) : new float[]{0f,0f};
        float[] vn = (sp.length>2 && !sp[2].isEmpty()) ? vns.get(objIndex(sp[2], vns.size())) : new float[]{0f,0f,1f};

        Vertex v = new Vertex();
        v.x=p[0]; v.y=p[1]; v.z=p[2];
        v.u=vt[0]; v.v=vt[1];
        v.nx=vn[0]; v.ny=vn[1]; v.nz=vn[2];

        int idx = out.verts.size();
        out.verts.add(v);
        map.put(tok, idx);
        return idx;
    }

    private static int objIndex(String s, int n) { int i = Integer.parseInt(s); return (i < 0 ? n + i : i - 1); }
    private static float f(String[] a, int i) { return Float.parseFloat(a[i]); }

    private static void recalcNormals(Data d) {
        for (Vertex v : d.verts) { v.nx=0; v.ny=0; v.nz=0; }
        for (Tri t : d.tris) {
            Vertex A=d.verts.get(t.a), B=d.verts.get(t.b), C=d.verts.get(t.c);
            float ux=B.x-A.x, uy=B.y-A.y, uz=B.z-A.z;
            float vx=C.x-A.x, vy=C.y-A.y, vz=C.z-A.z;
            float nx = uy*vz - uz*vy;
            float ny = uz*vx - ux*vz;
            float nz = ux*vy - uy*vx;
            float len = (float)Math.sqrt(nx*nx + ny*ny + nz*nz);
            if (len < 1e-7f) continue;
            nx/=len; ny/=len; nz/=len;
            A.nx+=nx; A.ny+=ny; A.nz+=nz;
            B.nx+=nx; B.ny+=ny; B.nz+=nz;
            C.nx+=nx; C.ny+=ny; C.nz+=nz;
        }
        for (Vertex v : d.verts) {
            float len = (float)Math.sqrt(v.nx*v.nx + v.ny*v.ny + v.nz*v.nz);
            if (len < 1e-7f) { v.nx=0; v.ny=0; v.nz=1; }
            else { v.nx/=len; v.ny/=len; v.nz/=len; }
        }
    }

    private static String parseMtlForFirstMapKd(InputStream in) throws IOException {
        try (var br = new BufferedReader(new InputStreamReader(in))) {
            Pattern p = Pattern.compile("(?i)map_Kd\\s+(.+)$");
            for (String line; (line = br.readLine()) != null; ) {
                var m = p.matcher(line.trim());
                if (m.find()) return m.group(1).replace('\\','/').trim();
            }
        }
        return null;
    }

    private static float wrap01(float t) {
        if (t >= 0f && t <= 1f) return t;
        t = t - (float)Math.floor(t);
        return (t == 1f ? 0f : t);
    }
}
