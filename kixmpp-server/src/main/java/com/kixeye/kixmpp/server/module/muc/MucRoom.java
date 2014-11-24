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

import org.jdom2.Element;
import org.jdom2.Namespace;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.kixeye.kixmpp.KixmppJid;
import com.kixeye.kixmpp.date.XmppDateUtils;
import com.kixeye.kixmpp.server.cluster.message.RoomBroadcastTask;
import com.kixeye.kixmpp.server.module.bind.BindKixmppServerModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple muc room.
 *
 * @author ebahtijaragic
 */
public class MucRoom {
    private final MucService service;
    private final KixmppJid roomJid;
    private final MucKixmppServerModule mucModule;
    private final String roomId;
    private final MucRoomSettings settings;

    private Map<KixmppJid, MucRole> jidRoles = new HashMap<>();
    private Map<KixmppJid, MucAffiliation> jidAffiliations = new HashMap<>();
    
    private Map<KixmppJid, String> nicknamesByBareJid = new HashMap<>();
    private Map<String, User> usersByNickname = new HashMap<>();
    
    /**
     * @param service
     * @param roomJid
     * @param settings
     */
    public MucRoom(MucService service, KixmppJid roomJid, MucRoomSettings settings) {
        this.service = service;
        this.roomJid = roomJid;
        this.roomId = roomJid.getNode();
        this.mucModule = service.getServer().module(MucKixmppServerModule.class);
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

    /**
     * Adds a user.
     * 
     * @param jid
     * @param nickname
     * @param role
     * @param affiliation
     */
    public void addUser(KixmppJid jid, String nickname, MucRole role, MucAffiliation affiliation) {
        jid = jid.withoutResource();
        checkForNicknameInUse(nickname, jid);

        nicknamesByBareJid.put(jid, nickname);
        jidRoles.put(jid, role);
        jidAffiliations.put(jid, affiliation);
    }
    
    /**
     * A user requets to join the room.
     * @param channel
     * @param nickname
     */
    public void join(Channel channel, String nickname) {
    	join(channel, nickname, null);
    }

    /**
     * A user requests to join the room.
     *
     * @param channel
     * @param nickname
     * @param mucStanza
     */
    public void join(Channel channel, String nickname, Element mucStanza) {
        KixmppJid jid = channel.attr(BindKixmppServerModule.JID).get();

        if (settings.isOpen() && !jidRoles.containsKey(jid.withoutResource())) {
            addUser(jid, nickname, MucRole.Participant, MucAffiliation.Member);
        }
        
        verifyMembership(jid.withoutResource());
        checkForNicknameInUse(nickname, jid);

        User user = usersByNickname.get(nickname);

        boolean existingUser = true;
        if (user == null) {
            user = new User(nickname, jid.withoutResource());
            usersByNickname.put(nickname, user);
            existingUser = false;
        }

        Client client = user.addClient(new Client(jid, nickname, channel));

        // xep-0045 7.2.3 begin
        // self presence
        channel.writeAndFlush(createPresence(roomJid.withResource(nickname), jid, MucRole.Participant, null));

        if (settings.isPresenceEnabled() && !existingUser) {
            // Send presence from existing occupants to new occupant
            sendExistingOccupantsPresenceToNewOccupant(user, channel);

            // Send new occupant's presence to all occupants
            broadcastPresence(user, MucRole.Participant, null);
        }
        // xep-0045 7.2.3 end

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

							channel.write(message);
						}
						channel.flush();
					}
				}
        	}
        }

        channel.closeFuture().addListener(new CloseChannelListener(client));
    }

	private void sendExistingOccupantsPresenceToNewOccupant(User newUser, Channel channel) {
		KixmppJid jid = channel.attr(BindKixmppServerModule.JID).get();

		for (User user : usersByNickname.values()) {


			if (newUser.getBareJid().equals(user.getBareJid())) {
				continue;
			}

			MucRole role = jidRoles.get(jid.withoutResource());
			Element presence = createPresence(roomJid.withResource(user.getNickname()), jid, role, null);
			channel.write(presence);
		}
		if (!usersByNickname.isEmpty()) {
			channel.flush();
		}
	}

	private void broadcastPresence(User fromUser, MucRole role, String type) {
		for (User user : usersByNickname.values()) {
			if (user.getBareJid().equals(fromUser.getBareJid())) {
				continue;
			}
			user.receivePresence(fromUser, role, type);
		}
	}

    private void checkForNicknameInUse(String nickname, KixmppJid jid) {
        User user = usersByNickname.get(nickname);
        if (user != null && !user.getBareJid().equals(jid.withoutResource())) {
            throw new NicknameInUseException(this, nickname);
        }
    }

    private void verifyMembership(KixmppJid jid) {
    	MucAffiliation affiliation = jidAffiliations.get(jid);
    	
    	if (affiliation == null) {
            throw new RoomJoinNotAllowedException(this, jid);
    	}
    	
    	MucRole role = jidRoles.get(jid);
    	
    	if (role == null) {
    		switch (affiliation) {
				case Owner:
				case Admin:
					role = MucRole.Moderator;
					break;
				case Member:
					role = MucRole.Participant;
					break;
				case None:
					role = MucRole.Visitor;
					break;
				case Outcast:
		            throw new RoomJoinNotAllowedException(this, jid);
				default:
					role = MucRole.None;
					break;
    		}
    	}
    	
    	jidRoles.put(jid, role);
    	jidAffiliations.put(jid, affiliation);
    }

    /**
     * Remove the {@link User} associated with the given {@link KixmppJid} from the
     * room.  This will remove all {@link Client}s associated with the {@link User}.
     *
     * @param address
     * @return
     */
    public boolean removeUser(KixmppJid address) {
	    KixmppJid bareJid = address.withoutResource();
        String nickname = nicknamesByBareJid.get(bareJid);
        if (nickname == null) {
            //user is not in the room
            return false;
        }
        User user = usersByNickname.get(nickname);
        if (user == null) {
            //user is no longer connected to the room
            return false;
        }
        user.removeClients();
        removeDisconnectedUser(user);
        return true;
    }

	public boolean userLeft(Channel channel, String nickname) {
		User user = usersByNickname.get(nickname);
		if (user == null) {
			//user not found
			return false;
		}
		Client client = user.getClient(channel);
		if (client == null) {
			//client not found
			return false;
		}
		user.removeClient(client);
		removeDisconnectedUser(user);

		return true;
	}

    /**
     * A user leaves the room.
     *
     * @param client
     */
    private void leave(Client client) {
        User user = usersByNickname.get(client.getNickname());
        
        if (user != null) {
	        user.removeClient(client);
	        removeDisconnectedUser(user);
        }
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

	private Element createPresence(KixmppJid from, KixmppJid to, MucRole role, String type) {
		Element presence = new Element("presence");

		presence.setAttribute("id", UUID.randomUUID().toString());
		presence.setAttribute("from", from.toString());
		presence.setAttribute("to", to.toString());

		Element x = new Element("x", Namespace.getNamespace("http://jabber.org/protocol/muc#user"));

		if (type != null) {
			presence.setAttribute("type", type);
		}

		Element item = new Element("item", Namespace.getNamespace("http://jabber.org/protocol/muc#user"));
		item.setAttribute("affiliation", "member");

		switch (role) {
			case Participant:
				item.setAttribute("role", "participant");
				break;
			case Moderator:
				item.setAttribute("role", "moderator");
				break;
			case Visitor:
				item.setAttribute("role", "visitor");
				break;
		}
		x.addContent(item);
		presence.addContent(x);

		return presence;
	}

    /**
     * Broadcasts a message using supplied nickname.
     *
     * @param fromAddress
     * @param messages
     */
    public void receiveMessages(KixmppJid fromAddress, boolean sendToCluster, String... messages) {
        if (fromAddress == null) {
            return;
        }
        
        MucRole fromRole = jidRoles.get(fromAddress.withoutResource());
        
        if (fromRole == null) {
        	removeUser(fromAddress);
        	return;
        }
        
        switch (fromRole) {
			case None:
			case Visitor:
				// TODO maybe send back and error?
				return;
			default:
				break;
        }
        
        String fromNickname = nicknamesByBareJid.get(fromAddress.withoutResource());
        //TODO validate fromAddress is roomJid or is a member of the room
        KixmppJid fromRoomJid = roomJid.withResource(fromNickname);
        
        mucModule.publishMessage(roomJid, fromRoomJid, fromNickname, messages);

        for (User to : usersByNickname.values()) {
            MucRole toRole = jidRoles.get(to.bareJid.withoutResource());
            
        	switch (toRole) {
	        	case Participant:
	        	case Moderator:
	            	to.receiveMessages(fromRoomJid, messages);
	            	break;
	            default:
	            	// TODO maybe send error?
	            	break;
        	}
        }
        
        if (sendToCluster) {
            service.getServer().getCluster().sendMessageToAll(new RoomBroadcastTask(this, service.getSubDomain(), roomId, fromRoomJid, fromNickname, messages), false);
        }
    }

    public void receive(KixmppJid fromAddress, String nickname, String... messages) {
        KixmppJid fromRoomJid = roomJid.withoutResource().withResource(nickname);
        
        for (User to : usersByNickname.values()) {
            to.receiveMessages(fromRoomJid, messages);
        }
    }

    /**
     * Sends an direct invitation to a user.  Note: The smack client does not recognize this as a valid room invite.
     */
    public void sendDirectInvite(KixmppJid from, Channel userChannelToInvite, String reason) {
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

    /**
     * Sends a mediated invitation to a user.
     */
    public void sendMediatedInvite(KixmppJid from, Channel userChannelToInvite, String reason) {

        Element invite = new Element("invite");
        invite.setAttribute("from", from.getFullJid());
        if (reason != null) {
            Element el = new Element("reason");
            el.setText(reason);
            invite.addContent(el);
        }

        Element x = new Element("x", Namespace.getNamespace("http://jabber.org/protocol/muc#user"));
        x.addContent(invite);

        Element message = new Element("message");
        message.setAttribute("to", userChannelToInvite.attr(BindKixmppServerModule.JID).get().getFullJid());
        message.setAttribute("from", roomJid.getFullJid());
        message.addContent(x);

        userChannelToInvite.writeAndFlush(message);
    }

    public Collection<User> getUsers() {
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

    private void removeDisconnectedUser(User user) {
        if (user.getClientCount() == 0) {
	        MucRole role = jidRoles.get(user.getBareJid());

	        if (settings.isPresenceEnabled()) {
		        broadcastPresence(user, role, "unavailable");
	        }
	        this.usersByNickname.remove(user.getNickname());
	        this.jidAffiliations.remove(user.getBareJid());
	        this.jidRoles.remove(user.getBareJid());
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

	    public void receivePresence(User fromUser, MucRole role, String type) {
		    for (Client client : clientsByAddress.values()) {
			    Element presence = createPresence(roomJid.withResource(fromUser.getNickname()), client.getAddress(), role, type);
			    client.getChannel().writeAndFlush(presence);
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

        public void removeClients() {
            clientsByAddress.clear();
            clientsByChannel.clear();
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
