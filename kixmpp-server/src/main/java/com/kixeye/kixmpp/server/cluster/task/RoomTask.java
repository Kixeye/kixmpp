package com.kixeye.kixmpp.server.cluster.task;

import com.kixeye.kixmpp.server.module.muc.MucRoom;


public abstract class RoomTask extends ClusterTask {

    private transient MucRoom room;

    private String gameId;
    private String roomId;

    public RoomTask() {
    }

    protected RoomTask(MucRoom room, String gameId, String roomId) {
        this.room = room;
        this.gameId = gameId;
        this.roomId = roomId;
    }

    public void setRoom(MucRoom room) {
        this.room = room;
    }

    public MucRoom getRoom() {
        return room;
    }

    public String getGameId() {
        return gameId;
    }

    public String getRoomId() {
        return roomId;
    }
}
