package server;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import models.CanvasItem;

public class Room {
    private final String code;
    private final String ownerNickname;
    private final List<ClientHandler> members = new CopyOnWriteArrayList<>();
    private final List<CanvasItem> canvasItems = Collections.synchronizedList(new ArrayList<>());
    private final List<ChatMessage> chatMessages = Collections.synchronizedList(new ArrayList<>());
    private static final String SAVE_DIR = "saved_canvases/";

    public static class ChatMessage implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String sender;
        public final String message;
        public final long timestamp;

        public ChatMessage(String sender, String message, long timestamp) {
            this.sender = sender;
            this.message = message;
            this.timestamp = timestamp;
        }
    }

    public Room(String code, String ownerNickname) {
        this.code = code;
        this.ownerNickname = ownerNickname;
        loadFromFile();
    }

    public String getCode() {
        return code;
    }

    public String getOwnerNickname() {
        return ownerNickname;
    }

    public void addMember(ClientHandler handler) {
        members.add(handler);
    }

    public void removeMember(ClientHandler handler) {
        members.remove(handler);
    }

    public List<ClientHandler> getMembers() {
        return members;
    }

    public boolean isEmpty() {
        return members.isEmpty();
    }

    public void addCanvasItem(CanvasItem item) {
        canvasItems.add(item);
        saveToFile();
    }

    public void clearCanvas() {
        canvasItems.clear();
        saveToFile();
    }

    public void removeCanvasItemById(String id) {
        if (id == null)
            return;
        synchronized (canvasItems) {
            canvasItems.removeIf(item -> id.equals(item.getIdOfImage()));
        }
        saveToFile();
    }

    public List<CanvasItem> getCanvasSnapshot() {
        synchronized (canvasItems) {
            return new ArrayList<>(canvasItems);
        }
    }

    public void addChatMessage(String sender, String message) {
        ChatMessage chatMsg = new ChatMessage(sender, message, System.currentTimeMillis());
        chatMessages.add(chatMsg);
        saveToFile();
    }

    public List<ChatMessage> getChatMessages() {
        synchronized (chatMessages) {
            return new ArrayList<>(chatMessages);
        }
    }

    public List<String> getMemberNicknames() {
        List<String> nicks = new ArrayList<>();
        for (ClientHandler h : members) {
            if (h.getNickname() != null)
                nicks.add(h.getNickname());
        }
        return nicks;
    }

    private void saveToFile() {
        try {
            File dir = new File(SAVE_DIR);
            if (!dir.exists())
                dir.mkdirs();
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new FileOutputStream(SAVE_DIR + code + ".canvas"))) {
                synchronized (canvasItems) {
                    oos.writeObject(new ArrayList<>(canvasItems));
                }
                synchronized (chatMessages) {
                    oos.writeObject(new ArrayList<>(chatMessages));
                }
            }
        } catch (IOException e) {
            System.err.println("[SAVE] Failed to save room " + code + ": " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadFromFile() {
        File file = new File(SAVE_DIR + code + ".canvas");
        if (!file.exists())
            return;
        try (ObjectInputStream ois = new ObjectInputStream(
                new FileInputStream(file))) {
            List<CanvasItem> loadedItems = (List<CanvasItem>) ois.readObject();
            synchronized (canvasItems) {
                canvasItems.clear();
                canvasItems.addAll(loadedItems);
            }

            try {
                List<ChatMessage> loadedChat = (List<ChatMessage>) ois.readObject();
                synchronized (chatMessages) {
                    chatMessages.clear();
                    chatMessages.addAll(loadedChat);
                }
                System.out.println("[SAVE] Loaded " + loadedItems.size() + " items and " + loadedChat.size()
                        + " messages for room " + code);
            } catch (EOFException e) {
                System.out.println(
                        "[SAVE] Loaded " + loadedItems.size() + " items for room " + code + " (no chat history)");
            }

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("[SAVE] Failed to load room " + code + ": " + e.getMessage());
        }
    }
}
