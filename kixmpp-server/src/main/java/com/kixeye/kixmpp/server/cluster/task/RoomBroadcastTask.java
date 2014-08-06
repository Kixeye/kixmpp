package com.kixeye.kixmpp.server.cluster.task;


import com.kixeye.kixmpp.server.module.muc.MucRoom;
import org.jdom2.Element;

public class RoomBroadcastTask extends RoomTask {

    private String from;
    private Element body;

    public RoomBroadcastTask() {
    }

    public RoomBroadcastTask(MucRoom room, String gameId, String roomId, String from, Element body) {
        super(room,gameId,roomId);
        this.from = from;
        this.body = body;
    }

    @Override
    public void run() {
        getRoom().broadcast(from,body);
    }
}
