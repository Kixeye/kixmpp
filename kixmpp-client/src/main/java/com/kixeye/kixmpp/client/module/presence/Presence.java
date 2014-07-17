package com.kixeye.kixmpp.client.module.presence;

/**
 * Presence information.
 * 
 * @author ebahtijaragic
 */
public class Presence {
	private final String from;
	private final String to;
	private final String type;
	private final String status;
	private final String show;

	/**
	 * @param from
	 * @param to
	 * @param type
	 * @param status
	 * @param show
	 */
	public Presence(String from, String to, String type, String status,
			String show) {
		this.from = from;
		this.to = to;
		this.type = type;
		this.status = status;
		this.show = show;
	}
	
	/**
	 * @param type
	 * @param status
	 * @param show
	 */
	public Presence(String type, String status, String show) {
		this.from = null;
		this.to = null;
		this.type = type;
		this.status = status;
		this.show = show;
	}
	
	/**
	 * Default constructor.
	 */
	public Presence() {
		this.from = null;
		this.to = null;
		this.type = null;
		this.status = null;
		this.show = null;
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
	 * @return the type
	 */
	public String getType() {
		return type;
	}

	/**
	 * @return the status
	 */
	public String getStatus() {
		return status;
	}

	/**
	 * @return the show
	 */
	public String getShow() {
		return show;
	}
}
