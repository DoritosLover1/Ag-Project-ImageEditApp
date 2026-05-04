package server;

import models.*;
import network.NetworkProtocol;
import java.io.*;
import java.net.Socket;
import java.util.List;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final RoomManager roomManager;
    private PrintWriter out;
    private BufferedReader in;

    private String nickname;
    private Room currentRoom;

    public ClientHandler(Socket socket, RoomManager roomManager) {
        this.socket = socket;
        this.roomManager = roomManager;
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));

            String line;
            while ((line = in.readLine()) != null) {
                handleRawMessage(line);
            }
        } catch (Exception e) {
            System.err.println("[CLIENT] Error for " + nickname + ": " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void handleRawMessage(String raw) {
        String[] p = raw.split(NetworkProtocol.SEPARATOR);
        if (p.length < 4)
            return;

        String sender = p[2];
        String command = p[3];

        try {
            switch (command) {
                case NetworkProtocol.CMD_LOGIN:
                    String requested = p[4].trim();
                    if (roomManager.registerNickname(requested)) {
                        this.nickname = requested;
                        System.out.println("[SERVER] User logged in: " + nickname);
                    } else {
                        send(NetworkProtocol.buildError("Nickname taken or invalid."));
                    }
                    break;

                case NetworkProtocol.CMD_CREATE_ROOM:
                    if (nickname == null)
                        return;
                    leaveCurrentRoom();
                    currentRoom = roomManager.createRoom(nickname);
                    currentRoom.addMember(this);
                    send(NetworkProtocol.buildRoomInfo(currentRoom.getCode()));
                    broadcastUserList();
                    break;

                case NetworkProtocol.CMD_JOIN_ROOM:
                    if (nickname == null)
                        return;
                    String code = p[4].trim().toUpperCase();
                    Room room = roomManager.getRoom(code);
                    if (room != null) {
                        leaveCurrentRoom();
                        currentRoom = room;
                        currentRoom.addMember(this);
                        send(NetworkProtocol.buildRoomInfo(currentRoom.getCode()));
                        broadcastUserList();

                        // Send existing items to the new member
                        for (CanvasItem item : currentRoom.getCanvasSnapshot()) {
                            send(buildItemMessage(item));
                        }
                    } else {
                        send(NetworkProtocol.buildError("Room not found."));
                    }
                    break;

                case NetworkProtocol.CMD_LEAVE_ROOM:
                    leaveCurrentRoom();
                    break;

                case NetworkProtocol.CMD_SQUARE:
                case NetworkProtocol.CMD_CIRCLE:
                case NetworkProtocol.CMD_LINE:
                case NetworkProtocol.CMD_FREEHAND:
                case NetworkProtocol.CMD_TEXT:
                    if (currentRoom != null) {
                        DrawShape shape = DrawShape.fromNetworkProtocol(p);
                        currentRoom.addCanvasItem(new CanvasItem(shape, nickname));
                        broadcastToOthers(raw);
                    }
                    break;

                case NetworkProtocol.CMD_IMAGE:
                    if (currentRoom != null) {
                        PastedImage img = new PastedImage();
                        img.setXOfImage(Integer.parseInt(p[4]));
                        img.setYOfImage(Integer.parseInt(p[5]));
                        img.setWidthOfImage(Integer.parseInt(p[6]));
                        img.setHeightOfImage(Integer.parseInt(p[7]));
                        img.setImageData(java.util.Base64.getDecoder().decode(p[8]));
                        img.setIdOfImage(p[9]);
                        currentRoom.addCanvasItem(new CanvasItem(img, nickname));
                        broadcastToOthers(raw);
                    }
                    break;

                case NetworkProtocol.CMD_CURSOR:
                    if (currentRoom != null) {
                        broadcastToOthers(raw);
                    }
                    break;

                case NetworkProtocol.CMD_DELETE:
                    if (currentRoom != null) {
                        currentRoom.removeCanvasItemById(p[4]);
                        broadcastToOthers(raw);
                    }
                    break;

                case NetworkProtocol.CMD_CLEAR:
                    if (currentRoom != null) {
                        currentRoom.clearCanvas();
                        broadcastToAll(raw);
                    }
                    break;
            }
        } catch (Exception ex) {
            System.err.println("[SERVER] Protocol Error: " + ex.getMessage());
        }
    }

    private String buildItemMessage(CanvasItem item) {
        if (item.getItemType() == CanvasItem.ItemType.SHAPE) {
            return item.getShape().toNetworkString(item.getAddedBy());
        } else {
            PastedImage img = item.getImage();
            return NetworkProtocol.buildImage(item.getAddedBy(),
                    img.getXOfImage(), img.getYOfImage(), img.getWidthOfImage(), img.getHeightOfImage(),
                    img.getImageData(), img.getIdOfImage());
        }
    }

    private void leaveCurrentRoom() {
        if (currentRoom != null) {
            currentRoom.removeMember(this);
            broadcastUserList(); // Diğerlerine güncelleme gönder
            roomManager.removeRoomIfEmpty(currentRoom.getCode());
            currentRoom = null;
        }
    }

    private void broadcastUserList() {
        if (currentRoom == null)
            return;
        String listMsg = NetworkProtocol.buildUserList(currentRoom.getMemberNicknames());
        broadcastToAll(listMsg);
    }

    public void send(String msg) {
        if (out != null)
            out.println(msg);
    }

    private void broadcastToOthers(String msg) {
        if (currentRoom == null)
            return;
        for (ClientHandler member : currentRoom.getMembers()) {
            if (member != this)
                member.send(msg);
        }
    }

    private void broadcastToAll(String msg) {
        if (currentRoom == null)
            return;
        for (ClientHandler member : currentRoom.getMembers()) {
            member.send(msg);
        }
    }

    private void cleanup() {
        leaveCurrentRoom();
        if (nickname != null)
            roomManager.unregisterNickname(nickname);
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    public String getNickname() {
        return nickname;
    }
}
