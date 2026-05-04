package network;

import models.*;
import uiframe.DrawingCanvas;
import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

public class ClientNetworkManager {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String username;
    private DrawingCanvas canvas;

    private Consumer<String> onRoomJoined;
    private Consumer<java.util.List<String>> onUserListUpdated;
    private Consumer<String> onError;

    public ClientNetworkManager(String host, int port, String username, DrawingCanvas canvas) {
        this.username = username;
        this.canvas = canvas;
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
                if (onError != null) SwingUtilities.invokeLater(() -> onError.accept(e.getMessage()));
                e.printStackTrace(); 
            }
        }).start();
    }

    private void handleMessage(String raw) {
        String[] p = raw.split(NetworkProtocol.SEPARATOR);
        if (p.length < 4) return;

        String sender = p[2];
        String command = p[3];

        if (sender.equals(username) && !command.equals(NetworkProtocol.CMD_CLEAR)) return;

        switch (command) {
            case NetworkProtocol.CMD_SQUARE:
            case NetworkProtocol.CMD_CIRCLE:
            case NetworkProtocol.CMD_LINE:
            case NetworkProtocol.CMD_FREEHAND:
            case NetworkProtocol.CMD_TEXT:
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
                if (onRoomJoined != null) onRoomJoined.accept(p[4]);
                break;
            case NetworkProtocol.CMD_USER_LIST:
                if (onUserListUpdated != null) {
                    String[] users = p[4].split(",");
                    onUserListUpdated.accept(java.util.Arrays.asList(users));
                }
                break;
            case NetworkProtocol.CMD_ERROR:
                if (onError != null) onError.accept(p[4]);
                break;
        }
    }

    public void createRoom() {
        sendRaw(NetworkProtocol.buildCreateRoom(username));
    }

    public void joinRoom(String code) {
        sendRaw(NetworkProtocol.buildJoinRoom(username, code));
    }

    public void sendShape(DrawShape shape) {
        sendRaw(shape.toNetworkString(username));
    }

    public void sendCursor(CursorPosition cp) {
        sendRaw(NetworkProtocol.buildCursor(username, cp.getX(), cp.getY(), cp.getColor()));
    }

    public void sendRaw(String data) {
        if (out != null) out.println(data);
    }

    public void setOnRoomJoined(Consumer<String> callback) { this.onRoomJoined = callback; }
    public void setOnUserListUpdated(Consumer<java.util.List<String>> callback) { this.onUserListUpdated = callback; }
    public void setOnError(Consumer<String> callback) { this.onError = callback; }
}
