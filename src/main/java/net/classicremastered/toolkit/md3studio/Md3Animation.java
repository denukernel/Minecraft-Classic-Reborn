package net.classicremastered.toolkit.md3studio;

import net.classicremastered.toolkit.md3studio.MD3Studio.*;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Validated animated MD3 data. The original layout retains tags and shader records. */
final class Md3Animation {
    final byte[] original;
    final int frameCount, frameOffset;
    final List<Surface> surfaces = new ArrayList<>();

    static final class Surface {
        String name;
        int vertexOffset, uvOffset, vertexCount;
        final List<Mesh> frames = new ArrayList<>();
        boolean visible = true;
    }

    private Md3Animation(byte[] bytes, int frames, int frameOffset) {
        original = bytes;
        frameCount = frames;
        this.frameOffset = frameOffset;
    }

    static Md3Animation read(File file) throws IOException {
        long size = Files.size(file.toPath());
        if (size < 108 || size > 128L * 1024 * 1024) throw new IOException("Invalid MD3 file size");
        byte[] bytes = Files.readAllBytes(file.toPath());
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (b.getInt(0) != 0x33504449 || b.getInt(4) != 15)
            throw new IOException("Expected MD3 version 15");
        int frames = b.getInt(76), tags = b.getInt(80), surfaces = b.getInt(84);
        int frameOffset = b.getInt(92),
                tagOffset = b.getInt(96),
                offset = b.getInt(100),
                end = b.getInt(104);
        if (frames < 1
                || frames > 4096
                || tags < 0
                || surfaces < 1
                || surfaces > 1024
                || end != bytes.length) throw new IOException("Invalid MD3 header counts");
        range(bytes, frameOffset, (long) frames * 56, 108);
        if (tags > 0) range(bytes, tagOffset, (long) frames * tags * 112, 108);
        Md3Animation animation = new Md3Animation(bytes, frames, frameOffset);
        long vertices = 0;
        for (int s = 0; s < surfaces; s++) {
            range(bytes, offset, 108, 108);
            if (b.getInt(offset) != 0x33504449) throw new IOException("Invalid surface identifier");
            int nf = b.getInt(offset + 72),
                    shaders = b.getInt(offset + 76),
                    nv = b.getInt(offset + 80),
                    nt = b.getInt(offset + 84);
            int triangles = b.getInt(offset + 88),
                    shaderOffset = b.getInt(offset + 92),
                    uv = b.getInt(offset + 96),
                    xyz = b.getInt(offset + 100),
                    length = b.getInt(offset + 104);
            if (nf != frames || shaders < 0 || nv < 1 || nt < 1 || length < 108)
                throw new IOException("Invalid surface counts");
            vertices += (long) frames * nv;
            if (vertices > 4000000)
                throw new IOException("MD3 exceeds four million animated vertices");
            range(bytes, offset, length, 108);
            local(length, triangles, (long) nt * 12);
            local(length, shaderOffset, (long) shaders * 68);
            local(length, uv, (long) nv * 8);
            local(length, xyz, (long) frames * nv * 8);
            Surface surface = new Surface();
            surface.name = text(bytes, offset + 4, 64);
            surface.vertexOffset = offset + xyz;
            surface.uvOffset = offset + uv;
            surface.vertexCount = nv;
            String shader = shaders == 0 ? "" : text(bytes, offset + shaderOffset, 64);
            for (int f = 0; f < frames; f++) {
                Mesh mesh = new Mesh();
                mesh.materialName = shader;
                for (int v = 0; v < nv; v++) {
                    int p = offset + xyz + (f * nv + v) * 8;
                    Vertex vertex = new Vertex();
                    vertex.x = b.getShort(p) / 64f;
                    vertex.y = b.getShort(p + 2) / 64f;
                    vertex.z = b.getShort(p + 4) / 64f;
                    int normal = b.getShort(p + 6) & 65535;
                    double lat = (normal >>> 8) * 2 * Math.PI / 255,
                            lng = (normal & 255) * 2 * Math.PI / 255;
                    vertex.nx = (float) (Math.cos(lat) * Math.sin(lng));
                    vertex.ny = (float) (Math.sin(lat) * Math.sin(lng));
                    vertex.nz = (float) Math.cos(lng);
                    vertex.u = b.getFloat(offset + uv + v * 8);
                    vertex.v = b.getFloat(offset + uv + v * 8 + 4);
                    if (!Float.isFinite(vertex.u) || !Float.isFinite(vertex.v))
                        throw new IOException("Non-finite texture coordinates");
                    mesh.vertices.add(vertex);
                }
                for (int t = 0; t < nt; t++) {
                    int p = offset + triangles + t * 12;
                    int a = b.getInt(p), c = b.getInt(p + 4), d = b.getInt(p + 8);
                    if (a < 0 || c < 0 || d < 0 || a >= nv || c >= nv || d >= nv)
                        throw new IOException("Triangle index outside surface");
                    mesh.addTri(a, c, d);
                }
                surface.frames.add(mesh);
            }
            animation.surfaces.add(surface);
            offset += length;
        }
        return animation;
    }

