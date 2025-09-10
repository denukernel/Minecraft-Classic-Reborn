package net.classicremastered.minecraft.model;

import java.nio.*;

public class MD3Surface {
    public String name;
    public int numFrames, numVerts, numTris;
    public int ofsTris, ofsUV, ofsVerts, ofsEnd;
    public int size;

    public int[] tris;
    public float[] uvs;
    public short[] verts; // raw packed verts (x,y,z,normal) for frame 0 only here

    public static MD3Surface read(ByteBuffer buf) {
        int start = buf.position();

        byte[] id = new byte[4];
        buf.get(id);
        if (!new String(id).equals("IDP3"))
            throw new RuntimeException("Bad surface ID");

        byte[] nameBytes = new byte[64];
        buf.get(nameBytes);
        String surfName = new String(nameBytes).trim();

        buf.getInt(); // flags
        int numFrames = buf.getInt();
        int numShaders = buf.getInt();
        int numVerts = buf.getInt();
        int numTris = buf.getInt();

        int ofsTris = buf.getInt();
        int ofsShaders = buf.getInt();
        int ofsUV = buf.getInt();
        int ofsVerts = buf.getInt();
        int ofsEnd = buf.getInt();

        MD3Surface s = new MD3Surface();
        s.name = surfName;
        s.numFrames = numFrames;
        s.numVerts = numVerts;
        s.numTris = numTris;
        s.ofsTris = ofsTris;
        s.ofsUV = ofsUV;
        s.ofsVerts = ofsVerts;
        s.ofsEnd = ofsEnd;
        s.size = ofsEnd;

        // --- Triangles ---
        buf.position(start + ofsTris);
        s.tris = new int[numTris * 3];
        for (int i = 0; i < s.tris.length; i++) s.tris[i] = buf.getInt();

        // --- UVs ---
        buf.position(start + ofsUV);
        s.uvs = new float[numVerts * 2];
        for (int i = 0; i < numVerts; i++) {
            s.uvs[i * 2]     = buf.getFloat();
            s.uvs[i * 2 + 1] = buf.getFloat();
        }

        // --- Verts (frame 0 only for now) ---
        buf.position(start + ofsVerts);
        s.verts = new short[numVerts * 4];
        for (int i = 0; i < s.verts.length; i++) {
            s.verts[i] = buf.getShort();
        }

        buf.position(start + ofsEnd); // move to end of this surface
        return s;
    }
}
