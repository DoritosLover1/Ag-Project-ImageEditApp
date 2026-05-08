package models;

import java.io.Serializable;

public class CanvasItem implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum ItemType {
        SHAPE, IMAGE
    }

    private ItemType itemType;
    private DrawShape shape;
    private PastedImage image;
    private String addedBy;

    public CanvasItem(DrawShape shape, String addedBy) {
        this.itemType = ItemType.SHAPE;
        this.shape = shape;
        this.addedBy = addedBy;
    }

    public CanvasItem(PastedImage image, String addedBy) {
        this.itemType = ItemType.IMAGE;
        this.image = image;
        this.addedBy = addedBy;
    }

    public ItemType getItemType() {
        return itemType;
    }

    public DrawShape getShape() {
        return shape;
    }

    public PastedImage getImage() {
        return image;
    }

    public String getAddedBy() {
        return addedBy;
    }

    public String getIdOfImage() {
        if (itemType == ItemType.SHAPE && shape != null)
            return shape.getIdOfShape();
        if (itemType == ItemType.IMAGE && image != null)
            return image.getIdOfImage();
        return null;
    }

    public String getLabel() {
        if (itemType == ItemType.IMAGE)
            return "Resim  ← " + addedBy;
        if (shape != null) {
            switch (shape.getShapeType()) {
                case RECTANGLE:
                    return "Dikdörtgen  ← " + addedBy;
                case CIRCLE:
                    return "Elips  ← " + addedBy;
                case LINE:
                    return "Çizgi  ← " + addedBy;
                case FREEHAND:
                    return "Çizim  ← " + addedBy;
                case TRIANGLE:
                    return "Üçgen  ← " + addedBy;
            }
        }
        return "Bilinmeyen  ← " + addedBy;
    }
}
