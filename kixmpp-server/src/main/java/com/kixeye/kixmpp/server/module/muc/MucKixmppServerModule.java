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

import io.netty.channel.Channel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.fusesource.hawtdispatch.Task;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kixeye.kixmpp.KixmppJid;
import com.kixeye.kixmpp.handler.KixmppStanzaHandler;
import com.kixeye.kixmpp.server.KixmppServer;
import com.kixeye.kixmpp.server.module.KixmppServerModule;
import com.kixeye.kixmpp.server.module.bind.BindKixmppServerModule;

/**
 * Handles presence.
 * 
 * @author ebahtijaragic
 */
public class MucKixmppServerModule implements KixmppServerModule {
	private static final Logger logger = LoggerFactory.getLogger(MucKixmppServerModule.class);
	
	public static final MucHistoryProvider NOOP_HISTORY_PROVIDER = new MucHistoryProvider() {
		private final List<MucHistory> emptyList = Collections.unmodifiableList(new ArrayList<MucHistory>(0));
		
		@Override
		public List<MucHistory> getHistory(KixmppJid roomJid, Integer maxChars, Integer maxStanzas, Integer seconds, String since) {
			return emptyList;
		}
	};

	private Set<MucRoomMessageListener> messageListeners = Collections.newSetFromMap(new ConcurrentHashMap<MucRoomMessageListener, Boolean>());

	private KixmppServer server;
	
	private ConcurrentHashMap<String, MucService> services = new ConcurrentHashMap<>();
	
	private MucHistoryProvider historyProvider = NOOP_HISTORY_PROVIDER;
	
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
	 * @param listener the listener to add
	 */
	public void addRoomMessageListener(MucRoomMessageListener listener) {
		messageListeners.add(listener);
	}

	/**
	 * @param listener the listener to remove
	 */
	public void removeRoomMessageListener(MucRoomMessageListener listener) {
		messageListeners.remove(listener);
	}
	
	/**
	 * Publish a message for the listeners to pick up.
	 * 
	 * @param room
	 * @param sender
	 * @param messages
	 */
	protected void publishMessage(MucRoom room, KixmppJid sender, String senderNickname, String... messages) {
		for (MucRoomMessageListener listener : messageListeners) {
			try {
				listener.handle(room, sender, senderNickname, messages);
			} catch (Exception e) {
				logger.error("Error while invoking listener: [{}].", listener, e);
			}
		}
	}
	
	/**
	 * Adds a {@link InMemoryMucService}
	 * 
	 * @param name
	 * @param service
	 * @return
	 */
	public MucService addService(String name) {
		return addService(name.toLowerCase(), new InMemoryMucService(server, name));
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
	
	/**
	 * @return the historyProvider
	 */
	public MucHistoryProvider getHistoryProvider() {
		return historyProvider;
	}

	/**
	 * @param historyProvider the historyProvider to set
	 */
	public void setHistoryProvider(MucHistoryProvider historyProvider) {
		this.historyProvider = historyProvider;
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
                        server.getEventEngine().publishTask(room.getRoomJid(), 
                        		new JoinRoomTask(channel, room, fullRoomJid.getResource(), x));
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
                        
                        server.getEventEngine().publishTask(room.getRoomJid(), 
                        		new ReceiveMessageTask(channel.attr(BindKixmppServerModule.JID).get(), room, body.getText()));
					} // TODO handle else
				} // TODO handle else
			}
		}
	};
	
	private static class JoinRoomTask extends Task {
		private final Channel channel;
		private final MucRoom room;
		private final String nickname;
		private final Element x;

		public JoinRoomTask(Channel channel, MucRoom room, String nickname, Element x) {
			this.channel = channel;
			this.room = room;
			this.nickname = nickname;
			this.x = x;
		}

		public void run() {
			room.join(channel, nickname, x);
		}
	}
	
	private static class ReceiveMessageTask extends Task {
		private final KixmppJid sender;
		private final MucRoom room;
		private final String body;
		
		public ReceiveMessageTask(KixmppJid sender, MucRoom room, String body) {
			this.sender = sender;
			this.room = room;
			this.body = body;
		}

		public void run() {
            room.receiveMessages(sender, true, body);
		}
	}
}
