package server;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Random;
public class RoomManager {
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Set<String> takenNicknames = ConcurrentHashMap.newKeySet();
    private final Random random = new Random();
    public boolean registerNickname(String nickname) {
        if (nickname == null || nickname.trim().isEmpty())
            return false;
        return takenNicknames.add(nickname.trim().toLowerCase());
    }
    public void unregisterNickname(String nickname) {
        if (nickname != null)
            takenNicknames.remove(nickname.trim().toLowerCase());
    }
    public Room createRoom(String ownerNickname) {
        String code;
        do {
            code = generateCode();
        } while (rooms.containsKey(code));
        Room room = new Room(code, ownerNickname);
        rooms.put(code, room);
        System.out.println("[ROOM] Created room " + code + " by " + ownerNickname);
        return room;
    }
    public Room getRoom(String code) {
        if (code == null)
            return null;
        return rooms.get(code.toUpperCase());
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
