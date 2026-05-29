package grpcserver.service;

import models.CanvasItem;
import models.DrawShape;
import models.PastedImage;
import paicollab.v1.*;
import server.Room;

import java.util.Base64;

final class GrpcRoomPersistence {
    private GrpcRoomPersistence() {
    }

    static void applyToRoom(Room room, Event ev) {
        if (room == null || ev == null)
            return;

        switch (ev.getType()) {
            case EVENT_TYPE_SHAPE -> persistShape(room, ev);
            case EVENT_TYPE_IMAGE -> persistImage(room, ev);
            case EVENT_TYPE_DELETE -> persistDelete(room, ev);
            case EVENT_TYPE_CLEAR -> persistClear(room, ev);
            case EVENT_TYPE_CHAT -> persistChat(room, ev);
            default -> {
            }
        }
    }

    private static void persistShape(Room room, Event ev) {
        if (!ev.hasShape())
            return;
        DrawShape s = GrpcRoomSnapshot.toDrawShape(ev.getShape());
        room.addCanvasItem(new CanvasItem(s, ev.getSender()));
    }

    private static void persistImage(Room room, Event ev) {
        if (!ev.hasImage())
            return;
        ImageEvent imgEv = ev.getImage();
        PastedImage img = new PastedImage();
        img.setXOfImage(imgEv.getX());
        img.setYOfImage(imgEv.getY());
        img.setWidthOfImage(imgEv.getW());
        img.setHeightOfImage(imgEv.getH());
        img.setImageData(imgEv.getPngBytes().toByteArray());
        img.setIdOfImage(imgEv.getId());
        room.addCanvasItem(new CanvasItem(img, ev.getSender()));
    }

    private static void persistDelete(Room room, Event ev) {
        if (!ev.hasDelete())
            return;
        room.removeCanvasItemById(ev.getDelete().getTargetId());
    }

    private static void persistClear(Room room, Event ev) {
        room.clearCanvas();
    }

    private static void persistChat(Room room, Event ev) {
        if (!ev.hasChat())
            return;
        String msg = ev.getChat().getMessage();
        if (msg == null || msg.isBlank())
            return;
        room.addChatMessage(ev.getSender(), msg.trim());
    }
}
