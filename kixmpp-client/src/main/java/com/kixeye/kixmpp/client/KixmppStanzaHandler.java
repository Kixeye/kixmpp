package com.kixeye.kixmpp.client;

import org.jdom2.Element;

/**
 * Handles stanzas.
 * 
 * @author ebahtijaragic
 */
public interface KixmppStanzaHandler {
	/**
	 * Handles a stanza.
	 * 
	 * @param stanza
	 */
	public void handle(Element stanza);
}
