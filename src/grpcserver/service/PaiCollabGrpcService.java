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

public final class PaiCollabGrpcService extends PaiCollabServiceGrpc.PaiCollabServiceImplBase {
    private final RoomManager roomManager;
    private final RabbitBus bus;

    private final Set<String> activeNicknamesLower = ConcurrentHashMap.newKeySet();
    private final Map<String, String> nicknameToRoom = new ConcurrentHashMap<>();

    private Channel sharedPublishChannel;

    public PaiCollabGrpcService(RoomManager roomManager, RabbitBus bus) {
        this.roomManager = roomManager;
        this.bus = bus;
        try {
            this.sharedPublishChannel = bus.openChannel();
        } catch (IOException e) {
            System.err.println("[gRPC] Failed to open shared publish channel: " + e.getMessage());
            this.sharedPublishChannel = null;
        }
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

        try {
            synchronized (this) {
                if (sharedPublishChannel == null || !sharedPublishChannel.isOpen()) {
                    sharedPublishChannel = bus.openChannel(); // Kanal kazara kapandıysa iyileşme (recovery)
                }
                bus.publishRoom(sharedPublishChannel, roomCode, request.toByteArray());
            }
        } catch (IOException e) {
            responseObserver.onNext(Ack.newBuilder().setSuccess(false).setErrorMessage("Publish failed.").build());
            responseObserver.onCompleted();
            return;
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

        try {
            final Channel ch = bus.openChannel();

            String tag = bus.subscribeRoom(ch, roomCode, (body, headers) -> {
                try {
                    Event ev = Event.parseFrom(body);
                    responseObserver.onNext(ev);
                } catch (Exception ignored) {
                }
            });

            if (responseObserver instanceof io.grpc.stub.ServerCallStreamObserver) {
                io.grpc.stub.ServerCallStreamObserver<Event> serverCallObserver = (io.grpc.stub.ServerCallStreamObserver<Event>) responseObserver;

                serverCallObserver.setOnCancelHandler(() -> {
                    try {
                        if (ch.isOpen() && tag != null) {
                            ch.basicCancel(tag);
                        }
                    } catch (Exception ignored) {
                    }

                    leaveRoomInternal(nick);
                    safeClose(ch);
                    activeNicknamesLower.remove(nick.toLowerCase(Locale.ROOT));
                    System.out.println("[gRPC] User " + nick + " left room and RabbitMQ channel closed safely.");
                });
            }

        } catch (IOException e) {
            responseObserver
                    .onError(Status.UNAVAILABLE.withDescription("RabbitMQ connection failed").asRuntimeException());
        }
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
