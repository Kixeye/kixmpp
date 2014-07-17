package com.kixeye.kixmpp.client.module.presence;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jdom2.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kixeye.kixmpp.client.KixmppClient;
import com.kixeye.kixmpp.client.KixmppStanzaHandler;
import com.kixeye.kixmpp.client.module.KixmppModule;

/**
 * A module that handles presence info.
 * 
 * @author ebahtijaragic
 */
public class PresenceKixmppModule implements KixmppModule {
	private static final Logger logger = LoggerFactory.getLogger(PresenceKixmppModule.class);
	
	private Set<PresenceListener> presenceListeners = Collections.newSetFromMap(new ConcurrentHashMap<PresenceListener, Boolean>());
	
	private KixmppClient client = null;
	
	/**
	 * @param listener the listener to add
	 */
	public void addPresenceListener(PresenceListener listener) {
		presenceListeners.add(listener);
	}

	/**
	 * @param listener the listener to add
	 */
	public void removePresenceListener(PresenceListener listener) {
		presenceListeners.remove(listener);
	}

	/**
	 * @see com.kixeye.kixmpp.client.module.KixmppModule#install(com.kixeye.kixmpp.client.KixmppClient)
	 */
	public void install(KixmppClient client) {
		this.client = client;

		client.getHandlerRegistry().register("presence", "jabber:client", presenceHandler);
	}

	/**
	 * @see com.kixeye.kixmpp.client.module.KixmppModule#uninstall(com.kixeye.kixmpp.client.KixmppClient)
	 */
	public void uninstall(KixmppClient client) {
		client.getHandlerRegistry().unregister("presence", "jabber:client", presenceHandler);
	}

	/**
	 * Updates the current user's presence.
	 *     
	 * @param presence
	 */
    public void updatePresence(Presence presence) {
    	Element presenceElement = new Element("presence");
    	
    	if (presence.getType() != null) {
    		presenceElement.setAttribute("type", presence.getType());
    	}
    	
    	if (presence.getStatus() != null) {
    		Element statusElement = new Element("status");
    		statusElement.setText(presence.getStatus());
    		
    		presenceElement.addContent(statusElement);
    	}
    	
    	if (presence.getShow() != null) {
    		Element showElement = new Element("show");
    		showElement.setText(presence.getShow());
    		
    		presenceElement.addContent(showElement);
    	}
		
		client.sendStanza(presenceElement);
    }

	private KixmppStanzaHandler presenceHandler = new KixmppStanzaHandler() {
		public void handle(Element stanza) {
			Presence presence = new Presence(stanza.getAttributeValue("from"), 
					stanza.getAttributeValue("to"), 
					stanza.getAttributeValue("type"), 
					stanza.getChildText("status"), 
					stanza.getChildText("show"));
			
			for (PresenceListener listener : presenceListeners) {
				try {
					listener.handle(presence);
				} catch (Exception e) {
					logger.error("Exception thrown while executing Presence listener", e);
				}
			}
		}
	};
}
