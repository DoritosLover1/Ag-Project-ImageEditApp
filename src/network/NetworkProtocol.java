package network;
import java.util.Base64;
import java.util.UUID;
import java.util.List;
public class NetworkProtocol {
    public static final String SEPARATOR = "\\|";
    public static final String JOINER = "|";
    public static final String CMD_SQUARE = "SQUARE";
    public static final String CMD_CIRCLE = "CIRCLE";
    public static final String CMD_LINE = "LINE";
    public static final String CMD_TRIANGLE = "TRIANGLE";
    public static final String CMD_FREEHAND = "FREEHAND";
    public static final String CMD_TEXT = "TEXT";
    public static final String CMD_IMAGE = "IMAGE";
    public static final String CMD_DELETE = "DELETE";
    public static final String CMD_CURSOR = "CURSOR";
    public static final String CMD_CLEAR = "CLEAR";
    public static final String CMD_LOGIN = "LOGIN";
    public static final String CMD_CREATE_ROOM = "CREATE_ROOM";
    public static final String CMD_JOIN_ROOM = "JOIN_ROOM";
    public static final String CMD_LEAVE_ROOM = "LEAVE_ROOM";
    public static final String CMD_ROOM_INFO = "ROOM_INFO";
    public static final String CMD_USER_LIST = "USER_LIST";
    public static final String CMD_ERROR = "ERROR";
    public static final String CMD_NEW_USERNAME = "NEW_USERNAME";
    public static final String CMD_NAME_CHANGED = "NAME_CHANGED";
    public static final String CMD_LOGIN_SUCCESS = "LOGIN_SUCCESS";
    private static String buildBase(String sender, String command, String data) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        long timestamp = System.currentTimeMillis();
        return id + JOINER + timestamp + JOINER + sender + JOINER + command + JOINER + data;
    }
    public static String buildLogin(String username) {
        return buildBase(username, CMD_LOGIN, username);
    }
    public static String buildCreateRoom(String username) {
        return buildBase(username, CMD_CREATE_ROOM, "NEW");
    }
    public static String buildJoinRoom(String username, String code) {
        return buildBase(username, CMD_JOIN_ROOM, code);
    }
    public static String buildLeaveRoom(String username) {
        return buildBase(username, CMD_LEAVE_ROOM, "LEAVE");
    }
    public static String buildRoomInfo(String code) {
        return buildBase("SERVER", CMD_ROOM_INFO, code);
    }
    public static String buildUserList(List<String> users) {
        String data = String.join(",", users);
        return buildBase("SERVER", CMD_USER_LIST, data);
    }
    public static final String buildError(String message) {
        return buildBase("SERVER", CMD_ERROR, message);
    }
    public static String buildChangeName(String oldNickname, String newNickname) {
        return buildBase(oldNickname, CMD_NEW_USERNAME, oldNickname + "|" + newNickname);
    }
    public static String buildNameChanged(String newNickname) {
        return buildBase("SERVER", CMD_NAME_CHANGED, newNickname);
    }
    public static String buildLoginSuccess(String nickname) {
        return buildBase("SERVER", CMD_LOGIN_SUCCESS, nickname);
    }
    public static String buildSquare(String sender, int x, int y, int w, int h, String color, int stroke,
            boolean filled, String id) {
        String data = String.format("%d|%d|%d|%d|%s|%d|%b|%s", x, y, w, h, color, stroke, filled, id);
        return buildBase(sender, CMD_SQUARE, data);
    }
    public static String buildCircle(String sender, int x, int y, int w, int h, String color, int stroke,
            boolean filled, String id) {
        String data = String.format("%d|%d|%d|%d|%s|%d|%b|%s", x, y, w, h, color, stroke, filled, id);
        return buildBase(sender, CMD_CIRCLE, data);
    }
    public static String buildTriangle(String sender, int x1, int y1, int x2, int y2, int x3, int y3, String color,
            int stroke, boolean filled, String id) {
        String data = String.format("%d|%d|%d|%d|%d|%d|%s|%d|%b|%s", x1, y1, x2, y2, x3, y3, color, stroke, filled,
                id);
        return buildBase(sender, CMD_TRIANGLE, data);
    }
    public static String buildLine(String sender, int x1, int y1, int x2, int y2, String color, int stroke, String id) {
        String data = String.format("%d|%d|%d|%d|%s|%d|%s", x1, y1, x2, y2, color, stroke, id);
        return buildBase(sender, CMD_LINE, data);
    }
    public static String buildFreehand(String sender, int[] xs, int[] ys, String color, int stroke, String id) {
        StringBuilder sbX = new StringBuilder();
        StringBuilder sbY = new StringBuilder();
        for (int i = 0; i < xs.length; i++) {
            sbX.append(xs[i]).append(i == xs.length - 1 ? "" : ",");
            sbY.append(ys[i]).append(i == ys.length - 1 ? "" : ",");
        }
        String data = String.format("%s|%s|%s|%d|%s", sbX.toString(), sbY.toString(), color, stroke, id);
        return buildBase(sender, CMD_FREEHAND, data);
    }
    public static String buildText(String sender, int x, int y, String text, String color, String id) {
        String data = String.format("%d|%d|%s|%s|%s", x, y, text, color, id);
        return buildBase(sender, CMD_TEXT, data);
    }
    public static String buildDelete(String sender, String targetId) {
        return buildBase(sender, CMD_DELETE, targetId);
    }
    public static String buildClear(String sender) {
        return buildBase(sender, CMD_CLEAR, "ALL");
    }
    public static String buildCursor(String sender, int x, int y, String color) {
        String data = String.format("%d|%d|%s", x, y, color);
        return buildBase(sender, CMD_CURSOR, data);
    }
    public static String buildImage(String sender, int x, int y, int w, int h, byte[] imageData, String id) {
        String base64 = Base64.getEncoder().encodeToString(imageData);
        String data = String.format("%d|%d|%d|%d|%s|%s", x, y, w, h, base64, id);
        return buildBase(sender, CMD_IMAGE, data);
    }
}
