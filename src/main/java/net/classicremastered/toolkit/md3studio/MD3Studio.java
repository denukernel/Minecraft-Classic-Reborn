package net.classicremastered.toolkit.md3studio;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Minimal, self‑contained Java Swing app for OBJ \u2194 MD3 with a simple 3D preview.
 *
 * Features:
 * - Import OBJ
 * - Import MD3 (single-frame, single-surface subset)
 * - Export OBJ
 * - Export MD3
 * - Load texture PNG and preview
 * - Export a UV template PNG with stable random colors (like TextureTemplateExporter)
 *
 * NOTE: MD3 support is a pragmatic subset aimed at static meshes:
 *  • 1 frame, N surfaces (merged on load)
 *  • no tags/animation
 *  • vertex positions scaled by 1/64 as per MD3
 *  • normals packed to lat/lng bytes
 *
 * You can split/refactor into separate files later; kept in one file for easy drop-in.
 */
public class MD3Studio {
    public static void main(String[] args) { SwingUtilities.invokeLater(MD3Studio::new); }

    // ===== Model structures =====
    static final class Vertex {
        float x, y, z;   // position
        float nx, ny, nz; // normal (unit)
        float u, v;      // UV [0..1]
    }

    static final class Triangle { int a, b, c; }

    static final class Mesh {
        java.util.List<Vertex> vertices = new ArrayList<>();
        java.util.List<Triangle> tris = new ArrayList<>();
        String materialName = ""; // used for OBJ/MD3 shader name
        // quick helpers
        int addVertex(Vertex v) { vertices.add(v); return vertices.size()-1; }
        void addTri(int a, int b, int c){ Triangle t=new Triangle(); t.a=a; t.b=b; t.c=c; tris.add(t);}    }

    static final class Model {
        Mesh mesh = new Mesh();
        BufferedImage texture; // optional diffuse atlas
        String texturePath;    // for convenience
        int atlasW() { return texture != null ? texture.getWidth() : 1024; }
        int atlasH() { return texture != null ? texture.getHeight(): 1024; }
    }

    // ===== GUI =====
    private final JFrame frame;
    private final ViewerPanel viewer;
    private Model model = new Model();
    private File lastDir = new File(".");

    public MD3Studio() {
        frame = new JFrame("MD3 Studio — OBJ↔MD3 + Texture Tools");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        viewer = new ViewerPanel();
        frame.add(viewer, BorderLayout.CENTER);
        frame.setJMenuBar(buildMenuBar());
        frame.add(buildStatusBar(), BorderLayout.SOUTH);
        frame.setSize(1024, 768);
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }

    private JMenuBar buildMenuBar() {
        JMenuBar mb = new JMenuBar();
        // File
        JMenu file = new JMenu("File");
        file.add(item("Import OBJ…", e -> importOBJ()));
        file.add(item("Import MD3…", e -> importMD3()));
        file.addSeparator();
        file.add(item("Export OBJ…", e -> exportOBJ()));
        file.add(item("Export MD3…", e -> exportMD3()));
        file.addSeparator();
        file.add(item("Load Texture PNG…", e -> loadTexture()));
        file.add(item("Export UV Template PNG…", e -> exportTemplate()));
        file.addSeparator();
        file.add(item("Quit", e -> frame.dispose()));
        mb.add(file);

        // View
        JMenu view = new JMenu("View");
        JCheckBoxMenuItem wire = new JCheckBoxMenuItem("Wireframe");
        wire.addActionListener(e -> { viewer.wireframe = wire.isSelected(); viewer.repaint(); });
        view.add(wire);
        JCheckBoxMenuItem textured = new JCheckBoxMenuItem("Textured", true);
        textured.addActionListener(e -> { viewer.showTexture = textured.isSelected(); viewer.repaint(); });
        view.add(textured);
        mb.add(view);

        // Tools
        JMenu tools = new JMenu("Tools");
        tools.add(item("Recalculate vertex normals", e -> { recalcNormals(model.mesh); viewer.repaint(); }));
        tools.add(item("Center + scale to unit box", e -> { normalizeMesh(model.mesh); viewer.repaint(); }));
        mb.add(tools);
        return mb;
    }

    private JComponent buildStatusBar() {
        JPanel p = new JPanel(new BorderLayout());
        JLabel hint = new JLabel("LMB: rotate · RMB: pan · Wheel: zoom · T: toggle texture · W: wireframe");
        hint.setBorder(BorderFactory.createEmptyBorder(4,8,4,8));
        p.add(hint, BorderLayout.WEST);
        return p;
    }

    private JMenuItem item(String name, ActionListener a) { JMenuItem i=new JMenuItem(name); i.addActionListener(a); return i; }

