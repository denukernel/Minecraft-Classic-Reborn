package net.classicremastered.toolkit.mapedit3d;

import net.classicremastered.minecraft.MovingObjectPosition;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.LevelIO;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.util.MathHelper;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;

/**
 * Classic3DMapEditorApp — 3D view on the left, “classic” sidebar on the right.
 *
 * Major additions:
 *  • Right sidebar with “Block Types” grid (selects block id)
 *  • Tabs: Brush • View • Selection • Load/Save
 *  • Brush size + 2D (disc) / 3D (cube) paint, continuous right-drag place
 *  • Plane (XZ/XY/YZ) + layer spinner for top-down workflows
 *  • Export Top-Down PNG (simple height render)
 */
public final class Classic3DMapEditorApp {

    // ---------- Paths ----------
    private static File appDir() {
        String appdata = System.getenv("APPDATA");
        if (appdata == null || appdata.isEmpty()) appdata = System.getProperty("user.home");
        File dir = new File(appdata, ".mcraft/client");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }
    private static File creativeFile() { return new File(appDir(), "levelc.dat"); }
    private static File survivalFile() { return new File(appDir(), "levels.dat"); }

    // ---------- State ----------
    private Level levelCreative;
    private Level levelSurvival;
    private Level active;
    private boolean editingCreative = true;

    // selection / painting
    private int selectedBlockId = 1;     // stone
    private int skyboxColor = 0x202531;

    // camera
    private float camX = 16, camY = 40, camZ = 16;
    private float yaw = 180, pitch = -25;
    private float fov = 70f;

    // render tuning
    private int drawRadiusXZ = 64;
    private int drawRadiusY  = 48;

    // UI
    private Frame frame;
    private Canvas canvas;
    private JPanel rightPanel;
    private JLabel lblActive, lblSelected, lblPos;

    // Brush UI state
    private int brushSize = 1;         // radius (1 = single cell)
    private boolean brush3D = false;   // false = 2D disc in plane, true = 3D cube
    private enum Plane { XZ, XY, YZ }
    private Plane plane = Plane.XZ;
    private int layerIndex = 16;       // used by XY/YZ views & 2D brush
    private boolean topDownSnap = false;

    // mouse down edge tracking
    private boolean leftPrev = false;
    private boolean rightPrev = false;

    // I/O helper
    private final LevelIO io = new LevelIO(null);

    public static void main(String[] args) throws Exception {
        configureNativesFromClient();
        new Classic3DMapEditorApp().run();
    }

    private static void configureNativesFromClient() {
        String appdata = System.getenv("APPDATA");
        if (appdata == null || appdata.isEmpty()) appdata = System.getProperty("user.home");
        File dir = new File(appdata, ".mcraft\\client\\native//windows");
        String path = dir.getAbsolutePath();
        System.setProperty("org.lwjgl.librarypath", path);
        System.setProperty("net.java.games.input.librarypath", path);
        try {
            String jlp = System.getProperty("java.library.path", "");
            if (!jlp.contains(path)) {
                System.setProperty("java.library.path", jlp + File.pathSeparator + path);
                java.lang.reflect.Field sysPaths = ClassLoader.class.getDeclaredField("sys_paths");
                sysPaths.setAccessible(true);
                sysPaths.set(null, null);
            }
        } catch (Throwable ignored) {}
        System.out.println("[natives] org.lwjgl.librarypath=" + System.getProperty("org.lwjgl.librarypath"));
    }

    // --- tiny HUD text helper (unchanged) ---
    private static final class GLText {
        private int texId = 0; private int w = 0, h = 0; private String cached = null;
        void draw(int x, int y, String text) {
            if (text == null) return;
            if (!text.equals(cached)) {
                cached = text;
                java.awt.Font font = new java.awt.Font("Dialog", java.awt.Font.PLAIN, 14);
                BufferedImage tmp = new BufferedImage(1,1, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = tmp.createGraphics();
                g2.setFont(font);
                int tw = Math.max(1, g2.getFontMetrics().stringWidth(text) + 8);
                int th = Math.max(1, g2.getFontMetrics().getHeight() + 6);
                g2.dispose();

                BufferedImage img = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
                g2 = img.createGraphics();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(new Color(0,0,0,120));
                g2.fillRect(0,0,tw,th);
                g2.setColor(Color.WHITE);
                int baseY = (th - g2.getFontMetrics().getHeight())/2 + g2.getFontMetrics().getAscent();
                g2.drawString(text, 6, baseY);
                g2.dispose();

                int[] argb = img.getRGB(0,0,tw,th,null,0,tw);
                java.nio.ByteBuffer buf = org.lwjgl.BufferUtils.createByteBuffer(tw*th*4);
                for (int p : argb) {
                    buf.put((byte)((p>>16)&255)); buf.put((byte)((p>>8)&255));
                    buf.put((byte)((p)&255));     buf.put((byte)((p>>24)&255));
                }
                buf.flip();
                if (texId == 0) texId = GL11.glGenTextures();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, tw, th, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
                w = tw; h = th;
            }
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
            GL11.glColor4f(1,1,1,1);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0,0); GL11.glVertex2f(x,     y);
            GL11.glTexCoord2f(1,0); GL11.glVertex2f(x + w, y);
            GL11.glTexCoord2f(1,1); GL11.glVertex2f(x + w, y + h);
            GL11.glTexCoord2f(0,1); GL11.glVertex2f(x,     y + h);
            GL11.glEnd();
            GL11.glDisable(GL11.GL_TEXTURE_2D);
        }
        void dispose(){ if (texId != 0){ GL11.glDeleteTextures(texId); texId = 0; } }
    }

