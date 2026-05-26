package grpcserver;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

/**
 * Minimal RabbitMQ helper for room-scoped fanout.
 *
 * Exchange per room: paicollab.room.<ROOM_CODE> (type: fanout)
 * Each subscriber gets an exclusive auto-delete queue bound to that exchange.
 */
public final class RabbitBus implements Closeable {
    public static final String ROOM_EXCHANGE_PREFIX = "paicollab.room.";

    private final Connection connection;

    public RabbitBus(String host, int port, String username, String password, String virtualHost)
            throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(username);
        factory.setPassword(password);
        factory.setVirtualHost(virtualHost);
        factory.setConnectionTimeout((int) Duration.ofSeconds(8).toMillis());
        factory.setHandshakeTimeout((int) Duration.ofSeconds(8).toMillis());
        factory.setAutomaticRecoveryEnabled(true);
        factory.setTopologyRecoveryEnabled(true);
        this.connection = factory.newConnection("PaiCollab-RabbitBus");
    }

    public Channel openChannel() throws IOException {
        return connection.createChannel();
    }

    public static String roomExchange(String roomCode) {
        return ROOM_EXCHANGE_PREFIX + roomCode;
    }

    public void ensureRoomExchange(Channel ch, String roomCode) throws IOException {
        ch.exchangeDeclare(roomExchange(roomCode), BuiltinExchangeType.FANOUT, true);
    }

    public void publishRoom(Channel ch, String roomCode, byte[] payload) throws IOException {
        ensureRoomExchange(ch, roomCode);
        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                .contentType("application/octet-stream")
                .deliveryMode(2) // persistent
                .build();
        ch.basicPublish(roomExchange(roomCode), "", props, payload);
    }

    /**
     * Create an exclusive queue bound to room exchange, consume it, and call handler for each delivery.
     * Returns consumerTag; caller must cancel it.
     */
    public String subscribeRoom(Channel ch,
                               String roomCode,
                               BiConsumer<byte[], Map<String, Object>> handler) throws IOException {
        ensureRoomExchange(ch, roomCode);

        // Exclusive, server-named queue; auto-delete when channel/connection closes
        String queue = ch.queueDeclare("", false, true, true, null).getQueue();
        ch.queueBind(queue, roomExchange(roomCode), "");

        ch.basicQos(200);
        DeliverCallback cb = (consumerTag, delivery) -> {
            handler.accept(delivery.getBody(), delivery.getProperties().getHeaders());
        };
        return ch.basicConsume(queue, true, cb, consumerTag -> { });
    }

    public static String envOrDefault(String v, String def) {
        String s = System.getenv(v);
        return (s == null || s.isBlank()) ? def : s.trim();
    }

    public static int envIntOrDefault(String v, int def) {
        try {
            String s = System.getenv(v);
            if (s == null || s.isBlank())
                return def;
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    public static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void close() throws IOException {
        connection.close();
    }
}

