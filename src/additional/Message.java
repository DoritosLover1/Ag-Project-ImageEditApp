package additional;

import java.io.Serializable;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type {
        // Connection
        SET_NICKNAME,
        NICKNAME_OK,
        NICKNAME_TAKEN,

        // Room
        CREATE_ROOM,
        JOIN_ROOM,
        ROOM_CREATED,
        ROOM_JOINED,
        ROOM_NOT_FOUND,
        ROOM_FULL,
        USER_JOINED,
        USER_LEFT,

        // Canvas actions
        DRAW_SHAPE,
        PASTE_IMAGE,
        CLEAR_CANVAS,
        CANVAS_STATE,   // full state sync on join

        // Cursor
        CURSOR_MOVE,

        // Members sync
        MEMBERS_LIST,   // payload = List<String> nicknames

        // Item deletion (cut)
        DELETE_ITEM,    // payload = String itemId
        DELETE_ITEMS,   // payload = List<String> itemIds (bulk area-cut)

        // Error
        ERROR
    }

    private Type type;
    private String senderNickname;
    private String roomCode;
    private Object payload;  // DrawShape, CursorPos, ImageData, String, List<CanvasItem>

    public Message() {}

    public Message(Type type) {
        this.type = type;
    }

    public Message(Type type, Object payload) {
        this.type = type;
        this.payload = payload;
    }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public String getSenderNickname() { return senderNickname; }
    public void setSenderNickname(String senderNickname) { this.senderNickname = senderNickname; }

    public String getRoomCode() { return roomCode; }
    public void setRoomCode(String roomCode) { this.roomCode = roomCode; }

    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }

    @Override
    public String toString() {
        return "Message{type=" + type + ", sender=" + senderNickname + ", room=" + roomCode + "}";
    }
}
