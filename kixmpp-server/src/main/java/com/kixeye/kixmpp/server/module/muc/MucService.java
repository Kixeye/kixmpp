package com.kixeye.kixmpp.server.module.muc;

/**
 * A service that handles MUCs.
 * 
 * @author ebahtijaragic
 */
public interface MucService {
	/**
	 * Adds a {@link MucRoom}.
	 * 
	 * @param name
	 * @return
	 */
	public MucRoom addRoom(String name);
	
	/**
	 * Gets a {@link MucRoom}.
	 * 
	 * @param name
	 * @return
	 */
	public MucRoom getRoom(String name);
}
