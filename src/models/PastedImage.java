package models;

import java.io.Serializable;

public class PastedImage implements Serializable {
    private static final long serialVersionUID = 1L;

    private byte[] imageData;
    private int x, y;
    private int width, height;
    private String id;

    public PastedImage() {
    }

    public byte[] getImageData() {
        return imageData;
    }

    public void setImageData(byte[] imageData) {
        this.imageData = imageData;
    }

    public int getXOfImage() {
        return x;
    }

    public void setXOfImage(int x) {
        this.x = x;
    }

    public int getYOfImage() {
        return y;
    }

    public void setYOfImage(int y) {
        this.y = y;
    }

    public int getWidthOfImage() {
        return width;
    }

    public void setWidthOfImage(int width) {
        this.width = width;
    }

    public int getHeightOfImage() {
        return height;
    }

    public void setHeightOfImage(int height) {
        this.height = height;
    }

    public String getIdOfImage() {
        return id;
    }

    public void setIdOfImage(String id) {
        this.id = id;
    }
}
