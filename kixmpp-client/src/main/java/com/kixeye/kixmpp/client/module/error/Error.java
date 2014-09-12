package com.kixeye.kixmpp.client.module.error;

import org.jdom2.Element;

import com.kixeye.kixmpp.KixmppJid;

/**
 * Defines a reusable error.
 * 
 * @author ebahtijaragic
 */
public class Error {
	private final Element rootElement;
	
	private final KixmppJid by;
	
	private final String type;
	private final Integer code;
	
	private final Element conditionElement;

	/**
	 * @param rootElement
	 * @param by
	 * @param type
	 * @param code
	 * @param conditionElement
	 */
	public Error(Element rootElement, KixmppJid by, String type, Integer code, Element conditionElement) {
		this.rootElement = rootElement;
		this.by = by;
		this.type = type;
		this.code = code;
		this.conditionElement = conditionElement;
	}

	/**
	 * @return the rootElement
	 */
	public Element getRootElement() {
		return rootElement;
	}

	/**
	 * @return the by
	 */
	public KixmppJid getBy() {
		return by;
	}

	/**
	 * @return the type
	 */
	public String getType() {
		return type;
	}

	/**
	 * @return the code
	 */
	public Integer getCode() {
		return code;
	}

	/**
	 * @return the conditionElement
	 */
	public Element getConditionElement() {
		return conditionElement;
	}
}
