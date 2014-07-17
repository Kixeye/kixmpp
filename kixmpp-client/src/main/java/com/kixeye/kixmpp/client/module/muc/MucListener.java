package com.kixeye.kixmpp.client.module.muc;

/**
 * Listens to MUC events.
 * 
 * @author ebahtijaragic
 */
public interface MucListener<T> {
	/**
	 * Handles an event.
	 * 
	 * @param event
	 */
	public void handle(T event);
}
