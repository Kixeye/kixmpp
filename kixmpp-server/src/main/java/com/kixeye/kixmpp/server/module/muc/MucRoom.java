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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.jdom2.Element;
import org.jdom2.Namespace;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.kixeye.kixmpp.KixmppJid;
import com.kixeye.kixmpp.date.XmppDateUtils;
import com.kixeye.kixmpp.server.KixmppServer;
import com.kixeye.kixmpp.server.cluster.task.RoomBroadcastTask;
import com.kixeye.kixmpp.server.module.bind.BindKixmppServerModule;

/**
 * A simple muc room.
 *
 * @author ebahtijaragic
 */
public class MucRoom {
    private final KixmppServer server;
    private final KixmppJid roomJid;
    private final MucKixmppServerModule mucModule;
    private final String gameId;
    private final String roomId;
    private final MucRoomSettings settings;

    private HashMap<KixmppJid, String> memberNicknamesByBareJid = new HashMap<>();
    private Map<String, User> usersByNickname = new HashMap<>();
    
    /**
     * @param server
     * @param roomJid
     * @param settings
     */
    public MucRoom(KixmppServer server, KixmppJid roomJid, MucRoomSettings settings) {
        this.server = server;
        this.roomJid = roomJid;
        this.gameId = roomJid.getDomain().split(Pattern.quote("."))[0];
        this.roomId = roomJid.getNode();
        this.mucModule = server.module(MucKixmppServerModule.class);
        this.settings = new MucRoomSettings(settings);
    }

    /**
     * Getter from roomJid
     *
     * @return
     */
    public KixmppJid getRoomJid() {
        return roomJid;
    }

    public void addMember(KixmppJid jid, String nickname) {
        jid = jid.withoutResource();
        checkForNicknameInUse(nickname, jid);
        memberNicknamesByBareJid.put(jid, nickname);
    }
    
    /**
     * A user requets to join the room.
     * @param channel
     * @param nickname
     * @param mucStanza
     */
    public void join(Channel channel, String nickname) {
    	join(channel, nickname, null);
    }

    /**
     * A user requests to join the room.
     *
     * @param channel
     * @param nickname
     */
    public void join(Channel channel, String nickname, Element mucStanza) {
        KixmppJid jid = channel.attr(BindKixmppServerModule.JID).get();

        checkForMemberOnly(jid);
        checkForNicknameInUse(nickname, jid);
        memberNicknamesByBareJid.put(jid.withoutResource(),nickname);

        User user = usersByNickname.get(nickname);
        if (user == null) {
            user = new User(nickname, jid.withoutResource());
        }
        Client client = user.addClient(new Client(jid, nickname, channel));

        usersByNickname.put(nickname, user);

        Element presence = new Element("presence");
        presence.setAttribute("id", UUID.randomUUID().toString());
        presence.setAttribute("from", roomJid.withResource(nickname).toString());
        presence.setAttribute("to", jid.toString());

        Element x = new Element("x", Namespace.getNamespace("http://jabber.org/protocol/muc#user"));

        x.addContent(new Element("item", Namespace.getNamespace("http://jabber.org/protocol/muc#user"))
                .setAttribute("affiliation", "member")
                .setAttribute("role", "participant"));

        presence.addContent(x);

        channel.writeAndFlush(presence);

        if (settings.getSubject() != null) {
            Element message = new Element("message");
            message.setAttribute("id", UUID.randomUUID().toString());
            message.setAttribute("from", roomJid.withResource(nickname).toString());
            message.setAttribute("to", channel.attr(BindKixmppServerModule.JID).get().toString());
            message.setAttribute("type", "groupchat");

            message.addContent(new Element("subject").setText(settings.getSubject()));

            channel.writeAndFlush(message);
        }
        
        
        if (mucStanza != null) {
        	Element history = mucStanza.getChild("history", mucStanza.getNamespace());
        	
        	if (history != null) {
		        MucHistoryProvider historyProvider = mucModule.getHistoryProvider();
		        
		        if (historyProvider != null) {
		        	Integer maxChars = null;
		        	Integer maxStanzas = null;
		        	Integer seconds = null;
		        	
		        	String parsableString = history.getAttributeValue("maxchars");
		        	if (parsableString != null) {
		        		try {
		        			maxChars = Integer.parseInt(parsableString);
		        		} catch (Exception e) {}
		        	}
		        	parsableString = history.getAttributeValue("maxstanzas");
		        	if (parsableString != null) {
		        		try {
		        			maxStanzas = Integer.parseInt(parsableString);
		        		} catch (Exception e) {}
		        	}
		        	parsableString = history.getAttributeValue("seconds");
		        	if (parsableString != null) {
		        		try {
		        			seconds = Integer.parseInt(parsableString);
		        		} catch (Exception e) {}
		        	}
		        	
		        	String since = history.getAttributeValue("since");
		        	
		        	List<MucHistory> historyItems = historyProvider.getHistory(roomJid, maxChars, maxStanzas, seconds, since);
		        	
		        	if (historyItems != null) {
		        		for (MucHistory historyItem : historyItems) {
		        			Element message = new Element("message")
		        				.setAttribute("id", UUID.randomUUID().toString())
		        				.setAttribute("from", roomJid.withResource(historyItem.getNickname()).toString())
		        				.setAttribute("to", channel.attr(BindKixmppServerModule.JID).get().toString())
		        				.setAttribute("type", "groupchat");
		        			message.addContent(new Element("body").setText(historyItem.getBody()));
		        			
		        			Element addresses = new Element("addresses", Namespace.getNamespace("http://jabber.org/protocol/address"));
		        			addresses.addContent(new Element("address", addresses.getNamespace()).setAttribute("type", "ofrom").setAttribute("jid", historyItem.getFrom().toString()));
		        			message.addContent(addresses);
		        			
		        			message.addContent(new Element("delay", Namespace.getNamespace("urn:xmpp:delay"))
		        					.setAttribute("from", roomJid.toString())
		        					.setAttribute("stamp", XmppDateUtils.format(historyItem.getTimestamp())));
		        			
		        			channel.writeAndFlush(message);
		        		}
		        	}
		        }
        	}
        }

        channel.closeFuture().addListener(new CloseChannelListener(client));
    }

