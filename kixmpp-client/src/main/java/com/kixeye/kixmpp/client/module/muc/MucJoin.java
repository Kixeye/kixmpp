package com.kixeye.kixmpp.client.module.muc;

/**
 * A message about the user joining the room.
 * 
 * @author ebahtijaragic
 */
public class MucJoin {
	private final String roomJid;
	private final String userJid;
	private final String affiliation;
	private final String role;

	/**
	 * @param roomJid
	 * @param userJid
	 * @param affiliation
	 * @param role
	 */
	public MucJoin(String roomJid, String userJid, String affiliation,
			String role) {
		this.roomJid = roomJid;
		this.userJid = userJid;
		this.affiliation = affiliation;
		this.role = role;
	}

	/**
	 * @return the roomJid
	 */
	public String getRoomJid() {
		return roomJid;
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
