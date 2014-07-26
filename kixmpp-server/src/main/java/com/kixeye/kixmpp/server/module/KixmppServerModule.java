package com.kixeye.kixmpp.server.module;

import java.util.List;

import org.jdom2.Element;

import com.kixeye.kixmpp.module.KixmppModule;
import com.kixeye.kixmpp.server.KixmppServer;

/**
 * A module for a {@link KixmppServer}
 * 
 * @author ebahtijaragic
 */
public interface KixmppServerModule extends KixmppModule<KixmppServer> {
	/**
	 * Gets a list of features added by this module.
	 * 
	 * @return
	 */
	public List<Element> getFeatures();
}
