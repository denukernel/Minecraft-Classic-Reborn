package net.classicremastered.toolkit.mapedit3d;

import net.classicremastered.minecraft.*;
import net.classicremastered.minecraft.level.*;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.*;
import net.classicremastered.minecraft.model.*;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.*;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.*;
import org.lwjgl.util.glu.GLU;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import javax.swing.*;

/** A single-document, 3D-first editor. All world mutations belong to the render thread. */
public final class Classic3DMapEditorApp {
    private final WorldEditorDocument document = new WorldEditorDocument();
    private final Queue<Runnable> commands = new ConcurrentLinkedQueue<>();
    private JFrame frame;
    private Canvas canvas;
    private JPanel slice;
    private JLabel status;
    private final DefaultListModel<String> palette = new DefaultListModel<>();
    private final DefaultListModel<Mob> mobList = new DefaultListModel<>();
    private JList<Mob> entities;
    private JSpinner mx, my, mz, rotation, health, layer;
    private volatile int block = Block.STONE.id, brush = 1, viewLayer = 16;
    private volatile String tool = "Place";
    private volatile boolean running = true;
    private float x = 32, y = 28, z = 48, yaw = 0, pitch = 30;
    private int mouseX, mouseY;
    private boolean dialogOpen;
    private volatile boolean viewportInputEnabled;
    private boolean painting, selectionStarted;
    private int[] first, last;
    private Mob selected;
    private boolean updatingList;
    private TextureManager textures;
    private int opaqueList, translucentList, regionX = Integer.MIN_VALUE, regionZ;
    private boolean rebuild = true;
    private long nextTick;

    public static void main(String[] args) throws Exception {
        net.classicremastered.toolkit.debugmodel.LWJGLNatives.useMCraftClientNatives();
        new Classic3DMapEditorApp().run(args);
    }

    private void run(String[] args) throws Exception {
        MobRegistry.bootstrapDefaults();
        SwingUtilities.invokeAndWait(this::buildUi);
        try {
            Display.setParent(canvas);
            Display.create(new PixelFormat());
            if (!Keyboard.isCreated()) Keyboard.create();
            if (!Mouse.isCreated()) Mouse.create();
            Minecraft.mcDir = appDir();
            textures = new TextureManager(new GameSettings(null, appDir()));
            Mob.modelCache = new ModelManager();
            opaqueList = GL11.glGenLists(2);
            translucentList = opaqueList + 1;
            if (args.length > 0) document.open(new File(args[0]));
            else createWorld("Finite");
            resetCamera();
            refresh();
            nextTick = System.nanoTime();
            while (running) {
                for (Runnable command; (command = commands.poll()) != null; ) command.run();
                pollInput();
                moveCamera();
                if (document.simulating() && System.nanoTime() >= nextTick) {
                    document.view().tickEntities();
                    document.view().tick();
                    nextTick = System.nanoTime() + 50_000_000;
                    rebuild = true;
                }
                if (canvas.isShowing()) render();
                Display.update();
                Display.sync(30);
            }
        } finally {
            document.close();
            if (Display.isCreated()) Display.destroy();
            SwingUtilities.invokeLater(frame::dispose);
        }
    }

    private static File appDir() {
        String root = System.getenv("APPDATA");
        File dir =
                new File(
                        root == null ? System.getProperty("user.home") : root,
                        ".minecraftclassicreborn/client");
        dir.mkdirs();
        return dir;
    }

