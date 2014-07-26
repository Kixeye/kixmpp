package com.kixeye.kixmpp.server.module.session;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

import java.util.LinkedList;
import java.util.List;

import org.jdom2.Element;
import org.jdom2.Namespace;

import com.kixeye.kixmpp.server.KixmppServer;
import com.kixeye.kixmpp.server.KixmppStanzaHandler;
import com.kixeye.kixmpp.server.module.KixmppModule;

/**
 * Handles sessions.
 * 
 * @author ebahtijaragic
 */
public class KixmppSessionModule implements KixmppModule {
	public static AttributeKey<Boolean> IS_SESSION_ESTABLISHED = AttributeKey.valueOf("IS_SESSION_ESTABLISHED");
	
	private KixmppServer server;
	
	/**
	 * @see com.kixeye.kixmpp.server.module.KixmppModule#install(com.kixeye.kixmpp.server.KixmppServer)
	 */
	public void install(KixmppServer server) {
		this.server = server;
		
		this.server.getHandlerRegistry().register("iq", null, SESSION_HANDLER);
	}

	/**
	 * @see com.kixeye.kixmpp.server.module.KixmppModule#uninstall(com.kixeye.kixmpp.server.KixmppServer)
	 */
	public void uninstall(KixmppServer server) {
		this.server.getHandlerRegistry().unregister("iq", null, SESSION_HANDLER);
	}

	/**
	 * @see com.kixeye.kixmpp.server.module.KixmppModule#getFeatures()
	 */
	public List<Element> getFeatures() {
		List<Element> features = new LinkedList<>();
		
		Element bind = new Element("bind", null, "urn:ietf:params:xml:ns:xmpp-bind");
		
		features.add(bind);
		
		return features;
	}
	
	private KixmppStanzaHandler SESSION_HANDLER = new KixmppStanzaHandler() {
		/**
		 * @see com.kixeye.kixmpp.server.KixmppStanzaHandler#handle(io.netty.channel.Channel, org.jdom2.Element)
		 */
		public void handle(Channel channel, Element stanza) {
			Element session = stanza.getChild("session", Namespace.getNamespace("urn:ietf:params:xml:ns:xmpp-session"));
			
			if (session != null) {
				channel.attr(IS_SESSION_ESTABLISHED).set(true);
				
				Element iq = new Element("iq");
				iq.setAttribute("type", "result");
				iq.setAttribute("from", server.getDomain());
				
				String id = stanza.getAttributeValue("id");
				
				if (id != null) {
					iq.setAttribute("id", id);
				}
				
				channel.writeAndFlush(iq);
			}
		}
	};
}