    Mesh frame(int index, boolean all) {
        Mesh merged = new Mesh();
        for (int s = 0; s < surfaces.size(); s++) {
            Surface surface = surfaces.get(s);
            if (!all && !surface.visible) continue;
            Mesh mesh = surface.frames.get(index);
            int base = merged.vertices.size();
            merged.vertices.addAll(mesh.vertices);
            merged.materialName = mesh.materialName;
            for (Triangle triangle : mesh.tris) {
                Triangle t = new Triangle();
                t.a = base + triangle.a;
                t.b = base + triangle.b;
                t.c = base + triangle.c;
                t.surface = s;
                merged.tris.add(t);
            }
        }
        return merged;
    }

    void write(File file) throws IOException {
        byte[] bytes = original.clone();
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (Surface surface : surfaces)
            for (int f = 0; f < frameCount; f++) {
                Mesh mesh = surface.frames.get(f);
                for (int v = 0; v < surface.vertexCount; v++) {
                    Vertex vertex = mesh.vertices.get(v);
                    int p = surface.vertexOffset + (f * surface.vertexCount + v) * 8;
                    b.putShort(p, packed(vertex.x));
                    b.putShort(p + 2, packed(vertex.y));
                    b.putShort(p + 4, packed(vertex.z));
                    int lat =
                            (int) Math.round(Math.atan2(vertex.ny, vertex.nx) * 255 / (2 * Math.PI))
                                    & 255;
                    int lng =
                            (int)
                                            Math.round(
                                                    Math.acos(Math.max(-1, Math.min(1, vertex.nz)))
                                                            * 255
                                                            / (2 * Math.PI))
                                    & 255;
                    b.putShort(p + 6, (short) ((lat << 8) | lng));
                    b.putFloat(surface.uvOffset + v * 8, vertex.u);
                    b.putFloat(surface.uvOffset + v * 8 + 4, vertex.v);
                }
            }
        for (int f = 0; f < frameCount; f++) {
            Mesh mesh = frame(f, true);
            float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE},
                    max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            for (Vertex vertex : mesh.vertices) {
                float[] p = {vertex.x, vertex.y, vertex.z};
                for (int axis = 0; axis < 3; axis++) {
                    min[axis] = Math.min(min[axis], p[axis]);
                    max[axis] = Math.max(max[axis], p[axis]);
                }
            }
            for (int axis = 0; axis < 3; axis++) {
                b.putFloat(frameOffset + f * 56 + axis * 4, min[axis]);
                b.putFloat(frameOffset + f * 56 + 12 + axis * 4, max[axis]);
            }
        }
        Files.write(file.toPath(), bytes);
    }

    private static short packed(float value) throws IOException {
        if (!Float.isFinite(value) || value < -512 || value > 32767f / 64)
            throw new IOException("Vertex exceeds MD3 coordinate range");
        return (short) Math.round(value * 64);
    }

    private static String text(byte[] bytes, int offset, int count) {
        int end = offset;
        while (end < offset + count && bytes[end] != 0) end++;
        return new String(bytes, offset, end - offset, StandardCharsets.US_ASCII);
    }

    private static void local(int end, int offset, long count) throws IOException {
        if (offset < 108 || count < 0 || offset + count > end)
            throw new IOException("Surface data outside declared bounds");
    }

    private static void range(byte[] bytes, int offset, long count, int min) throws IOException {
        if (offset < min || count < 0 || offset + count > bytes.length)
            throw new IOException("MD3 data outside file bounds");
    }
}
