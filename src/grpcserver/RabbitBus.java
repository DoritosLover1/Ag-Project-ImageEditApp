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

import java.util.concurrent.ConcurrentHashMap;

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

    private final ConcurrentHashMap<String, Boolean> declaredExchanges = new ConcurrentHashMap<>();

    private String roomExchange(String roomCode) {
        return "paicollab.room." + roomCode;
    }

    public void ensureRoomExchange(Channel ch, String roomCode) throws IOException {
        String exchangeName = roomExchange(roomCode);
        if (!declaredExchanges.containsKey(exchangeName)) {
            synchronized (declaredExchanges) {
                if (!declaredExchanges.containsKey(exchangeName)) {
                    ch.exchangeDeclare(exchangeName, BuiltinExchangeType.FANOUT, true);
                    declaredExchanges.put(exchangeName, true);
                }
            }
        }
    }

    public void publishRoom(Channel ch, String roomCode, byte[] payload) throws IOException {
        ensureRoomExchange(ch, roomCode);
        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                .contentType("application/octet-stream")
                .deliveryMode(2) // persistent
                .build();
        ch.basicPublish(roomExchange(roomCode), "", props, payload);
    }

    public String subscribeRoom(Channel ch,
            String roomCode,
            BiConsumer<byte[], Map<String, Object>> handler) throws IOException {
        ensureRoomExchange(ch, roomCode);

        String queue = ch.queueDeclare("", false, true, true, null).getQueue();
        ch.queueBind(queue, roomExchange(roomCode), "");

        ch.basicQos(200);
        DeliverCallback cb = (consumerTag, delivery) -> {
            handler.accept(delivery.getBody(), delivery.getProperties().getHeaders());
        };
        return ch.basicConsume(queue, true, cb, consumerTag -> {
        });
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
