package grpcserver;

import grpcserver.service.PaiCollabGrpcService;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import server.RoomManager;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public final class GrpcServerMain {
    public static void main(String[] args) throws Exception {
        Properties env = loadDotEnv();

        int grpcPort = parseInt(env.getProperty("GRPC_PORT"), 50051);
        String rabbitHost = stripQuotes(env.getProperty("RABBIT_HOST", "localhost"));
        int rabbitPort = parseInt(env.getProperty("RABBIT_PORT"), 5672);
        String rabbitUser = stripQuotes(env.getProperty("RABBIT_USER", "guest"));
        String rabbitPass = stripQuotes(env.getProperty("RABBIT_PASS", "guest"));
        String rabbitVhost = stripQuotes(env.getProperty("RABBIT_VHOST", "/"));

        RoomManager roomManager = new RoomManager();
        RabbitBus bus = new RabbitBus(rabbitHost, rabbitPort, rabbitUser, rabbitPass, rabbitVhost);

        PaiCollabGrpcService svc = new PaiCollabGrpcService(roomManager, bus);

        Server server = NettyServerBuilder.forPort(grpcPort)
                .addService(svc)
                .build()
                .start();

        System.out.println("[gRPC] Server started on port " + grpcPort);
        System.out.println("[RabbitMQ] " + rabbitHost + ":" + rabbitPort + " vhost=" + rabbitVhost);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[gRPC] Shutting down...");
            try {
                server.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
            try {
                bus.close();
            } catch (IOException ignored) {
            }
        }));

        server.awaitTermination();
    }

    private static Properties loadDotEnv() {
        Properties p = new Properties();
        try (FileInputStream fis = new FileInputStream(".env")) {
            p.load(fis);
        } catch (IOException ignored) {
        }
        return p;
    }

    private static int parseInt(String v, int def) {
        try {
            if (v == null)
                return def;
            return Integer.parseInt(stripQuotes(v).trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static String stripQuotes(String s) {
        if (s == null)
            return null;
        return s.trim().replace("\"", "");
    }
}