    private void run() throws Exception {
        setupAwtWindow();
        createDisplayOn(canvas);
        reloadBoth();
        loop();
        Display.destroy();
        frame.dispose();
        System.exit(0);
    }
    private final class Map2DPanel extends JPanel {
        Map2DPanel() { setPreferredSize(new Dimension(980, 800));
            // mouse paint
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mousePressed(java.awt.event.MouseEvent e) { paintAt(e); }
            });
            addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override public void mouseDragged(java.awt.event.MouseEvent e) { paintAt(e); }
            });
        }

        void relayout() { revalidate(); repaint(); }

        private void paintAt(java.awt.event.MouseEvent e) {
            if (active == null) return;
            Point p = e.getPoint();
            int bx, by, bz;  // target cell from mouse (plane-aware)
            int cell = viewZoom;

            switch (viewPlane) {
                case XY: {
                    bx = p.x / cell; by = p.y / cell; bz = viewLayer;
                    break;
                }
                case XZ: {
                    bx = p.x / cell; by = viewLayer;  bz = active.depth - 1 - (p.y / cell);
                    break;
                }
                default: { // YZ
                    bx = viewLayer; by = p.y / cell;  bz = active.depth - 1 - (p.x / cell);
                }
            }
            if (!active.isInBounds(bx, by, bz)) return;

            boolean place = SwingUtilities.isLeftMouseButton(e);
            int id = place ? selectedBlockId : 0;
            // 2D/3D + shape brush
            int r = Math.max(1, brushSize);
            if (mode2D) {
                for (int dy=-r+1; dy<=r-1; dy++)
                    for (int dx=-r+1; dx<=r-1; dx++) {
                        int x=bx, y=by, z=bz;
                        if (viewPlane==ViewPlane.XY) { x=bx+dx; y=by+dy; }
                        if (viewPlane==ViewPlane.XZ) { x=bx+dx; z=bz+dy; }
                        if (viewPlane==ViewPlane.YZ) { y=by+dy; z=bz+dx; }
                        if (!active.isInBounds(x,y,z)) continue;
                        if (brushRound && (dx*dx + dy*dy) > (r-1)*(r-1)) continue;
                        active.setTile(x,y,z,id);
                    }
            } else {
                // 3D cube centered on (bx,by,bz)
                for (int z=bz-r+1; z<=bz+r-1; z++)
                    for (int y=by-r+1; y<=by+r-1; y++)
                        for (int x=bx-r+1; x<=bx+r-1; x++)
                            if (active.isInBounds(x,y,z)) active.setTile(x,y,z,id);
            }
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (active == null) { g.drawString("No level loaded.", 10, 20); return; }

            int cell = viewZoom;
            int W = (viewPlane==ViewPlane.YZ) ? active.depth : active.width;
            int H = (viewPlane==ViewPlane.XZ) ? active.depth : active.height;
            setPreferredSize(new Dimension(W*cell, H*cell));

            for (int y=0; y<H; y++) for (int x=0; x<W; x++) {
                int id, rx=x, ry=y, rz=viewLayer;
                switch (viewPlane) {
                    case XY: rx=x; ry=y; rz=viewLayer; break;
                    case XZ: rx=x; ry=viewLayer; rz=active.depth-1-y; break;
                    default: rx=viewLayer; ry=y; rz=active.depth-1-x; break;
                }
                id = active.isInBounds(rx,ry,rz) ? active.getTile(rx,ry,rz) : 0;

                int rgb = (id==0?0x202531:(0xFF000000|colorFor(id)));
                g.setColor(new Color(rgb, true));
                g.fillRect(x*cell, y*cell, cell, cell);
            }

            // overlay “viewing layer …” label like Omen
            g.setColor(new Color(0,0,0,160));
            g.fillRect(6,6, 680, 18);
            g.setColor(Color.WHITE);
            g.drawString(String.format("Viewing layer %s = %d in the %s plane.   Selected Tool: Brush - %s mode, %s, size %d",
                    (viewPlane==ViewPlane.XY?"Z":viewPlane==ViewPlane.XZ?"Y":"X"),
                    viewLayer,
                    viewPlane.name().replace('_','-'),
                    (mode2D?"2d":"3d"),
                    (brushSquare?"Square Brush":"Round Brush"),
                    brushSize),
                10, 20);
        }
    }

    // ---------- AWT + LWJGL bootstrap ----------
    private void setupAwtWindow() {
        frame = new Frame("Classic 3D Map Editor (Omen-ish)");
        frame.setLayout(new BorderLayout());

        // Top hint row
        frame.add(buildTopBar(), BorderLayout.NORTH);

        // ----- CENTER: 2D and 3D views in tabs -----
        map2D = new Map2DPanel();
        JPanel twoD = new JPanel(new BorderLayout());
        twoD.add(new JScrollPane(map2D), BorderLayout.CENTER);
        twoD.add(buildOmenBottomBar(), BorderLayout.SOUTH);

        canvas = new Canvas();
        canvas.setPreferredSize(new Dimension(980, 800));
        JPanel threeD = new JPanel(new BorderLayout());
        threeD.add(canvas, BorderLayout.CENTER);

        JTabbedPane views = new JTabbedPane();
        views.add("2D", twoD);
        views.add("3D", threeD);
        frame.add(views, BorderLayout.CENTER);

        // Right: Omen-like control stack
        rightPanel = buildOmenRightSidebar();   // <— use the Omen sidebar
        frame.add(rightPanel, BorderLayout.EAST);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                try { Display.destroy(); } catch (Throwable ignored) {}
                System.exit(0);
            }
        });
    }


    private Component buildTopBar() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        JLabel lb = new JLabel("Left: select; Delete: remove; Right: paint; Wheel: Block ID | Tab: toggle creative/survival | F5 save | F6 reload");
        row.add(lb);
        JButton pick = new JButton("Pick Block ID…");
        pick.addActionListener(e -> {
            String s = JOptionPane.showInputDialog(frame, "Block ID (0..255)", selectedBlockId);
            if (s != null) try {
                int v = Integer.parseInt(s.trim());
                if (v < 0 || v > 255) throw new NumberFormatException();
                setSelectedBlock(v);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(frame, "Invalid ID");
            }
        });
        row.add(pick);
        return row;
    }

    // ---------- Sidebar ----------
    private JPanel buildRightSidebar() {
        JPanel side = new JPanel(new BorderLayout());
        side.setPreferredSize(new Dimension(360, 800));

        // Header status
        JPanel status = new JPanel(new GridLayout(3,1));
        status.setBorder(new TitledBorder("Status"));
        lblActive   = new JLabel();
        lblSelected = new JLabel();
        lblPos      = new JLabel();
        status.add(lblActive);
        status.add(lblSelected);
        status.add(lblPos);
        side.add(status, BorderLayout.NORTH);
        updateStatusLabels();

        // Middle: block palette + tabs
        JPanel mid = new JPanel(new BorderLayout(0,8));

        // Block palette grid
        JPanel palette = makePalettePanel();
        palette.setBorder(new TitledBorder("Block Types"));
        mid.add(new JScrollPane(palette,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED),
                BorderLayout.NORTH);

        // Tabs: Brush / View / Selection / LoadSave
        JTabbedPane tabs = new JTabbedPane();
        tabs.add("Brush", makeBrushTab());
        tabs.add("View",  makeViewTab());
        tabs.add("Selection", makeSelectionTab());
        tabs.add("Load/Save", makeIoTab());
        mid.add(tabs, BorderLayout.CENTER);

        side.add(mid, BorderLayout.CENTER);
        return side;
    }

    // ----- Palette -----
    private static final int[] DEFAULT_IDS = {
            1,2,3,4,5,12,13,14,15,16,
            17,18,19,20,21,22,23,24,35,41,
            42,45,46,47,48,49
    };

    private JPanel makePalettePanel() {
        JPanel grid = new JPanel(new GridLayout(0, 10, 4, 4));
        ButtonGroup group = new ButtonGroup();

        for (int id : DEFAULT_IDS) {
            JToggleButton b = new JToggleButton(blockIcon(id));
            b.setToolTipText(id + " : " + blockName(id));
            if (id == selectedBlockId) b.setSelected(true);
            b.addActionListener(e -> setSelectedBlock(id));
            group.add(b);
            grid.add(b);
        }
        // + custom field
        JPanel tail = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JTextField tf = new JTextField(Integer.toString(selectedBlockId), 3);
        JButton go = new JButton("Set");
        go.addActionListener(e -> {
            try { setSelectedBlock(Integer.parseInt(tf.getText().trim())); }
            catch (Exception ignored) {}
        });
        tail.add(new JLabel("ID:")); tail.add(tf); tail.add(go);
        JPanel outer = new JPanel(new BorderLayout());
        outer.add(grid, BorderLayout.CENTER);
        outer.add(tail, BorderLayout.SOUTH);
        return outer;
    }

    private void setSelectedBlock(int id) {
        selectedBlockId = Math.max(0, Math.min(255, id));
        updateStatusLabels();
    }

    private static String blockName(int id) {
        try { return (Block.blocks[id] != null ? Block.blocks[id].name : "?"); }
        catch (Throwable t) { return "?"; }
    }

    private static Icon blockIcon(int id) {
        int rgb = colorFor(id);
        BufferedImage img = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(rgb | 0xFF000000, true));
        g.fillRect(2,2,20,20);
        g.setColor(Color.BLACK); g.drawRect(2,2,20,20);
        g.dispose();
        return new ImageIcon(img);
    }

    // ----- Brush tab -----
    private JPanel makeBrushTab() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        // size
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JSlider size = new JSlider(1, 32, brushSize);
        size.setPaintTicks(true); size.setMajorTickSpacing(8); size.setMinorTickSpacing(1);
        size.addChangeListener(e -> brushSize = Math.max(1, size.getValue()));
        row1.add(new JLabel("Size:")); row1.add(size);
        p.add(row1);

        // 2D/3D
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JRadioButton r2d = new JRadioButton("2D disc", !brush3D);
        JRadioButton r3d = new JRadioButton("3D cube", brush3D);
        ButtonGroup g = new ButtonGroup();
        g.add(r2d); g.add(r3d);
        r2d.addActionListener(e -> brush3D = false);
        r3d.addActionListener(e -> brush3D = true);
        row2.add(new JLabel("Mode:")); row2.add(r2d); row2.add(r3d);
        p.add(row2);

        JLabel hint = new JLabel("Right-drag paints with the brush.");
        hint.setForeground(new Color(0,120,0));
        p.add(hint);

        return p;
    }

    // ----- View tab -----
    private JPanel makeViewTab() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        // plane
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        ButtonGroup grp = new ButtonGroup();
        JRadioButton bxz = new JRadioButton("XZ (top)", plane == Plane.XZ);
        JRadioButton bxy = new JRadioButton("XY (front)", plane == Plane.XY);
        JRadioButton byz = new JRadioButton("YZ (side)", plane == Plane.YZ);
        grp.add(bxz); grp.add(bxy); grp.add(byz);
        row1.add(new JLabel("Plane:")); row1.add(bxz); row1.add(bxy); row1.add(byz);
        p.add(row1);

        bxz.addActionListener(e -> { plane = Plane.XZ; setTopDownForPlane(); });
        bxy.addActionListener(e -> { plane = Plane.XY; setTopDownForPlane(); });
        byz.addActionListener(e -> { plane = Plane.YZ; setTopDownForPlane(); });

        // layer
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        SpinnerNumberModel m = new SpinnerNumberModel(layerIndex, 0, 255, 1);
        JSpinner sp = new JSpinner(m);
        sp.addChangeListener(e -> layerIndex = (Integer) sp.getValue());
        row2.add(new JLabel("Layer:")); row2.add(sp);
        p.add(row2);

        // FOV + radius (quick mirrors of fields)
        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JSlider fovS = new JSlider(30, 110, (int) fov);
        fovS.addChangeListener(e -> fov = fovS.getValue());
        row3.add(new JLabel("FOV:")); row3.add(fovS);
        p.add(row3);

        JPanel row4 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JSlider radXZ = new JSlider(16, 160, drawRadiusXZ);
        JSlider radY  = new JSlider(8, 96, drawRadiusY);
        radXZ.addChangeListener(e -> drawRadiusXZ = radXZ.getValue());
        radY.addChangeListener(e -> drawRadiusY = radY.getValue());
        row4.add(new JLabel("Draw R XZ:")); row4.add(radXZ);
        row4.add(new JLabel(" Y:")); row4.add(radY);
        p.add(row4);

        JCheckBox snap = new JCheckBox("Top-down camera", topDownSnap);
        snap.addActionListener(e -> { topDownSnap = snap.isSelected(); setTopDownForPlane(); });
        p.add(snap);

        return p;
    }

    private void setTopDownForPlane() {
        if (!topDownSnap) return;
        // snap camera orientation to a useful angle for each plane
        switch (plane) {
            case XZ: pitch = -90; yaw = 180; break; // look straight down
            case XY: pitch =   0; yaw = 180; break; // look -Z
            case YZ: pitch =   0; yaw =  90; break; // look +X
        }
        // also nudge camera inside bounds
        if (active != null) {
            camX = Math.max(1, Math.min(active.width - 2, camX));
            camY = Math.max(1, Math.min(active.height - 2, camY));
            camZ = Math.max(1, Math.min(active.depth - 2, camZ));
        }
    }

    // ----- Selection tab (visual box is rendered in GL; here are actions) -----
    private JPanel makeSelectionTab() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JButton fill = new JButton("Fill selection with Block");
        fill.addActionListener(e -> applyFillSelection(selectedBlockId));
        JButton clear = new JButton("Clear selection");
        clear.addActionListener(e -> applyFillSelection(0));

        p.add(fill);
        p.add(clear);
        JLabel hint = new JLabel("Tip: Left click twice to define selection corners.");
        hint.setForeground(new Color(0,0,130));
        p.add(hint);
        return p;
    }

    // ----- IO tab -----
    private JPanel makeIoTab() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JButton bNew = new JButton("New Map…");
        bNew.addActionListener(e -> dialogNewMap());

        JButton bLoad = new JButton("Reload (F6)");
        bLoad.addActionListener(e -> reloadBoth());

        JButton bSave = new JButton("Save (F5)");
        bSave.addActionListener(e -> saveBoth());

        JButton bExport = new JButton("Export Top-Down PNG");
        bExport.addActionListener(e -> exportTopdownPNG());

        JButton bToggle = new JButton("Toggle Creative/Survival (Tab)");
        bToggle.addActionListener(e -> {
            editingCreative = !editingCreative;
            setActive(editingCreative ? levelCreative : levelSurvival);
        });

        p.add(bNew); p.add(Box.createVerticalStrut(6));
        p.add(bLoad); p.add(bSave); p.add(Box.createVerticalStrut(6));
        p.add(bExport); p.add(Box.createVerticalStrut(6));
        p.add(bToggle);
        return p;
    }

    private void dialogNewMap() {
        JTextField fx = new JTextField("128", 5);
        JTextField fy = new JTextField("64", 5);
        JTextField fz = new JTextField("128", 5);
        JPanel panel = new JPanel(new GridLayout(0,2,6,4));
        panel.add(new JLabel("Width (X):"));  panel.add(fx);
        panel.add(new JLabel("Height (Y):")); panel.add(fy);
        panel.add(new JLabel("Depth (Z):"));  panel.add(fz);
        if (JOptionPane.showConfirmDialog(frame, panel, "New Map", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            try {
                int w = Math.max(8, Integer.parseInt(fx.getText().trim()));
                int h = Math.max(8, Integer.parseInt(fy.getText().trim()));
                int d = Math.max(8, Integer.parseInt(fz.getText().trim()));
                Level fresh = new Level();
                fresh.setData(w, h, d, new byte[w*h*d]);
                fresh.xSpawn = w/2; fresh.ySpawn = h/2; fresh.zSpawn = d/2;
                if (editingCreative) levelCreative = fresh; else levelSurvival = fresh;
                setActive(fresh);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Invalid sizes.");
            }
        }
    }

    private void exportTopdownPNG() {
        try {
            if (active == null) return;
            int w = active.width, d = active.depth;
            BufferedImage img = new BufferedImage(w, d, BufferedImage.TYPE_INT_ARGB);
            for (int z = 0; z < d; z++) {
                for (int x = 0; x < w; x++) {
                    // simple: find highest non-air column
                    int y = active.height - 1;
                    int id = 0;
                    for (; y >= 0; y--) { id = active.getTile(x,y,z); if (id != 0) break; }
                    int rgb = (id == 0 ? 0x00000000 : 0xFF000000 | colorFor(id));
                    img.setRGB(x, d - 1 - z, rgb); // origin top-left like the screenshot
                }
            }
            File out = new File(appDir(), "classic_topdown.png");
            javax.imageio.ImageIO.write(img, "png", out);
            JOptionPane.showMessageDialog(frame, "Exported:\n" + out.getAbsolutePath());
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Export failed:\n" + ex, "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void createDisplayOn(Canvas parent) throws Exception {
        Display.setParent(parent);
        Display.setDisplayMode(new DisplayMode(parent.getWidth(), parent.getHeight()));
        Display.setTitle("Classic 3D Map Editor");
        Display.create();
        Keyboard.create();
        Mouse.create();
        initGL();
    }

    // ---------- GL ----------
    private void initGL() {
        GL11.glViewport(0,0,canvas.getWidth(),canvas.getHeight());
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity();
        GLU.gluPerspective(fov, canvas.getWidth()/(float)canvas.getHeight(), 0.1f, 500.0f);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glShadeModel(GL11.GL_FLAT);
    }

    // ---------- Loop ----------
    private void loop() {
        long last = System.nanoTime();
        while (!Display.isCloseRequested()) {
            long now = System.nanoTime();
            float dt = (now - last) / 1_000_000_000f;
            last = now;

            handleInput(dt);
            render();

            Display.update();
            Display.sync(60);
        }
    }

    // ---------- I/O ----------
    private void reloadBoth() {
        levelCreative = safeLoad(creativeFile());
        if (levelCreative == null) {
            levelCreative = new Level();
            byte[] empty = new byte[64*32*64];
            levelCreative.setData(64, 32, 64, empty);
        }
        levelSurvival = safeLoad(survivalFile());
        if (levelSurvival == null) {
            levelSurvival = new Level();
            byte[] empty = new byte[64*32*64];
            levelSurvival.setData(64, 32, 64, empty);
        }
        setActive(editingCreative ? levelCreative : levelSurvival);
    }

    private Level safeLoad(File f) {
        try { if (!f.isFile()) return null; return io.load(f); }
        catch (Throwable t) { t.printStackTrace(); return null; }
    }

    private void saveBoth() {
        try {
            io.save(levelCreative, creativeFile());
            io.save(levelSurvival, survivalFile());
            System.out.println("[save] wrote: " + creativeFile().getAbsolutePath());
            System.out.println("[save] wrote: " + survivalFile().getAbsolutePath());
            JOptionPane.showMessageDialog(frame, "Saved both files:\n" +
                    creativeFile().getAbsolutePath() + "\n" +
                    survivalFile().getAbsolutePath());
        } catch (Throwable t) {
            t.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Save failed:\n" + t, "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void setActive(Level lvl) {
        active = lvl;
        camX = active.xSpawn + 0.5f;
        camY = Math.max(8, active.ySpawn + 6);
        camZ = active.zSpawn + 0.5f;
        layerIndex = Math.min(active.height - 1, Math.max(0, active.ySpawn));
        updateStatusLabels();
    }

    private void updateStatusLabels() {
        if (lblActive != null) {
            lblActive.setText(editingCreative ? "Active: levelc.dat (Creative)" :
                                               "Active: levels.dat (Survival)");
        }
        if (lblSelected != null) {
            lblSelected.setText("Selected Block: " + selectedBlockId + " (" + blockName(selectedBlockId) + ")");
        }
        if (lblPos != null) {
            lblPos.setText("Cam: " + (int)camX + "," + (int)camY + "," + (int)camZ);
        }
    }

    // ---------- Input ----------
    private void handleInput(float dt) {
        if (Keyboard.isKeyDown(Keyboard.KEY_ESCAPE)) {
            Display.destroy(); frame.dispose(); System.exit(0);
        }

        while (Keyboard.next()) {
            if (!Keyboard.getEventKeyState()) continue;
            int k = Keyboard.getEventKey();

            // quick IDs
            switch (k) {
                case Keyboard.KEY_0: setSelectedBlock(20); break;
                case Keyboard.KEY_1: setSelectedBlock(1);  break;
                case Keyboard.KEY_2: setSelectedBlock(2);  break;
                case Keyboard.KEY_3: setSelectedBlock(3);  break;
                case Keyboard.KEY_4: setSelectedBlock(4);  break;
                case Keyboard.KEY_5: setSelectedBlock(5);  break;
                case Keyboard.KEY_6: setSelectedBlock(12); break;
                case Keyboard.KEY_7: setSelectedBlock(13); break;
                case Keyboard.KEY_8: setSelectedBlock(41); break;
                case Keyboard.KEY_9: setSelectedBlock(42); break;
            }

            if (k == Keyboard.KEY_TAB) { editingCreative = !editingCreative; setActive(editingCreative ? levelCreative : levelSurvival); }
            if (k == Keyboard.KEY_F5) saveBoth();
            if (k == Keyboard.KEY_F6) reloadBoth();

            if (k == Keyboard.KEY_RETURN) doEdit(true);   // place once
            if (k == Keyboard.KEY_DELETE) doEdit(false);  // remove once
        }

        // mouse wheel → change selected block id
        int dw = Mouse.getDWheel();
        if (dw != 0) setSelectedBlock(selectedBlockId + (dw > 0 ? 1 : -1));

        // LOOK: Middle mouse OR RMB+Alt
        boolean lookMode = Mouse.isButtonDown(2) || (Mouse.isButtonDown(1) && Keyboard.isKeyDown(Keyboard.KEY_LMENU));
        if (lookMode) {
            yaw   += Mouse.getDX() * 0.25f;
            pitch -= Mouse.getDY() * 0.25f;
            if (pitch < -89) pitch = -89;
            if (pitch >  89) pitch =  89;
        } else {
            // drain deltas
            Mouse.getDX(); Mouse.getDY();
        }

        // fly
        float speed = 12f;
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) speed *= 0.25f;
        if (Keyboard.isKeyDown(Keyboard.KEY_LMENU))  speed *= 3.0f;

        float mx = 0, my = 0, mz = 0;
        if (Keyboard.isKeyDown(Keyboard.KEY_W)) mz -= 1;
        if (Keyboard.isKeyDown(Keyboard.KEY_S)) mz += 1;
        if (Keyboard.isKeyDown(Keyboard.KEY_A)) mx -= 1;
        if (Keyboard.isKeyDown(Keyboard.KEY_D)) mx += 1;
        if (Keyboard.isKeyDown(Keyboard.KEY_SPACE))    my += 1;
        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)) my -= 1;

        if (mx != 0 || my != 0 || mz != 0) {
            float yawRad = (float) Math.toRadians(yaw);
            float sin = MathHelper.sin(yawRad);
            float cos = MathHelper.cos(yawRad);
            camX += (mx * cos + mz * sin) * speed * dt;
            camZ += (-mx * sin + mz * cos) * speed * dt;
            camY += my * speed * dt;
            updateStatusLabels();
        }

        if (active == null) return;

        // --- Mouse actions ---
        boolean leftNow  = Mouse.isButtonDown(0);
        boolean rightNow = Mouse.isButtonDown(1);
        boolean ctrlHeld = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);

        // Ctrl+LMB = selection corners; plain LMB drag = erase
        if (leftNow) {
            if (ctrlHeld && !leftPrev) {
                int[] p = pickBlockFromCursor();
                if (!selecting) { selAx=p[0]; selAy=p[1]; selAz=p[2]; selecting=true; hasSelection=false; }
                else            { selBx=p[0]; selBy=p[1]; selBz=p[2]; selecting=false; hasSelection=true; }
            } else {
                doEdit(false); // erase while dragging
            }
        }
        leftPrev = leftNow;

        // RMB paints (unless holding Alt, which switched to lookMode)
        if (rightNow && !Keyboard.isKeyDown(Keyboard.KEY_LMENU)) {
            doEdit(true);
        }
        rightPrev = rightNow;
    }


    private static final float REACH = 120f;

    /** Returns {x,y,z} of the block under the cursor (snaps to a cell). */
    private int[] pickBlockFromCursor() {
        // Build the same ray math used by doEdit()
        float yawR   = (float) Math.toRadians(yaw);
        float pitchR = (float) Math.toRadians(pitch);
        float cx = MathHelper.cos(-yawR - (float)Math.PI);
        float sx = MathHelper.sin(-yawR - (float)Math.PI);
        float cy = -MathHelper.cos(-pitchR);
        float sy = MathHelper.sin(-pitchR);
        float dx = sx * cy, dy = sy, dz = cx * cy;

        Vec3D start = new Vec3D(camX, camY, camZ);
        Vec3D end   = new Vec3D(camX + dx*REACH, camY + dy*REACH, camZ + dz*REACH);

        MovingObjectPosition hit = active.clip(new Vec3D(start.x, start.y, start.z),
                                               new Vec3D(end.x,   end.y,   end.z));
        if (hit != null) return new int[]{hit.x, hit.y, hit.z};

        float x = start.x, y = start.y, z = start.z;
        int bx = (int)Math.floor(x), by = (int)Math.floor(y), bz = (int)Math.floor(z);
        float step = 0.25f, dist = 0f;
        while (dist < REACH) {
            x += dx*step; y += dy*step; z += dz*step; dist += step;
            int ix = (int)Math.floor(x), iy = (int)Math.floor(y), iz = (int)Math.floor(z);
            if (!active.isInBounds(ix, iy, iz)) break;
            bx = ix; by = iy; bz = iz;
        }
        return new int[]{bx, by, bz};
    }
 // --- Omen-like 2D view state ---
    private enum ViewPlane { XY, XZ, YZ }
    private ViewPlane viewPlane = ViewPlane.XY;
    private int viewLayer = 31;        // “Viewing layer Z=31 in the X-Y plane”
    private int viewZoom  = 4;         // pixels per block (2..16 is reasonable)
    private boolean brushRound = false;
    private boolean brushSquare = true;
    private boolean mode2D = true;     // 2D mode in Brush tab; 3D paints cube

    // hook to the 2D canvas (created below)
    private Map2DPanel map2D;

 // call this instead of buildRightSidebar() in setupAwtWindow()
    private JPanel buildOmenRightSidebar() {
        JPanel side = new JPanel(new BorderLayout());
        side.setPreferredSize(new Dimension(360, 800));

        // Block palette (reuse your makePalettePanel + status)
        JPanel status = new JPanel(new GridLayout(3,1));
        status.setBorder(new javax.swing.border.TitledBorder("Status"));
        lblActive   = new JLabel();
        lblSelected = new JLabel();
        lblPos      = new JLabel();
        status.add(lblActive); status.add(lblSelected); status.add(lblPos);
        side.add(status, BorderLayout.NORTH);

        JPanel palette = makePalettePanel();
        palette.setBorder(new javax.swing.border.TitledBorder("Block Types"));
        side.add(new JScrollPane(palette,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED), BorderLayout.CENTER);

        // Tabs
        JTabbedPane tabs = new JTabbedPane();
        tabs.add("Brush", makeOmenBrushTab());
        tabs.add("Flood Fill", makeOmenFloodTab());
        tabs.add("Prefabs", new JPanel());     // stub
        tabs.add("Selection", makeSelectionTab());
        tabs.add("Info", makeInfoTab());
        side.add(tabs, BorderLayout.SOUTH);

        updateStatusLabels();
        return side;
    }
    private JPanel makeOmenBrushTab() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        // Size row: Omen-like slider + quick buttons
        JSlider size = new JSlider(1, 32, brushSize);
        size.setPaintTicks(true); size.setMajorTickSpacing(16); size.setMinorTickSpacing(1);
        size.addChangeListener(e -> brushSize = Math.max(1, size.getValue()));
        p.add(labeled("Size", size));

        // 2D / 3D
        JRadioButton r2d = new JRadioButton("2d mode", true);
        JRadioButton r3d = new JRadioButton("3d mode", false);
        ButtonGroup g1 = new ButtonGroup(); g1.add(r2d); g1.add(r3d);
        r2d.addActionListener(e -> mode2D = true);
        r3d.addActionListener(e -> mode2D = false);
        JPanel modeRow = new JPanel(new FlowLayout(FlowLayout.LEFT)); modeRow.add(r2d); modeRow.add(r3d);
        p.add(modeRow);

        // Shape
        JRadioButton round = new JRadioButton("Round Brush", false);
        JRadioButton square = new JRadioButton("Square Brush", true);
        ButtonGroup g2 = new ButtonGroup(); g2.add(round); g2.add(square);
        round.addActionListener(e -> { brushRound=true; brushSquare=false; });
        square.addActionListener(e -> { brushRound=false; brushSquare=true; });
        JPanel shapeRow = new JPanel(new FlowLayout(FlowLayout.LEFT)); shapeRow.add(round); shapeRow.add(square);
        p.add(shapeRow);

        return p;
    }

    private JPanel makeOmenFloodTab() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JButton flood = new JButton("Flood Fill (current plane)");
        flood.addActionListener(e -> floodFillCurrentPlane());
        p.add(flood);
        return p;
    }
    private void floodFillCurrentPlane() {
        if (active == null) return;
        // start at center of panel
        int cx, cy;
        cx = Math.max(0, Math.min(map2D.getWidth()/Math.max(1,viewZoom)/2, (viewPlane==ViewPlane.YZ?active.depth:active.width)-1));
        cy = Math.max(0, Math.min(map2D.getHeight()/Math.max(1,viewZoom)/2, (viewPlane==ViewPlane.XZ?active.depth:active.height)-1));

        // convert canvas (cx,cy) -> level (x,y,z)
        int sx=0, sy=0, sz=0;
        switch (viewPlane) {
            case XY: sx=cx; sy=cy; sz=viewLayer; break;
            case XZ: sx=cx; sy=viewLayer; sz=active.depth-1-cy; break;
            case YZ: sx=viewLayer; sy=cy; sz=active.depth-1-cx; break;
        }
        if (!active.isInBounds(sx,sy,sz)) return;

        final int target = active.getTile(sx,sy,sz);
        if (target == selectedBlockId) return;

        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
        q.add(new int[]{sx,sy,sz});
        while (!q.isEmpty()) {
            int[] v = q.poll();
            int x=v[0], y=v[1], z=v[2];
            if (!active.isInBounds(x,y,z)) continue;
            if (active.getTile(x,y,z) != target) continue;
            active.setTile(x,y,z, selectedBlockId);
            // 4-neighbors in plane (or 6 if XY and flood in 3D; we keep plane)
            if (viewPlane==ViewPlane.XY) { q.add(new int[]{x+1,y,z}); q.add(new int[]{x-1,y,z}); q.add(new int[]{x,y+1,z}); q.add(new int[]{x,y-1,z}); }
            if (viewPlane==ViewPlane.XZ) { q.add(new int[]{x+1,y,z}); q.add(new int[]{x-1,y,z}); q.add(new int[]{x,y,z+1}); q.add(new int[]{x,y,z-1}); }
            if (viewPlane==ViewPlane.YZ) { q.add(new int[]{x,y+1,z}); q.add(new int[]{x,y-1,z}); q.add(new int[]{x,y,z+1}); q.add(new int[]{x,y,z-1}); }
        }
        map2D.repaint();
    }
 // Save a single layer from current plane as PNG
    private void exportLayerPNG(File out) throws Exception {
        if (active == null) return;
        int W = (viewPlane==ViewPlane.YZ) ? active.depth : active.width;
        int H = (viewPlane==ViewPlane.XZ) ? active.depth : active.height;
        BufferedImage img = new BufferedImage(W,H,BufferedImage.TYPE_INT_ARGB);
        for (int y=0; y<H; y++) for (int x=0; x<W; x++) {
            int rx=x, ry=y, rz=viewLayer;
            switch (viewPlane) {
                case XY: break;
                case XZ: ry=viewLayer; rz=active.depth-1-y; break;
                case YZ: rx=viewLayer; rz=active.depth-1-x; break;
            }
            int id = active.isInBounds(rx,ry,rz)? active.getTile(rx,ry,rz):0;
            int rgb = id==0?0x00000000:(0xFF000000|colorFor(id));
            img.setRGB(x,y,rgb);
        }
        javax.imageio.ImageIO.write(img, "png", out);
    }

    // Load layer from PNG into current plane
    private void importLayerPNG(File in) throws Exception {
        if (active == null) return;
        BufferedImage img = javax.imageio.ImageIO.read(in);
        int W = Math.min(img.getWidth(),  (viewPlane==ViewPlane.YZ)?active.depth:active.width);
        int H = Math.min(img.getHeight(), (viewPlane==ViewPlane.XZ)?active.depth:active.height);
        for (int y=0; y<H; y++) for (int x=0; x<W; x++) {
            int a = (img.getRGB(x,y) >>> 24) & 255;
            int id = (a==0) ? 0 : selectedBlockId;
            int rx=x, ry=y, rz=viewLayer;
            switch (viewPlane) {
                case XY: break;
                case XZ: ry=viewLayer; rz=active.depth-1-y; break;
                case YZ: rx=viewLayer; rz=active.depth-1-x; break;
            }
            if (active.isInBounds(rx,ry,rz)) active.setTile(rx,ry,rz,id);
        }
        map2D.repaint();
    }

    // Heightmap: grayscale → height in Y; fills with selected block up to height
    private void importHeightmapPNG(File in) throws Exception {
        if (active == null) return;
        BufferedImage img = javax.imageio.ImageIO.read(in);
        int W = Math.min(img.getWidth(),  active.width);
        int D = Math.min(img.getHeight(), active.depth);
        for (int z=0; z<D; z++) for (int x=0; x<W; x++) {
            int rgb = img.getRGB(x,z);
            int gray = ((rgb>>16)&255 + (rgb>>8)&255 + (rgb&255)) / 3;
            int h = (int) Math.round((gray/255.0) * (active.height-1));
            for (int y=0; y<active.height; y++) {
                active.setTile(x,y,z, y<=h ? selectedBlockId : 0);
            }
        }
        map2D.repaint();
    }
    public void exportFullHDScreenshot(File out) throws Exception {
        if (active == null) return;

        // render a full top-down (XZ) to an offscreen image, then scale to 1920×1080 with letterbox
        int srcW = active.width, srcH = active.depth;
        BufferedImage src = new BufferedImage(srcW, srcH, BufferedImage.TYPE_INT_ARGB);

        for (int z = 0; z < active.depth; z++) {
            for (int x = 0; x < active.width; x++) {
                int y = active.height - 1, id = 0;
                for (; y >= 0; y--) { id = active.getTile(x,y,z); if (id != 0) break; }
                int rgb = (id==0?0x00000000:(0xFF000000|colorFor(id)));
                src.setRGB(x, active.depth-1-z, rgb);
            }
        }

        BufferedImage outImg = new BufferedImage(1920,1080,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = outImg.createGraphics();
        g.setColor(new Color(0x202531)); g.fillRect(0,0,1920,1080);

        // fit with aspect preserve
        double sx = 1920.0 / srcW, sy = 1080.0 / srcH;
        double s = Math.min(sx, sy);
        int dw = (int)Math.round(srcW * s);
        int dh = (int)Math.round(srcH * s);
        int dx = (1920 - dw)/2, dy = (1080 - dh)/2;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(src, dx, dy, dw, dh, null);

        // header strip
        g.setColor(new Color(0,0,0,160));
        g.fillRect(12,12, 800, 26);
        g.setColor(Color.WHITE);
        g.drawString("Omen-style overview  (Top-down X-Z)   Selected: " + selectedBlockId + " " + blockName(selectedBlockId), 18, 30);
        g.dispose();

        javax.imageio.ImageIO.write(outImg, "png", out);
    }

    private JPanel makeInfoTab() {
        JPanel p = new JPanel(new GridLayout(0,1));
        p.add(new JLabel("Omen-style controls:"));
        p.add(new JLabel("• Layer Up/Down, Zoom In/Out, X-Y / Y-Z / X-Z"));
        p.add(new JLabel("• Left = paint   Right = erase   (in 2D view)"));
        return p;
    }

    private JComponent labeled(String name, JComponent c) {
        JPanel p = new JPanel(new BorderLayout(6,0));
        p.add(new JLabel(name), BorderLayout.WEST);
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    private JComponent buildOmenBottomBar() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));

        JButton up   = new JButton("Layer Up");
        JButton down = new JButton("Layer Down");
        JButton zin  = new JButton("Zoom In");
        JButton zout = new JButton("Zoom Out");
        JButton bXY  = new JButton("X-Y");
        JButton bYZ  = new JButton("Y-Z");
        JButton bXZ  = new JButton("X-Z");

        up.addActionListener(e -> { viewLayer = Math.min(viewLayer+1, (active!=null?active.height-1:255)); map2D.repaint(); });
        down.addActionListener(e -> { viewLayer = Math.max(viewLayer-1, 0); map2D.repaint(); });
        zin.addActionListener(e -> { viewZoom = Math.min(viewZoom+1, 16); map2D.relayout(); });
        zout.addActionListener(e -> { viewZoom = Math.max(viewZoom-1, 1);  map2D.relayout(); });

        bXY.addActionListener(e -> { viewPlane = ViewPlane.XY; map2D.relayout(); });
        bYZ.addActionListener(e -> { viewPlane = ViewPlane.YZ; map2D.relayout(); });
        bXZ.addActionListener(e -> { viewPlane = ViewPlane.XZ; map2D.relayout(); });

        row.add(up); row.add(down); row.add(zin); row.add(zout);
        row.add(bXY); row.add(bYZ); row.add(bXZ);
        return row;
    }

    private void doEdit(boolean place) {
        if (active == null) return;

        // Ray from camera
        float yawR   = (float) Math.toRadians(yaw);
        float pitchR = (float) Math.toRadians(pitch);
        float cx = MathHelper.cos(-yawR - (float) Math.PI);
        float sx = MathHelper.sin(-yawR - (float) Math.PI);
        float cy = -MathHelper.cos(-pitchR);
        float sy = MathHelper.sin(-pitchR);
        float dx = sx * cy, dy = sy, dz = cx * cy;
        Vec3D start = new Vec3D(camX, camY, camZ);
        Vec3D end   = new Vec3D(camX + dx * REACH, camY + dy * REACH, camZ + dz * REACH);

        MovingObjectPosition hit = active.clip(new Vec3D(start.x, start.y, start.z),
                                               new Vec3D(end.x,   end.y,   end.z));
        if (hit == null) return;

        int hx = hit.x, hy = hit.y, hz = hit.z;

        // Single-cell erase fast path
        if (!place && brushSize <= 1) { active.setTile(hx, hy, hz, 0); return; }

        // Placement anchor (adjacent when placing, else hit cell)
        int px = hx, py = hy, pz = hz;
        if (place) {
            switch (hit.face) {
                case 0: py -= 1; break; // bottom
                case 1: py += 1; break; // top
                case 2: pz -= 1; break; // north
                case 3: pz += 1; break; // south
                case 4: px -= 1; break; // west
                case 5: px += 1; break; // east
            }
        }
        // Clamp anchor inside bounds to avoid all-OOB loops
        px = Math.max(0, Math.min(active.width  - 1, px));
        py = Math.max(0, Math.min(active.height - 1, py));
        pz = Math.max(0, Math.min(active.depth  - 1, pz));

        int r = Math.max(1, brushSize);

        if (!mode2D) {
            // 3D cube brush
            int x0 = Math.max(0, px - r + 1), x1 = Math.min(active.width  - 1, px + r - 1);
            int y0 = Math.max(0, py - r + 1), y1 = Math.min(active.height - 1, py + r - 1);
            int z0 = Math.max(0, pz - r + 1), z1 = Math.min(active.depth  - 1, pz + r - 1);
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++)
                    for (int x = x0; x <= x1; x++)
                        active.setTile(x, y, z, place ? selectedBlockId : 0);
            return;
        }

        // 2D brush on the chosen view plane, supports round/square
        switch (viewPlane) {
            case XZ: {
                int y = place ? py : hy;
                int z0 = Math.max(0, pz - r + 1), z1 = Math.min(active.depth - 1, pz + r - 1);
                int x0 = Math.max(0, px - r + 1), x1 = Math.min(active.width - 1, px + r - 1);
                int rr = (r - 1) * (r - 1);
                for (int zz = z0; zz <= z1; zz++)
                    for (int xx = x0; xx <= x1; xx++) {
                        int dx1 = xx - px, dz1 = zz - pz;
                        if (brushRound && (dx1*dx1 + dz1*dz1) > rr) continue;
                        if (active.isInBounds(xx, y, zz)) active.setTile(xx, y, zz, place ? selectedBlockId : 0);
                    }
                break;
            }
            case XY: {
                int z = viewLayer;
                int y0 = Math.max(0, py - r + 1), y1 = Math.min(active.height - 1, py + r - 1);
                int x0 = Math.max(0, px - r + 1), x1 = Math.min(active.width  - 1, px + r - 1);
                int rr = (r - 1) * (r - 1);
                for (int yy = y0; yy <= y1; yy++)
                    for (int xx = x0; xx <= x1; xx++) {
                        int dx1 = xx - px, dy0 = yy - py;
                        if (brushRound && (dx1*dx1 + dy0*dy0) > rr) continue;
                        if (active.isInBounds(xx, yy, z)) active.setTile(xx, yy, z, place ? selectedBlockId : 0);
                    }
                break;
            }
            case YZ: {
                int x = viewLayer;
                int y0 = Math.max(0, py - r + 1), y1 = Math.min(active.height - 1, py + r - 1);
                int z0 = Math.max(0, pz - r + 1), z1 = Math.min(active.depth  - 1, pz + r - 1);
                int rr = (r - 1) * (r - 1);
                for (int yy = y0; yy <= y1; yy++)
                    for (int zz = z0; zz <= z1; zz++) {
                        int dz1 = zz - pz, dy0 = yy - py;
                        if (brushRound && (dz1*dz1 + dy0*dy0) > rr) continue;
                        if (active.isInBounds(x, yy, zz)) active.setTile(x, yy, zz, place ? selectedBlockId : 0);
                    }
                break;
            }
        }
    }


    // ---------- Render ----------
    private void render() {
        if (active == null) return;

        // camera matrices
        GL11.glViewport(0,0,canvas.getWidth(), canvas.getHeight());
        GL11.glClearColor(((skyboxColor>>16)&255)/255f, ((skyboxColor>>8)&255)/255f, (skyboxColor&255)/255f, 1f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity();
        GLU.gluPerspective(fov, canvas.getWidth()/(float)canvas.getHeight(), 0.05f, 500f);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity();

        // Build a look vector
        GL11.glRotatef(pitch, 1,0,0);
        GL11.glRotatef(yaw, 0,1,0);
        GL11.glTranslatef(-camX, -camY, -camZ);

        // ground grid
        drawGrid();

        // draw blocks in a radius
        int minX = (int)Math.floor(camX - drawRadiusXZ);
        int maxX = (int)Math.ceil (camX + drawRadiusXZ);
        int minZ = (int)Math.floor(camZ - drawRadiusXZ);
        int maxZ = (int)Math.ceil (camZ + drawRadiusXZ);
        int minY = (int)Math.floor(camY - drawRadiusY);
        int maxY = (int)Math.ceil (camY + drawRadiusY);

        clampRange(active, minX, maxX, minY, maxY, minZ, maxZ);

        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    int id = active.getTile(x, y, z);
                    if (id == 0) continue;

                    boolean drawN = neighborAir(active, x, y, z-1);
                    boolean drawS = neighborAir(active, x, y, z+1);
                    boolean drawW = neighborAir(active, x-1, y, z);
                    boolean drawE = neighborAir(active, x+1, y, z);
                    boolean drawD = neighborAir(active, x, y-1, z);
                    boolean drawU = neighborAir(active, x, y+1, z);
                    if (!(drawN|drawS|drawW|drawE|drawD|drawU)) continue;

                    int rgb = colorFor(id);
                    float r = ((rgb>>16)&255)/255f, g = ((rgb>>8)&255)/255f, b = (rgb&255)/255f;

                    GL11.glColor3f(r,g,b);
                    GL11.glBegin(GL11.GL_QUADS);
                    float x1=x, y1=y, z1=z, x2=x+1, y2=y+1, z2=z+1;

                    if (drawD) { GL11.glVertex3f(x1, y1, z2); GL11.glVertex3f(x1, y1, z1); GL11.glVertex3f(x2, y1, z1); GL11.glVertex3f(x2, y1, z2); }
                    if (drawU) { GL11.glVertex3f(x2, y2, z2); GL11.glVertex3f(x2, y2, z1); GL11.glVertex3f(x1, y2, z1); GL11.glVertex3f(x1, y2, z2); }
                    if (drawN) { GL11.glVertex3f(x1, y2, z1); GL11.glVertex3f(x2, y2, z1); GL11.glVertex3f(x2, y1, z1); GL11.glVertex3f(x1, y1, z1); }
                    if (drawS) { GL11.glVertex3f(x1, y2, z2); GL11.glVertex3f(x1, y1, z2); GL11.glVertex3f(x2, y1, z2); GL11.glVertex3f(x2, y2, z2); }
                    if (drawW) { GL11.glVertex3f(x1, y2, z2); GL11.glVertex3f(x1, y2, z1); GL11.glVertex3f(x1, y1, z1); GL11.glVertex3f(x1, y1, z2); }
                    if (drawE) { GL11.glVertex3f(x2, y1, z2); GL11.glVertex3f(x2, y1, z1); GL11.glVertex3f(x2, y2, z1); GL11.glVertex3f(x2, y2, z2); }
                    GL11.glEnd();
                }
            }
        }

        // --- Selection render (precise) ---
        if (selecting || hasSelection) {
            int bx = selBx, by = selBy, bz = selBz;
            if (selecting) {
                int[] p = pickBlockFromCursor();
                bx = p[0]; by = p[1]; bz = p[2];
            }
            int minx = Math.min(selAx, bx), maxx = Math.max(selAx, bx) + 1;
            int miny = Math.min(selAy, by), maxy = Math.max(selAy, by) + 1;
            int minz = Math.min(selAz, bz), maxz = Math.max(selAz, bz) + 1;

            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glColor4f(1f, 1f, 0f, 1f); // yellow
            GL11.glBegin(GL11.GL_LINES);
            // bottom
            GL11.glVertex3f(minx, miny, minz); GL11.glVertex3f(maxx, miny, minz);
            GL11.glVertex3f(maxx, miny, minz); GL11.glVertex3f(maxx, miny, maxz);
            GL11.glVertex3f(maxx, miny, maxz); GL11.glVertex3f(minx, miny, maxz);
            GL11.glVertex3f(minx, miny, maxz); GL11.glVertex3f(minx, miny, minz);
            // top
            GL11.glVertex3f(minx, maxy, minz); GL11.glVertex3f(maxx, maxy, minz);
            GL11.glVertex3f(maxx, maxy, minz); GL11.glVertex3f(maxx, maxy, maxz);
            GL11.glVertex3f(maxx, maxy, maxz); GL11.glVertex3f(minx, maxy, maxz);
            GL11.glVertex3f(minx, maxy, maxz); GL11.glVertex3f(minx, maxy, minz);
            // verticals
            GL11.glVertex3f(minx, miny, minz); GL11.glVertex3f(minx, maxy, minz);
            GL11.glVertex3f(maxx, miny, minz); GL11.glVertex3f(maxx, maxy, minz);
            GL11.glVertex3f(maxx, miny, maxz); GL11.glVertex3f(maxx, maxy, maxz);
            GL11.glVertex3f(minx, miny, maxz); GL11.glVertex3f(minx, maxy, maxz);
            GL11.glEnd();
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }

        // HUD
        drawHud();
    }

    private void drawGrid() {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glColor3f(0.15f,0.18f,0.22f);
        GL11.glBegin(GL11.GL_LINES);
        int R = 128;
        for (int i = -R; i <= R; i++) {
            GL11.glVertex3f(i, 0, -R); GL11.glVertex3f(i, 0, R);
            GL11.glVertex3f(-R, 0, i); GL11.glVertex3f(R, 0, i);
        }
        GL11.glEnd();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    private final GLText hudLine1 = new GLText();
    private final GLText hudLine2 = new GLText();
    private final GLText hudLine3 = new GLText();

    private void drawHud() {
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix(); GL11.glLoadIdentity();
        GL11.glOrtho(0, canvas.getWidth(), canvas.getHeight(), 0, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);  GL11.glPushMatrix(); GL11.glLoadIdentity();

        String line1 = editingCreative ? "ACTIVE: levelc.dat (Creative)" : "ACTIVE: levels.dat (Survival)";
        String line2 = "Block ID: " + selectedBlockId + " (" + blockName(selectedBlockId) + ")";
        String line3 = "Pos: " + (int)camX + "," + (int)camY + "," + (int)camZ;

        hudLine1.draw(10, 10, line1);
        hudLine2.draw(10, 30, line2);
        hudLine3.draw(10, 50, line3);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);  GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix();
    }

    private static void clampRange(Level lv,
            int[] minX, int[] maxX,
            int[] minY, int[] maxY,
            int[] minZ, int[] maxZ) {} // <- ignore

