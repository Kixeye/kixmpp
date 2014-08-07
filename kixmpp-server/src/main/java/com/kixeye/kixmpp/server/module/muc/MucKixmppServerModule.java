package com.kixeye.kixmpp.server.module.muc;

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

import com.kixeye.kixmpp.server.module.bind.BindKixmppServerModule;
import io.netty.channel.Channel;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.jdom2.Element;
import org.jdom2.Namespace;

import com.kixeye.kixmpp.KixmppJid;
import com.kixeye.kixmpp.handler.KixmppStanzaHandler;
import com.kixeye.kixmpp.server.KixmppServer;
import com.kixeye.kixmpp.server.module.KixmppServerModule;

/**
 * Handles presence.
 * 
 * @author ebahtijaragic
 */
public class MucKixmppServerModule implements KixmppServerModule {
	private KixmppServer server;
	
	private ConcurrentHashMap<String, MucService> services = new ConcurrentHashMap<>();
	
	/**
	 * @see com.kixeye.kixmpp.server.module.KixmppModule#install(com.kixeye.kixmpp.server.KixmppServer)
	 */
	public void install(KixmppServer server) {
		this.server = server;
		
		this.server.getEventEngine().registerGlobalStanzaHandler("presence", JOIN_ROOM_HANDLER);
		this.server.getEventEngine().registerGlobalStanzaHandler("message", ROOM_MESSAGE_HANDLER);
	}

	/**
	 * @see com.kixeye.kixmpp.server.module.KixmppModule#uninstall(com.kixeye.kixmpp.server.KixmppServer)
	 */
	public void uninstall(KixmppServer server) {
		this.server.getEventEngine().unregisterGlobalStanzaHandler("presence", JOIN_ROOM_HANDLER);
		this.server.getEventEngine().unregisterGlobalStanzaHandler("message", ROOM_MESSAGE_HANDLER);
	}
	
	/**
	 * Adds a {@link InMemoryMucService}
	 * 
	 * @param name
	 * @param service
	 * @return
	 */
	public MucService addService(String name) {
		return addService(name.toLowerCase(), new InMemoryMucService(server, name + "." + server.getDomain()));
	}

	/**
	 * Adds a {@link MucService}.
	 * 
	 * @param name
	 * @param service
	 * @return
	 */
	public MucService addService(String name, MucService service) {
		MucService prevService = services.putIfAbsent(name.toLowerCase(), service);
		
		return prevService == null ? service : prevService;
	}
	
	/**
	 * Gets a {@link MucService}.
	 * 
	 * @param name
	 * @return
	 */
	public MucService getService(String name) {
		return services.get(name);
	}
	
	/**
	 * @see com.kixeye.kixmpp.server.module.KixmppModule#getFeatures(io.netty.channel.Channel)
	 */
	public List<Element> getFeatures(Channel channel) {
		return null;
	}
	
	private KixmppStanzaHandler JOIN_ROOM_HANDLER = new KixmppStanzaHandler() {
		/**
		 * @see com.kixeye.kixmpp.server.KixmppStanzaHandler#handle(io.netty.channel.Channel, org.jdom2.Element)
		 */
		public void handle(Channel channel, Element stanza) {
			Element x = stanza.getChild("x", Namespace.getNamespace("http://jabber.org/protocol/muc"));
			
			if (x != null) {
				KixmppJid fullRoomJid = KixmppJid.fromRawJid(stanza.getAttributeValue("to"));
				
				MucService service = services.get(fullRoomJid.getDomain().toLowerCase().replace("." + server.getDomain(), ""));

				if (service != null) {
					MucRoom room = service.getRoom(fullRoomJid.getNode());
					
					if (room != null) {
						room.join(channel, fullRoomJid.getResource());
					} // TODO handle else
				} // TODO handle else
			}
		}
	};
	
	private KixmppStanzaHandler ROOM_MESSAGE_HANDLER = new KixmppStanzaHandler() {
		/**
		 * @see com.kixeye.kixmpp.server.KixmppStanzaHandler#handle(io.netty.channel.Channel, org.jdom2.Element)
		 */
		public void handle(Channel channel, Element stanza) {
			if ("groupchat".equals(stanza.getAttributeValue("type"))) {
				KixmppJid fullRoomJid = KixmppJid.fromRawJid(stanza.getAttributeValue("to"));

				MucService service = services.get(fullRoomJid.getDomain().toLowerCase().replace("." + server.getDomain(), ""));
				
				if (service != null) {
					MucRoom room = service.getRoom(fullRoomJid.getNode());

					if (room != null) {
                        Element body = stanza.getChild("body", stanza.getNamespace());
                        room.receiveMessages(channel.attr(BindKixmppServerModule.JID).get(), body.getText());
					} // TODO handle else
				} // TODO handle else
			}
		}
	};
}
