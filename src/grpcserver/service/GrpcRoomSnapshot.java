package grpcserver.service;

import models.CanvasItem;
import models.DrawShape;
import models.PastedImage;
import paicollab.v1.*;
import server.Room;

import java.util.ArrayList;
import java.util.List;

public final class GrpcRoomSnapshot {
    private GrpcRoomSnapshot() {
    }

    public static List<Event> buildSnapshot(Room room) {
        List<Event> out = new ArrayList<>();
        if (room == null)
            return out;

        // Canvas items
        for (CanvasItem item : room.getCanvasSnapshot()) {
            if (item.getItemType() == CanvasItem.ItemType.SHAPE) {
                Event ev = Event.newBuilder()
                        .setRoomCode(room.getCode())
                        .setTimestampMs(System.currentTimeMillis())
                        .setSender(item.getAddedBy())
                        .setType(EventType.EVENT_TYPE_SHAPE)
                        .setShape(fromDrawShape(item.getShape()))
                        .build();
                out.add(ev);
            } else {
                PastedImage img = item.getImage();
                Event ev = Event.newBuilder()
                        .setRoomCode(room.getCode())
                        .setTimestampMs(System.currentTimeMillis())
                        .setSender(item.getAddedBy())
                        .setType(EventType.EVENT_TYPE_IMAGE)
                        .setImage(ImageEvent.newBuilder()
                                .setId(img.getIdOfImage())
                                .setX(img.getXOfImage())
                                .setY(img.getYOfImage())
                                .setW(img.getWidthOfImage())
                                .setH(img.getHeightOfImage())
                                .setPngBytes(com.google.protobuf.ByteString.copyFrom(img.getImageData()))
                                .build())
                        .build();
                out.add(ev);
            }
        }

        // Chat history
        for (Room.ChatMessage cm : room.getChatMessages()) {
            Event ev = Event.newBuilder()
                    .setRoomCode(room.getCode())
                    .setTimestampMs(System.currentTimeMillis())
                    .setSender("SERVER")
                    .setType(EventType.EVENT_TYPE_CHAT_HISTORY)
                    .setChatHistory(ChatHistoryEvent.newBuilder()
                            .setOriginalSender(cm.sender)
                            .setMessage(cm.message)
                            .setOriginalTimestampMs(cm.timestamp)
                            .build())
                    .build();
            out.add(ev);
        }

        return out;
    }

    public static ShapeEvent fromDrawShape(DrawShape s) {
        ShapeEvent.Builder b = ShapeEvent.newBuilder();
        b.setId(s.getIdOfShape() == null ? "" : s.getIdOfShape());
        b.setColor(s.getColorOfShape() == null ? "#000000" : s.getColorOfShape());
        b.setStroke(s.getStrokeWidthOfShape());
        b.setFilled(s.isFilledShape());

        DrawShape.ShapeType t = s.getShapeType();
        if (t == null) {
            b.setShapeType(ShapeType.SHAPE_TYPE_UNSPECIFIED);
            return b.build();
        }

        switch (t) {
            case RECTANGLE -> {
                b.setShapeType(ShapeType.SHAPE_TYPE_SQUARE);
                b.setX(s.getXOfShape());
                b.setY(s.getYOfShape());
                b.setW(s.getWidthOfShape());
                b.setH(s.getHeightOfShape());
            }
            case CIRCLE -> {
                b.setShapeType(ShapeType.SHAPE_TYPE_CIRCLE);
                b.setX(s.getXOfShape());
                b.setY(s.getYOfShape());
                b.setW(s.getWidthOfShape());
                b.setH(s.getHeightOfShape());
            }
            case LINE -> {
                b.setShapeType(ShapeType.SHAPE_TYPE_LINE);
                b.setX1(s.getXOfShape());
                b.setY1(s.getYOfShape());
                b.setX2(s.getX2OfShape());
                b.setY2(s.getY2OfShape());
            }
            case TRIANGLE -> {
                b.setShapeType(ShapeType.SHAPE_TYPE_TRIANGLE);
                b.setTx1(s.getXOfShape());
                b.setTy1(s.getYOfShape());
                b.setTx2(s.getX2OfShape());
                b.setTy2(s.getY2OfShape());
                b.setTx3(s.getX3OfShape());
                b.setTy3(s.getY3OfShape());
            }
            case FREEHAND -> {
                b.setShapeType(ShapeType.SHAPE_TYPE_FREEHAND);
                int[] xs = s.getFreehandXPointsOfShape();
                int[] ys = s.getFreehandYPointsOfShape();
                if (xs != null)
                    for (int x : xs)
                        b.addXs(x);
                if (ys != null)
                    for (int y : ys)
                        b.addYs(y);
            }
            default -> b.setShapeType(ShapeType.SHAPE_TYPE_UNSPECIFIED);
        }
        return b.build();
    }

    public static DrawShape toDrawShape(ShapeEvent ev) {
        DrawShape s = new DrawShape();
        s.setIdOfShape(ev.getId());
        s.setColorOfShape(ev.getColor());
        s.setStrokeWidthOfShape(ev.getStroke());
        s.setFilledShape(ev.getFilled());

        switch (ev.getShapeType()) {
            case SHAPE_TYPE_SQUARE -> {
                s.setShapeType(DrawShape.ShapeType.RECTANGLE);
                s.setXOfShape(ev.getX());
                s.setYOfShape(ev.getY());
                s.setWidthOfShape(ev.getW());
                s.setHeightOfShape(ev.getH());
            }
            case SHAPE_TYPE_CIRCLE -> {
                s.setShapeType(DrawShape.ShapeType.CIRCLE);
                s.setXOfShape(ev.getX());
                s.setYOfShape(ev.getY());
                s.setWidthOfShape(ev.getW());
                s.setHeightOfShape(ev.getH());
            }
            case SHAPE_TYPE_LINE -> {
                s.setShapeType(DrawShape.ShapeType.LINE);
                s.setXOfShape(ev.getX1());
                s.setYOfShape(ev.getY1());
                s.setX2OfShape(ev.getX2());
                s.setY2OfShape(ev.getY2());
            }
            case SHAPE_TYPE_TRIANGLE -> {
                s.setShapeType(DrawShape.ShapeType.TRIANGLE);
                s.setXOfShape(ev.getTx1());
                s.setYOfShape(ev.getTy1());
                s.setX2OfShape(ev.getTx2());
                s.setY2OfShape(ev.getTy2());
                s.setX3OfShape(ev.getTx3());
                s.setY3OfShape(ev.getTy3());
            }
            case SHAPE_TYPE_FREEHAND -> {
                s.setShapeType(DrawShape.ShapeType.FREEHAND);
                int[] xs = new int[ev.getXsCount()];
                int[] ys = new int[ev.getYsCount()];
                for (int i = 0; i < ev.getXsCount(); i++)
                    xs[i] = ev.getXs(i);
                for (int i = 0; i < ev.getYsCount(); i++)
                    ys[i] = ev.getYs(i);
                s.setFreehandXPointsOfShape(xs);
                s.setFreehandYPointsOfShape(ys);
            }
            default -> s.setShapeType(DrawShape.ShapeType.RECTANGLE);
        }
        return s;
    }
}
