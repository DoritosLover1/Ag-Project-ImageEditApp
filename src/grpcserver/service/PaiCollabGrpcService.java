package grpcserver.service;

import com.google.protobuf.ByteString;
import com.rabbitmq.client.Channel;
import grpcserver.RabbitBus;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import paicollab.v1.*;
import server.Room;
import server.RoomManager;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

/**
 * gRPC service that reuses existing Room/RoomManager storage, and uses RabbitMQ
 * fanout to broadcast room events.
 *
 * Notes:
 * - Nickname uniqueness is enforced server-wide (like your NIO server).
 * - Snapshot is returned in RoomEnterResponse as a list of Events.
 * - Live broadcast uses RabbitMQ exchange per room.
 */
public final class PaiCollabGrpcService extends PaiCollabServiceGrpc.PaiCollabServiceImplBase {
    private final RoomManager roomManager;
    private final RabbitBus bus;

    // Basic session bookkeeping
    private final Set<String> activeNicknamesLower = ConcurrentHashMap.newKeySet();
    private final Map<String, String> nicknameToRoom = new ConcurrentHashMap<>();

    public PaiCollabGrpcService(RoomManager roomManager, RabbitBus bus) {
        this.roomManager = roomManager;
        this.bus = bus;
    }

    @Override
    public void login(LoginRequest request, StreamObserver<LoginResponse> responseObserver) {
        String requested = safeTrim(request.getRequestedNickname());
        if (requested.isEmpty()) {
            responseObserver.onNext(LoginResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorMessage("Nickname invalid.")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        String key = requested.toLowerCase(Locale.ROOT);
        boolean added = activeNicknamesLower.add(key);
        if (!added) {
            responseObserver.onNext(LoginResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorMessage("Nickname taken or invalid.")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        responseObserver.onNext(LoginResponse.newBuilder()
                .setSuccess(true)
                .setApprovedNickname(requested)
                .build());
        responseObserver.onCompleted();
        System.out.println("[gRPC] User logged in: " + requested);
    }

    @Override
    public void createRoom(RoomCreateRequest request, StreamObserver<RoomEnterResponse> responseObserver) {
        String nick = safeTrim(request.getNickname());
        if (nick.isEmpty() || !activeNicknamesLower.contains(nick.toLowerCase(Locale.ROOT))) {
            responseObserver.onNext(RoomEnterResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorMessage("Not logged in.")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        leaveRoomInternal(nick);

        Room room = roomManager.createRoom(nick);
        room.addGrpcMember(nick);
        nicknameToRoom.put(nick, room.getCode());

        RoomEnterResponse resp = buildEnterResponse(room);
        responseObserver.onNext(resp);
        responseObserver.onCompleted();

        // Broadcast updated user list
        broadcastUserList(room);
    }

    @Override
    public void joinRoom(RoomJoinRequest request, StreamObserver<RoomEnterResponse> responseObserver) {
        String nick = safeTrim(request.getNickname());
        String code = safeTrim(request.getRoomCode()).toUpperCase(Locale.ROOT);
        if (nick.isEmpty() || !activeNicknamesLower.contains(nick.toLowerCase(Locale.ROOT))) {
            responseObserver.onNext(RoomEnterResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorMessage("Not logged in.")
                    .build());
            responseObserver.onCompleted();
            return;
        }
        if (code.isEmpty()) {
            responseObserver.onNext(RoomEnterResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorMessage("Room code required.")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        Room room = roomManager.getRoom(code);
        if (room == null) {
            responseObserver.onNext(RoomEnterResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorMessage("Room not found.")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        leaveRoomInternal(nick);

        room.addGrpcMember(nick);
        nicknameToRoom.put(nick, room.getCode());

        RoomEnterResponse resp = buildEnterResponse(room);
        responseObserver.onNext(resp);
        responseObserver.onCompleted();

        broadcastUserList(room);
        System.out.println("[gRPC] User " + nick + " joined room " + code);
    }

    @Override
    public void leaveRoom(RoomLeaveRequest request, StreamObserver<Ack> responseObserver) {
        String nick = safeTrim(request.getNickname());
        leaveRoomInternal(nick);
        responseObserver.onNext(Ack.newBuilder().setSuccess(true).build());
        responseObserver.onCompleted();
        System.out.println("[gRPC] User " + nick + " left room");
    }

    @Override
    public void changeName(ChangeNameRequest request, StreamObserver<ChangeNameResponse> responseObserver) {
        String oldNick = safeTrim(request.getOldNickname());
        String newNick = safeTrim(request.getNewNickname());

        if (oldNick.isEmpty() || newNick.isEmpty()) {
            responseObserver.onNext(ChangeNameResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorMessage("Invalid nickname.")
                    .build());
            responseObserver.onCompleted();
            return;
        }
        if (!activeNicknamesLower.contains(oldNick.toLowerCase(Locale.ROOT))) {
            responseObserver.onNext(ChangeNameResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorMessage("Not logged in.")
                    .build());
            responseObserver.onCompleted();
            return;
        }
        if (activeNicknamesLower.contains(newNick.toLowerCase(Locale.ROOT))) {
            responseObserver.onNext(ChangeNameResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorMessage("Nickname '" + newNick + "' is already taken.")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        // swap in sets/maps
        activeNicknamesLower.remove(oldNick.toLowerCase(Locale.ROOT));
        activeNicknamesLower.add(newNick.toLowerCase(Locale.ROOT));

        String roomCode = nicknameToRoom.remove(oldNick);
        if (roomCode != null) {
            nicknameToRoom.put(newNick, roomCode);
            Room room = roomManager.getRoom(roomCode);
            if (room != null) {
                room.removeGrpcMember(oldNick);
                room.addGrpcMember(newNick);
                broadcastUserList(room);
            }
        }

        responseObserver.onNext(ChangeNameResponse.newBuilder()
                .setSuccess(true)
                .setApprovedNickname(newNick)
                .build());
        responseObserver.onCompleted();
        System.out.println("[gRPC] User " + oldNick + " changed name to " + newNick);
    }

    @Override
    public void sendEvent(Event request, StreamObserver<Ack> responseObserver) {
        String roomCode = safeTrim(request.getRoomCode());
        String sender = safeTrim(request.getSender());

        if (roomCode.isEmpty() || sender.isEmpty()) {
            responseObserver.onNext(Ack.newBuilder().setSuccess(false).setErrorMessage("Invalid event.").build());
            responseObserver.onCompleted();
            return;
        }

        // Persist only what your original server persisted.
        // Cursor is not persisted; Clear/Delete/Shape/Image/Chat are persisted in Room.
        Room room = roomManager.getRoom(roomCode);
        if (room == null) {
            responseObserver.onNext(Ack.newBuilder().setSuccess(false).setErrorMessage("Room not found.").build());
            responseObserver.onCompleted();
            return;
        }

        try {
            GrpcRoomPersistence.applyToRoom(room, request);
        } catch (Exception e) {
            responseObserver.onNext(Ack.newBuilder().setSuccess(false).setErrorMessage("Persist failed.").build());
            responseObserver.onCompleted();
            return;
        }

        // Publish live to RabbitMQ
        Channel ch = null;
        try {
            ch = bus.openChannel();
            bus.publishRoom(ch, roomCode, request.toByteArray());
        } catch (IOException e) {
            responseObserver.onNext(Ack.newBuilder().setSuccess(false).setErrorMessage("Publish failed.").build());
            responseObserver.onCompleted();
            return;
        } finally {
            if (ch != null) {
                try {
                    ch.close();
                } catch (Exception ignored) {
                }
            }
        }

        responseObserver.onNext(Ack.newBuilder().setSuccess(true).build());
        responseObserver.onCompleted();
    }

    @Override
    public void subscribe(SubscribeRequest request, StreamObserver<Event> responseObserver) {
        String nick = safeTrim(request.getNickname());
        String roomCode = safeTrim(request.getRoomCode()).toUpperCase(Locale.ROOT);

        if (nick.isEmpty() || roomCode.isEmpty()) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT.withDescription("nickname/room_code required").asRuntimeException());
            return;
        }
        if (!roomCode.equals(nicknameToRoom.get(nick))) {
            responseObserver.onError(Status.PERMISSION_DENIED.withDescription("Not in that room").asRuntimeException());
            return;
        }

        Channel ch;
        try {
            ch = bus.openChannel();
        } catch (IOException e) {
            responseObserver.onError(Status.UNAVAILABLE.withDescription("RabbitMQ unavailable").asRuntimeException());
            return;
        }

        final String[] consumerTagHolder = new String[1];
        try {
            String tag = bus.subscribeRoom(ch, roomCode, (body, headers) -> {
                try {
                    Event ev = Event.parseFrom(body);
                    responseObserver.onNext(ev);
                } catch (Exception ignored) {
                }
            });
            consumerTagHolder[0] = tag;
        } catch (IOException e) {
            safeClose(ch);
            responseObserver
                    .onError(Status.UNAVAILABLE.withDescription("RabbitMQ subscribe failed").asRuntimeException());
            return;
        }

        if (responseObserver instanceof io.grpc.stub.ServerCallStreamObserver) {
            io.grpc.stub.ServerCallStreamObserver<Event> serverCallObserver = (io.grpc.stub.ServerCallStreamObserver<Event>) responseObserver;
            serverCallObserver.setOnCancelHandler(() -> {
                leaveRoomInternal(nick);
                safeClose(ch);
                activeNicknamesLower.remove(nick.toLowerCase(Locale.ROOT));
                System.out.println("[gRPC] User " + nick + " left room (stream cancelled)");
            });
        }

        // When client cancels stream, gRPC will drop; we can't always detect
        // immediately without interceptors.
        // Best-effort: rely on Rabbit auto-delete exclusive queue + channel close when
        // JVM detects cancellation.
        // We'll close channel when StreamObserver errors/completes; but we don't get
        // callbacks here.
        // So: we attach a weak cleanup via a thread that monitors interruption.
        new Thread(() -> {
            try {
                // Keep thread alive until channel is closed by broker/recovery.
                while (ch.isOpen()) {
                    Thread.sleep(2500);
                }
            } catch (InterruptedException ignored) {
            } finally {
                try {
                    if (consumerTagHolder[0] != null && ch.isOpen()) {
                        ch.basicCancel(consumerTagHolder[0]);
                    }
                } catch (Exception ignored) {
                }
                safeClose(ch);
            }
        }, "grpc-subscribe-" + nick + "-" + roomCode).start();
    }

    private void broadcastUserList(Room room) {
        List<String> users = room.getMemberNicknames();
        Event ev = Event.newBuilder()
                .setRoomCode(room.getCode())
                .setTimestampMs(System.currentTimeMillis())
                .setSender("SERVER")
                .setType(EventType.EVENT_TYPE_USER_LIST)
                .setUserList(UserListEvent.newBuilder().addAllUsers(users).build())
                .build();
        Channel ch = null;
        try {
            ch = bus.openChannel();
            bus.publishRoom(ch, room.getCode(), ev.toByteArray());
        } catch (IOException ignored) {
        } finally {
            if (ch != null) {
                try {
                    ch.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private RoomEnterResponse buildEnterResponse(Room room) {
        // Snapshot = persisted canvas items + persisted chat messages (history)
        List<Event> snapshot = GrpcRoomSnapshot.buildSnapshot(room);
        List<String> users = room.getMemberNicknames();

        return RoomEnterResponse.newBuilder()
                .setSuccess(true)
                .setRoomCode(room.getCode())
                .addAllSnapshotEvents(snapshot)
                .addAllUsers(users)
                .build();
    }

    private void leaveRoomInternal(String nick) {
        if (nick == null || nick.isBlank())
            return;

        String roomCode = nicknameToRoom.remove(nick);
        if (roomCode == null)
            return;

        Room room = roomManager.getRoom(roomCode);
        if (room != null) {
            room.removeGrpcMember(nick);
            broadcastUserList(room);
            roomManager.removeRoomIfEmpty(roomCode);
        }
    }

    private static String safeTrim(String s) {
        if (s == null)
            return "";
        return s.trim();
    }

    private static void safeClose(Channel ch) {
        try {
            ch.close();
        } catch (Exception ignored) {
        }
    }
}
