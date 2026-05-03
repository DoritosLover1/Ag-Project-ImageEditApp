package additional;

import java.io.Serializable;

public class PastedImage implements Serializable {
    private static final long serialVersionUID = 1L;

    private byte[] imageData;   // raw PNG/JPEG bytes
    private int x, y;
    private int width, height;
    private String id;

    public PastedImage() {}

    public byte[] getImageData() { return imageData; }
    public void setImageData(byte[] imageData) { this.imageData = imageData; }

    public int getX() { return x; }
    public void setX(int x) { this.x = x; }

    public int getY() { return y; }
    public void setY(int y) { this.y = y; }

    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }

    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
}
