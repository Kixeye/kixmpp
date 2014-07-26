package com.kixeye.kixmpp.server.module.muc;

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
				message.addContent(body);
				
				userChannel.writeAndFlush(message);
			}
		}
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
