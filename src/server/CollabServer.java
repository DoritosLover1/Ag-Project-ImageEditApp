package server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class CollabServer {
    public static int TCP_PORT = 12345;
    private final RoomManager roomManager = new RoomManager();
    private final Queue<ClientHandler> activeClients = new ConcurrentLinkedQueue<>();
    private Selector selector;
    private ServerSocketChannel serverChannel;
    private volatile boolean running = true;
    private String serverIp = "localhost";

    public void start() throws IOException {
        java.util.Properties env = new java.util.Properties();
        try (java.io.FileInputStream fis = new java.io.FileInputStream(".env")) {
            env.load(fis);
            if (env.getProperty("SERVER_IP") != null) {
                serverIp = env.getProperty("SERVER_IP").trim().replace("\"", "");
            }
            if (env.getProperty("PORT") != null) {
                TCP_PORT = Integer.parseInt(env.getProperty("PORT").trim().replace("\"", ""));
            }
        } catch (IOException e) {
            serverIp = "localhost";
        }

        selector = Selector.open();
        serverChannel = ServerSocketChannel.open();
        try {
            serverChannel.bind(new InetSocketAddress(serverIp, TCP_PORT));
        } catch (IOException e) {
            serverIp = "localhost";
            TCP_PORT = 12345;
            serverChannel.bind(new InetSocketAddress(serverIp, TCP_PORT));
        }
        serverChannel.configureBlocking(false);
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("╔══════════════════════════════╗");
        System.out.println("║   CollabPaint NIO Server     ║");
        System.out.println("║   IP: " + serverIp + "       ║");
        System.out.println("║   TCP Port: " + TCP_PORT + " ║");
        System.out.println("╚══════════════════════════════╝");

        while (running) {
            selector.select();
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();

                if (!key.isValid())
                    continue;

                if (key.isAcceptable()) {
                    handleAccept(key);
                } else if (key.isReadable()) {
                    handleRead(key);
                }
            }
        }
    }

    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel server = (ServerSocketChannel) key.channel();
        SocketChannel client = server.accept();
        client.configureBlocking(false);

        ClientHandler handler = new ClientHandler(client, roomManager, this);
        client.register(selector, SelectionKey.OP_READ, handler);
        activeClients.add(handler);

        System.out.println("[SERVER] New connection from: " + client.getRemoteAddress());
    }

    private void handleRead(SelectionKey key) {
        ClientHandler handler = (ClientHandler) key.attachment();
        try {
            handler.handleRead();
        } catch (IOException e) {
            System.err.println("[SERVER] Read error: " + e.getMessage());
            handler.cleanup();
        }
    }

    public boolean isNicknameTaken(String nickname) {
        if (nickname == null || nickname.trim().isEmpty())
            return true;
        String target = nickname.trim().toLowerCase();
        for (ClientHandler handler : activeClients) {
            String existing = handler.getNickname();
            if (existing != null && existing.toLowerCase().equals(target)) {
                return true;
            }
        }
        return false;
    }

    public void removeClient(ClientHandler handler) {
        activeClients.remove(handler);
    }

    public static void main(String[] args) throws IOException {
        new CollabServer().start();
    }
}