//keep your call site as-is; just drop this in:
private static void clampRange(Level lv, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
if (lv == null) return;
if (minX < 0) minX = 0;             if (maxX > lv.width  - 1)  maxX = lv.width  - 1;
if (minY < 0) minY = 0;             if (maxY > lv.height - 1)  maxY = lv.height - 1;
if (minZ < 0) minZ = 0;             if (maxZ > lv.depth  - 1)  maxZ = lv.depth  - 1;
// write back by returning new ints isn’t possible here, so instead:
// change the caller to read the clamped values from a small helper:
}

    // --- selection state ---
    private int selAx, selAy, selAz;
    private boolean hasSelection = false;
    private int selBx, selBy, selBz;
    private boolean selecting = false;

    private void applyFillSelection(int id) {
        if (!hasSelection) return;
        int minx = Math.min(selAx, selBx), maxx = Math.max(selAx, selBx);
        int miny = Math.min(selAy, selBy), maxy = Math.max(selAy, selBy);
        int minz = Math.min(selAz, selBz), maxz = Math.max(selAz, selBz);
        for (int y = miny; y <= maxy; y++)
            for (int z = minz; z <= maxz; z++)
                for (int x = minx; x <= maxx; x++)
                    if (active.isInBounds(x,y,z)) active.setTile(x,y,z, id);
    }

    private static boolean neighborAir(Level lv, int x, int y, int z) {
        if (!lv.isInBounds(x,y,z)) return true;
        return lv.getTile(x,y,z) == 0;
    }

    // ---------- Color LUT ----------
    private static int colorFor(int id) {
        switch (id) {
            case 0:  return 0x00000000;
            case 1:  return 0x7f7f7f; // stone
            case 2:  return 0x5aa04a; // grass
            case 3:  return 0x6b4b2a; // dirt
            case 4:  return 0x8a8a8a; // cobble
            case 5:  return 0x9c7b4f; // wood
            case 8:  return 0x3a56d1;
            case 9:  return 0x3a56d1;
            case 10: return 0xcc5533;
            case 11: return 0xcc5533;
            case 12: return 0xcbb27d; // sand
            case 13: return 0x7e7465; // gravel
            case 14: return 0xddd18b; // gold ore
            case 15: return 0xd0d0d0; // iron ore
            case 16: return 0x404040; // coal ore
            case 17: return 0x6a4d2d; // log
            case 18: return 0x3f6a2c; // leaves
            case 19: return 0xf8e76a; // sponge
            case 20: return 0xbdd0ff; // glass
            case 41: return 0xfff07a; // gold block
            case 42: return 0xc9c9c9; // iron block
            case 45: return 0x9b4a32; // brick
            case 46: return 0xd05a5a; // tnt
            case 47: return 0x8c6b43; // bookshelf-ish
            case 48: return 0x65705a; // mossy
            case 49: return 0x1a0f29; // obsidian
            default: return ((id*97)&0xff)<<16 | ((id*57)&0xff)<<8 | ((id*23)&0xff);
        }
    }
}
