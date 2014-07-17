package com.kixeye.kixmpp.client.module.muc;

/**
 * A MUC message.
 * 
 * @author ebahtijaragic
 */
public class MucMessage {
	private final String from;
	private final String to;
	private final String language;
	private final String body;

	/**
	 * @param from
	 * @param to
	 * @param language
	 * @param body
	 */
	public MucMessage(String from, String to, String language, String body) {
		this.from = from;
		this.to = to;
		this.language = language;
		this.body = body;
	}

	/**
	 * @return the from
	 */
	public String getFrom() {
		return from;
	}

	/**
	 * @return the to
	 */
	public String getTo() {
		return to;
	}

	/**
	 * @return the language
	 */
	public String getLanguage() {
		return language;
	}

	/**
	 * @return the body
	 */
	public String getBody() {
		return body;
	}
}
