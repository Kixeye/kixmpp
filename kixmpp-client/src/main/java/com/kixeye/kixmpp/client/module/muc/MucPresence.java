package com.kixeye.kixmpp.client.module.muc;

import com.kixeye.kixmpp.client.module.presence.Presence;

/**
 * Represents a MUC presence for a user.
 * 
 * @author ebahtijaragic
 */
public class MucPresence extends Presence {
	private final String userJid;
	private final String affiliation;
	private final String role;

	/**
	 * @param from
	 * @param to
	 * @param type
	 * @param status
	 * @param show
	 * @param userJid
	 * @param affiliation
	 * @param role
	 */
	public MucPresence(String from, String to, String type, String status,
			String show, String userJid, String affiliation, String role) {
		super(from, to, type, status, show);
		this.userJid = userJid;
		this.affiliation = affiliation;
		this.role = role;
	}

	/**
	 * @param from
	 * @param to
	 * @param type
	 * @param userJid
	 * @param affiliation
	 * @param role
	 */
	public MucPresence(String from, String to, String type, String userJid,
			String affiliation, String role) {
		super(from, to, type);
		this.userJid = userJid;
		this.affiliation = affiliation;
		this.role = role;
	}

	/**
	 * @return the userJid
	 */
	public String getUserJid() {
		return userJid;
	}

	/**
	 * @return the affiliation
	 */
	public String getAffiliation() {
		return affiliation;
	}

	/**
	 * @return the role
	 */
	public String getRole() {
		return role;
	}
}
