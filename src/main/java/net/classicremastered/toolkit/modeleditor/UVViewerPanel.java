package net.classicremastered.toolkit.modeleditor;

import java.awt.image.BufferedImage;



import javax.swing.*;
import java.awt.*;

final class UVViewerPanel extends JPanel {
        private final ModelEditorApp editor;
        private final java.util.function.Supplier<BufferedImage> texSupplier;
        private final java.util.function.Supplier<EditableModel> modelSupplier;
        private final java.util.function.Supplier<EditablePart> selSupplier;

        private double zoom = 1.0;
        private double panX = 0, panY = 0;
        private Point lastDrag = null;
        private boolean needsFit = true;

        void fitToView() { needsFit = true; repaint(); }

        // local Rect (only for this panel)
        private final class Rect {
            final int x, y, w, h;

            Rect(int x, int y, int w, int h) {
                this.x = x;
                this.y = y;
                this.w = w;
                this.h = h;
            }
        }

        UVViewerPanel(ModelEditorApp editor, java.util.function.Supplier<BufferedImage> texSupplier,
                java.util.function.Supplier<EditableModel> modelSupplier,
                java.util.function.Supplier<EditablePart> selSupplier) {
            this.editor = editor;
            this.texSupplier = texSupplier;
            this.modelSupplier = modelSupplier;
            this.selSupplier = selSupplier;

            setBackground(new Color(0x131317));

            addMouseWheelListener(e -> {
                double old = zoom;
                zoom *= (e.getPreciseWheelRotation() < 0 ? 1.1 : 0.9);
                zoom = Math.max(0.1, Math.min(16, zoom));
                Point p = e.getPoint();
                panX = p.x - (p.x - panX) * (zoom / old);
                panY = p.y - (p.y - panY) * (zoom / old);
                repaint();
            });
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e) || SwingUtilities.isMiddleMouseButton(e)) {
                        lastDrag = e.getPoint();
                    }
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        pickPartAt(e.getPoint());
                    }
                }

                @Override
                public void mouseReleased(java.awt.event.MouseEvent e) {
                    lastDrag = null;
                }
            });
            addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override
                public void mouseDragged(java.awt.event.MouseEvent e) {
                    if (lastDrag != null) {
                        panX += e.getX() - lastDrag.x;
                        panY += e.getY() - lastDrag.y;
                        lastDrag = e.getPoint();
                        repaint();
                    }
                }
            });
        }

        void setTexture(BufferedImage img) {
            needsFit = true;
            repaint();
        }

        void setSelected(EditablePart p) {
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            BufferedImage tex = texSupplier.get();
            EditableModel m = modelSupplier.get();
            if (m == null)
                return;

            int aw = (tex != null ? tex.getWidth() : Math.max(1, m.atlasW));
            int ah = (tex != null ? tex.getHeight() : Math.max(1, m.atlasH));

            if (needsFit && getWidth() > 32 && getHeight() > 32) {
                zoom = Math.max(0.1, Math.min(16, Math.min((getWidth() - 32.0) / aw, (getHeight() - 32.0) / ah)));
                panX = (getWidth() - aw * zoom) * 0.5;
                panY = (getHeight() - ah * zoom) * 0.5;
                needsFit = false;
            }

            drawChecker(g);

            g.translate(panX, panY);
            g.scale(zoom, zoom);
            g.setFont(g.getFont().deriveFont((float)(12.0 / zoom)));
            g.setStroke(new BasicStroke((float)(1.0 / zoom)));

            if (tex != null)
                g.drawImage(tex, 0, 0, null);
            else {
                g.setColor(new Color(0x2a3240));
                g.fillRect(0, 0, aw, ah);
                g.setColor(new Color(0x44506a));
                g.drawRect(0, 0, aw - 1, ah - 1);
            }

            // all parts UVs
            for (EditablePart p : m.parts)
                drawPartFaces(g, p, false);

            // highlight selected
            EditablePart sel = selSupplier.get();
            if (sel != null) {
                g.setColor(new Color(0x5aa0ff));
                g.setStroke(new BasicStroke(2.0f / (float) zoom));
                for (Rect r : faceRects(sel))
                    g.drawRect(r.x, r.y, r.w, r.h);
                Rect nameAt = faceRects(sel)[4]; // -Z
                g.drawString(sel.name, (float)(nameAt.x + 4.0 / zoom), (float)(nameAt.y + 12.0 / zoom));
            }
            } finally { g.dispose(); }
        }

        private void drawChecker(Graphics2D g) {
            final int cell = 16;
            for (int y = 0; y < getHeight(); y += cell)
                for (int x = 0; x < getWidth(); x += cell) {
                    boolean a = ((x ^ y) & cell) == 0;
                    g.setColor(a ? new Color(0x1E1E24) : new Color(0x16161C));
                    g.fillRect(x, y, cell, cell);
                }
        }

        // Classic cross layout, per-face rects (Blockbench-like)
        private Rect[] faceRects(EditablePart p) {
            int u = p.u, v = p.v, w = p.w, h = p.h, d = p.d;
            Rect negY = new Rect(u + d, v, w, d); // bottom
            Rect posY = new Rect(u + d + w, v, w, d); // top
            Rect negX = new Rect(u, v + d, d, h);
            Rect posX = new Rect(u + d + w, v + d, d, h);
            Rect negZ = new Rect(u + d, v + d, w, h); // front
            Rect posZ = new Rect(u + d + w + d, v + d, w, h); // back
            return new Rect[] { negY, posY, negX, posX, negZ, posZ };
        }

        private void drawPartFaces(Graphics2D g, EditablePart p, boolean highlight) {
            Color fill = pastelFromName(p.name, highlight ? 140 : 90);
            Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

            for (Rect r : faceRects(p)) {
                if (r.w <= 0 || r.h <= 0)
                    continue;
                g.setColor(fill);
                g.fillRect(r.x, r.y, r.w, r.h);
                if (highlight) {
                    g.setColor(new Color(0x5aa0ff));
                    g.setStroke(new BasicStroke(2.0f / (float) zoom));
                    g.drawRect(r.x, r.y, r.w, r.h);
                }
            }
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);

            for (EditablePart c : p.children)
                drawPartFaces(g, c, false);
        }

        private Color pastelFromName(String name, int alpha) {
            int h = (name == null ? 0 : name.hashCode());
            float hue = ((h >>> 1) & 0xFFFF) / 65535f;
            float sat = 0.30f, val = 1.00f;
            int rgb = java.awt.Color.HSBtoRGB(hue, sat, val);
            return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, Math.max(0, Math.min(255, alpha)));
        }

        private void pickPartAt(Point screenPt) {
            EditableModel m = modelSupplier.get();
            if (m == null)
                return;
            double tx = (screenPt.x - panX) / zoom;
            double ty = (screenPt.y - panY) / zoom;

            EditablePart hit = pickInList(m.parts, tx, ty);
            if (hit != null) {
                editor.selected = hit;
                editor.refreshPartListSelect(hit);
                repaint();
            }
        }

        private EditablePart pickInList(java.util.List<EditablePart> list, double x, double y) {
            for (EditablePart p : list) {
                EditablePart child = pickInList(p.children, x, y);
                if (child != null)
                    return child;
                for (Rect r : faceRects(p))
                    if (x >= r.x && y >= r.y && x < r.x + r.w && y < r.y + r.h)
                        return p;
            }
            return null;
        }
    }