    private void submit(Action action) {
        commands.add(
                () -> {
                    try {
                        action.run();
                        rebuild = true;
                        refresh();
                    } catch (Exception error) {
                        SwingUtilities.invokeLater(
                                () ->
                                        JOptionPane.showMessageDialog(
                                                frame,
                                                error.getMessage(),
                                                "Editor error",
                                                JOptionPane.ERROR_MESSAGE));
                    }
                });
    }

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }

    private JButton button(String text, Action action) {
        JButton button = new JButton(text);
        button.addActionListener(e -> submit(action));
        return button;
    }

    private void buildUi() {
        frame = new JFrame("Classic World Editor");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(
                new WindowAdapter() {
                    public void windowClosing(WindowEvent e) {
                        submit(
                                () -> {
                                    if (confirmDiscard()) running = false;
                                });
                    }
                });
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.add(button("New", this::newWorld));
        bar.add(button("Open", this::open));
        bar.add(button("Save", () -> save(false)));
        bar.add(button("Save as", () -> save(true)));
        bar.add(button("Undo", document::undo));
        bar.add(button("Redo", document::redo));
        bar.add(button("Test / Stop", this::toggleSimulation));
        bar.add(
                button(
                        "Reset test",
                        () -> {
                            document.stopSimulation();
                            toggleSimulation();
                        }));
        bar.add(button("Frame spawn", this::resetCamera));
        frame.add(bar, BorderLayout.NORTH);
        canvas = new Canvas();
        canvas.setPreferredSize(new Dimension(920, 660));
        installInput();
        slice =
                new JPanel() {
                    protected void paintComponent(Graphics graphics) {
                        super.paintComponent(graphics);
                        // The slice image is produced on the world-owning thread.
                        if (sliceImage != null)
                            graphics.drawImage(sliceImage, 0, 0, getWidth(), getHeight(), null);
                    }
                };
        slice.addMouseListener(
                new MouseAdapter() {
                    public void mousePressed(MouseEvent e) {
                        int px = e.getX(),
                                py = e.getY(),
                                width = slice.getWidth(),
                                height = slice.getHeight();
                        submit(
                                () -> {
                                    int bx = (int) x - 32 + px * 64 / Math.max(1, width);
                                    int bz = (int) z - 32 + py * 64 / Math.max(1, height);
                                    edit(
                                            () ->
                                                    paint(
                                                            bx,
                                                            viewLayer,
                                                            bz,
                                                            e.getButton() == MouseEvent.BUTTON3
                                                                    ? 0
                                                                    : block));
                                });
                    }
                });
        JTabbedPane views = new JTabbedPane();
        views.addTab("3D world", canvas);
        JPanel slices = new JPanel(new BorderLayout());
        slices.add(slice);
        layer = spinner(16, 0, 2047, 1);
        layer.addChangeListener(
                e -> submit(() -> viewLayer = ((Number) layer.getValue()).intValue()));
        slices.add(layer, BorderLayout.SOUTH);
        views.addTab("2D slice", slices);
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(285, 660));
        JComboBox<String> tools =
                new JComboBox<>(new String[] {"Place", "Remove", "Pick", "Select", "Mob"});
        tools.addActionListener(e -> tool = (String) tools.getSelectedItem());
        sidebar.add(new JLabel("Edit tool"));
        sidebar.add(tools);
        JSpinner size = spinner(1, 1, 8, 1);
        size.addChangeListener(e -> brush = ((Number) size.getValue()).intValue());
        sidebar.add(row("Brush radius", size));
        JTextField search = new JTextField();
        sidebar.add(row("Find block", search));
        JList<String> blocks = new JList<>(palette);
        blocks.setVisibleRowCount(8);
        blocks.addListSelectionListener(
                e -> {
                    String value = blocks.getSelectedValue();
                    if (value != null)
                        block = Integer.parseInt(value.substring(0, value.indexOf(' ')));
                });
        search.getDocument()
                .addDocumentListener(
                        new javax.swing.event.DocumentListener() {
                            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                                filterBlocks(search.getText());
                            }

                            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                                filterBlocks(search.getText());
                            }

                            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                                filterBlocks(search.getText());
                            }
                        });
        filterBlocks("");
        sidebar.add(new JScrollPane(blocks));
        sidebar.add(button("Fill selection", () -> edit(() -> fill(block))));
        sidebar.add(button("Clear selection", () -> edit(() -> fill(0))));
        sidebar.add(new JLabel("Mobs"));
        String[] names = MobRegistry.displayNames();
        Arrays.sort(names);
        JComboBox<String> types = new JComboBox<>(names);
        sidebar.add(types);
        sidebar.add(
                button(
                        "Add at camera",
                        () ->
                                edit(
                                        () -> {
                                            Mob mob =
                                                    MobRegistry.create(
                                                            MobRegistry.idOfName(
                                                                    (String)
                                                                            types
                                                                                    .getSelectedItem()),
                                                            document.level(),
                                                            x,
                                                            y,
                                                            z);
                                            if (mob == null)
                                                throw new IOException("Cannot create mob");
                                            document.level().addEntity(mob);
                                            selected = mob;
                                        })));
        entities = new JList<>(mobList);
        entities.setVisibleRowCount(5);
        entities.setCellRenderer(
                (list, mob, index, selected, focus) -> {
                    JLabel label = new JLabel(mob.getClass().getSimpleName());
                    label.setOpaque(true);
                    label.setBackground(selected ? new Color(190, 215, 255) : Color.WHITE);
                    return label;
                });
        entities.addListSelectionListener(
                e -> {
                    if (!updatingList && !e.getValueIsAdjusting()) {
                        Mob value = entities.getSelectedValue();
                        commands.add(
                                () -> {
                                    selected = value;
                                    refreshProperties();
                                });
                    }
                });
        sidebar.add(new JScrollPane(entities));
        mx = spinner(0, -30000000, 30000000, .25);
        my = spinner(0, -2048, 4096, .25);
        mz = spinner(0, -30000000, 30000000, .25);
        rotation = spinner(0, -360, 360, 5);
        health = spinner(20, 1, 100000, 1);
        sidebar.add(row("X", mx));
        sidebar.add(row("Y", my));
        sidebar.add(row("Z", mz));
        sidebar.add(row("Yaw", rotation));
        sidebar.add(row("Health", health));
        JButton apply = new JButton("Apply mob properties");
        apply.addActionListener(
                e -> {
                    float px = number(mx), py = number(my), pz = number(mz), rot = number(rotation);
                    int hp = (int) number(health);
                    submit(
                            () ->
                                    edit(
                                            () -> {
                                                if (selected == null) return;
                                                selected.moveTo(px, py, pz, rot, selected.xRot);
                                                selected.yBodyRot = selected.yBodyRotO = rot;
                                                selected.health = hp;
                                            }));
                });
        sidebar.add(apply);
        sidebar.add(button("Duplicate mob", () -> edit(this::duplicateMob)));
        sidebar.add(
                button(
                        "Delete mob",
                        () ->
                                edit(
                                        () -> {
                                            if (selected != null) {
                                                document.level().removeEntity(selected);
                                                selected = null;
                                            }
                                        })));
        sidebar.add(button("World settings", this::settings));
        JScrollPane controls = new JScrollPane(sidebar);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, views, controls);
        split.setResizeWeight(1);
        frame.add(split);
        status = new JLabel("WASD move | space / C vertical | right drag look | left drag edit");
        frame.add(status, BorderLayout.SOUTH);
        frame.pack();
        frame.setMinimumSize(new Dimension(900, 600));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private volatile java.awt.image.BufferedImage sliceImage;

    private void filterBlocks(String query) {
        palette.clear();
        for (int id = 1; id < Block.blocks.length; id++) {
            Block value = Block.blocks[id];
            if (value == null) continue;
            String label = id + " " + blockName(id);
            if (label.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))
                palette.addElement(label);
        }
    }

    private static String blockName(int id) {
        for (java.lang.reflect.Field field : Block.class.getFields()) {
            try {
                if (field.getType() == Block.class && field.get(null) == Block.blocks[id])
                    return field.getName().replace('_', ' ');
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return Block.blocks[id].getClass().getSimpleName();
    }

    private static JSpinner spinner(double value, double min, double max, double step) {
        return new JSpinner(new SpinnerNumberModel(value, min, max, step));
    }

    private static JPanel row(String text, JComponent component) {
        JPanel panel = new JPanel(new BorderLayout(4, 2));
        panel.add(new JLabel(text), BorderLayout.WEST);
        panel.add(component);
        panel.setMaximumSize(new Dimension(1000, 32));
        return panel;
    }

    private static float number(JSpinner spinner) {
        return ((Number) spinner.getValue()).floatValue();
    }

    private void installInput() {
        canvas.setFocusable(true);
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addPropertyChangeListener(
                        "focusOwner",
                        event -> {
                            if (event.getNewValue() instanceof Component focused
                                    && SwingUtilities.isDescendingFrom(focused, frame)) {
                                viewportInputEnabled = focused == canvas;
                            }
                        });
    }

    private boolean viewportActive() {
        return viewportInputEnabled && !dialogOpen && canvas.isShowing() && Display.isActive();
    }

    private void pollInput() {
        boolean active = !dialogOpen && canvas.isShowing() && Display.isActive();
        while (Keyboard.next()) {
            if (!active || !viewportInputEnabled || !Keyboard.getEventKeyState()) continue;
            boolean control =
                    Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)
                            || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
            if (!control) continue;
            switch (Keyboard.getEventKey()) {
                case Keyboard.KEY_O -> submit(this::open);
                case Keyboard.KEY_S -> {
                    boolean saveAs =
                            Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                                    || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
                    submit(() -> save(saveAs));
                }
                case Keyboard.KEY_Z -> submit(document::undo);
                case Keyboard.KEY_Y -> submit(document::redo);
            }
        }
        int dx = Mouse.getDX(), dy = Mouse.getDY(), wheel = Mouse.getDWheel();
        mouseX = Mouse.getX();
        mouseY = canvas.getHeight() - 1 - Mouse.getY();
        if (active && viewportInputEnabled && Mouse.isButtonDown(1)) {
            yaw += dx * .3f;
            pitch = Math.max(-89, Math.min(89, pitch - dy * .3f));
        }
        if (active && wheel != 0) y += wheel / 120f;
        while (Mouse.next()) {
            if (active && Mouse.getEventButton() >= 0 && Mouse.getEventButtonState()) {
                viewportInputEnabled = true;
            }
            if (Mouse.getEventButton() != 0) continue;
            if (active && Mouse.getEventButtonState()) {
                submit(
                        () -> {
                            if (document.simulating()) return;
                            document.begin();
                            if (tool.equals("Select")) selectionStarted = false;
                            painting = true;
                            selectOrPaint();
                        });
            } else commands.add(this::finishStroke);
        }
        if (!viewportActive()) finishStroke();
        else if (painting && Mouse.isButtonDown(0) && (dx != 0 || dy != 0))
            submit(this::selectOrPaint);
    }

    private void finishStroke() {
        if (!painting) return;
        painting = false;
        submit(document::commit);
    }

    private void moveCamera() {
        if (!viewportActive()) return;
        float speed =
                Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)
                        ? 1f
                        : .25f;
        float sy = (float) Math.sin(Math.toRadians(yaw)),
                cy = (float) Math.cos(Math.toRadians(yaw));
        if (Keyboard.isKeyDown(Keyboard.KEY_W)) {
            x += sy * speed;
            z -= cy * speed;
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_S)
                && !Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)
                && !Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) {
            x -= sy * speed;
            z += cy * speed;
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_A)) {
            x -= cy * speed;
            z -= sy * speed;
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_D)) {
            x += cy * speed;
            z += sy * speed;
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_SPACE)) y += speed;
        if (Keyboard.isKeyDown(Keyboard.KEY_C)) y -= speed;
    }

    private Vec3D rayEnd() {
        float width = Math.max(1, canvas.getWidth()), height = Math.max(1, canvas.getHeight());
        double scale = Math.tan(Math.toRadians(35));
        double rx = (2 * mouseX / width - 1) * width / height * scale;
        double ry = (1 - 2 * mouseY / height) * scale;
        double cp = Math.cos(Math.toRadians(pitch)), sp = Math.sin(Math.toRadians(pitch));
        double cy = Math.cos(Math.toRadians(yaw)), sy = Math.sin(Math.toRadians(yaw));
        double up = ry * cp - sp, forward = -ry * sp - cp;
        return new Vec3D(
                x + (float) (rx * cy - forward * sy) * 100,
                y + (float) up * 100,
                z + (float) (rx * sy + forward * cy) * 100);
    }

    private void selectOrPaint() throws Exception {
        Level world = document.level();
        Vec3D start = new Vec3D(x, y, z), end = rayEnd();
        MovingObjectPosition hit = world.clip(start, end);
        if (tool.equals("Mob")) {
            selected = null;
            double distance = hit == null ? Double.MAX_VALUE : dist(start, hit.vec);
            for (Object value : world.blockMap.all)
                if (value instanceof Mob mob && !(mob instanceof Player)) {
                    MovingObjectPosition entityHit = mob.bb.clip(start, end);
                    if (entityHit != null && dist(start, entityHit.vec) < distance) {
                        selected = mob;
                        distance = dist(start, entityHit.vec);
                    }
                }
            refreshProperties();
            return;
        }
        if (hit == null) return;
        if (tool.equals("Pick")) {
            block = world.getTile(hit.x, hit.y, hit.z);
            return;
        }
        if (tool.equals("Select")) {
            last = new int[] {hit.x, hit.y, hit.z};
            if (!selectionStarted) {
                first = last.clone();
                selectionStarted = true;
            }
            return;
        }
        int bx = hit.x, by = hit.y, bz = hit.z;
        if (tool.equals("Place")) {
            if (hit.face == 0) by--;
            if (hit.face == 1) by++;
            if (hit.face == 2) bz--;
            if (hit.face == 3) bz++;
            if (hit.face == 4) bx--;
            if (hit.face == 5) bx++;
        }
        paint(bx, by, bz, tool.equals("Remove") ? 0 : block);
    }

    private static double dist(Vec3D a, Vec3D b) {
        return Math.pow(a.x - b.x, 2) + Math.pow(a.y - b.y, 2) + Math.pow(a.z - b.z, 2);
    }

    private void paint(int bx, int by, int bz, int id) {
        Level world = document.level();
        for (int dx = 1 - brush; dx < brush; dx++)
            for (int dy = 1 - brush; dy < brush; dy++)
                for (int dz = 1 - brush; dz < brush; dz++)
                    if (inBounds(world, bx + dx, by + dy, bz + dz))
                        world.setTile(bx + dx, by + dy, bz + dz, id);
        rebuild = true;
    }

    private static boolean inBounds(Level world, int x, int y, int z) {
        if (world instanceof LevelInfiniteFlat || world instanceof LevelInfiniteTerrain)
            return y >= 0 && y < world.depth;
        return world.isInBounds(x, y, z);
    }

    private void fill(int id) throws IOException {
        if (first == null || last == null) return;
        long count =
                (long) (Math.abs(first[0] - last[0]) + 1)
                        * (Math.abs(first[1] - last[1]) + 1)
                        * (Math.abs(first[2] - last[2]) + 1);
        if (count > 1000000) throw new IOException("Selection exceeds one million blocks");
        Level world = document.level();
        for (int bx = Math.min(first[0], last[0]); bx <= Math.max(first[0], last[0]); bx++)
            for (int by = Math.min(first[1], last[1]); by <= Math.max(first[1], last[1]); by++)
                for (int bz = Math.min(first[2], last[2]); bz <= Math.max(first[2], last[2]); bz++)
                    if (inBounds(world, bx, by, bz)) world.setTile(bx, by, bz, id);
        selectionStarted = false;
    }

    private void edit(Action action) throws Exception {
        document.begin();
        try {
            action.run();
        } finally {
            document.commit();
        }
    }

    private void duplicateMob() throws IOException {
        if (selected == null) return;
        // Use the world codec to copy all persisted equipment and per-mob properties.
        Level clone =
                new LevelIO(null)
                        .load(
                                new ByteArrayInputStream(
                                        WorldEditorDocument.snapshot(document.level())));
        try {
            int index = mobs(document.level()).indexOf(selected);
            java.util.List<Mob> copies = mobs(clone);
            if (index < 0 || index >= copies.size())
                throw new IOException("Cannot duplicate this mob");
            Mob copy = copies.get(index);
            clone.removeEntity(copy);
            copy.level = document.level();
            copy.setPos(selected.x + 1, selected.y, selected.z);
            document.level().addEntity(copy);
            selected = copy;
        } finally {
            if (clone instanceof LevelInfiniteFlat flat) flat.chunks().shutdown();
            if (clone instanceof LevelInfiniteTerrain terrain) terrain.chunks().shutdown();
        }
    }

    private static java.util.List<Mob> mobs(Level world) {
        java.util.List<Mob> result = new ArrayList<>();
        if (world != null && world.blockMap != null)
            for (Object value : world.blockMap.all)
                if (value instanceof Mob mob
                        && !(mob instanceof Player)
                        && !mob.removed
                        && mob.health > 0) result.add(mob);
        return result;
    }

    private void refreshProperties() {
        Mob value = selected;
        if (value == null) return;
        float px = value.x, py = value.y, pz = value.z, rot = value.yRot;
        int hp = value.health;
        SwingUtilities.invokeLater(
                () -> {
                    mx.setValue((double) px);
                    my.setValue((double) py);
                    mz.setValue((double) pz);
                    rotation.setValue((double) (rot % 360));
                    health.setValue((double) Math.max(1, hp));
                });
    }

    private void refresh() throws IOException {
        Level world = document.level();
        if (world == null) return;
        if (selected != null && !world.blockMap.all.contains(selected)) selected = null;
        java.util.List<Mob> values = mobs(world);
        boolean dirty = document.dirty();
        String text =
                (document.file() == null ? "Untitled" : document.file().getName())
                        + (dirty ? " *" : "")
                        + (document.simulating() ? " | TEST COPY" : " | Paused")
                        + " | WASD move, space/C vertical, right drag look, left edit";
        SwingUtilities.invokeLater(
                () -> {
                    status.setText(text);
                    frame.setTitle("Classic World Editor - " + text.split(" \\|")[0]);
                    updatingList = true;
                    mobList.clear();
                    for (Mob value : values) mobList.addElement(value);
                    entities.setSelectedValue(selected, true);
                    updatingList = false;
                });
        updateSlice();
    }

    private void updateSlice() {
        Level world = document.level();
        java.awt.image.BufferedImage image =
                new java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int dx = 0; dx < 64; dx++)
            for (int dz = 0; dz < 64; dz++) {
                int bx = (int) x - 32 + dx, bz = (int) z - 32 + dz;
                int id = inBounds(world, bx, viewLayer, bz) ? world.getTile(bx, viewLayer, bz) : 0;
                image.setRGB(
                        dx, dz, id == 0 ? 0x202830 : Color.HSBtoRGB((id * .071f) % 1, .5f, .8f));
            }
        sliceImage = image;
        SwingUtilities.invokeLater(slice::repaint);
    }

    private boolean confirmDiscard() throws Exception {
        if (!document.dirty()) return true;
        int answer =
                onUi(
                        () ->
                                JOptionPane.showConfirmDialog(
                                        frame,
                                        "Save changes before continuing?",
                                        "Unsaved world",
                                        JOptionPane.YES_NO_CANCEL_OPTION));
        if (answer == JOptionPane.CANCEL_OPTION || answer == JOptionPane.CLOSED_OPTION)
            return false;
        return answer != JOptionPane.YES_OPTION || save(false);
    }

    private <T> T onUi(Callable<T> call) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) return call.call();
        FutureTask<T> task = new FutureTask<>(call);
        dialogOpen = true;
        viewportInputEnabled = false;
        try {
            SwingUtilities.invokeLater(task);
            // The native child window belongs to this thread. Modal AWT dialogs send
            // it synchronous window messages, so this thread must keep pumping them.
            while (!task.isDone()) {
                if (Display.isCreated()) Display.processMessages();
                Thread.sleep(5);
            }
            return task.get();
        } finally {
            dialogOpen = false;
            if (Keyboard.isCreated()) {
                Keyboard.poll();
                while (Keyboard.next()) {}
            }
            if (Mouse.isCreated()) {
                Mouse.poll();
                while (Mouse.next()) {}
                Mouse.getDX();
                Mouse.getDY();
                Mouse.getDWheel();
            }
            SwingUtilities.invokeLater(
                    () -> {
                        if (frame.isDisplayable()) canvas.requestFocusInWindow();
                    });
        }
    }

    private File choose(boolean save) throws Exception {
        return onUi(
                () -> {
                    JFileChooser picker = new JFileChooser(appDir());
                    picker.setFileFilter(
                            new javax.swing.filechooser.FileNameExtensionFilter(
                                    "Classic Reborn world (*.dat)", "dat"));
                    if (document.file() != null) picker.setSelectedFile(document.file());
                    int result = save ? picker.showSaveDialog(frame) : picker.showOpenDialog(frame);
                    if (result != JFileChooser.APPROVE_OPTION) return null;
                    File file = picker.getSelectedFile();
                    if (save && !file.getName().toLowerCase(Locale.ROOT).endsWith(".dat"))
                        file = new File(file + ".dat");
                    if (save
                            && file.exists()
                            && (document.file() == null || !file.equals(document.file()))
                            && JOptionPane.showConfirmDialog(
                                            frame,
                                            "Replace " + file.getName() + "?",
                                            "Save as",
                                            JOptionPane.YES_NO_OPTION)
                                    != JOptionPane.YES_OPTION) return null;
                    return file;
                });
    }

    private void open() throws Exception {
        if (!confirmDiscard()) return;
        File file = choose(false);
        if (file == null) return;
        document.open(file);
        first = last = null;
        selected = null;
        resetCamera();
    }

    private boolean save(boolean as) throws Exception {
        File file = as || document.file() == null ? choose(true) : document.file();
        if (file == null) return false;
        document.save(file);
        return true;
    }

    private void newWorld() throws Exception {
        if (!confirmDiscard()) return;
        String type =
                onUi(
                        () ->
                                (String)
                                        JOptionPane.showInputDialog(
                                                frame,
                                                "World type",
                                                "New world",
                                                JOptionPane.PLAIN_MESSAGE,
                                                null,
                                                new String[] {
                                                    "Finite", "Infinite flat", "Infinite terrain"
                                                },
                                                "Finite"));
        if (type != null) {
            createWorld(type);
            resetCamera();
            first = last = null;
            selected = null;
        }
    }

    private void createWorld(String type) throws IOException {
        Level world;
        if (type.equals("Infinite flat")) world = new LevelInfiniteFlat(System.nanoTime(), 64);
        else if (type.equals("Infinite terrain"))
            world = new LevelInfiniteTerrain(System.nanoTime(), 64);
        else {
            world = new Level();
            world.setData(64, 32, 64, new byte[64 * 32 * 64]);
            for (int bx = 0; bx < 64; bx++)
                for (int bz = 0; bz < 64; bz++) world.setTile(bx, 0, bz, Block.GRASS.id);
            world.xSpawn = 32;
            world.ySpawn = 3;
            world.zSpawn = 32;
        }
        world.name = "Untitled";
        world.creativeMode = true;
        document.replace(world, null);
    }

    private void resetCamera() {
        Level world = document.view();
        x = world.xSpawn;
        y = Math.min(world.depth - 2, world.ySpawn + 12);
        z = world.zSpawn + 18;
        yaw = 0;
        pitch = 30;
        rebuild = true;
    }

    private void toggleSimulation() throws IOException {
        if (document.simulating()) {
            document.stopSimulation();
            return;
        }
        document.startSimulation();
        Level world = document.view();
        // Replace the cloned player only in the disposable simulation world.
        if (world.player != null) world.removeEntity(world.player);
        Player player = new PreviewPlayer(world);
        player.setPos(x, y, z);
        nextTick = System.nanoTime();
    }

    /** A simulation target with world physics and no game keyboard or HUD dependency. */
    private static final class PreviewPlayer extends Player {
        PreviewPlayer(Level world) {
            super(world);
        }

        @Override
        public void aiStep() {
            move(xd, yd, zd);
            yd = (yd - .08f) * .98f;
            xd *= .8f;
            zd *= .8f;
        }
    }

    private void settings() throws Exception {
        Level world = document.level();
        String current =
                world.xSpawn
                        + ","
                        + world.ySpawn
                        + ","
                        + world.zSpawn
                        + ","
                        + world.timeOfDay
                        + ","
                        + world.creativeMode
                        + ","
                        + world.doDayNightCycle
                        + ","
                        + world.skyColor
                        + ","
                        + world.fogColor
                        + ","
                        + world.cloudColor
                        + ","
                        + world.waterLevel;
        String value =
                onUi(
                        () ->
                                JOptionPane.showInputDialog(
                                        frame,
                                        "Spawn X,Y,Z, time, creative, daylight cycle, sky RGB, fog"
                                                + " RGB, cloud RGB, water level",
                                        current));
        if (value == null) return;
        String[] fields = value.split(",");
        if (fields.length != 10) throw new IOException("Enter ten comma-separated values");
        int sx = Integer.parseInt(fields[0].trim()),
                sy = Integer.parseInt(fields[1].trim()),
                sz = Integer.parseInt(fields[2].trim()),
                time = Integer.parseInt(fields[3].trim());
        int sky = Integer.decode(fields[6].trim()),
                fog = Integer.decode(fields[7].trim()),
                cloud = Integer.decode(fields[8].trim()),
                water = Integer.parseInt(fields[9].trim());
        if (!inBounds(world, sx, sy, sz) || water < 0 || water >= world.depth)
            throw new IOException("Spawn or water level is outside the world");
        if (!fields[4].trim().matches("(?i)true|false")
                || !fields[5].trim().matches("(?i)true|false"))
            throw new IOException("Use true or false for switches");
        edit(
                () -> {
                    world.xSpawn = sx;
                    world.ySpawn = sy;
                    world.zSpawn = sz;
                    world.setTime(time);
                    world.creativeMode = Boolean.parseBoolean(fields[4].trim());
                    world.doDayNightCycle = Boolean.parseBoolean(fields[5].trim());
                    world.skyColor = sky;
                    world.fogColor = fog;
                    world.cloudColor = cloud;
                    world.waterLevel = water;
                });
    }

    private void render() {
        Level world = document.view();
        GL11.glViewport(0, 0, canvas.getWidth(), Math.max(1, canvas.getHeight()));
        GL11.glClearColor(
                ((world.skyColor >> 16) & 255) / 255f,
                ((world.skyColor >> 8) & 255) / 255f,
                (world.skyColor & 255) / 255f,
                1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, .1f);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GLU.gluPerspective(
                70, canvas.getWidth() / (float) Math.max(1, canvas.getHeight()), .05f, 220);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glRotatef(pitch, 1, 0, 0);
        GL11.glRotatef(yaw, 0, 1, 0);
        GL11.glTranslatef(-x, -y, -z);
        int cx = Math.floorDiv((int) Math.floor(x), 16),
                cz = Math.floorDiv((int) Math.floor(z), 16);
        if (rebuild || cx != regionX || cz != regionZ) {
            regionX = cx;
            regionZ = cz;
            for (int pass = 0; pass < 2; pass++) {
                GL11.glNewList(opaqueList + pass, GL11.GL_COMPILE);
                ShapeRenderer renderer = ShapeRenderer.instance;
                renderer.begin();
                for (int bx = cx * 16 - 32; bx < cx * 16 + 48; bx++)
                    for (int bz = cz * 16 - 32; bz < cz * 16 + 48; bz++)
                        for (int by = 0; by < world.depth; by++) {
                            if (!inBounds(world, bx, by, bz)) continue;
                            int id = world.getTile(bx, by, bz);
                            if (id > 0
                                    && Block.blocks[id] != null
                                    && Block.blocks[id].getRenderPass() == pass)
                                Block.blocks[id].render(world, bx, by, bz, renderer);
                        }
                renderer.end();
                GL11.glEndList();
            }
            rebuild = false;
        }
        GL11.glBindTexture(
                GL11.GL_TEXTURE_2D,
                textures.load(net.classicremastered.minecraft.render.BlockTextureAtlas.RESOURCE));
        GL11.glCallList(opaqueList);
        net.classicremastered.minecraft.level.ChestController.render(
                world, textures, net.classicremastered.minecraft.render.FrustrumImpl.update());
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        GL11.glCallList(translucentList);
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);
        for (Mob mob : mobs(world))
            if (Math.abs(mob.x - x) < 96 && Math.abs(mob.z - z) < 96) mob.render(textures, 1);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        if (selected != null && !document.simulating())
            outline(
                    selected.bb.x0,
                    selected.bb.y0,
                    selected.bb.z0,
                    selected.bb.x1,
                    selected.bb.y1,
                    selected.bb.z1);
        if (first != null && last != null)
            outline(
                    Math.min(first[0], last[0]),
                    Math.min(first[1], last[1]),
                    Math.min(first[2], last[2]),
                    Math.max(first[0], last[0]) + 1,
                    Math.max(first[1], last[1]) + 1,
                    Math.max(first[2], last[2]) + 1);
    }

    private static void outline(float x0, float y0, float z0, float x1, float y1, float z1) {
        GL11.glColor3f(1, 1, 0);
        GL11.glLineWidth(2);
        float[][] points = {
            {x0, y0, z0},
            {x1, y0, z0},
            {x1, y0, z1},
            {x0, y0, z1},
            {x0, y1, z0},
            {x1, y1, z0},
            {x1, y1, z1},
            {x0, y1, z1}
        };
        int[][] edges = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6},
            {3, 7}
        };
        GL11.glBegin(GL11.GL_LINES);
        for (int[] edge : edges)
            for (int index : edge)
                GL11.glVertex3f(points[index][0], points[index][1], points[index][2]);
        GL11.glEnd();
        GL11.glColor3f(1, 1, 1);
    }
}