    private void checkForNicknameInUse(String nickname, KixmppJid jid) {
        User user = usersByNickname.get(nickname);
        if (user != null && !user.getBareJid().equals(jid.withoutResource())) {
            throw new NicknameInUseException(this, nickname);
        }
    }

    private void checkForMemberOnly(KixmppJid jid) {
        if (settings.isMembersOnly()) {
            if (memberNicknamesByBareJid.containsKey(jid.withoutResource())) {
                return;
            }
            throw new MembersOnlyException(this, jid);
        }
    }

    /**
     * A user leaves the room.
     *
     * @param client
     */
    private void leave(Client client) {
        User user = usersByNickname.get(client.getNickname());
        user.removeClient(client);
        removeUser(user);
    }

    private Element createMessage(String id, KixmppJid from, KixmppJid to, String type, String bodyText) {
        Element message = new Element("message");

        message.setAttribute("to", to.getFullJid());
        message.setAttribute("from", from.getFullJid());
        message.setAttribute("type", type);
        message.setAttribute("id", id);

        Element body = new Element("body");
        body.addContent(bodyText);

        message.addContent(body);

        return message;
    }


    /**
     * Broadcasts a message.
     *
     * @param messages
     */
    public void receiveMessages(String... messages) {
        receiveMessages(roomJid, messages);
    }

    /**
     * Broadcasts a message using supplied nickname.
     *
     * @param fromAddress
     * @param messages
     */
    public void receiveMessages(KixmppJid fromAddress, String... messages) {
        if (fromAddress == null) {
            return;
        }
        String fromNickname = memberNicknamesByBareJid.get(fromAddress.withoutResource());
        //TODO validate fromAddress is roomJid or is a member of the room
        KixmppJid fromRoomJid = roomJid.withoutResource().withResource(fromNickname);

        for (User to : usersByNickname.values()) {
            to.receiveMessages(fromRoomJid, messages);
        }
        server.getCluster().sendMessageToAll(new RoomBroadcastTask(this, gameId, roomId, fromRoomJid, messages), false);
    }

    public void receive(KixmppJid fromAddress, String...messages){
        for (User to : usersByNickname.values()) {
            to.receiveMessages(fromAddress, messages);
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

    public Collection<User> getUsers(){
        return Lists.newArrayList(usersByNickname.values());
    }

    public Collection<Client> getClients() {
        List<Client> clients = Lists.newArrayList();
        for (User user : usersByNickname.values()) {
            clients.addAll(user.getConnections());
        }
        return clients;
    }

    private class CloseChannelListener implements GenericFutureListener<Future<? super Void>> {
        private final Client client;

        /**
         * @param client
         */
        public CloseChannelListener(Client client) {
            this.client = client;
        }

        public void operationComplete(Future<? super Void> future) throws Exception {
            leave(client);
        }
    }

    private void removeUser(User user) {
        if (user.getClientCount() == 0) {
            this.usersByNickname.remove(user.getNickname());
        }
    }

    /**
     * Represents a single user within a room. A User owns a unique nickname
     * within the room, but may have multiple Connections (1 per full JID)
     */
    public class User {
        private String nickname;
        private KixmppJid bareJid;
        private Map<Channel, Client> clientsByChannel = new HashMap<>();
        private Map<KixmppJid, Client> clientsByAddress = new HashMap<>();

        public User(String nickname, KixmppJid bareJid) {
            this.nickname = nickname;
            this.bareJid = bareJid.withoutResource();
        }

        public Client addClient(Client client) {
            Preconditions.checkNotNull(client.getAddress().getResource());

            clientsByChannel.put(client.getChannel(), client);
            clientsByAddress.put(client.getAddress(), client);

            return client;
        }

        public Client getClient(Channel channel) {
            return clientsByChannel.get(channel);
        }

        public Client getClient(KixmppJid address) {
            return clientsByAddress.get(address);
        }

        public KixmppJid getBareJid() {
            return bareJid;
        }

        public void receiveMessages(KixmppJid fromAddress, String... messages) {
            for (Client client : clientsByAddress.values()) {
                for (String message : messages) {
                    Element stanza = createMessage(UUID.randomUUID().toString(),
                            fromAddress,
                            client.getAddress(),
                            "groupchat",
                            message);
                    client.getChannel().writeAndFlush(stanza);
                }
            }
        }

        public Collection<Client> getConnections() {
            return clientsByAddress.values();
        }

        public void removeClient(Client client) {
            clientsByAddress.remove(client.getAddress());
            clientsByChannel.remove(client.getChannel());
        }

        public String getNickname() {
            return nickname;
        }

        public int getClientCount() {
            return clientsByAddress.size();
        }
    }


    /**
     * Represents single connected occupant in the room.
     */
    public class Client {
        private KixmppJid address;
        private String nickname;
        private Channel channel;

        public Client(KixmppJid address, String nickname, Channel channel) {
            Preconditions.checkNotNull(address.getResource());

            this.address = address;
            this.nickname = nickname;
            this.channel = channel;
        }

        public String getNickname() {
            return nickname;
        }

        public KixmppJid getAddress() {
            return address;
        }

        public Channel getChannel() {
            return channel;
        }
    }
}