    // ===== File actions =====
    private void importOBJ() {
        JFileChooser fc = chooser("OBJ", "obj");
        if (fc.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        lastDir = fc.getCurrentDirectory();
        try {
            Model m = ObjIO.readOBJ(fc.getSelectedFile());
            this.model = m; viewer.setModel(m); frame.setTitle("MD3 Studio — " + fc.getSelectedFile().getName());
        } catch (Exception ex) { error(ex); }
    }

    private void importMD3() {
        JFileChooser fc = chooser("MD3", "md3");
        if (fc.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        lastDir = fc.getCurrentDirectory();
        try {
            Model m = Md3IO.readMD3(fc.getSelectedFile());
            // try load texture by shader name if present
            if (m.mesh.materialName != null && !m.mesh.materialName.isEmpty()) {
                File png = new File(lastDir, guessPngName(m.mesh.materialName));
                if (png.isFile()) { m.texture = ImageIO.read(png); m.texturePath = png.getAbsolutePath(); }
            }
            this.model = m; viewer.setModel(m); frame.setTitle("MD3 Studio — " + fc.getSelectedFile().getName());
        } catch (Exception ex) { error(ex); }
    }

    private void exportOBJ() {
        JFileChooser fc = chooser("OBJ", "obj");
        if (fc.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        lastDir = fc.getCurrentDirectory();
        try { ObjIO.writeOBJ(fc.getSelectedFile(), model); info("Saved OBJ."); }
        catch (Exception ex) { error(ex); }
    }

    private void exportMD3() {
        JFileChooser fc = chooser("MD3", "md3");
        if (fc.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        lastDir = fc.getCurrentDirectory();
        try { Md3IO.writeMD3(fc.getSelectedFile(), model); info("Saved MD3 (static, single frame)."); }
        catch (Exception ex) { error(ex); }
    }

    private void loadTexture() {
        JFileChooser fc = chooser("PNG", "png");
        if (fc.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        lastDir = fc.getCurrentDirectory();
        try {
            BufferedImage tex = ImageIO.read(fc.getSelectedFile());
            model.texture = tex; model.texturePath = fc.getSelectedFile().getAbsolutePath();
            viewer.repaint();
        } catch (Exception ex) { error(ex); }
    }

    private void exportTemplate() {
        JFileChooser fc = chooser("PNG", "png");
        if (fc.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        lastDir = fc.getCurrentDirectory();
        try {
            TextureTools.exportUVTemplatePNG(fc.getSelectedFile(), model, 255, 48, model.atlasW(), model.atlasH());
            info("Wrote template PNG.");
        } catch (Exception ex) { error(ex); }
    }

    private JFileChooser chooser(String desc, String ext) {
        JFileChooser fc = new JFileChooser(lastDir);
        fc.setFileFilter(new FileNameExtensionFilter(desc + " (*."+ext+")", ext));
        return fc;
    }

    private void info(String s){ JOptionPane.showMessageDialog(frame, s, "Info", JOptionPane.INFORMATION_MESSAGE); }
    private void error(Exception ex){ ex.printStackTrace(); JOptionPane.showMessageDialog(frame, ex.toString(), "Error", JOptionPane.ERROR_MESSAGE); }

    private static String guessPngName(String shaderName) {
        String s = shaderName;
        if (s.endsWith(".tga")) s = s.substring(0, s.length()-4)+".png";
        if (!s.toLowerCase(Locale.ROOT).endsWith(".png")) s += ".png";
        return new File(s).getName();
    }

    // ===== Viewer Panel =====
    final class ViewerPanel extends JPanel implements MouseListener, MouseMotionListener, MouseWheelListener, KeyListener {
        float yaw = 30, pitch = 20; // degrees
        float zoom = 2.2f;          // camera distance
        float panX = 0, panY = 0;
        boolean wireframe = false;
        boolean showTexture = true;
        Point last;

        ViewerPanel(){ setBackground(new Color(28,30,34));
            addMouseListener(this); addMouseMotionListener(this); addMouseWheelListener(this); addKeyListener(this); setFocusable(true); }

        void setModel(Model m){ MD3Studio.this.model = m; repaint(); }

        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int W = getWidth(), H = getHeight();

            // grid
            g.setColor(new Color(255,255,255,24));
            for (int i=-10;i<=10;i++) {
                int x = W/2 + (int)((i)*40);
                int y = H/2 + (int)((i)*40);
                g.drawLine(0, H/2, W, H/2);
                g.drawLine(W/2, 0, W/2, H);
                break; // keep it simple, just axes
            }

            if (model == null || model.mesh.vertices.isEmpty()) return;

            // Build transformed vertices
            float[] mat = MD3Studio.buildViewMatrix(yaw, pitch, zoom, panX, panY, W, H);
            java.util.List<int[]> proj = new ArrayList<>(model.mesh.vertices.size());
            java.util.List<float[]> nvs = new ArrayList<>(model.mesh.vertices.size());
            for (Vertex v : model.mesh.vertices) {
                float[] p = mul(mat, new float[]{v.x, v.y, v.z, 1});
                int sx = (int)p[0], sy = (int)p[1];
                proj.add(new int[]{sx,sy});
                nvs.add(new float[]{v.nx,v.ny,v.nz});
            }

            // simple painter's order by avg depth (not perfect but fine)
            float[] depths = new float[model.mesh.tris.size()];
            Integer[] order = new Integer[model.mesh.tris.size()];
            for (int i=0;i<order.length;i++){ order[i]=i; }
            for (int i=0;i<model.mesh.tris.size();i++){
                Triangle t = model.mesh.tris.get(i);
                float z = model.mesh.vertices.get(t.a).z + model.mesh.vertices.get(t.b).z + model.mesh.vertices.get(t.c).z;
                depths[i] = z/3f;
            }
            Arrays.sort(order, Comparator.comparingDouble(i -> depths[i])); // back to front

            // draw triangles
            for (int idx : order) {
                Triangle t = model.mesh.tris.get(idx);
                int[] A = proj.get(t.a), B = proj.get(t.b), C = proj.get(t.c);
                int[] xs = {A[0],B[0],C[0]};
                int[] ys = {A[1],B[1],C[1]};

                if (showTexture && model.texture != null) {
                    // Flat UV mapping via AffineTransform (approximate, not perspective-correct)
                    Vertex va = model.mesh.vertices.get(t.a);
                    Vertex vb = model.mesh.vertices.get(t.b);
                    Vertex vc = model.mesh.vertices.get(t.c);
                    float ua = va.u*model.atlasW(), va_ = va.v*model.atlasH();
                    float ub = vb.u*model.atlasW(), vb_ = vb.v*model.atlasH();
                    float uc = vc.u*model.atlasW(), vc_ = vc.v*model.atlasH();

                    Polygon poly = new Polygon(xs, ys, 3);
                    TexturePaint tp = texturePaintForTriangle(model.texture, ua,va_, ub,vb_, uc,vc_, xs,ys);
                    if (tp != null) {
                        Paint old = g.getPaint();
                        g.setPaint(tp);
                        g.fill(poly);
                        g.setPaint(old);
                    } else {
                        g.setColor(new Color(160,160,200));
                        g.fillPolygon(poly);
                    }
                } else {
                    // flat shade
                    float[] n = triNormal(model.mesh.vertices.get(t.a), model.mesh.vertices.get(t.b), model.mesh.vertices.get(t.c));
                    float shade = Math.max(0.15f, dot(n, new float[]{0.3f,0.6f,0.7f}));
                    int c = (int)(shade*200);
                    g.setColor(new Color(c,c,c));
                    g.fillPolygon(xs, ys, 3);
                }

                if (wireframe) {
                    g.setColor(new Color(0,0,0,160));
                    g.drawPolygon(xs, ys, 3);
                }
            }
        }

        // crude affine texture mapping for triangle via 3-point mapping to unit space
        private TexturePaint texturePaintForTriangle(BufferedImage tex,
                                                     float ua, float va, float ub, float vb, float uc, float vc,
                                                     int[] xs, int[] ys) {
            // Map texture triangle bbox to screen triangle via AffineTransform
            int minx = Math.min(xs[0], Math.min(xs[1], xs[2]));
            int miny = Math.min(ys[0], Math.min(ys[1], ys[2]));
            int maxx = Math.max(xs[0], Math.max(xs[1], xs[2]));
            int maxy = Math.max(ys[0], Math.max(ys[1], ys[2]));
            if (maxx<=minx || maxy<=miny) return null;
            BufferedImage sub = tex;
            // Use the whole texture; AffineTransform maps it accordingly.
            AffineTransform at = new AffineTransform();
            // Build affine from texture tri to screen tri using barycentric affine.
            // Solve linear system for affine, but here we approximate by mapping (0,0),(1,0),(0,1)
            // This is not exact for arbitrary UVs. For simplicity return a simple TexturePaint with bbox.
            return new TexturePaint(sub, new Rectangle(minx, miny, Math.max(1, maxx-minx), Math.max(1, maxy-miny)));
        }

        private float[] buildViewMatrix(){ return MD3Studio.buildViewMatrix(yaw,pitch,zoom,panX,panY,getWidth(),getHeight()); }

        // Interaction
        @Override public void mousePressed(MouseEvent e){ last = e.getPoint(); requestFocusInWindow(); }
        @Override public void mouseReleased(MouseEvent e){}
        @Override public void mouseDragged(MouseEvent e){ if (last==null) { last=e.getPoint(); return; }
            int dx=e.getX()-last.x, dy=e.getY()-last.y;
            if (SwingUtilities.isLeftMouseButton(e)) { yaw += dx*0.5f; pitch += dy*0.5f; pitch = Math.max(-89, Math.min(89, pitch)); }
            else if (SwingUtilities.isRightMouseButton(e)) { panX += dx*0.01f; panY += dy*0.01f; }
            last=e.getPoint(); repaint(); }
        @Override public void mouseWheelMoved(MouseWheelEvent e){ zoom *= Math.pow(1.1, e.getPreciseWheelRotation()); zoom = Math.max(0.2f, Math.min(10f, zoom)); repaint(); }
        @Override public void mouseMoved(MouseEvent e){}
        @Override public void mouseClicked(MouseEvent e){}
        @Override public void mouseEntered(MouseEvent e){}
        @Override public void mouseExited(MouseEvent e){}
        @Override public void keyTyped(KeyEvent e){}
        @Override public void keyPressed(KeyEvent e){ if (e.getKeyChar()=='t' || e.getKeyChar()=='T'){ showTexture=!showTexture; repaint(); }
            if (e.getKeyChar()=='w' || e.getKeyChar()=='W'){ wireframe=!wireframe; repaint(); } }
        @Override public void keyReleased(KeyEvent e){}
    }

    // ===== Math/transform helpers =====
    static float[] mul(float[] m, float[] v){
        return new float[]{
                m[0]*v[0]+m[4]*v[1]+m[8]*v[2]+m[12]*v[3],
                m[1]*v[0]+m[5]*v[1]+m[9]*v[2]+m[13]*v[3],
                m[2]*v[0]+m[6]*v[1]+m[10]*v[2]+m[14]*v[3],
                m[3]*v[0]+m[7]*v[1]+m[11]*v[2]+m[15]*v[3]
        };
    }

    static float[] buildViewMatrix(float yaw, float pitch, float zoom, float panX, float panY, int W, int H){
        double ry = Math.toRadians(yaw), rp = Math.toRadians(pitch);
        float sry=(float)Math.sin(ry), cry=(float)Math.cos(ry);
        float srp=(float)Math.sin(rp), crp=(float)Math.cos(rp);
        float scale = Math.min(W,H)/2f * (1f/zoom);
        // Compose rotation * scale * translate to screen
        float[] m = new float[16]; Arrays.fill(m,0); m[15]=1;
        // rotation Yaw (around Y) and Pitch (around X) applied to model space
        // We'll bake into a 2D projection (x,z) -> screen, y -> screen y
        // For simplicity, project to screen without perspective.
        // Build a 3x3 rotation then scale and translate.
        float r00 =  cry; float r01 = 0; float r02 = sry;
        float r10 =  srp*sry; float r11 = crp; float r12 = -srp*cry;
        float r20 = -crp*sry; float r21 = srp; float r22 =  crp*cry;
        m[0]= r00*scale; m[4]= r01*scale; m[8] = r02*scale; m[12]= W/2f + panX*scale;
        m[1]= r10*scale; m[5]= r11*scale; m[9] = r12*scale; m[13]= H/2f + panY*scale;
        m[2]= r20;       m[6]= r21;       m[10]= r22;       m[14]= 0;
        m[3]=0; m[7]=0; m[11]=0; m[15]=1;
        return m;
    }

    static float[] triNormal(Vertex a, Vertex b, Vertex c){
        float ux=b.x-a.x, uy=b.y-a.y, uz=b.z-a.z;
        float vx=c.x-a.x, vy=c.y-a.y, vz=c.z-a.z;
        float nx = uy*vz - uz*vy;
        float ny = uz*vx - ux*vz;
        float nz = ux*vy - uy*vx;
        float len = (float)Math.sqrt(nx*nx+ny*ny+nz*nz); if (len<1e-6f) return new float[]{0,0,1};
        return new float[]{nx/len, ny/len, nz/len};
    }

    static float dot(float[] a, float[] b){ return a[0]*b[0]+a[1]*b[1]+a[2]*b[2]; }

    // ===== Mesh utilities =====
    static void normalizeMesh(Mesh m){
        if (m.vertices.isEmpty()) return;
        float minx=Float.MAX_VALUE,miny=Float.MAX_VALUE,minz=Float.MAX_VALUE;
        float maxx=-minx,maxy=-miny,maxz=-minz;
        for (Vertex v: m.vertices){ minx=Math.min(minx,v.x); miny=Math.min(miny,v.y); minz=Math.min(minz,v.z);
            maxx=Math.max(maxx,v.x); maxy=Math.max(maxy,v.y); maxz=Math.max(maxz,v.z);}        
        float cx=(minx+maxx)/2f, cy=(miny+maxy)/2f, cz=(minz+maxz)/2f;
        float sx=maxx-minx, sy=maxy-miny, sz=maxz-minz; float s=Math.max(sx, Math.max(sy, sz)); if (s<1e-6f) s=1; s=1f/s;
        for (Vertex v: m.vertices){ v.x=(v.x-cx)*s; v.y=(v.y-cy)*s; v.z=(v.z-cz)*s; }
    }

    static void recalcNormals(Mesh m){
        for (Vertex v: m.vertices){ v.nx=0;v.ny=0;v.nz=0; }
        for (Triangle t: m.tris){ Vertex a=m.vertices.get(t.a), b=m.vertices.get(t.b), c=m.vertices.get(t.c);
            float[] n = triNormal(a,b,c);
            a.nx+=n[0]; a.ny+=n[1]; a.nz+=n[2];
            b.nx+=n[0]; b.ny+=n[1]; b.nz+=n[2];
            c.nx+=n[0]; c.ny+=n[1]; c.nz+=n[2];
        }
        for (Vertex v: m.vertices){ float l=(float)Math.sqrt(v.nx*v.nx+v.ny*v.ny+v.nz*v.nz); if(l<1e-6f){v.nx=0;v.ny=0;v.nz=1;} else { v.nx/=l; v.ny/=l; v.nz/=l; } }
    }

    // ===== OBJ IO =====
    static final class ObjIO {
        static Model readOBJ(File f) throws Exception {
            java.util.List<float[]> vs = new ArrayList<>();
            java.util.List<float[]> vts = new ArrayList<>();
            java.util.List<float[]> vns = new ArrayList<>();
            Map<String,Integer> map = new HashMap<>();
            Mesh mesh = new Mesh();
            String mtllib = null; String usemtl = "";
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                String line; while ((line = br.readLine()) != null) {
                    line=line.trim(); if (line.isEmpty()||line.startsWith("#")) continue;
                    String[] tok = line.split("\\s+");
                    switch (tok[0]){
                        case "v": vs.add(new float[]{Float.parseFloat(tok[1]), Float.parseFloat(tok[2]), Float.parseFloat(tok[3])}); break;
                        case "vt": vts.add(new float[]{Float.parseFloat(tok[1]), 1f-(tok.length>2?Float.parseFloat(tok[2]):0f)}); break; // flip V
                        case "vn": vns.add(new float[]{Float.parseFloat(tok[1]), Float.parseFloat(tok[2]), Float.parseFloat(tok[3])}); break;
                        case "f": {
                            int[] idx = new int[tok.length-1];
                            for (int i=1;i<tok.length;i++) idx[i-1] = indexFor(tok[i], vs, vts, vns, map, mesh);
                            for (int i=1;i+1<idx.length;i++) mesh.addTri(idx[0], idx[i], idx[i+1]);
                        } break;
                        case "mtllib": mtllib = tok.length>1?tok[1]:null; break;
                        case "usemtl": usemtl = tok.length>1?tok[1]:""; break;
                    }
                }
            }
            if (usemtl!=null) mesh.materialName = usemtl;
            if (mesh.vertices.isEmpty()) throw new IOException("OBJ has no geometry");
            Model m = new Model(); m.mesh = mesh; if (m.mesh.vertices.stream().noneMatch(v -> v.nx!=0||v.ny!=0||v.nz!=0)) recalcNormals(mesh);
            return m;
        }
        private static int indexFor(String token, java.util.List<float[]> vs, java.util.List<float[]> vts, java.util.List<float[]> vns,
                                    Map<String,Integer> map, Mesh mesh){
            Integer idx = map.get(token); if (idx!=null) return idx;
            String[] sp = token.split("/"); int vi = parseIndex(sp[0], vs.size());
            float[] v = vs.get(vi);
            float[] vt = (sp.length>1 && !sp[1].isEmpty()) ? vts.get(parseIndex(sp[1], vts.size())) : new float[]{0,0};
            float[] vn = (sp.length>2 && !sp[2].isEmpty()) ? vns.get(parseIndex(sp[2], vns.size())) : new float[]{0,0,1};
            Vertex out = new Vertex(); out.x=v[0]; out.y=v[1]; out.z=v[2]; out.u=vt[0]; out.v=vt[1]; out.nx=vn[0]; out.ny=vn[1]; out.nz=vn[2];
            int newIdx = mesh.addVertex(out); map.put(token, newIdx); return newIdx;
        }
        private static int parseIndex(String s, int size){ int i=Integer.parseInt(s); return (i<0? size+i : i-1); }

        static void writeOBJ(File f, Model m) throws Exception {
            try (PrintWriter pw = new PrintWriter(new FileWriter(f))) {
                pw.println("# MD3 Studio export");
                if (m.mesh.materialName!=null && !m.mesh.materialName.isEmpty()) pw.println("usemtl "+m.mesh.materialName);
                for (Vertex v: m.mesh.vertices) pw.printf(Locale.ROOT, "v %f %f %f\n", v.x, v.y, v.z);
                for (Vertex v: m.mesh.vertices) pw.printf(Locale.ROOT, "vt %f %f\n", v.u, 1f - v.v);
                for (Vertex v: m.mesh.vertices) pw.printf(Locale.ROOT, "vn %f %f %f\n", v.nx, v.ny, v.nz);
                for (Triangle t: m.mesh.tris) pw.printf("f %d/%d/%d %d/%d/%d %d/%d/%d\n",
                        t.a+1,t.a+1,t.a+1, t.b+1,t.b+1,t.b+1, t.c+1,t.c+1,t.c+1);
            }
        }
    }

    // ===== MD3 IO (subset) =====
    static final class Md3IO {
        static final int MD3_IDENT = 0x33504449; // "IDP3"
        static final int MD3_VERSION = 15;

        static Model readMD3(File file) throws Exception {
            byte[] data = readAll(file);
            ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            int ident = bb.getInt(); int version = bb.getInt();
            if (ident != MD3_IDENT || version != MD3_VERSION) throw new IOException("Not an MD3 v15 file");
            String name = readZ(bb,64);
            bb.getInt(); // flags
            int numFrames = bb.getInt();
            int numTags = bb.getInt();
            int numSurfaces = bb.getInt();
            bb.getInt(); // numSkins
            int ofsFrames = bb.getInt();
            int ofsTags = bb.getInt();
            int ofsSurfaces = bb.getInt();
            int ofsEnd = bb.getInt();

            Mesh merged = new Mesh();
            for (int s=0;s<numSurfaces;s++) {
                int surfOfs = ofsSurfaces;
                for (int i=0;i<s;i++) surfOfs = nextSurfaceOffset(bb, surfOfs);
                parseSurface(bb, surfOfs, merged);
            }
            Model m = new Model(); m.mesh = merged; recalcNormals(m.mesh); return m;
        }

        private static int nextSurfaceOffset(ByteBuffer bb, int surfOfs){
            int pos = bb.position(); bb.position(surfOfs);
            bb.getInt(); // ident
            bb.position(bb.position()+64+4); // name+flags
            int numFrames=bb.getInt(); int numShaders=bb.getInt(); int numVerts=bb.getInt(); int numTris=bb.getInt();
            int ofsTris=bb.getInt(); int ofsShaders=bb.getInt(); int ofsSt=bb.getInt(); int ofsXyz=bb.getInt(); int ofsEnd=bb.getInt();
            int next = surfOfs + ofsEnd; bb.position(pos); return next;
        }

        private static void parseSurface(ByteBuffer bb, int surfOfs, Mesh out){
            int pos = bb.position(); bb.position(surfOfs);
            int ident = bb.getInt(); if (ident!=MD3_IDENT) throw new RuntimeException("Surface ident mismatch");
            String surfName = readZ(bb,64);
            bb.getInt(); // flags
            int numFrames=bb.getInt(); int numShaders=bb.getInt(); int numVerts=bb.getInt(); int numTris=bb.getInt();
            int ofsTris=bb.getInt(); int ofsShaders=bb.getInt(); int ofsSt=bb.getInt(); int ofsXyz=bb.getInt(); int ofsEnd=bb.getInt();
            int base = surfOfs;

            // Shaders (texture names)
            String shaderName = "";
            if (numShaders>0){ int sp = base+ofsShaders; bb.position(sp); shaderName = readZ(bb,64); }
            if (shaderName!=null) out.materialName = shaderName;

            // UVs
            float[][] st = new float[numVerts][2];
            bb.position(base+ofsSt);
            for (int i=0;i<numVerts;i++){ st[i][0]=bb.getFloat(); st[i][1]=bb.getFloat(); }

            // Vertices of frame 0
            float[][] xyz = new float[numVerts][3]; float[][] nrm = new float[numVerts][3];
            bb.position(base+ofsXyz);
            for (int i=0;i<numVerts;i++){
                short x=bb.getShort(), y=bb.getShort(), z=bb.getShort();
                int normal = bb.getShort() & 0xFFFF; // lat/lng packed
                float fx=x/64f, fy=y/64f, fz=z/64f;
                xyz[i][0]=fx; xyz[i][1]=fy; xyz[i][2]=fz;
                float[] n = decodeMd3Normal(normal); nrm[i]=n;
            }

            int baseIndex = out.vertices.size();
            for (int i=0;i<numVerts;i++){ Vertex v=new Vertex(); v.x=xyz[i][0]; v.y=xyz[i][1]; v.z=xyz[i][2]; v.u=st[i][0]; v.v=st[i][1]; v.nx=nrm[i][0]; v.ny=nrm[i][1]; v.nz=nrm[i][2]; out.addVertex(v); }

            // Triangles
            bb.position(base+ofsTris);
            for (int i=0;i<numTris;i++){ int a=bb.getInt(), b=bb.getInt(), c=bb.getInt(); out.addTri(baseIndex+a, baseIndex+b, baseIndex+c); }

            bb.position(pos);
        }

        static void writeMD3(File file, Model model) throws Exception {
            // MD3 requires unified vertex/uv arrays. Our mesh already uses unified vertices.
            Mesh m = model.mesh; if (m.vertices.isEmpty() || m.tris.isEmpty()) throw new IOException("No mesh to export");

            // Build surface (single surface)
            final String surfName = (m.materialName!=null && !m.materialName.isEmpty()) ? m.materialName : "mesh";
            final int numFrames = 1, numVerts = m.vertices.size(), numTris = m.tris.size(), numShaders = 1;

            // Precompute buffers sizes
            int headerSize = 108;
            int ofsFrames = headerSize; int framesSize = 56 * numFrames; // md3Frame_t 56 bytes each
            int ofsTags = ofsFrames + framesSize; int tagsSize = 0; // none
            int ofsSurfaces = ofsTags + tagsSize;

            // surface header is 108 bytes
            int surfHeader = 108;
            int trisSize = numTris * 12;
            int shadersSize = numShaders * 72; // name[64] + int
            int stSize = numVerts * 8;
            int xyzSize = numFrames * numVerts * 8; // short x,y,z + short normal
            int surfSize = surfHeader + trisSize + shadersSize + stSize + xyzSize;
            int ofsEnd = ofsSurfaces + surfSize;

            ByteBuffer bb = ByteBuffer.allocate(ofsEnd).order(ByteOrder.LITTLE_ENDIAN);
            // header
            bb.putInt(MD3_IDENT); bb.putInt(MD3_VERSION); putZ(bb, new File(file.getName()).getName(), 64);
            bb.putInt(0); // flags
            bb.putInt(numFrames); bb.putInt(0); bb.putInt(1); bb.putInt(numShaders);
            bb.putInt(ofsFrames); bb.putInt(ofsTags); bb.putInt(ofsSurfaces); bb.putInt(ofsEnd);

            // frame 0 (bounds etc.)
            float[] bounds = bounds(m);
            bb.position(ofsFrames);
            // mins, maxs, origin(3), radius, name[16]
            bb.putFloat(bounds[0]); bb.putFloat(bounds[1]); bb.putFloat(bounds[2]);
            bb.putFloat(bounds[3]); bb.putFloat(bounds[4]); bb.putFloat(bounds[5]);
            bb.putFloat(0); bb.putFloat(0); bb.putFloat(0); // origin
            bb.putFloat(Math.max(Math.max(bounds[3]-bounds[0], bounds[4]-bounds[1]), bounds[5]-bounds[2]) / 2f); // radius approx
            putZ(bb, "frame0", 16);

            // surface
            int surfOfs = ofsSurfaces; bb.position(surfOfs);
            bb.putInt(MD3_IDENT); putZ(bb, surfName, 64); bb.putInt(0);
            bb.putInt(numFrames); bb.putInt(numShaders); bb.putInt(numVerts); bb.putInt(numTris);
            int localOfsTris = 108;
            int localOfsShaders = localOfsTris + trisSize;
            int localOfsSt = localOfsShaders + shadersSize;
            int localOfsXyz = localOfsSt + stSize;
            int localOfsEnd = localOfsXyz + xyzSize;
            bb.putInt(localOfsTris); bb.putInt(localOfsShaders); bb.putInt(localOfsSt); bb.putInt(localOfsXyz); bb.putInt(localOfsEnd);

            // triangles (indices)
            bb.position(surfOfs + localOfsTris);
            for (Triangle t: m.tris){ bb.putInt(t.a); bb.putInt(t.b); bb.putInt(t.c); }

            // shader
            bb.position(surfOfs + localOfsShaders);
            putZ(bb, surfName, 64); bb.putInt(0);

            // st
            bb.position(surfOfs + localOfsSt);
            for (Vertex v: m.vertices){ bb.putFloat(v.u); bb.putFloat(v.v); }

            // xyz + normal (frame 0)
            bb.position(surfOfs + localOfsXyz);
            for (Vertex v: m.vertices){
                short x=(short)Math.round(v.x*64f); short y=(short)Math.round(v.y*64f); short z=(short)Math.round(v.z*64f);
                int n = encodeMd3Normal(v.nx, v.ny, v.nz);
                bb.putShort(x); bb.putShort(y); bb.putShort(z); bb.putShort((short)(n & 0xFFFF));
            }

            // write file
            try (FileOutputStream fos = new FileOutputStream(file)){ fos.write(bb.array()); }
        }

        private static float[] bounds(Mesh m){
            float minx=Float.MAX_VALUE,miny=Float.MAX_VALUE,minz=Float.MAX_VALUE;
            float maxx=-minx,maxy=-miny,maxz=-minz;
            for (Vertex v: m.vertices){ minx=Math.min(minx,v.x); miny=Math.min(miny,v.y); minz=Math.min(minz,v.z);
                maxx=Math.max(maxx,v.x); maxy=Math.max(maxy,v.y); maxz=Math.max(maxz,v.z);}        
            return new float[]{minx,miny,minz,maxx,maxy,maxz};
        }

        private static int encodeMd3Normal(float nx, float ny, float nz){
            // Convert normal to lat/lng bytes (0..255) as in MD3
            double lat = Math.acos(Math.max(-1, Math.min(1, nz))); // 0..pi
            double lng = Math.atan2(ny, nx); // -pi..pi
            int lat8 = (int)Math.round(lat * 255.0 / Math.PI);
            int lng8 = (int)Math.round((lng + Math.PI) * 255.0 / (2*Math.PI));
            lat8 = (lat8 & 0xFF); lng8 = (lng8 & 0xFF);
            return (lng8<<8) | lat8; // NOTE: many docs use lat in low byte, lng in high
        }

        private static float[] decodeMd3Normal(int packed){
            int lat8 = (packed & 0xFF); int lng8 = (packed >> 8) & 0xFF;
            double lat = lat8 * Math.PI / 255.0; double lng = (lng8 * 2*Math.PI / 255.0) - Math.PI;
            float nx = (float)(Math.cos(lng) * Math.sin(lat));
            float ny = (float)(Math.sin(lng) * Math.sin(lat));
            float nz = (float)(Math.cos(lat));
            return new float[]{nx,ny,nz};
        }

        private static String readZ(ByteBuffer bb, int max){
            byte[] b = new byte[max]; bb.get(b);
            int n=0; while (n<b.length && b[n]!=0) n++;
            return new String(b, 0, n, StandardCharsets.US_ASCII);
        }
        private static void putZ(ByteBuffer bb, String s, int size){
            byte[] b = s.getBytes(StandardCharsets.US_ASCII); int n=Math.min(b.length, size-1);
            bb.put(b,0,n); for (int i=n;i<size;i++) bb.put((byte)0);
        }
        private static byte[] readAll(File f) throws IOException { try (FileInputStream in = new FileInputStream(f)){ return in.readAllBytes(); } }
    }

    // ===== Texture tools (UV template like the user-provided exporter) =====
    static final class TextureTools {
        /** Exports a UV template PNG:
         *  - off-model pixels stay fully transparent
         *  - optional semi-transparent checker background
         *  - triangles are filled with stable random colors (no outlines)
         */
        public static void exportUVTemplatePNG(File out, Model m, int faceAlpha, int checkerAlpha, int W, int H) throws Exception {
            if (W<=0) W=1024; if (H<=0) H=1024;
            BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setComposite(AlphaComposite.Src);
            g.setColor(new Color(0,0,0,0)); g.fillRect(0,0,W,H);
            if (checkerAlpha>0) drawChecker(g, W, H, checkerAlpha);

            // Draw each triangle in UV space
            Random stable = new Random(0xC0FFEE42L);
            for (int i=0;i<m.mesh.tris.size();i++){
                Triangle t = m.mesh.tris.get(i);
                Vertex a=m.mesh.vertices.get(t.a), b=m.mesh.vertices.get(t.b), c=m.mesh.vertices.get(t.c);
                int[] xs = {(int)Math.round(a.u*W), (int)Math.round(b.u*W), (int)Math.round(c.u*W)};
                int[] ys = {(int)Math.round(a.v*H), (int)Math.round(b.v*H), (int)Math.round(c.v*H)};
                // Stable color per tri using name+indices
                long seed = (((long)t.a)<<42) ^ (((long)t.b)<<21) ^ (t.c) ^ 0x9E3779B97F4A7C15L;
                Random rnd = new Random(seed);
                float hue = rnd.nextFloat();
                float sat = 0.55f + rnd.nextFloat()*0.35f;
                float val = 0.85f + rnd.nextFloat()*0.15f;
                int rgb = Color.HSBtoRGB(hue, sat, val);
                Color fill = new Color((rgb>>16)&0xFF, (rgb>>8)&0xFF, rgb&0xFF, clamp(faceAlpha));
                g.setComposite(AlphaComposite.SrcOver);
                g.setColor(fill);
                g.fillPolygon(xs, ys, 3);
            }
            g.dispose();
            ImageIO.write(img, "png", out);
        }

        private static int clamp(int a){ return Math.max(0, Math.min(255, a)); }
        private static void drawChecker(Graphics2D g, int W, int H, int alpha){
            g.setComposite(AlphaComposite.SrcOver);
            final int cell = 16;
            Color A = new Color(43,47,54, alpha);
            Color B = new Color(36,41,50, alpha);
            for (int y=0;y<H;y+=cell) for (int x=0;x<W;x+=cell){ g.setColor((((x^y)&cell)==0)?A:B); g.fillRect(x,y, Math.min(cell,W-x), Math.min(cell,H-y)); }
        }
    }
}
