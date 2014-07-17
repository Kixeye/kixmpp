package com.kixeye.kixmpp.client.module.presence;

/**
 * A listener for presence messages.
 * 
 * @author ebahtijaragic
 */
public interface PresenceListener {
	/**
	 * Handles a presence.
	 * 
	 * @param presence
	 */
	public void handle(Presence presence);
}
