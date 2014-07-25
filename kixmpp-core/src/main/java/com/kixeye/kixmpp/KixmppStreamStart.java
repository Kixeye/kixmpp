package com.kixeye.kixmpp;

import org.jdom2.Element;

/**
 * An event indicating a stream started.
 * 
 * @author ebahtijaragic
 */
public class KixmppStreamStart {
	private final Element streamStartElement;

	/**
	 * Creates a stream start.
	 * 
	 * @param streamStartElement
	 */
	public KixmppStreamStart(Element streamStartElement) {
		this.streamStartElement = streamStartElement;
	}

	/**
	 * @return the streamStartElement
	 */
	public Element getStreamStartElement() {
		return streamStartElement;
	}
}
