package com.kixeye.kixmpp.server.cluster.task;


import com.kixeye.kixmpp.server.module.muc.MucRoom;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.XMLOutputter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;

public class RoomBroadcastTask extends RoomTask {

    private static transient final Logger logger = LoggerFactory.getLogger(RoomBroadcastTask.class);

    private String from;
    private String body;

    public RoomBroadcastTask() {
    }

    public RoomBroadcastTask(MucRoom room, String gameId, String roomId, String from, Element stanza) {
        super(room,gameId,roomId);
        this.from = from;
        this.body = new XMLOutputter().outputString(stanza);
    }


    @Override
    public void run() {
        Element stanza = null;
        try {
            stanza = new SAXBuilder().build(new StringReader(body)).getRootElement();
        } catch (Exception e) {
            logger.error("parsing body", e);
            return;
        }
        getRoom().broadcast(from,stanza);
    }
}
