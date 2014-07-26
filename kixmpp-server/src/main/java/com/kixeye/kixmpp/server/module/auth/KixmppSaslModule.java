package com.kixeye.kixmpp.server.module.auth;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.handler.codec.base64.Base64;
import io.netty.util.AttributeKey;

import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jdom2.Element;

import com.kixeye.kixmpp.server.KixmppJid;
import com.kixeye.kixmpp.server.KixmppServer;
import com.kixeye.kixmpp.server.KixmppStanzaHandler;
import com.kixeye.kixmpp.server.module.KixmppModule;
import com.kixeye.kixmpp.server.module.bind.KixmppBindModule;

/**
 * Handles SASL auth.
 * 
 * @author ebahtijaragic
 */
public class KixmppSaslModule implements KixmppModule {
	public static AttributeKey<Boolean> IS_AUTHENTICATED = AttributeKey.valueOf("IS_AUTHENTICATED");
	
	private Map<String, String> users = new ConcurrentHashMap<>();
	
	private KixmppServer server;
	
	/**
	 * @see com.kixeye.kixmpp.server.module.KixmppModule#install(com.kixeye.kixmpp.server.KixmppServer)
	 */
	public void install(KixmppServer server) {
		this.server = server;
		
		this.server.getHandlerRegistry().register("auth", null, AUTH_HANDLER);
	}

	/**
	 * @see com.kixeye.kixmpp.server.module.KixmppModule#uninstall(com.kixeye.kixmpp.server.KixmppServer)
	 */
	public void uninstall(KixmppServer server) {
		this.server.getHandlerRegistry().unregister("auth", null, AUTH_HANDLER);
	}

	/**
	 * @see com.kixeye.kixmpp.server.module.KixmppModule#getFeatures()
	 */
	public List<Element> getFeatures() {
		List<Element> features = new LinkedList<>();
		
		Element mechanisms = new Element("mechanisms", null, "urn:ietf:params:xml:ns:xmpp-sasl");
		
		Element plainMechanism = new Element("mechanism");
		plainMechanism.setText("PLAIN");
		
		mechanisms.addContent(plainMechanism);
		
		features.add(mechanisms);
		
		return features;
	}
	
	/**
	 * Adds a user.
	 */
	public void addUser(String username, String password) {
		users.put(username, password);
	}
	
	/**
	 * Removes a user.
	 */
	public void removeUser(String username) {
		users.remove(username);
	}
	
	private KixmppStanzaHandler AUTH_HANDLER = new KixmppStanzaHandler() {
		/**
		 * @see com.kixeye.kixmpp.server.KixmppStanzaHandler#handle(io.netty.channel.Channel, org.jdom2.Element)
		 */
		public void handle(Channel channel, Element stanza) {
			if ("PLAIN".equals(stanza.getAttributeValue("mechanism"))) {
				String base64Encoded = stanza.getText();
				
				ByteBuf encodecCredentials = channel.alloc().buffer().writeBytes(base64Encoded.getBytes(StandardCharsets.UTF_8));
				ByteBuf rawCredentials = Base64.decode(encodecCredentials);
				String raw = rawCredentials.toString(StandardCharsets.UTF_8);
				encodecCredentials.release();
				rawCredentials.release();
				
				String[] credentialsSplit = raw.split("\0");
				if (credentialsSplit.length > 1) {
					String username = credentialsSplit[1];
					
					String password = users.get(username);
					
					if (password != null && password.equals(credentialsSplit[2])) {
						channel.attr(IS_AUTHENTICATED).set(true);
						channel.attr(KixmppBindModule.JID).set(new KixmppJid(username, server.getDomain(), UUID.randomUUID().toString().replace("-", "")));
						
						Element success = new Element("success", null, "urn:ietf:params:xml:ns:xmpp-sasl");
						
						channel.writeAndFlush(success);
					} else {
						Element failure = new Element("failure", null, "urn:ietf:params:xml:ns:xmpp-sasl");
						
						channel.writeAndFlush(failure);
					}
				} else {
					Element failure = new Element("failure", null, "urn:ietf:params:xml:ns:xmpp-sasl");
					
					channel.writeAndFlush(failure);
				}
			} else {
				Element failure = new Element("failure", null, "urn:ietf:params:xml:ns:xmpp-sasl");
				
				channel.writeAndFlush(failure);
			}
		}
	};
}
