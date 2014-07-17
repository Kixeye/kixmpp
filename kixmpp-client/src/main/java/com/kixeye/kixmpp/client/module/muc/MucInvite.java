package com.kixeye.kixmpp.client.module.muc;

/**
 * Represents a MUC invite.
 * 
 * @author ebahtijaragic
 */
public class MucInvite {
	private final String from;
	private final String to;
	private final String roomJid;

	/**
	 * @param from
	 * @param to
	 * @param roomJid
	 */
	public MucInvite(String from, String to, String roomJid) {
		this.from = from;
		this.to = to;
		this.roomJid = roomJid;
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
	 * @return the roomJid
	 */
	public String getRoomJid() {
		return roomJid;
	}
}
