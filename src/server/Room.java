package server;
import models.CanvasItem;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
public class Room {
    private final String code;
    private final String ownerNickname;
    private final List<ClientHandler> members = new CopyOnWriteArrayList<>();
    private final List<CanvasItem> canvasItems = Collections.synchronizedList(new ArrayList<>());
    private static final String SAVE_DIR = "saved_canvases/";
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
            List<CanvasItem> loaded = (List<CanvasItem>) ois.readObject();
            synchronized (canvasItems) {
                canvasItems.clear();
                canvasItems.addAll(loaded);
            }
            System.out.println("[SAVE] Loaded " + loaded.size() + " items for room " + code);
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("[SAVE] Failed to load room " + code + ": " + e.getMessage());
        }
    }
}
