package com.kixeye.kixmpp.server.module.muc;

import com.kixeye.kixmpp.KixmppJid;

/**
 * Holds Muc history.
 * 
 * @author ebahtijaragic
 */
public class MucHistory {
	private final KixmppJid from;
	private final KixmppJid to;
	private final String nickname;
	private final String body;
	private final long timestamp;

	/**
	 * @param from
	 * @param to
	 * @param nickname
	 * @param body
	 * @param timestamp
	 */
	public MucHistory(KixmppJid from, KixmppJid to, String nickname,
			String body, long timestamp) {
		this.from = from;
		this.to = to;
		this.nickname = nickname;
		this.body = body;
		this.timestamp = timestamp;
	}

	/**
	 * @return the from
	 */
	public KixmppJid getFrom() {
		return from;
	}

	/**
	 * @return the to
	 */
	public KixmppJid getTo() {
		return to;
	}

	/**
	 * @return the nickname
	 */
	public String getNickname() {
		return nickname;
	}

	/**
	 * @return the body
	 */
	public String getBody() {
		return body;
	}

	/**
	 * @return the timestamp
	 */
	public long getTimestamp() {
		return timestamp;
	}
}
