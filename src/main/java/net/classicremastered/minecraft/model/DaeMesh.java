// src/com/mojang/minecraft/model/DaeMesh.java
package net.classicremastered.minecraft.model;

import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.*;

/**
 * Tiny Collada (.dae) mesh reader for Fast64-style exports.
 * - Reads first <geometry>/<mesh> only
 * - Supports POSITION (required), optional TEXCOORD, optional NORMAL
 * - Supports <triangles> or <polylist> with "vcount" 3 only (else triangulate fan)
 * - Ignores skin/controllers/animations (static mesh)
 * - Produces ObjMesh.Data (unified verts)
 */
public final class DaeMesh {

    // Promote to top-level (class-scope) so static helpers can use it.
    private static final class Inp {
        String semantic;
        String source;
        int offset;
    }

    public static ObjMesh.Data load(InputStream in) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
        Element root = doc.getDocumentElement();

        // Find first geometry/mesh
        NodeList geos = root.getElementsByTagName("geometry");
        if (geos.getLength() == 0) throw new IllegalStateException("No <geometry> in DAE");
        Element geo = (Element) geos.item(0);
        Element mesh = firstChild(geo, "mesh");
        if (mesh == null) throw new IllegalStateException("<mesh> missing");

        // Collect <source> float arrays
        Map<String, float[]> floatArrays = new HashMap<>();
        NodeList sources = mesh.getElementsByTagName("source");
        for (int i = 0; i < sources.getLength(); i++) {
            Element src = (Element) sources.item(i);
            String id = src.getAttribute("id");
            Element fa = firstChild(src, "float_array");
            if (fa == null) continue;
            floatArrays.put(id, parseFloats(fa.getTextContent()));
        }

        // <vertices> mapping: id -> POSITION source
        Map<String, String> verticesMap = new HashMap<>();
        NodeList verts = mesh.getElementsByTagName("vertices");
        for (int i = 0; i < verts.getLength(); i++) {
            Element v = (Element) verts.item(i);
            String vid = v.getAttribute("id");
            NodeList inputs = v.getElementsByTagName("input");
            for (int j = 0; j < inputs.getLength(); j++) {
                Element inp = (Element) inputs.item(j);
                if ("POSITION".equals(inp.getAttribute("semantic"))) {
                    String src = stripHash(inp.getAttribute("source"));
                    verticesMap.put(vid, src);
                }
            }
        }

        // Accessor stride map
        Map<String, Integer> stridesMap = new HashMap<>();
        NodeList tiCommon = mesh.getElementsByTagName("technique_common");
        for (int i = 0; i < tiCommon.getLength(); i++) {
            Element tc = (Element) tiCommon.item(i);
            NodeList accs = tc.getElementsByTagName("accessor");
            for (int j = 0; j < accs.getLength(); j++) {
                Element acc = (Element) accs.item(j);
                String src = stripHash(acc.getAttribute("source"));
                int st = parseInt(acc.getAttribute("stride"), 3);
                stridesMap.put(src, st);
            }
        }

        // Find triangles/polylist
        Element tris = firstChild(mesh, "triangles");
        Element poly = (tris == null ? firstChild(mesh, "polylist") : null);
        Element prim = (tris != null ? tris : poly);
        if (prim == null) throw new IllegalStateException("No <triangles>/<polylist>");

        // Inputs for the primitive: map offset -> input
        Map<Integer, Inp> inpByOff = new HashMap<>();
        int maxOff = -1;
        NodeList pInputs = prim.getElementsByTagName("input");
        for (int i = 0; i < pInputs.getLength(); i++) {
            Element inp = (Element) pInputs.item(i);
            Inp ii = new Inp();
            ii.semantic = inp.getAttribute("semantic");
            ii.source = stripHash(inp.getAttribute("source"));
            ii.offset = parseInt(inp.getAttribute("offset"), 0);
            inpByOff.put(ii.offset, ii);
            if (ii.offset > maxOff) maxOff = ii.offset;
        }

