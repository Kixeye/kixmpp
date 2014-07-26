package com.kixeye.kixmpp.client.module.muc;

/*
 * #%L
 * KIXMPP
 * %%
 * Copyright (C) 2014 KIXEYE, Inc
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jdom2.Element;
import org.jdom2.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kixeye.kixmpp.client.KixmppClient;
import com.kixeye.kixmpp.client.KixmppStanzaHandler;
import com.kixeye.kixmpp.client.module.KixmppModule;

/**
 * A {@link KixmppModule} that deals with MUCs.
 * 
 * @author ebahtijaragic
 */
public class MucKixmppModule implements KixmppModule {
	private static final Logger logger = LoggerFactory.getLogger(MucKixmppModule.class);
	
	private Set<MucListener<MucMessage>> messageListeners = Collections.newSetFromMap(new ConcurrentHashMap<MucListener<MucMessage>, Boolean>());
	private Set<MucListener<MucInvite>> invitationListeners = Collections.newSetFromMap(new ConcurrentHashMap<MucListener<MucInvite>, Boolean>());
	private Set<MucListener<MucJoin>> joinListeners = Collections.newSetFromMap(new ConcurrentHashMap<MucListener<MucJoin>, Boolean>());
	
	private KixmppClient client = null;
	
	/**
	 * @param listener the listener to add
	 */
	public void addMessageListener(MucListener<MucMessage> listener) {
		messageListeners.add(listener);
	}

	/**
	 * @param listener the listener to add
	 */
	public void removeMessageListener(MucListener<MucMessage> listener) {
		messageListeners.remove(listener);
	}

	/**
	 * @param listener the listener to add
	 */
	public void addInviteListener(MucListener<MucInvite> listener) {
		invitationListeners.add(listener);
	}

	/**
	 * @param listener the listener to add
	 */
	public void removeInviteListener(MucListener<MucInvite> listener) {
		invitationListeners.remove(listener);
	}
	
	/**
	 * @param listener the listener to add
	 */
	public void addJoinListener(MucListener<MucJoin> listener) {
		joinListeners.add(listener);
	}
	
	/**
	 * @param listener the listener to add
	 */
	public void removeJoinListener(MucListener<MucJoin> listener) {
		joinListeners.remove(listener);
	}
	
	/**
	 * Joins a room.
	 * 
	 * @param roomJid
	 * @param nickname
	 */
	public void joinRoom(String roomJid, String nickname) {
		Element presence = new Element("presence");
		presence.setAttribute("from", client.getJid());
		presence.setAttribute("to", roomJid + "/" + nickname);

		Element x = new Element("x", "http://jabber.org/protocol/muc");
		presence.addContent(x);

		Element history = new Element("history");
		history.setAttribute("maxstanzas", "0");
		x.addContent(history);
		
		client.sendStanza(presence);
	}
	
	/**
	 * Joins a room with an invitation.
	 * 
	 * @param invitation
	 * @param invitation
	 */
	public void joinRoom(MucInvite invitation, String nickname) {
		joinRoom(invitation.getRoomJid(), nickname);
	}
	
	/**
	 * Sends a room message to a room.
	 * 
	 * @param roomJid
	 * @param roomMessage
	 */
	public void sendRoomMessage(String roomJid, String roomMessage) {
		Element message = new Element("message");
		message.setAttribute("to", roomJid);
		message.setAttribute("from", roomJid);
		message.setAttribute("type", "groupchat");
		
		Element bodyElement = new Element("body");
		bodyElement.setText(roomMessage);
		message.addContent(bodyElement);

		client.sendStanza(message);
	}
	
	/**
	 * @see com.kixeye.kixmpp.client.module.KixmppModule#install(com.kixeye.kixmpp.client.KixmppClient)
	 */
	public void install(KixmppClient client) {
		this.client = client;
		
		client.getHandlerRegistry().register("message", "jabber:client", mucMessageHandler);
		client.getHandlerRegistry().register("presence", "jabber:client", mucPresenceHandler);
	}

	/**
	 * @see com.kixeye.kixmpp.client.module.KixmppModule#uninstall(com.kixeye.kixmpp.client.KixmppClient)
	 */
	public void uninstall(KixmppClient client) {
		client.getHandlerRegistry().unregister("message", "jabber:client", mucMessageHandler);
		client.getHandlerRegistry().unregister("presence", "jabber:client", mucPresenceHandler);
	}
	
	private KixmppStanzaHandler mucPresenceHandler = new KixmppStanzaHandler() {
		public void handle(Element stanza) {
			Element inX  = stanza.getChild("x", Namespace.getNamespace("http://jabber.org/protocol/muc#user"));
			
			if (inX != null) {
				Element inItem = inX.getChild("item", Namespace.getNamespace("http://jabber.org/protocol/muc#user"));
				
				if (inItem != null) {
					String from = stanza.getAttributeValue("from");
					
					MucJoin message = new MucJoin(from.substring(0, from.indexOf("/")), 
							inItem.getAttributeValue("jid"), 
							inItem.getAttributeValue("affiliation"), 
							inItem.getAttributeValue("role"));
					
					for (MucListener<MucJoin> listener : joinListeners) {
						try {
							listener.handle(message);
						} catch (Exception e) {
							logger.error("Exception thrown while executing MucJoin listener", e);
						}
					}
				}
			}
		}
	};
	
	private KixmppStanzaHandler mucMessageHandler = new KixmppStanzaHandler() {
		public void handle(Element stanza) {
			String type = stanza.getAttributeValue("type");
			
			if (type == null) {
				type = "";
			}
			
			switch (type) {
				case "chat":
					// ignore
					break;
				case "groupchat":
					String language = null;
					String bodyMessage = null;
					
					Element body = stanza.getChild("body", Namespace.getNamespace("jabber:client"));
					
					if (body != null) {
						bodyMessage = body.getText();
						language = body.getAttributeValue("xml:lang");

						MucMessage message = new MucMessage(stanza.getAttributeValue("from"), stanza.getAttributeValue("to"), language, bodyMessage);
						
						for (MucListener<MucMessage> listener : messageListeners) {
							try {
								listener.handle(message);
							} catch (Exception e) {
								logger.error("Exception thrown while executing MucInvite listener", e);
							}
						}
					}
					
					break;
				case "":
					// check if invite
					for (Element invitation : stanza.getChildren("x", Namespace.getNamespace("jabber:x:conference"))) {
						String roomJid = invitation.getAttributeValue("jid");
						
						MucInvite invite = new MucInvite(stanza.getAttributeValue("from"), stanza.getAttributeValue("to"), roomJid);
						
						for (MucListener<MucInvite> listener : invitationListeners) {
							try {
								listener.handle(invite);
							} catch (Exception e) {
								logger.error("Exception thrown while executing MucInvite listener", e);
							}
						}
					}
					break;
			}
		}
	};
}
