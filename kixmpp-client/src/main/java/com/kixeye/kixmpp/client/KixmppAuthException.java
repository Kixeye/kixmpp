package com.kixeye.kixmpp.client;


/**
 * An authentication exception that gets thrown when auth fails.
 * 
 * @author ebahtijaragic
 */
public class KixmppAuthException extends KixmppException {
	private static final long serialVersionUID = -2495670401789500578L;

	/**
	 * Creates an auth response.
	 * 
	 * @param response
	 */
	public KixmppAuthException(String response) {
		super(response);
	}
}
