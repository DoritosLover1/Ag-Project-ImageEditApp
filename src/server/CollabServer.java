package server;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
public class CollabServer {
    public static final int TCP_PORT = 12345;
    private final RoomManager roomManager = new RoomManager();
    private volatile boolean running = true;
    public void start() throws IOException {
        System.out.println("╔══════════════════════════════╗");
        System.out.println("║   CollabPaint Server v1.0    ║");
        System.out.println("║   Protocol: NetworkProtocol  ║");
        System.out.println("║   TCP Port: " + TCP_PORT + " ║");
        System.out.println("╚══════════════════════════════╝");
        try (ServerSocket serverSocket = new ServerSocket(TCP_PORT)) {
            System.out.println("[SERVER] Listening on port " + TCP_PORT);
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("[SERVER] New connection from: " + clientSocket.getInetAddress());
                    ClientHandler handler = new ClientHandler(clientSocket, roomManager);
                    Thread t = new Thread(handler, "ClientHandler-" + clientSocket.getPort());
                    t.setDaemon(true);
                    t.start();
                } catch (IOException e) {
                    if (running)
                        System.err.println("[SERVER] Accept error: " + e.getMessage());
                }
            }
        }
    }
    public static void main(String[] args) throws IOException {
        new CollabServer().start();
    }
}
