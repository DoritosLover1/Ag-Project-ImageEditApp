package additional;

import additional.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class DrawingCanvas extends JPanel {

    public enum Tool { SELECT, RECTANGLE, CIRCLE, LINE, FREEHAND }

    // --- Canvas items ---
    private final List<CanvasItem> items = new CopyOnWriteArrayList<>();
    private final Map<String, CursorPosition> remoteCursors = new HashMap<>();

    // --- Drawing state ---
    private Tool currentTool = Tool.FREEHAND;
    private Color currentColor = Color.BLACK;
    private int strokeWidth = 2;
    private boolean filled = false;

    private int startX, startY, currentX, currentY;
    private boolean drawing = false;
    private List<Point> freehandPoints = new ArrayList<>();

    // --- Selection state (Paint-style area cut) ---
    private Rectangle selectionRect = null;   // current drag rect while in SELECT mode
    private boolean selectDragging = false;

    // --- Callbacks ---
    private Consumer<DrawShape> onShapeDrawn;
    private Consumer<PastedImage> onImagePasted;
    private Consumer<CursorPosition> onCursorMoved;
    private Consumer<List<String>> onItemsCut;  // bulk delete: list of itemIds
    private Runnable onItemsChanged;

    private String myNickname;
    private String myCursorColor;

    private long lastCursorSend = 0;
    private static final long CURSOR_THROTTLE_MS = 33;

    // Animated selection dash offset
    private int dashOffset = 0;
    private javax.swing.Timer dashTimer;

    public DrawingCanvas() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(1200, 800));
        setupMouseListeners();
        setupKeyBindings();
        // Animate the selection dashes like Paint
        dashTimer = new javax.swing.Timer(80, e -> {
            if (selectionRect != null) { dashOffset = (dashOffset + 1) % 12; repaint(); }
        });
        dashTimer.start();
    }

    // ─────────────────────────── Mouse ───────────────────────────

    private void setupMouseListeners() {
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (currentTool == Tool.SELECT) {
                    selectionRect = null;
                    selectDragging = true;
                    startX = e.getX(); startY = e.getY();
                    currentX = startX; currentY = startY;
                    repaint();
                    return;
                }
                selectionRect = null;
                startX = e.getX(); startY = e.getY();
                currentX = startX; currentY = startY;
                drawing = true;
                freehandPoints.clear();
                freehandPoints.add(new Point(startX, startY));
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                currentX = e.getX(); currentY = e.getY();
                if (currentTool == Tool.SELECT) {
                    // Update the selection rectangle live
                    selectionRect = makeRect(startX, startY, currentX, currentY);
                    repaint();
                    return;
                }
                if (currentTool == Tool.FREEHAND) {
                    freehandPoints.add(new Point(currentX, currentY));
                }
                repaint();
                sendCursorMove(e.getX(), e.getY());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                currentX = e.getX(); currentY = e.getY();
                if (currentTool == Tool.SELECT) {
                    selectDragging = false;
                    selectionRect = makeRect(startX, startY, currentX, currentY);
                    // Only cut if dragged a meaningful area
                    if (selectionRect.width > 4 && selectionRect.height > 4) {
                        applyCut();
                    } else {
                        selectionRect = null;
                        repaint();
                    }
                    return;
                }
                if (!drawing) return;
                drawing = false;
                finalizeShape();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                sendCursorMove(e.getX(), e.getY());
            }
        };
        addMouseListener(adapter);
        addMouseMotionListener(adapter);
    }

    /** Cut all items that intersect the current selectionRect */
    private void applyCut() {
        if (selectionRect == null) return;
        List<String> toDelete = new ArrayList<>();
        for (CanvasItem item : items) {
            Rectangle bounds = getBounds(item);
            if (bounds != null && selectionRect.intersects(bounds)) {
                String id = item.getId();
                if (id != null) toDelete.add(id);
            }
        }
        selectionRect = null;
        if (!toDelete.isEmpty()) {
            // Remove locally
            Set<String> idSet = new HashSet<>(toDelete);
            items.removeIf(i -> idSet.contains(i.getId()));
            repaint();
            fireItemsChanged();
            // Notify server / app
            if (onItemsCut != null) onItemsCut.accept(toDelete);
        } else {
            repaint();
        }
    }

    /** Remove items by id (called when DELETE_ITEMS arrives from server for other clients) */
    public void removeItemsByIds(List<String> ids) {
        Set<String> idSet = new HashSet<>(ids);
        items.removeIf(i -> idSet.contains(i.getId()));
        SwingUtilities.invokeLater(() -> { repaint(); fireItemsChanged(); });
    }

    private Rectangle makeRect(int x1, int y1, int x2, int y2) {
        return new Rectangle(Math.min(x1, x2), Math.min(y1, y2),
                             Math.abs(x2 - x1), Math.abs(y2 - y1));
    }

    private Rectangle getBounds(CanvasItem item) {
        if (item.getItemType() == CanvasItem.ItemType.IMAGE) {
            PastedImage pi = item.getImage();
            if (pi == null) return null;
            return new Rectangle(pi.getX(), pi.getY(), pi.getWidth(), pi.getHeight());
        }
        DrawShape s = item.getShape();
        if (s == null) return null;
        switch (s.getShapeType()) {
            case RECTANGLE:
            case CIRCLE:
                return new Rectangle(s.getX(), s.getY(), s.getWidth(), s.getHeight());
            case LINE: {
                int x = Math.min(s.getX(), s.getX2());
                int y = Math.min(s.getY(), s.getY2());
                int w = Math.abs(s.getX2() - s.getX()) + 1;
                int h = Math.abs(s.getY2() - s.getY()) + 1;
                return new Rectangle(x, y, w, h);
            }
            case FREEHAND: {
                int[] xs = s.getFreehandXPoints();
                int[] ys = s.getFreehandYPoints();
                if (xs == null || xs.length == 0) return null;
                int minX = Arrays.stream(xs).min().getAsInt();
                int minY = Arrays.stream(ys).min().getAsInt();
                int maxX = Arrays.stream(xs).max().getAsInt();
                int maxY = Arrays.stream(ys).max().getAsInt();
                return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
            }
            default: return null;
        }
    }

    // ─────────────────────────── Cursor ───────────────────────────

    private void sendCursorMove(int x, int y) {
        long now = System.currentTimeMillis();
        if (now - lastCursorSend < CURSOR_THROTTLE_MS) return;
        lastCursorSend = now;
        if (onCursorMoved != null && myNickname != null)
            onCursorMoved.accept(new CursorPosition(x, y, myNickname, myCursorColor));
    }

    // ─────────────────────────── Draw shapes ───────────────────────────

    private void finalizeShape() {
        DrawShape shape = buildShape();
        if (shape == null) return;
        shape.setId(UUID.randomUUID().toString());
        items.add(new CanvasItem(shape, myNickname));
        repaint();
        fireItemsChanged();
        if (onShapeDrawn != null) onShapeDrawn.accept(shape);
    }

    private DrawShape buildShape() {
        DrawShape s = new DrawShape();
        s.setColor(colorToHex(currentColor));
        s.setStrokeWidth(strokeWidth);
        s.setFilled(filled);

        int x = Math.min(startX, currentX);
        int y = Math.min(startY, currentY);
        int w = Math.abs(currentX - startX);
        int h = Math.abs(currentY - startY);

        switch (currentTool) {
            case RECTANGLE:
                if (w < 3 && h < 3) return null;
                s.setShapeType(DrawShape.ShapeType.RECTANGLE);
                s.setX(x); s.setY(y); s.setWidth(w); s.setHeight(h);
                return s;
            case CIRCLE:
                if (w < 3 && h < 3) return null;
                s.setShapeType(DrawShape.ShapeType.CIRCLE);
                s.setX(x); s.setY(y); s.setWidth(w); s.setHeight(h);
                return s;
            case LINE:
                if (w < 3 && h < 3) return null;
                s.setShapeType(DrawShape.ShapeType.LINE);
                s.setX(startX); s.setY(startY);
                s.setX2(currentX); s.setY2(currentY);
                return s;
            case FREEHAND:
                if (freehandPoints.size() < 2) return null;
                s.setShapeType(DrawShape.ShapeType.FREEHAND);
                int[] xs = freehandPoints.stream().mapToInt(p -> p.x).toArray();
                int[] ys = freehandPoints.stream().mapToInt(p -> p.y).toArray();
                s.setFreehandXPoints(xs); s.setFreehandYPoints(ys);
                return s;
            default:
                return null;
        }
    }

    // ─────────────────────────── Keyboard ───────────────────────────

    private void setupKeyBindings() {
        setFocusable(true);
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), "paste");
        getActionMap().put("paste", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { pasteFromClipboard(); }
        });
        // Escape cancels selection
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelSel");
        getActionMap().put("cancelSel", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                selectionRect = null; repaint();
            }
        });
    }

    public void pasteFromClipboard() {
        try {
            java.awt.datatransfer.Transferable t =
                Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (t == null) return;
            if (t.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.imageFlavor)) {
                Image img = (Image) t.getTransferData(java.awt.datatransfer.DataFlavor.imageFlavor);
                BufferedImage bi = toBufferedImage(img);
                byte[] bytes = toBytes(bi);

                PastedImage pi = new PastedImage();
                pi.setId(UUID.randomUUID().toString());
                pi.setImageData(bytes);
                pi.setX(50); pi.setY(50);
                pi.setWidth(Math.min(bi.getWidth(), 400));
                pi.setHeight(Math.min(bi.getHeight(), 300));

                items.add(new CanvasItem(pi, myNickname));
                repaint();
                fireItemsChanged();
                if (onImagePasted != null) onImagePasted.accept(pi);
            }
        } catch (Exception ex) {
            System.err.println("Paste error: " + ex.getMessage());
        }
    }

    // ─────────────────────────── Paint ───────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw all items
        for (CanvasItem item : items) {
            if (item.getItemType() == CanvasItem.ItemType.SHAPE)
                drawShape(g2, item.getShape());
            else
                drawImage(g2, item.getImage());
        }

        // Draw in-progress shape preview
        if (drawing && currentTool != Tool.FREEHAND && currentTool != Tool.SELECT) {
            g2.setColor(currentColor);
            g2.setStroke(new BasicStroke(strokeWidth));
            drawPreview(g2);
        }

        // Draw Paint-style selection rectangle
        if (selectionRect != null) {
            // Semi-transparent blue fill
            g2.setColor(new Color(100, 160, 255, 40));
            g2.fillRect(selectionRect.x, selectionRect.y, selectionRect.width, selectionRect.height);

            // Animated dashed border (like Paint)
            float[] dash = {6f, 4f};
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10f, dash, dashOffset));
            g2.setColor(new Color(30, 100, 220));
            g2.drawRect(selectionRect.x, selectionRect.y, selectionRect.width, selectionRect.height);

            // "Kes" hint label inside the rect if large enough
            if (selectionRect.width > 60 && selectionRect.height > 24 && !selectDragging) {
                g2.setFont(new Font("SansSerif", Font.BOLD, 11));
                String hint = "✂ Kes";
                int tw = g2.getFontMetrics().stringWidth(hint);
                int tx = selectionRect.x + (selectionRect.width - tw) / 2;
                int ty = selectionRect.y + selectionRect.height / 2 + 4;
                g2.setColor(new Color(0, 60, 180, 200));
                g2.fillRoundRect(tx - 6, ty - 14, tw + 12, 18, 6, 6);
                g2.setColor(Color.WHITE);
                g2.drawString(hint, tx, ty);
            }
        }

        // Remote cursors
        synchronized (remoteCursors) {
            for (CursorPosition cp : remoteCursors.values())
                drawRemoteCursor(g2, cp);
        }
    }

    private void drawShape(Graphics2D g2, DrawShape s) {
        if (s == null) return;
        g2.setColor(hexToColor(s.getColor()));
        g2.setStroke(new BasicStroke(s.getStrokeWidth(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (s.getShapeType()) {
            case RECTANGLE:
                if (s.isFilled()) g2.fillRect(s.getX(), s.getY(), s.getWidth(), s.getHeight());
                else              g2.drawRect(s.getX(), s.getY(), s.getWidth(), s.getHeight());
                break;
            case CIRCLE:
                if (s.isFilled()) g2.fillOval(s.getX(), s.getY(), s.getWidth(), s.getHeight());
                else              g2.drawOval(s.getX(), s.getY(), s.getWidth(), s.getHeight());
                break;
            case LINE:
                g2.drawLine(s.getX(), s.getY(), s.getX2(), s.getY2());
                break;
            case FREEHAND:
                int[] xs = s.getFreehandXPoints();
                int[] ys = s.getFreehandYPoints();
                if (xs != null && xs.length > 1) g2.drawPolyline(xs, ys, xs.length);
                break;
            case TEXT:
                if (s.getText() != null) {
                    g2.setFont(new Font("SansSerif", Font.PLAIN, 16));
                    g2.drawString(s.getText(), s.getX(), s.getY());
                }
                break;
        }
    }

    private void drawImage(Graphics2D g2, PastedImage pi) {
        if (pi == null || pi.getImageData() == null) return;
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(pi.getImageData()));
            if (img != null)
                g2.drawImage(img, pi.getX(), pi.getY(), pi.getWidth(), pi.getHeight(), null);
        } catch (IOException e) { System.err.println("Image draw error: " + e.getMessage()); }
    }

    private void drawPreview(Graphics2D g2) {
        int x = Math.min(startX, currentX), y = Math.min(startY, currentY);
        int w = Math.abs(currentX - startX), h = Math.abs(currentY - startY);
        g2.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (currentTool) {
            case RECTANGLE:
                if (filled) g2.fillRect(x, y, w, h); else g2.drawRect(x, y, w, h); break;
            case CIRCLE:
                if (filled) g2.fillOval(x, y, w, h); else g2.drawOval(x, y, w, h); break;
            case LINE:
                g2.drawLine(startX, startY, currentX, currentY); break;
        }
    }

    private void drawRemoteCursor(Graphics2D g2, CursorPosition cp) {
        Color c = hexToColor(cp.getColor());
        int[] cx = {cp.getX(), cp.getX() + 10, cp.getX() + 4};
        int[] cy = {cp.getY(), cp.getY() + 10, cp.getY() + 14};
        g2.setColor(c); g2.fillPolygon(cx, cy, 3);
        g2.setColor(Color.WHITE); g2.setStroke(new BasicStroke(1)); g2.drawPolygon(cx, cy, 3);
        g2.setFont(new Font("SansSerif", Font.BOLD, 11));
        g2.setColor(c);
        g2.fillRoundRect(cp.getX() + 12, cp.getY() + 2,
            g2.getFontMetrics().stringWidth(cp.getUsername()) + 8, 16, 6, 6);
        g2.setColor(Color.WHITE);
        g2.drawString(cp.getUsername(), cp.getX() + 16, cp.getY() + 14);
    }

    // ─────────────────────────── Public API ───────────────────────────

    public void addRemoteShape(DrawShape shape, String sender) {
        items.add(new CanvasItem(shape, sender));
        SwingUtilities.invokeLater(() -> { repaint(); fireItemsChanged(); });
    }

    public void addRemoteImage(PastedImage image, String sender) {
        items.add(new CanvasItem(image, sender));
        SwingUtilities.invokeLater(() -> { repaint(); fireItemsChanged(); });
    }

    public void removeItemById(String id) {
        items.removeIf(i -> id.equals(i.getId()));
        SwingUtilities.invokeLater(() -> { repaint(); fireItemsChanged(); });
    }

    public void updateRemoteCursor(CursorPosition cp) {
        synchronized (remoteCursors) { remoteCursors.put(cp.getUsername(), cp); }
        SwingUtilities.invokeLater(this::repaint);
    }

    public void removeRemoteCursor(String nickname) {
        synchronized (remoteCursors) { remoteCursors.remove(nickname); }
        SwingUtilities.invokeLater(this::repaint);
    }

    public void loadCanvasState(List<CanvasItem> snapshot) {
        items.clear(); items.addAll(snapshot);
        selectionRect = null;
        SwingUtilities.invokeLater(() -> { repaint(); fireItemsChanged(); });
    }

    public void clearCanvas() {
        items.clear(); selectionRect = null;
        SwingUtilities.invokeLater(() -> { repaint(); fireItemsChanged(); });
    }

    public List<CanvasItem> getItemsSnapshot() { return new ArrayList<>(items); }

    private void fireItemsChanged() {
        if (onItemsChanged != null) onItemsChanged.run();
    }

    // ─────────────────────────── Setters ───────────────────────────

    public void setCurrentTool(Tool tool) {
        this.currentTool = tool;
        if (tool == Tool.SELECT) {
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        } else {
            selectionRect = null;
            repaint();
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        }
    }

    public void setCurrentColor(Color color) { this.currentColor = color; }
    public Color getCurrentColor() { return currentColor; }
    public void setStrokeWidth(int w) { this.strokeWidth = w; }
    public void setFilled(boolean f) { this.filled = f; }
    public void setUsername(String n) { this.myNickname = n; }
    public void setCursorColor(Color cursorColor) { this.myCursorColor = cursorColor.toString(); }

    public void setOnShapeDrawn(Consumer<DrawShape> cb) { this.onShapeDrawn = cb; }
    public void setOnImagePasted(Consumer<PastedImage> cb) { this.onImagePasted = cb; }
    public void setOnCursorMoved(Consumer<CursorPosition> cb) { this.onCursorMoved = cb; }
    public void setOnItemsCut(Consumer<List<String>> cb) { this.onItemsCut = cb; }
    public void setOnItemsChanged(Runnable cb) { this.onItemsChanged = cb; }

    // ─────────────────────────── Helpers ───────────────────────────

    private static String colorToHex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static Color hexToColor(String hex) {
        if (hex == null) return Color.BLACK;
        try { return Color.decode(hex); } catch (Exception e) { return Color.BLACK; }
    }

    private static BufferedImage toBufferedImage(Image img) {
        if (img instanceof BufferedImage) return (BufferedImage) img;
        BufferedImage bi = new BufferedImage(img.getWidth(null), img.getHeight(null),
                                             BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = bi.createGraphics();
        g2.drawImage(img, 0, 0, null);
        g2.dispose();
        return bi;
    }
    
    public List<String> getItemDescriptions() {
        List<String> result = new ArrayList<>();
        for (CanvasItem item : items) {
            result.add(item.getLabel());
        }
        return result;
    }
    
    private static byte[] toBytes(BufferedImage img) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", bos);
        return bos.toByteArray();
    }
}
