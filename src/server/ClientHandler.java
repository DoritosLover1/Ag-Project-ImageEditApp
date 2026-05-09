package server;

import models.CanvasItem;
import models.DrawShape;
import models.PastedImage;
import network.NetworkProtocol;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class ClientHandler {
    private final SocketChannel channel;
    private final RoomManager roomManager;
    private final CollabServer server;
    private String nickname;
    private Room currentRoom;
    private final StringBuilder buffer = new StringBuilder();
    private final ByteBuffer readBuffer = ByteBuffer.allocate(8192);

    public ClientHandler(SocketChannel channel, RoomManager roomManager, CollabServer server) {
        this.channel = channel;
        this.roomManager = roomManager;
        this.server = server;
    }

    public void handleRead() throws IOException {
        readBuffer.clear();
        int bytesRead = channel.read(readBuffer);
        
        if (bytesRead == -1) {
            cleanup();
            return;
        }

        readBuffer.flip();
        String data = StandardCharsets.UTF_8.decode(readBuffer).toString();
        buffer.append(data);

        // Satır sonuna göre mesajları ayır (Pipe delimited protocol)
        int newlineIndex;
        while ((newlineIndex = buffer.indexOf("\n")) != -1) {
            String message = buffer.substring(0, newlineIndex).trim();
            buffer.delete(0, newlineIndex + 1);
            if (!message.isEmpty()) {
                handleRawMessage(message);
            }
        }
    }

    private void handleRawMessage(String raw) {
        String[] p = raw.split(NetworkProtocol.SEPARATOR);
        if (p.length < 4) return;

        String command = p[3];
        try {
            switch (command) {
                case NetworkProtocol.CMD_LOGIN:
                    String requested = p[4].trim();
                    if (!server.isNicknameTaken(requested)) {
                        this.nickname = requested;
                        send(NetworkProtocol.buildLoginSuccess(nickname));
                        System.out.println("[SERVER] User logged in: " + nickname);
                    } else {
                        send(NetworkProtocol.buildError("Nickname taken or invalid."));
                    }
                    break;
                case NetworkProtocol.CMD_CREATE_ROOM:
                    if (nickname == null) return;
                    leaveCurrentRoom();
                    currentRoom = roomManager.createRoom(nickname);
                    currentRoom.addMember(this);
                    send(NetworkProtocol.buildRoomInfo(currentRoom.getCode()));
                    broadcastUserList();
                    break;
                case NetworkProtocol.CMD_JOIN_ROOM:
                    if (nickname == null) return;
                    String code = p[4].trim().toUpperCase();
                    Room room = roomManager.getRoom(code);
                    if (room != null) {
                        leaveCurrentRoom();
                        currentRoom = room;
                        currentRoom.addMember(this);
                        send(NetworkProtocol.buildRoomInfo(currentRoom.getCode()));
                        broadcastUserList();
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
                case NetworkProtocol.CMD_NEW_USERNAME:
                    String oldNick = p[4].trim();
                    String newNick = p[5].trim();
                    if (!server.isNicknameTaken(newNick)) {
                        this.nickname = newNick;
                        send(NetworkProtocol.buildNameChanged(newNick));
                        broadcastUserList();
                        System.out.println("[SERVER] User changed name: " + oldNick + " -> " + newNick);
                    } else {
                        send(NetworkProtocol.buildError("Nickname '" + newNick + "' is already taken."));
                    }
                    break;
                case NetworkProtocol.CMD_SQUARE:
                case NetworkProtocol.CMD_CIRCLE:
                case NetworkProtocol.CMD_LINE:
                case NetworkProtocol.CMD_TRIANGLE:
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
                    if (currentRoom != null) broadcastToOthers(raw);
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
            broadcastUserList();
            roomManager.removeRoomIfEmpty(currentRoom.getCode());
            currentRoom = null;
        }
    }

    private void broadcastUserList() {
        if (currentRoom == null) return;
        String listMsg = NetworkProtocol.buildUserList(currentRoom.getMemberNicknames());
        broadcastToAll(listMsg);
    }

    public void send(String msg) {
        if (channel.isOpen()) {
            try {
                channel.write(ByteBuffer.wrap((msg + "\n").getBytes(StandardCharsets.UTF_8)));
            } catch (IOException e) {
                cleanup();
            }
        }
    }

    private void broadcastToOthers(String msg) {
        if (currentRoom == null) return;
        for (ClientHandler member : currentRoom.getMembers()) {
            if (member != this) member.send(msg);
        }
    }

    private void broadcastToAll(String msg) {
        if (currentRoom == null) return;
        for (ClientHandler member : currentRoom.getMembers()) {
            member.send(msg);
        }
    }

    public void cleanup() {
        try {
            leaveCurrentRoom();
            server.removeClient(this);
            channel.close();
        } catch (IOException ignored) {}
    }

    public String getNickname() {
        return nickname;
    }
}
