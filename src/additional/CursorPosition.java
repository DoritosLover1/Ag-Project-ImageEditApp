package additional;
import java.io.Serializable;

public class CursorPosition implements Serializable {
    private static final long serialVersionUID = 1L;

    private int x;
    private int y;
    private String username;
    private String color;

    public CursorPosition() {}

    public CursorPosition(int x, int y, String username, String color) {
        this.x = x;
        this.y = y;
        this.username = username;
        this.color = color;
    }

    public int getX() { return x; }
    public void setX(int x) { this.x = x; }

    public int getY() { return y; }
    public void setY(int y) { this.y = y; }

    public String getUsername() { return username; }
    public void setUsername(String nickname) { this.username = nickname; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
}
