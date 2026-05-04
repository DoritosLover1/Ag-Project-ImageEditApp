package models;

import network.NetworkProtocol;
import java.io.Serializable;

public class DrawShape implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum ShapeType {
        RECTANGLE, CIRCLE, LINE, FREEHAND, TEXT
    }

    private ShapeType shapeType;
    private int x, y, width, height;
    private int x2, y2; // for LINE end point
    private int[] freehandXPoints; // for FREEHAND
    private int[] freehandYPoints;
    private String color; // hex string e.g. "#FF0000"
    private int strokeWidth;
    private boolean filled;
    private String text; // for TEXT shapes
    private String id; // unique id for this shape

    public DrawShape() {
    }

    // Getters & Setters
    public ShapeType getShapeType() {
        return shapeType;
    }

    public void setShapeType(ShapeType shapeType) {
        this.shapeType = shapeType;
    }

    public int getXOfShape() {
        return x;
    }

    public void setXOfShape(int x) {
        this.x = x;
    }

    public int getYOfShape() {
        return y;
    }

    public void setYOfShape(int y) {
        this.y = y;
    }

    public int getWidthOfShape() {
        return width;
    }

    public void setWidthOfShape(int width) {
        this.width = width;
    }

    public int getHeightOfShape() {
        return height;
    }

    public void setHeightOfShape(int height) {
        this.height = height;
    }

    public int getX2OfShape() {
        return x2;
    }

    public void setX2OfShape(int x2) {
        this.x2 = x2;
    }

    public int getY2OfShape() {
        return y2;
    }

    public void setY2OfShape(int y2) {
        this.y2 = y2;
    }

    public int[] getFreehandXPointsOfShape() {
        return freehandXPoints;
    }

    public void setFreehandXPointsOfShape(int[] freehandXPoints) {
        this.freehandXPoints = freehandXPoints;
    }

    public int[] getFreehandYPointsOfShape() {
        return freehandYPoints;
    }

    public void setFreehandYPointsOfShape(int[] freehandYPoints) {
        this.freehandYPoints = freehandYPoints;
    }

    public String getColorOfShape() {
        return color;
    }

    public void setColorOfShape(String color) {
        this.color = color;
    }

    public int getStrokeWidthOfShape() {
        return strokeWidth;
    }

    public void setStrokeWidthOfShape(int strokeWidth) {
        this.strokeWidth = strokeWidth;
    }

    public boolean isFilledShape() {
        return filled;
    }

    public void setFilledShape(boolean filled) {
        this.filled = filled;
    }

    public String getTextOfShape() {
        return text;
    }

    public void setTextOfShape(String text) {
        this.text = text;
    }

    public String getIdOfShape() {
        return id;
    }

    public void setIdOfShape(String id) {
        this.id = id;
    }

    public String toNetworkString(String sender) {
        switch (shapeType) {
            case RECTANGLE:
                return NetworkProtocol.buildSquare(sender, x, y, width, height, color, strokeWidth, filled, id);
            case CIRCLE:
                return NetworkProtocol.buildCircle(sender, x, y, width, height, color, strokeWidth, filled, id);
            case LINE:
                return NetworkProtocol.buildLine(sender, x, y, x2, y2, color, strokeWidth, id);
            case FREEHAND:
                return NetworkProtocol.buildFreehand(sender, freehandXPoints, freehandYPoints, color, strokeWidth, id);
            case TEXT:
                return NetworkProtocol.buildText(sender, x, y, text, color, id);
            default:
                return NetworkProtocol.buildSquare(sender, x, y, width, height, color, strokeWidth, filled, id);
        }
    }

    public static DrawShape fromNetworkProtocol(String[] p) {
        String command = p[3];
        DrawShape s = new DrawShape();
        s.setIdOfShape(p[p.length - 1]); // Genelde son parametre ID

        if (command.equals(NetworkProtocol.CMD_SQUARE) || command.equals(NetworkProtocol.CMD_CIRCLE)) {
            s.setShapeType(command.equals(NetworkProtocol.CMD_SQUARE) ? ShapeType.RECTANGLE : ShapeType.CIRCLE);
            s.setXOfShape(Integer.parseInt(p[4]));
            s.setYOfShape(Integer.parseInt(p[5]));
            s.setWidthOfShape(Integer.parseInt(p[6]));
            s.setHeightOfShape(Integer.parseInt(p[7]));
            s.setColorOfShape(p[8]);
            s.setStrokeWidthOfShape(Integer.parseInt(p[9]));
            s.setFilledShape(Boolean.parseBoolean(p[10]));
            s.setIdOfShape(p[11]);
        } else if (command.equals(NetworkProtocol.CMD_LINE)) {
            s.setShapeType(ShapeType.LINE);
            s.setXOfShape(Integer.parseInt(p[4]));
            s.setYOfShape(Integer.parseInt(p[5]));
            s.setX2OfShape(Integer.parseInt(p[6]));
            s.setY2OfShape(Integer.parseInt(p[7]));
            s.setColorOfShape(p[8]);
            s.setStrokeWidthOfShape(Integer.parseInt(p[9]));
            s.setIdOfShape(p[10]);
        } else if (command.equals(NetworkProtocol.CMD_FREEHAND)) {
            s.setShapeType(ShapeType.FREEHAND);
            String[] xs = p[4].split(",");
            String[] ys = p[5].split(",");
            int[] ixs = new int[xs.length];
            int[] iys = new int[ys.length];
            for (int i = 0; i < xs.length; i++) {
                ixs[i] = Integer.parseInt(xs[i]);
                iys[i] = Integer.parseInt(ys[i]);
            }
            s.setFreehandXPointsOfShape(ixs);
            s.setFreehandYPointsOfShape(iys);
            s.setColorOfShape(p[6]);
            s.setStrokeWidthOfShape(Integer.parseInt(p[7]));
            s.setIdOfShape(p[8]);
        } else if (command.equals(NetworkProtocol.CMD_TEXT)) {
            s.setShapeType(ShapeType.TEXT);
            s.setXOfShape(Integer.parseInt(p[4]));
            s.setYOfShape(Integer.parseInt(p[5]));
            s.setTextOfShape(p[6]);
            s.setColorOfShape(p[7]);
            s.setIdOfShape(p[8]);
        }
        return s;
    }
}
