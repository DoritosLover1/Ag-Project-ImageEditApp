package network;

import grpcserver.RabbitBus;
import grpcserver.service.GrpcRoomSnapshot;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import models.CanvasItem;
import models.CursorPosition;
import models.DrawShape;
import models.PastedImage;
import paicollab.v1.*;
import server.Room;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class GrpcClientNetworkManager {
    private final String host;
    private final int port;

    private ManagedChannel channel;
    private PaiCollabServiceGrpc.PaiCollabServiceBlockingStub blocking;
    private PaiCollabServiceGrpc.PaiCollabServiceStub async;

    private volatile String nickname;
    private volatile String roomCode;

    private volatile uiframe.DrawingCanvas canvas;
    private volatile uiframe.ChatPanel chatPanel;
    private volatile VoiceChatManager voiceManager;

    private Consumer<String> onRoomJoined;
    private Consumer<List<String>> onUserListUpdated;
    private Consumer<String> onError;
    private Consumer<String> onNameChanged;
    private Consumer<String> onLoginSuccess;

    private StreamObserver<Event> subscription;

    public GrpcClientNetworkManager(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public GrpcClientNetworkManager(String host, int port,
            uiframe.DrawingCanvas canvas,
            uiframe.ChatPanel chatPanel) {
        this.host = host;
        this.port = port;
        this.canvas = canvas;
        this.chatPanel = chatPanel;
    }

    public void setCanvas(uiframe.DrawingCanvas canvas) {
        this.canvas = canvas;
    }

    public void setChatPanel(uiframe.ChatPanel chatPanel) {
        this.chatPanel = chatPanel;
    }

    public void setVoiceManager(VoiceChatManager voiceManager) {
        this.voiceManager = voiceManager;
        if (this.voiceManager != null) {
            this.voiceManager.setOnAudioCaptured(this::sendAudio);
        }
    }

    public void connectAndLogin(String requestedNickname) {
        this.nickname = requestedNickname;
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.blocking = PaiCollabServiceGrpc.newBlockingStub(channel);
        this.async = PaiCollabServiceGrpc.newStub(channel);

        new Thread(() -> {
            try {
                LoginResponse resp = blocking.login(LoginRequest.newBuilder()
                        .setRequestedNickname(requestedNickname)
                        .build());
                if (!resp.getSuccess()) {
                    fireError(resp.getErrorMessage());
                    return;
                }
                this.nickname = resp.getApprovedNickname();
                if (onLoginSuccess != null) {
                    SwingUtilities.invokeLater(() -> onLoginSuccess.accept(this.nickname));
                }
            } catch (Exception e) {
                fireError(e.getMessage());
            }
        }, "grpc-login").start();
    }

    public void createRoom() {
        callEnter(() -> blocking.createRoom(RoomCreateRequest.newBuilder().setNickname(nickname).build()));
    }

    public void joinRoom(String code) {
        String rc = (code == null) ? "" : code.trim().toUpperCase(Locale.ROOT);
        callEnter(() -> blocking.joinRoom(RoomJoinRequest.newBuilder().setNickname(nickname).setRoomCode(rc).build()));
    }

    public void leaveRoom() {
        String rc = roomCode;
        roomCode = null;
        cancelSubscribe();
        new Thread(() -> {
            try {
                blocking.leaveRoom(RoomLeaveRequest.newBuilder()
                        .setNickname(nickname)
                        .setRoomCode(rc == null ? "" : rc)
                        .build());
            } catch (Exception ignored) {
            }
        }, "grpc-leave").start();
    }

    public void changeName(String newNick) {
        new Thread(() -> {
            try {
                ChangeNameResponse resp = blocking.changeName(ChangeNameRequest.newBuilder()
                        .setOldNickname(nickname)
                        .setNewNickname(newNick == null ? "" : newNick.trim())
                        .build());
                if (!resp.getSuccess()) {
                    fireError(resp.getErrorMessage());
                    return;
                }
                nickname = resp.getApprovedNickname();
                if (onNameChanged != null) {
                    SwingUtilities.invokeLater(() -> onNameChanged.accept(nickname));
                }
            } catch (Exception e) {
                fireError(e.getMessage());
            }
        }, "grpc-change-name").start();
    }

    public void sendShape(DrawShape shape) {
        if (shape == null || roomCode == null)
            return;
        Event ev = Event.newBuilder()
                .setRoomCode(roomCode)
                .setTimestampMs(System.currentTimeMillis())
                .setSender(nickname)
                .setType(EventType.EVENT_TYPE_SHAPE)
                .setShape(grpcserver.service.GrpcRoomSnapshot.fromDrawShape(shape))
                .build();
        sendEventAsync(ev);
    }

    public void sendCursor(CursorPosition cp) {
        if (cp == null || roomCode == null)
            return;
        Event ev = Event.newBuilder()
                .setRoomCode(roomCode)
                .setTimestampMs(System.currentTimeMillis())
                .setSender(nickname)
                .setType(EventType.EVENT_TYPE_CURSOR)
                .setCursor(CursorEvent.newBuilder()
                        .setX(cp.getX())
                        .setY(cp.getY())
                        .setColor(cp.getColor() == null ? "" : cp.getColor())
                        .build())
                .build();
        sendEventAsync(ev);
    }

    public void sendChat(String message) {
        if (message == null || message.isBlank() || roomCode == null)
            return;
        Event ev = Event.newBuilder()
                .setRoomCode(roomCode)
                .setTimestampMs(System.currentTimeMillis())
                .setSender(nickname)
                .setType(EventType.EVENT_TYPE_CHAT)
                .setChat(ChatEvent.newBuilder().setMessage(message.trim()).build())
                .build();
        sendEventAsync(ev);
    }

    public void sendDelete(String targetId) {
        if (targetId == null || targetId.isBlank() || roomCode == null)
            return;
        Event ev = Event.newBuilder()
                .setRoomCode(roomCode)
                .setTimestampMs(System.currentTimeMillis())
                .setSender(nickname)
                .setType(EventType.EVENT_TYPE_DELETE)
                .setDelete(DeleteEvent.newBuilder().setTargetId(targetId.trim()).build())
                .build();
        sendEventAsync(ev);
    }

    public void sendClear() {
        if (roomCode == null)
            return;
        Event ev = Event.newBuilder()
                .setRoomCode(roomCode)
                .setTimestampMs(System.currentTimeMillis())
                .setSender(nickname)
                .setType(EventType.EVENT_TYPE_CLEAR)
                .setClear(ClearEvent.newBuilder().setScope("ALL").build())
                .build();
        sendEventAsync(ev);
    }

    public void sendImage(PastedImage pi) {
        if (pi == null || roomCode == null)
            return;
        Event ev = Event.newBuilder()
                .setRoomCode(roomCode)
                .setTimestampMs(System.currentTimeMillis())
                .setSender(nickname)
                .setType(EventType.EVENT_TYPE_IMAGE)
                .setImage(ImageEvent.newBuilder()
                        .setId(pi.getIdOfImage())
                        .setX(pi.getXOfImage())
                        .setY(pi.getYOfImage())
                        .setW(pi.getWidthOfImage())
                        .setH(pi.getHeightOfImage())
                        .setPngBytes(com.google.protobuf.ByteString.copyFrom(pi.getImageData()))
                        .build())
                .build();
        sendEventAsync(ev);
    }

    public void sendAudio(byte[] audioData) {
        if (audioData == null || roomCode == null)
            return;
        Event ev = Event.newBuilder()
                .setRoomCode(roomCode)
                .setTimestampMs(System.currentTimeMillis())
                .setSender(nickname)
                .setType(EventType.EVENT_TYPE_AUDIO)
                .setAudio(AudioEvent.newBuilder().setAudioData(com.google.protobuf.ByteString.copyFrom(audioData))
                        .build())
                .build();
        sendEventAsync(ev);
    }

    public void shutdown() {
        cancelSubscribe();
        if (channel != null) {
            try {
                channel.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
        }
    }

    public void setOnRoomJoined(Consumer<String> callback) {
        this.onRoomJoined = callback;
    }

    public void setOnUserListUpdated(Consumer<List<String>> callback) {
        this.onUserListUpdated = callback;
    }

    public void setOnError(Consumer<String> callback) {
        this.onError = callback;
    }

    public void setOnNameChanged(Consumer<String> callback) {
        this.onNameChanged = callback;
    }

    public void setOnLoginSuccess(Consumer<String> callback) {
        this.onLoginSuccess = callback;
    }

    public String getNickname() {
        return nickname;
    }

    private void callEnter(EnterCall call) {
        new Thread(() -> {
            try {
                RoomEnterResponse resp = call.run();
                if (!resp.getSuccess()) {
                    fireError(resp.getErrorMessage());
                    return;
                }
                this.roomCode = resp.getRoomCode();

                if (onRoomJoined != null) {
                    SwingUtilities.invokeLater(() -> onRoomJoined.accept(roomCode));
                }

                if (canvas != null) {
                    canvas.clearCanvas();
                }
                if (chatPanel != null) {
                    chatPanel.clearMessages();
                }

                if (onUserListUpdated != null) {
                    SwingUtilities.invokeLater(() -> onUserListUpdated.accept(resp.getUsersList()));
                }
                applySnapshotEvents(resp.getSnapshotEventsList());

                startSubscribe();
            } catch (Exception e) {
                fireError(e.getMessage());
            }
        }, "grpc-enter-room").start();
    }

    private void applySnapshotEvents(List<Event> snapshot) {
        if (snapshot == null)
            return;

        for (Event ev : snapshot) {
            if (ev == null)
                continue;

            String sender = ev.getSender();
            switch (ev.getType()) {
                case EVENT_TYPE_SHAPE: {
                    if (canvas == null || !ev.hasShape())
                        break;
                    DrawShape s = GrpcRoomSnapshot.toDrawShape(ev.getShape());
                    canvas.addShapeSilently(s, sender);
                    break;
                }
                case EVENT_TYPE_IMAGE: {
                    if (canvas == null || !ev.hasImage())
                        break;
                    ImageEvent imgEv = ev.getImage();
                    PastedImage pi = new PastedImage();
                    pi.setIdOfImage(imgEv.getId());
                    pi.setXOfImage(imgEv.getX());
                    pi.setYOfImage(imgEv.getY());
                    pi.setWidthOfImage(imgEv.getW());
                    pi.setHeightOfImage(imgEv.getH());
                    pi.setImageData(imgEv.getPngBytes().toByteArray());
                    canvas.addRemoteImage(pi, sender);
                    break;
                }
                case EVENT_TYPE_CHAT_HISTORY: {
                    if (chatPanel == null || !ev.hasChatHistory())
                        break;
                    ChatHistoryEvent h = ev.getChatHistory();
                    chatPanel.receiveHistoryMessage(h.getOriginalSender(), h.getMessage(), h.getOriginalTimestampMs());
                    break;
                }
                default:
                    break;
            }
        }
    }

    private void startSubscribe() {
        cancelSubscribe();
        if (roomCode == null)
            return;

        subscribeContext = io.grpc.Context.current().withCancellation();
        subscribeContext.run(() -> {
            async.subscribe(SubscribeRequest.newBuilder()
                    .setNickname(nickname)
                    .setRoomCode(roomCode)
                    .build(), new StreamObserver<>() {
                        @Override
                        public void onNext(Event ev) {
                            if (ev == null)
                                return;
                            if (roomCode == null)
                                return;
                            if (ev.getRoomCode() == null || !ev.getRoomCode().equals(roomCode))
                                return;

                            String sender = ev.getSender();
                            switch (ev.getType()) {
                                case EVENT_TYPE_USER_LIST: {
                                    if (onUserListUpdated != null && ev.hasUserList()) {
                                        SwingUtilities.invokeLater(
                                                () -> onUserListUpdated.accept(ev.getUserList().getUsersList()));
                                    }
                                    break;
                                }
                                case EVENT_TYPE_SHAPE: {
                                    System.out.println("[DEBUG] Received SHAPE event from: " + sender + ", canvas is: "
                                            + (canvas != null));
                                    if (canvas != null && ev.hasShape()) {
                                        DrawShape s = GrpcRoomSnapshot.toDrawShape(ev.getShape());
                                        canvas.addShapeSilently(s, sender);
                                    }
                                    break;
                                }
                                case EVENT_TYPE_IMAGE: {
                                    if (canvas != null && ev.hasImage()) {
                                        ImageEvent imgEv = ev.getImage();
                                        PastedImage pi = new PastedImage();
                                        pi.setIdOfImage(imgEv.getId());
                                        pi.setXOfImage(imgEv.getX());
                                        pi.setYOfImage(imgEv.getY());
                                        pi.setWidthOfImage(imgEv.getW());
                                        pi.setHeightOfImage(imgEv.getH());
                                        pi.setImageData(imgEv.getPngBytes().toByteArray());
                                        canvas.addRemoteImage(pi, sender);
                                    }
                                    break;
                                }
                                case EVENT_TYPE_DELETE: {
                                    if (canvas != null && ev.hasDelete()) {
                                        canvas.removeItemById(ev.getDelete().getTargetId());
                                    }
                                    break;
                                }
                                case EVENT_TYPE_CURSOR: {
                                    if (canvas == null || !ev.hasCursor())
                                        break;
                                    if (sender != null && sender.equals(nickname))
                                        break;
                                    CursorEvent ce = ev.getCursor();
                                    canvas.updateRemoteCursor(
                                            new CursorPosition(ce.getX(), ce.getY(), sender, ce.getColor()));
                                    break;
                                }
                                case EVENT_TYPE_CLEAR: {
                                    if (canvas != null) {
                                        canvas.clearCanvas();
                                    }
                                    break;
                                }
                                case EVENT_TYPE_CHAT: {
                                    if (chatPanel != null && ev.hasChat()) {
                                        chatPanel.receiveMessage(sender, ev.getChat().getMessage());
                                    }
                                    break;
                                }
                                case EVENT_TYPE_CHAT_HISTORY: {
                                    break;
                                }
                                case EVENT_TYPE_AUDIO: {
                                    if (voiceManager != null && ev.hasAudio()) {
                                        if (sender != null && !sender.equals(nickname)) {
                                            voiceManager.playAudio(sender, ev.getAudio().getAudioData().toByteArray());
                                        }
                                    }
                                    break;
                                }
                                default:
                                    break;
                            }
                        }

                        @Override
                        public void onError(Throwable t) {
                            if (roomCode == null)
                                return;
                            if (t instanceof io.grpc.StatusRuntimeException) {
                                io.grpc.Status status = ((io.grpc.StatusRuntimeException) t).getStatus();
                                if (status.getCode() == io.grpc.Status.Code.CANCELLED) {
                                    return; // Expected when leaving room
                                }
                            }
                            if (t.getMessage() != null && t.getMessage().contains("CANCELLED")) {
                                return;
                            }
                            fireError(t.getMessage());
                        }

                        @Override
                        public void onCompleted() {
                        }
                    });
        });
    }

    private volatile io.grpc.Context.CancellableContext subscribeContext;

    private void cancelSubscribe() {
        if (subscribeContext != null) {
            subscribeContext.cancel(null);
            subscribeContext = null;
        }
    }

    private void sendEventAsync(Event ev) {
        new Thread(() -> {
            try {
                Ack ack = blocking.sendEvent(ev);
                if (!ack.getSuccess()) {
                    fireError(ack.getErrorMessage());
                }
            } catch (Exception e) {
                fireError(e.getMessage());
            }
        }, "grpc-send-event").start();
    }

    private void fireError(String msg) {
        if (onError != null) {
            SwingUtilities.invokeLater(() -> onError.accept(msg == null ? "Unknown error" : msg));
        }
    }

    @FunctionalInterface
    private interface EnterCall {
        RoomEnterResponse run();
    }
}
