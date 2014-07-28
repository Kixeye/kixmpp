package com.kixeye.kixmpp.server.module.muc;

import java.util.concurrent.ConcurrentHashMap;

import com.kixeye.kixmpp.KixmppJid;

/**
 * A {@link MucService} that persists rooms in memory.
 * 
 * @author ebahtijaragic
 */
public class InMemoryMucService implements MucService {
	private ConcurrentHashMap<String, MucRoom> rooms = new ConcurrentHashMap<>();
	
	private final String serviceDomain;
	
	/**
	 * @param serviceDomain
	 */
	public InMemoryMucService(String serviceDomain) {
		this.serviceDomain = serviceDomain;
	}

	/**
	 * @see com.kixeye.kixmpp.server.module.muc.MucService#addRoom(java.lang.String)
	 */
	public MucRoom addRoom(String name) {
		MucRoom mucRoom = rooms.get(name);
		
		if (mucRoom == null) {
			mucRoom = new MucRoom(new KixmppJid(name, serviceDomain));
			
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
		return rooms.get(name);
	}
}