        // Resolve VERTEX → POSITION <source>
        for (Inp ii : inpByOff.values()) {
            if ("VERTEX".equals(ii.semantic)) {
                String posSrc = verticesMap.get(ii.source);
                if (posSrc != null) ii.source = posSrc;
            }
        }

        // Read index arrays
        Element pElem = firstChild(prim, "p");
        if (pElem == null) throw new IllegalStateException("<p> missing");
        int[] idx = parseInts(pElem.getTextContent());

        // polylist triangulation via <vcount> (assume triangles if absent)
        int[] vcount = null;
        if (poly != null) {
            Element vce = firstChild(poly, "vcount");
            if (vce != null) vcount = parseInts(vce.getTextContent());
        }

        // Helpers to fetch vectors from sources
        final class Src {
            final float[] data;
            final int stride;
            Src(String id) {
                this.data = floatArrays.get(id);
                this.stride = stridesMap.containsKey(id) ? stridesMap.get(id) : 3;
            }
            void read(int elementIndex, float[] dst, int n) {
                int base = elementIndex * stride;
                for (int i = 0; i < n; i++) {
                    dst[i] = (data != null && (base + i) < data.length) ? data[base + i] : 0f;
                }
            }
        }

        Src pos = null, nrm = null, uv = null;
        for (Inp ii : inpByOff.values()) {
            if ("POSITION".equals(ii.semantic)) pos = new Src(ii.source);
            if ("NORMAL".equals(ii.semantic))   nrm = new Src(ii.source);
            if ("TEXCOORD".equals(ii.semantic)) uv  = new Src(ii.source);
        }
        if (pos == null) throw new IllegalStateException("POSITION source missing");

        ObjMesh.Data out = new ObjMesh.Data();
        Map<String, Integer> cache = new HashMap<>();

        // Iterate primitive indices (blocks of (maxOff+1) per vertex)
        final int tuple = maxOff + 1;
        int cursor = 0;

        if (vcount == null) {
            // triangles: exactly 3 vertices per primitive
            while (cursor + tuple * 3 <= idx.length) {
                int base0 = cursor;
                int base1 = cursor + tuple;
                int base2 = cursor + tuple * 2;
                addTri(out, cache, pos, nrm, uv, idx, inpByOff, base0, base1, base2);
                cursor += tuple * 3;
            }
        } else {
            // polylist: fan triangulate each polygon
            for (int polyI = 0; polyI < vcount.length; polyI++) {
                int vc = vcount[polyI];
                int polyStart = cursor;
                for (int k = 1; k + 1 < vc; k++) {
                    int base0 = polyStart;
                    int base1 = polyStart + tuple * k;
                    int base2 = polyStart + tuple * (k + 1);
                    addTri(out, cache, pos, nrm, uv, idx, inpByOff, base0, base1, base2);
                }
                cursor += tuple * vc;
            }
        }

        // Compute normals if missing
        boolean anyN = false;
        for (ObjMesh.Vertex v : out.verts) {
            if (v.nx != 0 || v.ny != 0 || v.nz != 0) { anyN = true; break; }
        }
        if (!anyN) ObjIO_recalcNormals(out);

        // Wrap UVs into [0,1]
        for (ObjMesh.Vertex v : out.verts) {
            if (v.u < 0f || v.u > 1f) v.u = v.u - (float) Math.floor(v.u);
            if (v.v < 0f || v.v > 1f) v.v = v.v - (float) Math.floor(v.v);
        }

