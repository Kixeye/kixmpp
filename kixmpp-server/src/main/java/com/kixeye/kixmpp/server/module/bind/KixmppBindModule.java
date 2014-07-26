package com.kixeye.kixmpp.server.module.bind;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

import java.util.LinkedList;
import java.util.List;

import org.jdom2.Element;
import org.jdom2.Namespace;

import com.kixeye.kixmpp.server.KixmppJid;
import com.kixeye.kixmpp.server.KixmppServer;
import com.kixeye.kixmpp.server.KixmppStanzaHandler;
import com.kixeye.kixmpp.server.module.KixmppModule;

/**
 * Handles binds.
 * 
 * @author ebahtijaragic
 */
public class KixmppBindModule implements KixmppModule {
	public static AttributeKey<KixmppJid> JID = AttributeKey.valueOf("JID");
	
	private KixmppServer server;
	
	/**
	 * @see com.kixeye.kixmpp.server.module.KixmppModule#install(com.kixeye.kixmpp.server.KixmppServer)
	 */
	public void install(KixmppServer server) {
		this.server = server;
		
		this.server.getHandlerRegistry().register("iq", null, BIND_HANDLER);
	}

	/**
	 * @see com.kixeye.kixmpp.server.module.KixmppModule#uninstall(com.kixeye.kixmpp.server.KixmppServer)
	 */
	public void uninstall(KixmppServer server) {
		this.server.getHandlerRegistry().unregister("iq", null, BIND_HANDLER);
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
	
	private KixmppStanzaHandler BIND_HANDLER = new KixmppStanzaHandler() {
		/**
		 * @see com.kixeye.kixmpp.server.KixmppStanzaHandler#handle(io.netty.channel.Channel, org.jdom2.Element)
		 */
		public void handle(Channel channel, Element stanza) {
			Element bind = stanza.getChild("bind", Namespace.getNamespace("urn:ietf:params:xml:ns:xmpp-bind"));
			
			if (bind != null) {
				// handle the bind
				String resource = bind.getChildText("resource", Namespace.getNamespace("urn:ietf:params:xml:ns:xmpp-bind"));
				
				if (resource != null) {
					channel.attr(KixmppBindModule.JID).set(channel.attr(KixmppBindModule.JID).get().withResource(resource));
				}
				
				Element iq = new Element("iq");
				iq.setAttribute("type", "result");
				
				String id = stanza.getAttributeValue("id");
				
				if (id != null) {
					iq.setAttribute("id", id);
				}
				
				bind = new Element("bind", Namespace.getNamespace("urn:ietf:params:xml:ns:xmpp-bind"));
				bind.addContent(new Element("jid", Namespace.getNamespace("urn:ietf:params:xml:ns:xmpp-bind")).setText(channel.attr(KixmppBindModule.JID).get().toString()));
				
				iq.addContent(bind);
				
				channel.writeAndFlush(iq);
			}
		}
	};
}
