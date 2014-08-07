package com.kixeye.kixmpp.server.cluster.task;

/*
 * #%L
 * KIXMPP
 * %%
 * Copyright (C) 2014 KIXEYE, Inc
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */


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