        return out;
    }

    // ---------- helpers ----------
    private static Element firstChild(Element e, String tag) {
        NodeList nl = e.getElementsByTagName(tag);
        return (nl.getLength() == 0 ? null : (Element) nl.item(0));
    }
    private static String stripHash(String s) { return (s != null && s.startsWith("#")) ? s.substring(1) : s; }
    private static int parseInt(String s, int d) { try { return Integer.parseInt(s); } catch (Exception e) { return d; } }
    private static float[] parseFloats(String t) {
        String[] sp = t.trim().split("\\s+"); float[] f = new float[sp.length];
        for (int i = 0; i < sp.length; i++) { try { f[i] = Float.parseFloat(sp[i]); } catch (Exception ignore) {} }
        return f;
    }
    private static int[] parseInts(String t) {
        String[] sp = t.trim().split("\\s+"); int[] a = new int[sp.length];
        for (int i = 0; i < sp.length; i++) { try { a[i] = Integer.parseInt(sp[i]); } catch (Exception ignore) {} }
        return a;
    }

    private static void addTri(ObjMesh.Data out, Map<String,Integer> cache,
                               Object pos, Object nrm, Object uv,
                               int[] idx, Map<Integer, Inp> inputs,
                               int base0, int base1, int base2) {

        SrcWrap P = (SrcWrap) pos, N = (SrcWrap) nrm, T = (SrcWrap) uv;
        int a = addV(out, cache, P, N, T, idx, inputs, base0);
        int b = addV(out, cache, P, N, T, idx, inputs, base1);
        int c = addV(out, cache, P, N, T, idx, inputs, base2);
        ObjMesh.Tri t = new ObjMesh.Tri(); t.a = a; t.b = b; t.c = c; out.tris.add(t);
    }

    // Small wrapper so we can pass the local Src instances through static methods
    private interface SrcWrap {
        void read(int elementIndex, float[] dst, int n);
    }
    private static int addV(ObjMesh.Data out, Map<String,Integer> cache,
                            SrcWrap pos, SrcWrap nrm, SrcWrap uv,
                            int[] idx, Map<Integer, Inp> inputs, int base) {
        int pIdx = -1, tIdx = -1, nIdx = -1;

        for (Map.Entry<Integer, Inp> e : inputs.entrySet()) {
            int off = e.getKey();
            int colladaIndex = idx[base + off];
            String sem = e.getValue().semantic;
            if ("POSITION".equals(sem)) pIdx = colladaIndex;
            if ("TEXCOORD".equals(sem)) tIdx = colladaIndex;
            if ("NORMAL".equals(sem))   nIdx = colladaIndex;
        }

        float[] P = new float[3], N = new float[3], T = new float[2];
        if (pIdx >= 0) pos.read(pIdx, P, 3);
        if (uv != null && tIdx >= 0) uv.read(tIdx, T, 2);
        if (nrm != null && nIdx >= 0) nrm.read(nIdx, N, 3);

        String key = pIdx + "|" + tIdx + "|" + nIdx;
        Integer got = cache.get(key);
        if (got != null) return got;

        ObjMesh.Vertex v = new ObjMesh.Vertex();
        v.x = P[0]; v.y = P[1]; v.z = P[2];
        v.u = T[0]; v.v = T[1];
        v.nx = N[0]; v.ny = N[1]; v.nz = N[2];

        int outIdx = out.verts.size();
        out.verts.add(v);
        cache.put(key, outIdx);
        return outIdx;
    }

    private static void ObjIO_recalcNormals(ObjMesh.Data d) {
        for (ObjMesh.Vertex v : d.verts) { v.nx = v.ny = v.nz = 0f; }
        for (ObjMesh.Tri t : d.tris) {
            ObjMesh.Vertex A = d.verts.get(t.a), B = d.verts.get(t.b), C = d.verts.get(t.c);
            float ux = B.x - A.x, uy = B.y - A.y, uz = B.z - A.z;
            float vx = C.x - A.x, vy = C.y - A.y, vz = C.z - A.z;
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            float len = (float) Math.sqrt(nx*nx + ny*ny + nz*nz); if (len < 1e-7f) continue;
            nx /= len; ny /= len; nz /= len;
            A.nx += nx; A.ny += ny; A.nz += nz;
            B.nx += nx; B.ny += ny; B.nz += nz;
            C.nx += nx; C.ny += ny; C.nz += nz;
        }
        for (ObjMesh.Vertex v : d.verts) {
            float l = (float) Math.sqrt(v.nx*v.nx + v.ny*v.ny + v.nz*v.nz);
            if (l < 1e-7f) { v.nx = 0; v.ny = 0; v.nz = 1; }
            else { v.nx /= l; v.ny /= l; v.nz /= l; }
        }
    }
}
