package network;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import models.*;
import uiframe.DrawingCanvas;

public class ClientNetworkManager {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String username;
    private DrawingCanvas canvas;
    private Consumer<String> onRoomJoined;
    private Consumer<List<String>> onUserListUpdated;
    private Consumer<String> onError;
    private Consumer<String> onNameChanged;
    private Consumer<String> onLoginSuccess;

    private String host;
    private int port;

    public ClientNetworkManager(String host, int port, String username, DrawingCanvas canvas) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.canvas = canvas;
    }

    public void connect() {
        new Thread(() -> {
            try {
                socket = new Socket(host, port);
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                sendRaw(NetworkProtocol.buildLogin(username));
                String line;
                while ((line = in.readLine()) != null) {
                    final String msg = line;
                    SwingUtilities.invokeLater(() -> handleMessage(msg));
                }
            } catch (Exception e) {
                if (onError != null)
                    SwingUtilities.invokeLater(() -> onError.accept(e.getMessage()));
                e.printStackTrace();
            }
        }).start();
    }

    private void handleMessage(String raw) {
        String[] p = raw.split(NetworkProtocol.SEPARATOR);
        if (p.length < 4)
            return;

        String sender = p[2];
        String command = p[3];
        String data = extractData(raw);

        if (command.equals(NetworkProtocol.CMD_LOGIN_SUCCESS)) {
            String nick = !data.isEmpty() ? data : username;
            if (onLoginSuccess != null) {
                onLoginSuccess.accept(nick);
            }
            return;
        }

        if (!sender.equals("SERVER") && sender.equals(username) && !command.equals(NetworkProtocol.CMD_CLEAR)) {
            return;
        }

        switch (command) {
            case NetworkProtocol.CMD_SQUARE:
            case NetworkProtocol.CMD_CIRCLE:
            case NetworkProtocol.CMD_LINE:
            case NetworkProtocol.CMD_FREEHAND:
            case NetworkProtocol.CMD_TRIANGLE:
                canvas.addShapeSilently(DrawShape.fromNetworkProtocol(p), sender);
                break;
            case NetworkProtocol.CMD_CURSOR:
                canvas.updateRemoteCursor(new CursorPosition(
                        Integer.parseInt(p[4]), Integer.parseInt(p[5]), sender, p[6]));
                break;
            case NetworkProtocol.CMD_CLEAR:
                canvas.clearCanvas();
                break;
            case NetworkProtocol.CMD_DELETE:
                canvas.removeItemById(p[4]);
                break;
            case NetworkProtocol.CMD_IMAGE:
                PastedImage img = new PastedImage();
                img.setXOfImage(Integer.parseInt(p[4]));
                img.setYOfImage(Integer.parseInt(p[5]));
                img.setWidthOfImage(Integer.parseInt(p[6]));
                img.setHeightOfImage(Integer.parseInt(p[7]));
                img.setImageData(java.util.Base64.getDecoder().decode(p[8]));
                img.setIdOfImage(p[9]);
                canvas.addRemoteImage(img, sender);
                break;
            case NetworkProtocol.CMD_ROOM_INFO:
                if (onRoomJoined != null)
                    onRoomJoined.accept(p[4]);
                break;
            case NetworkProtocol.CMD_USER_LIST:
                if (onUserListUpdated != null) {
                    String[] users = data.split(",");
                    onUserListUpdated.accept(java.util.Arrays.asList(users));
                }
                break;
            case NetworkProtocol.CMD_ERROR:
                if (onError != null)
                    onError.accept(data);
                break;
            case NetworkProtocol.CMD_NAME_CHANGED:
                this.username = data;
                if (onNameChanged != null)
                    onNameChanged.accept(data);
                break;
            case NetworkProtocol.CMD_CHAT:
                if (onChatReceived != null) {
                    onChatReceived.accept(sender, data);
                }
                break;
            case NetworkProtocol.CMD_CHAT_HISTORY:
                if (onChatHistoryReceived != null) {
                    String[] parts = data.split("\\|", 3);
                    if (parts.length >= 3) {
                        String historySender = parts[0];
                        String encodedMsg = parts[1];
                        String historyMessage = new String(java.util.Base64.getDecoder().decode(encodedMsg), java.nio.charset.StandardCharsets.UTF_8);
                        long historyTimestamp = Long.parseLong(parts[2]);
                        onChatHistoryReceived.accept(historySender, historyMessage, historyTimestamp);
                    }
                }
                break;
        }
    }

    public void createRoom() {
        sendRaw(NetworkProtocol.buildCreateRoom(username));
    }

    public void joinRoom(String code) {
        sendRaw(NetworkProtocol.buildJoinRoom(username, code));
    }

    public void leaveRoom() {
        sendRaw(NetworkProtocol.buildLeaveRoom(username));
    }

    public void sendShape(DrawShape shape) {
        sendRaw(shape.toNetworkString(username));
    }

    public void sendCursor(CursorPosition cp) {
        sendRaw(NetworkProtocol.buildCursor(username, cp.getX(), cp.getY(), cp.getColor()));
    }

    public void sendRaw(String data) {
        if (out != null)
            out.println(data);
    }

    public void setOnRoomJoined(Consumer<String> callback) {
        this.onRoomJoined = callback;
    }

    public void setOnUserListUpdated(Consumer<java.util.List<String>> callback) {
        this.onUserListUpdated = callback;
    }

    public void setOnError(Consumer<String> callback) {
        this.onError = callback;
    }

    public void setOnNameChanged(Consumer<String> callback) {
        this.onNameChanged = callback;
    }

    private java.util.function.BiConsumer<String, String> onChatReceived;
    private TriConsumer<String, String, Long> onChatHistoryReceived;

    public void setOnChatReceived(java.util.function.BiConsumer<String, String> cb) {
        this.onChatReceived = cb;
    }

    public void setOnChatHistoryReceived(TriConsumer<String, String, Long> cb) {
        this.onChatHistoryReceived = cb;
    }

    private String extractData(String raw) {
        int separatorCount = 0;
        for (int i = 0; i < raw.length(); i++) {
            if (raw.charAt(i) == '|') {
                separatorCount++;
                if (separatorCount == 4) {
                    return raw.substring(i + 1);
                }
            }
        }
        return "";
    }

    public void setOnLoginSuccess(Consumer<String> callback) {
        this.onLoginSuccess = callback;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
