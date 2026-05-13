package server;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Random;
public class RoomManager {
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public Room createRoom(String ownerNickname) {
        String code;
        do {
            code = generateCode();
        } while (rooms.containsKey(code) || new java.io.File("saved_canvases/" + code + ".canvas").exists());
        Room room = new Room(code, ownerNickname);
        rooms.put(code, room);
        System.out.println("[ROOM] Created room " + code + " by " + ownerNickname);
        return room;
    }
    public Room getRoom(String code) {
        if (code == null)
            return null;
        String upperCode = code.toUpperCase();
        Room room = rooms.get(upperCode);
        
        // Bellekte yoksa dosyadan yüklemeyi dene
        if (room == null) {
            java.io.File file = new java.io.File("saved_canvases/" + upperCode + ".canvas");
            if (file.exists()) {
                room = new Room(upperCode, "System"); // Dosyadan yüklendiği için owner "System" olabilir
                rooms.put(upperCode, room);
                System.out.println("[ROOM] Loaded existing room from file: " + upperCode);
            }
        }
        return room;
    }
    public void removeRoomIfEmpty(String code) {
        Room room = rooms.get(code);
        if (room != null && room.isEmpty()) {
            rooms.remove(code);
            System.out.println("[ROOM] Removed empty room " + code);
        }
    }
    private String generateCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
