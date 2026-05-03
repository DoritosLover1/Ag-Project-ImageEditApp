package additional;

import java.io.Serializable;

public class DrawShape implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum ShapeType {
        RECTANGLE, CIRCLE, LINE, FREEHAND, TEXT
    }

    private ShapeType shapeType;
    private int x, y, width, height;
    private int x2, y2;                  // for LINE end point
    private int[] freehandXPoints;       // for FREEHAND
    private int[] freehandYPoints;
    private String color;                // hex string e.g. "#FF0000"
    private int strokeWidth;
    private boolean filled;
    private String text;                  // for TEXT shapes
    private String id;                    // unique id for this shape

    public DrawShape() {}

    // Getters & Setters
    public ShapeType getShapeType() { return shapeType; }
    public void setShapeType(ShapeType shapeType) { this.shapeType = shapeType; }

    public int getX() { return x; }
    public void setX(int x) { this.x = x; }

    public int getY() { return y; }
    public void setY(int y) { this.y = y; }

    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }

    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }

    public int getX2() { return x2; }
    public void setX2(int x2) { this.x2 = x2; }

    public int getY2() { return y2; }
    public void setY2(int y2) { this.y2 = y2; }

    public int[] getFreehandXPoints() { return freehandXPoints; }
    public void setFreehandXPoints(int[] freehandXPoints) { this.freehandXPoints = freehandXPoints; }

    public int[] getFreehandYPoints() { return freehandYPoints; }
    public void setFreehandYPoints(int[] freehandYPoints) { this.freehandYPoints = freehandYPoints; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public int getStrokeWidth() { return strokeWidth; }
    public void setStrokeWidth(int strokeWidth) { this.strokeWidth = strokeWidth; }

    public boolean isFilled() { return filled; }
    public void setFilled(boolean filled) { this.filled = filled; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
}
