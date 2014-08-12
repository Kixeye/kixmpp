package com.kixeye.kixmpp.server.module.muc;

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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.kixeye.kixmpp.KixmppJid;
import com.kixeye.kixmpp.server.KixmppServer;
import org.fusesource.hawtdispatch.Task;

/**
 * A {@link MucService} that persists rooms in memory.
 * 
 * @author ebahtijaragic
 */
public class InMemoryMucService implements MucService {
	private ConcurrentHashMap<String, MucRoom> rooms = new ConcurrentHashMap<>();

    private final KixmppServer server;
	private final String serviceDomain;

	
	/**
	 * @param serviceDomain
	 */
	public InMemoryMucService(KixmppServer server, String serviceDomain) {
		serviceDomain = serviceDomain.toLowerCase();
		this.server = server;
		this.serviceDomain = serviceDomain;
	}

    @Override
    public List<MucRoom> getRooms() {
        return new ArrayList<>(rooms.values());
    }

    @Override
    public void broadcast(final String... messages) {
        for(MucRoom room:rooms.values()){
            final MucRoom tmp = room;
            server.getEventEngine().publishTask( room.getRoomJid(), new Task() {
                @Override
                public void run() {
                    tmp.receiveMessages(false, messages);
                }
            });
        }
    }

    /**
     * @see com.kixeye.kixmpp.server.module.muc.MucService#addRoom(java.lang.String)
     */
    public MucRoom addRoom(String name) {
        return addRoom(name, new MucRoomSettings());
    }

    /**
     * @see com.kixeye.kixmpp.server.module.muc.MucService#addRoom(java.lang.String)
     */
    public MucRoom addRoom(String name, MucRoomSettings options) {
        name = name.toLowerCase();

        MucRoom mucRoom = rooms.get(name);

        if (mucRoom == null) {
            mucRoom = new MucRoom(server, new KixmppJid(name,serviceDomain), options);

            MucRoom prevRoom = rooms.putIfAbsent(name, mucRoom);

            if (prevRoom != null) {
                mucRoom = prevRoom;
            }
        }

        return mucRoom;
    }

	/**
	 * @see com.kixeye.kixmpp.server.module.muc.MucService#getRoom(java.lang.String)
	 */
	public MucRoom getRoom(String name) {
		name = name.toLowerCase();
		
		return rooms.get(name);
	}
}
