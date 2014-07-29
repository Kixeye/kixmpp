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
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jdom2.Element;
import org.jdom2.Namespace;

import com.kixeye.kixmpp.KixmppJid;
import com.kixeye.kixmpp.server.module.bind.BindKixmppServerModule;

/**
 * A simple muc room.
 * 
 * @author ebahtijaragic
 */
public class MucRoom {
	private final KixmppJid roomJid;
	private ConcurrentHashMap<String, Channel> users = new ConcurrentHashMap<>();
	private ConcurrentHashMap<Channel, String> channelToNickname = new ConcurrentHashMap<>();
	
	/**
	 * @param roomJid
	 */
	public MucRoom(KixmppJid roomJid) {
		this.roomJid = roomJid;
	}

	/**
	 * A user requests to join the room.
	 * 
	 * @param channel
	 * @param nickname
	 */
	public void join(Channel channel, String nickname) {
		synchronized (channel) {
			if (users.putIfAbsent(nickname, channel) == null) {
				channelToNickname.put(channel, nickname);
				
				Element presence = new Element("presence");
				presence.setAttribute("id", UUID.randomUUID().toString());
				presence.setAttribute("from", roomJid.withResource(nickname).toString());
				presence.setAttribute("to", channel.attr(BindKixmppServerModule.JID).get().toString());
				
				Element x = new Element("x", Namespace.getNamespace("http://jabber.org/protocol/muc#user"));
				
				x.addContent(new Element("item", Namespace.getNamespace("http://jabber.org/protocol/muc#user"))
						.setAttribute("affiliation", "member")
						.setAttribute("role", "participant"));
				
				presence.addContent(x);
				
				channel.writeAndFlush(presence);

				Element message = new Element("message");
				message.setAttribute("id", UUID.randomUUID().toString());
				message.setAttribute("from", roomJid.withResource(nickname).toString());
				message.setAttribute("to", channel.attr(BindKixmppServerModule.JID).get().toString());
				message.setAttribute("type", "groupchat");
				
				message.addContent(new Element("subject"));
				
				channel.writeAndFlush(message);
				
				channel.closeFuture().addListener(new CloseChannelListener(channel));
			} // TODO handle else
		}
	}
	
	/**
	 * A user leaves the room.
	 * 
	 * @param channel
	 */
	public void leave(Channel channel) {
		synchronized (channel) {
			String nickname = channelToNickname.remove(channel);
			
			if (nickname != null) {
				users.remove(nickname);
			}
		}
	}
	
	/**
	 * Broadcasts a message.
	 * 
	 * @param element
	 */
	public void broadcast(Channel channel, Element stanza) {
		if (channelToNickname.containsKey(channel)) {
			Element body = new Element("body");
			body.setText(stanza.getChildText("body", Namespace.getNamespace("jabber:client")));
			
			for (Channel userChannel : channelToNickname.keySet()) {
				Element message = new Element("message");
				message.setAttribute("id", UUID.randomUUID().toString());
				message.setAttribute("from", roomJid.withResource(channelToNickname.get(channel)).toString());
				message.setAttribute("to", userChannel.attr(BindKixmppServerModule.JID).get().toString());
				message.setAttribute("type", "groupchat");
				message.addContent(body.clone());
				
				userChannel.writeAndFlush(message);
			}
		}
	}
	
	/**
	 * Sends an invitation for a user.
	 */
	public void sendInvite(KixmppJid from, Channel userChannelToInvite, String reason) {
		Element message = new Element("message");
		message.setAttribute("to", userChannelToInvite.attr(BindKixmppServerModule.JID).get().getFullJid());
		if (from != null) {
			message.setAttribute("from", from.getFullJid());
		}
		
		Element x = new Element("x", Namespace.getNamespace("jabber:x:conference"));
		x.setAttribute("jid", roomJid.getFullJid());
		if (reason != null) {
			x.setAttribute("reason", reason);
		}
		
		message.addContent(x);
		
		userChannelToInvite.writeAndFlush(message);
	}
	
	private class CloseChannelListener implements GenericFutureListener<Future<? super Void>> {
		private final Channel channel;
		
		/**
		 * @param channel
		 */
		public CloseChannelListener(Channel channel) {
			this.channel = channel;
		}

		public void operationComplete(Future<? super Void> future) throws Exception {
			leave(channel);
		}
	}
}
