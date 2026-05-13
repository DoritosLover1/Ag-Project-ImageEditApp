package uiframe;

import models.*;
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
    public enum Tool {
        SELECT, RECTANGLE, CIRCLE, LINE, FREEHAND, TRIANGLE, ERASER
    }

    private final List<CanvasItem> items = new CopyOnWriteArrayList<>();
    private final Map<String, CursorPosition> remoteCursors = new HashMap<>();
    private Tool currentTool = Tool.FREEHAND;
    private Color currentColor = Color.BLACK;
    private int strokeWidth = 2;
    private boolean filled = false;
    private int startX, startY, currentX, currentY;
    private boolean drawing = false;
    private List<Point> freehandPoints = new ArrayList<>();
    private Rectangle selectionRect = null;
    private boolean selectDragging = false;
    private Consumer<DrawShape> onShapeDrawn;
    private Consumer<PastedImage> onImagePasted;
    private Consumer<CursorPosition> onCursorMoved;
    private Consumer<List<String>> onItemsCut;
    private Runnable onItemsChanged;
    private String myNickname;
    private String myCursorColor;
    private final List<CanvasItem> internalClipboard = new ArrayList<>();
    private long lastCursorSend = 0;
    private static final long CURSOR_THROTTLE_MS = 33;
    private int dashOffset = 0;
    private javax.swing.Timer dashTimer;

    public DrawingCanvas() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(1200, 800));
        setupMouseListeners();
        setupKeyBindings();
        dashTimer = new javax.swing.Timer(80, e -> {
            if (selectionRect != null) {
                dashOffset = (dashOffset + 1) % 12;
                repaint();
            }
        });
        dashTimer.start();
    }

    private void setupMouseListeners() {
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (currentTool == Tool.SELECT) {
                    selectionRect = null;
                    selectDragging = true;
                    startX = e.getX();
                    startY = e.getY();
                    currentX = startX;
                    currentY = startY;
                    repaint();
                    return;
                }
                selectionRect = null;
                startX = e.getX();
                startY = e.getY();
                currentX = startX;
                currentY = startY;
                drawing = true;
                freehandPoints.clear();
                freehandPoints.add(new Point(startX, startY));
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                currentX = e.getX();
                currentY = e.getY();
                if (currentTool == Tool.SELECT) {
                    selectionRect = makeRect(startX, startY, currentX, currentY);
                    repaint();
                    return;
                }
                if (currentTool == Tool.FREEHAND) {
                    freehandPoints.add(new Point(currentX, currentY));
                }
                if (currentTool == Tool.ERASER) {
                    eraseAt(currentX, currentY);
                }
                repaint();
                sendCursorMove(e.getX(), e.getY());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                currentX = e.getX();
                currentY = e.getY();
                if (currentTool == Tool.SELECT) {
                    selectDragging = false;
                    selectionRect = makeRect(startX, startY, currentX, currentY);
                    if (selectionRect.width > 4 && selectionRect.height > 4) {
                        applyCut();
                    } else {
                        selectionRect = null;
                        repaint();
                    }
                    return;
                }
                if (!drawing)
                    return;
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

    private void applyCut() {
        if (selectionRect == null)
            return;
        List<CanvasItem> toCut = new ArrayList<>();
        List<String> idsToDelete = new ArrayList<>();
        for (CanvasItem item : items) {
            Rectangle bounds = getBounds(item);
            if (bounds != null && selectionRect.intersects(bounds)) {
                toCut.add(item);
                idsToDelete.add(item.getIdOfImage());
            }
        }
        if (!toCut.isEmpty()) {
            internalClipboard.clear();
            internalClipboard.addAll(toCut);
            items.removeAll(toCut);
            selectionRect = null;
            repaint();
            fireItemsChanged();
            if (onItemsCut != null)
                onItemsCut.accept(idsToDelete);
        } else {
            selectionRect = null;
            repaint();
        }
    }

    private void pasteInternal() {
        if (internalClipboard.isEmpty())
            return;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (CanvasItem item : internalClipboard) {
            Rectangle b = getBounds(item);
            if (b != null) {
                minX = Math.min(minX, b.x);
                minY = Math.min(minY, b.y);
                maxX = Math.max(maxX, b.x + b.width);
                maxY = Math.max(maxY, b.y + b.height);
            }
        }
        int centerX = minX + (maxX - minX) / 2;
        int centerY = minY + (maxY - minY) / 2;
        Point mousePos = getMousePosition();
        int offX, offY;
        if (mousePos != null) {
            offX = mousePos.x - centerX;
            offY = mousePos.y - centerY;
        } else {
            offX = 20;
            offY = 20;
        }
        for (CanvasItem item : internalClipboard) {
            CanvasItem newItem = cloneItem(item, offX, offY);
            items.add(newItem);
            if (newItem.getItemType() == CanvasItem.ItemType.SHAPE) {
                if (onShapeDrawn != null)
                    onShapeDrawn.accept(newItem.getShape());
            } else {
                if (onImagePasted != null)
                    onImagePasted.accept(newItem.getImage());
            }
        }
        internalClipboard.clear();
        repaint();
        fireItemsChanged();
    }

    private CanvasItem cloneItem(CanvasItem item, int offX, int offY) {
        if (item.getItemType() == CanvasItem.ItemType.SHAPE) {
            DrawShape s = item.getShape();
            DrawShape n = new DrawShape();
            n.setShapeType(s.getShapeType());
            n.setXOfShape(s.getXOfShape() + offX);
            n.setYOfShape(s.getYOfShape() + offY);
            n.setWidthOfShape(s.getWidthOfShape());
            n.setHeightOfShape(s.getHeightOfShape());
            n.setX2OfShape(s.getX2OfShape() + offX);
            n.setY2OfShape(s.getY2OfShape() + offY);
            n.setX3OfShape(s.getX3OfShape() + offX);
            n.setY3OfShape(s.getY3OfShape() + offY);
            if (s.getFreehandXPointsOfShape() != null) {
                int[] xs = s.getFreehandXPointsOfShape().clone();
                int[] ys = s.getFreehandYPointsOfShape().clone();
                for (int i = 0; i < xs.length; i++) {
                    xs[i] += offX;
                    ys[i] += offY;
                }
                n.setFreehandXPointsOfShape(xs);
                n.setFreehandYPointsOfShape(ys);
            }
            n.setColorOfShape(s.getColorOfShape());
            n.setStrokeWidthOfShape(s.getStrokeWidthOfShape());
            n.setFilledShape(s.isFilledShape());
            n.setTextOfShape(s.getTextOfShape());
            n.setIdOfShape(UUID.randomUUID().toString());
            return new CanvasItem(n, myNickname);
        } else {
            PastedImage i = item.getImage();
            PastedImage n = new PastedImage();
            n.setImageData(i.getImageData());
            n.setXOfImage(i.getXOfImage() + offX);
            n.setYOfImage(i.getYOfImage() + offY);
            n.setWidthOfImage(i.getWidthOfImage());
            n.setHeightOfImage(i.getHeightOfImage());
            n.setIdOfImage(UUID.randomUUID().toString());
            return new CanvasItem(n, myNickname);
        }
    }

    private void eraseAt(int x, int y) {
        Rectangle eraserArea = new Rectangle(x - 10, y - 10, 20, 20);
        List<String> toDelete = new ArrayList<>();
        for (CanvasItem item : items) {
            Rectangle bounds = getBounds(item);
            if (bounds != null && eraserArea.intersects(bounds)) {
                toDelete.add(item.getIdOfImage());
            }
        }
        if (!toDelete.isEmpty()) {
            removeItemsByIds(toDelete);
            if (onItemsCut != null)
                onItemsCut.accept(toDelete);
        }
    }

    public void removeItemsByIds(List<String> ids) {
        Set<String> idSet = new HashSet<>(ids);
        items.removeIf(i -> idSet.contains(i.getIdOfImage()));
        SwingUtilities.invokeLater(() -> {
            repaint();
            fireItemsChanged();
        });
    }

    private Rectangle makeRect(int x1, int y1, int x2, int y2) {
        return new Rectangle(Math.min(x1, x2), Math.min(y1, y2),
                Math.abs(x2 - x1), Math.abs(y2 - y1));
    }

    private Rectangle getBounds(CanvasItem item) {
        if (item.getItemType() == CanvasItem.ItemType.IMAGE) {
            PastedImage pi = item.getImage();
            if (pi == null)
                return null;
            return new Rectangle(pi.getXOfImage(), pi.getYOfImage(), pi.getWidthOfImage(), pi.getHeightOfImage());
        }
        DrawShape s = item.getShape();
        if (s == null)
            return null;
        switch (s.getShapeType()) {
            case RECTANGLE:
            case CIRCLE:
                return new Rectangle(s.getXOfShape(), s.getYOfShape(), s.getWidthOfShape(), s.getHeightOfShape());
            case LINE: {
                int x = Math.min(s.getXOfShape(), s.getX2OfShape());
                int y = Math.min(s.getYOfShape(), s.getY2OfShape());
                int w = Math.abs(s.getX2OfShape() - s.getXOfShape()) + 1;
                int h = Math.abs(s.getY2OfShape() - s.getYOfShape()) + 1;
                return new Rectangle(x, y, w, h);
            }
            case TRIANGLE: {
                int minX = Math.min(s.getXOfShape(), Math.min(s.getX2OfShape(), s.getX3OfShape()));
                int minY = Math.min(s.getYOfShape(), Math.min(s.getY2OfShape(), s.getY3OfShape()));
                int maxX = Math.max(s.getXOfShape(), Math.max(s.getX2OfShape(), s.getX3OfShape()));
                int maxY = Math.max(s.getYOfShape(), Math.max(s.getY2OfShape(), s.getY3OfShape()));
                return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
            }
            case FREEHAND: {
                int[] xs = s.getFreehandXPointsOfShape();
                int[] ys = s.getFreehandYPointsOfShape();
                if (xs == null || xs.length == 0)
                    return null;
                int minX = Arrays.stream(xs).min().getAsInt();
                int minY = Arrays.stream(ys).min().getAsInt();
                int maxX = Arrays.stream(xs).max().getAsInt();
                int maxY = Arrays.stream(ys).max().getAsInt();
                return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
            }
            default:
                return null;
        }
    }

    private void sendCursorMove(int x, int y) {
        long now = System.currentTimeMillis();
        if (now - lastCursorSend < CURSOR_THROTTLE_MS)
            return;
        lastCursorSend = now;
        if (onCursorMoved != null && myNickname != null)
            onCursorMoved.accept(new CursorPosition(x, y, myNickname, myCursorColor));
    }

    private void finalizeShape() {
        DrawShape shape = buildShape();
        if (shape == null)
            return;
        shape.setIdOfShape(UUID.randomUUID().toString());
        items.add(new CanvasItem(shape, myNickname));
        repaint();
        fireItemsChanged();
        if (onShapeDrawn != null)
            onShapeDrawn.accept(shape);
    }

    private DrawShape buildShape() {
        DrawShape s = new DrawShape();
        s.setColorOfShape(colorToHex(currentColor));
        s.setStrokeWidthOfShape(strokeWidth);
        s.setFilledShape(filled);
        int x = Math.min(startX, currentX);
        int y = Math.min(startY, currentY);
        int w = Math.abs(currentX - startX);
        int h = Math.abs(currentY - startY);
        switch (currentTool) {
            case RECTANGLE:
                if (w < 3 && h < 3)
                    return null;
                s.setShapeType(DrawShape.ShapeType.RECTANGLE);
                s.setXOfShape(x);
                s.setYOfShape(y);
                s.setWidthOfShape(w);
                s.setHeightOfShape(h);
                return s;
            case CIRCLE:
                if (w < 3 && h < 3)
                    return null;
                s.setShapeType(DrawShape.ShapeType.CIRCLE);
                s.setXOfShape(x);
                s.setYOfShape(y);
                s.setWidthOfShape(w);
                s.setHeightOfShape(h);
                return s;
            case LINE:
                if (w < 3 && h < 3)
                    return null;
                s.setShapeType(DrawShape.ShapeType.LINE);
                s.setXOfShape(startX);
                s.setYOfShape(startY);
                s.setX2OfShape(currentX);
                s.setY2OfShape(currentY);
                return s;
            case TRIANGLE:
                if (w < 3 && h < 3)
                    return null;
                s.setShapeType(DrawShape.ShapeType.TRIANGLE);
                int tx = Math.min(startX, currentX);
                int tw = Math.abs(currentX - startX);
                s.setXOfShape(tx);
                s.setYOfShape(startY);
                s.setX2OfShape(tx + tw);
                s.setY2OfShape(startY);
                s.setX3OfShape(tx + tw / 2);
                s.setY3OfShape(currentY);
                return s;
            case FREEHAND:
                if (freehandPoints.size() < 2)
                    return null;
                s.setShapeType(DrawShape.ShapeType.FREEHAND);
                int[] xs = freehandPoints.stream().mapToInt(p -> p.x).toArray();
                int[] ys = freehandPoints.stream().mapToInt(p -> p.y).toArray();
                s.setFreehandXPointsOfShape(xs);
                s.setFreehandYPointsOfShape(ys);
                return s;
            default:
                return null;
        }
    }

    private void setupKeyBindings() {
        setFocusable(true);
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), "paste");
        getActionMap().put("paste", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pasteFromClipboard();
            }
        });
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelSel");
        getActionMap().put("cancelSel", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                selectionRect = null;
                repaint();
            }
        });
    }

    public void pasteFromClipboard() {
        if (!internalClipboard.isEmpty()) {
            pasteInternal();
            return;
        }
        try {
            java.awt.datatransfer.Transferable t = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (t == null)
                return;
            if (t.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.imageFlavor)) {
                Image img = (Image) t.getTransferData(java.awt.datatransfer.DataFlavor.imageFlavor);
                BufferedImage bi = toBufferedImage(img);
                byte[] bytes = toBytes(bi);
                PastedImage pi = new PastedImage();
                pi.setIdOfImage(UUID.randomUUID().toString());
                pi.setImageData(bytes);
                Point mousePos = getMousePosition();
                if (mousePos != null) {
                    pi.setXOfImage(mousePos.x - bi.getWidth() / 2);
                    pi.setYOfImage(mousePos.y - bi.getHeight() / 2);
                } else {
                    pi.setXOfImage(50);
                    pi.setYOfImage(50);
                }
                pi.setWidthOfImage(Math.min(bi.getWidth(), 400));
                pi.setHeightOfImage(Math.min(bi.getHeight(), 300));
                items.add(new CanvasItem(pi, myNickname));
                repaint();
                fireItemsChanged();
                if (onImagePasted != null)
                    onImagePasted.accept(pi);
            }
        } catch (Exception ex) {
            System.err.println("Paste error: " + ex.getMessage());
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (CanvasItem item : items) {
            if (item.getItemType() == CanvasItem.ItemType.SHAPE)
                drawShape(g2, item.getShape());
            else
                drawImage(g2, item.getImage());
        }
        if (drawing && currentTool != Tool.FREEHAND && currentTool != Tool.SELECT) {
            g2.setColor(currentColor);
            g2.setStroke(new BasicStroke(strokeWidth));
            drawPreview(g2);
        }
        if (selectionRect != null) {
            g2.setColor(new Color(100, 160, 255, 40));
            g2.fillRect(selectionRect.x, selectionRect.y, selectionRect.width, selectionRect.height);
            float[] dash = { 6f, 4f };
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10f, dash, dashOffset));
            g2.setColor(new Color(30, 100, 220));
            g2.drawRect(selectionRect.x, selectionRect.y, selectionRect.width, selectionRect.height);
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
        synchronized (remoteCursors) {
            for (CursorPosition cp : remoteCursors.values())
                drawRemoteCursor(g2, cp);
        }
    }

    private void drawShape(Graphics2D g2, DrawShape s) {
        if (s == null)
            return;
        g2.setColor(hexToColor(s.getColorOfShape()));
        g2.setStroke(new BasicStroke(s.getStrokeWidthOfShape(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (s.getShapeType()) {
            case RECTANGLE:
                if (s.isFilledShape())
                    g2.fillRect(s.getXOfShape(), s.getYOfShape(), s.getWidthOfShape(), s.getHeightOfShape());
                else
                    g2.drawRect(s.getXOfShape(), s.getYOfShape(), s.getWidthOfShape(), s.getHeightOfShape());
                break;
            case CIRCLE:
                if (s.isFilledShape())
                    g2.fillOval(s.getXOfShape(), s.getYOfShape(), s.getWidthOfShape(), s.getHeightOfShape());
                else
                    g2.drawOval(s.getXOfShape(), s.getYOfShape(), s.getWidthOfShape(), s.getHeightOfShape());
                break;
            case LINE:
                g2.drawLine(s.getXOfShape(), s.getYOfShape(), s.getX2OfShape(), s.getY2OfShape());
                break;
            case TRIANGLE:
                int[] xPoints = { s.getXOfShape(), s.getX2OfShape(), s.getX3OfShape() };
                int[] yPoints = { s.getYOfShape(), s.getY2OfShape(), s.getY3OfShape() };
                if (s.isFilledShape())
                    g2.fillPolygon(xPoints, yPoints, 3);
                else
                    g2.drawPolygon(xPoints, yPoints, 3);
                break;
            case FREEHAND:
                int[] xs = s.getFreehandXPointsOfShape();
                int[] ys = s.getFreehandYPointsOfShape();
                if (xs != null && xs.length > 1)
                    g2.drawPolyline(xs, ys, xs.length);
                break;
        }
    }

    private void drawImage(Graphics2D g2, PastedImage pi) {
        if (pi == null || pi.getImageData() == null)
            return;
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(pi.getImageData()));
            if (img != null)
                g2.drawImage(img, pi.getXOfImage(), pi.getYOfImage(), pi.getWidthOfImage(), pi.getHeightOfImage(),
                        null);
        } catch (IOException e) {
            System.err.println("Image draw error: " + e.getMessage());
        }
    }

    private void drawPreview(Graphics2D g2) {
        int x = Math.min(startX, currentX), y = Math.min(startY, currentY);
        int w = Math.abs(currentX - startX), h = Math.abs(currentY - startY);
        g2.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (currentTool) {
            case RECTANGLE:
                if (filled)
                    g2.fillRect(x, y, w, h);
                else
                    g2.drawRect(x, y, w, h);
                break;
            case CIRCLE:
                if (filled)
                    g2.fillOval(x, y, w, h);
                else
                    g2.drawOval(x, y, w, h);
                break;
            case TRIANGLE:
                int[] xPoints = { x, x + w, x + w / 2 };
                int[] yPoints = { startY, startY, currentY };
                if (filled)
                    g2.fillPolygon(xPoints, yPoints, 3);
                else
                    g2.drawPolygon(xPoints, yPoints, 3);
                break;
            case LINE:
                g2.drawLine(startX, startY, currentX, currentY);
                break;
        }
    }

    private void drawRemoteCursor(Graphics2D g2, CursorPosition cp) {
        Color c = hexToColor(cp.getColor());
        int[] cx = { cp.getX(), cp.getX() + 10, cp.getX() + 4 };
        int[] cy = { cp.getY(), cp.getY() + 10, cp.getY() + 14 };
        g2.setColor(c);
        g2.fillPolygon(cx, cy, 3);
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(1));
        g2.drawPolygon(cx, cy, 3);
        g2.setFont(new Font("SansSerif", Font.BOLD, 11));
        g2.setColor(c);
        g2.fillRoundRect(cp.getX() + 12, cp.getY() + 2,
                g2.getFontMetrics().stringWidth(cp.getUsername()) + 8, 16, 6, 6);
        g2.setColor(Color.WHITE);
        g2.drawString(cp.getUsername(), cp.getX() + 16, cp.getY() + 14);
    }

    public void addRemoteShape(DrawShape shape, String sender) {
        if (shape == null || hasItemWithId(shape.getIdOfShape())) return;
        items.add(new CanvasItem(shape, sender));
        SwingUtilities.invokeLater(() -> {
            repaint();
            fireItemsChanged();
        });
    }

    private boolean hasItemWithId(String id) {
        if (id == null) return false;
        for (CanvasItem item : items) {
            if (id.equals(item.getIdOfImage())) return true;
        }
        return false;
    }

    public void addRemoteImage(PastedImage image, String sender) {
        if (image == null || hasItemWithId(image.getIdOfImage())) return;
        items.add(new CanvasItem(image, sender));
        SwingUtilities.invokeLater(() -> {
            repaint();
            fireItemsChanged();
        });
    }

    public void removeItemById(String id) {
        items.removeIf(i -> id.equals(i.getIdOfImage()));
        SwingUtilities.invokeLater(() -> {
            repaint();
            fireItemsChanged();
        });
    }

    public void updateRemoteCursor(CursorPosition cp) {
        synchronized (remoteCursors) {
            remoteCursors.put(cp.getUsername(), cp);
        }
        SwingUtilities.invokeLater(this::repaint);
    }

    public void removeRemoteCursor(String nickname) {
        synchronized (remoteCursors) {
            remoteCursors.remove(nickname);
        }
        SwingUtilities.invokeLater(this::repaint);
    }

    public void syncCursors(java.util.List<String> activeNicknames) {
        synchronized (remoteCursors) {
            remoteCursors.keySet().removeIf(nick -> !activeNicknames.contains(nick));
        }
        SwingUtilities.invokeLater(this::repaint);
    }

    public void loadCanvasState(List<CanvasItem> snapshot) {
        items.clear();
        items.addAll(snapshot);
        selectionRect = null;
        SwingUtilities.invokeLater(() -> {
            repaint();
            fireItemsChanged();
        });
    }

    public void clearCanvas() {
        items.clear();
        synchronized (remoteCursors) {
            remoteCursors.clear();
        }
        selectionRect = null;
        SwingUtilities.invokeLater(() -> {
            repaint();
            fireItemsChanged();
        });
    }

    public List<CanvasItem> getItemsSnapshot() {
        return new ArrayList<>(items);
    }

    private void fireItemsChanged() {
        if (onItemsChanged != null)
            onItemsChanged.run();
    }

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

    public void setCurrentColor(Color color) {
        this.currentColor = color;
    }

    public Color getCurrentColor() {
        return currentColor;
    }

    public void setStrokeWidth(int w) {
        this.strokeWidth = w;
    }

    public void setFilled(boolean f) {
        this.filled = f;
    }

    public void setUsername(String n) {
        this.myNickname = n;
    }

    public void setCursorColor(Color cursorColor) {
        this.myCursorColor = String.format("#%02X%02X%02X",
                cursorColor.getRed(), cursorColor.getGreen(), cursorColor.getBlue());
    }

    public void setOnShapeDrawn(Consumer<DrawShape> cb) {
        this.onShapeDrawn = cb;
    }

    public void setOnImagePasted(Consumer<PastedImage> cb) {
        this.onImagePasted = cb;
    }

    public void setOnCursorMoved(Consumer<CursorPosition> cb) {
        this.onCursorMoved = cb;
    }

    public void setOnItemsCut(Consumer<List<String>> cb) {
        this.onItemsCut = cb;
    }

    public void setOnItemsChanged(Runnable cb) {
        this.onItemsChanged = cb;
    }

    private static String colorToHex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static Color hexToColor(String hex) {
        if (hex == null)
            return Color.BLACK;
        try {
            return Color.decode(hex);
        } catch (Exception e) {
            return Color.BLACK;
        }
    }

    private static BufferedImage toBufferedImage(Image img) {
        if (img instanceof BufferedImage)
            return (BufferedImage) img;
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

    public void addShapeSilently(DrawShape shape, String sender) {
        if (shape == null || hasItemWithId(shape.getIdOfShape())) return;
        items.add(new CanvasItem(shape, sender));
        SwingUtilities.invokeLater(this::repaint);
    }
}
