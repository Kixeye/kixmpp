package com.kixeye.kixmpp.server;

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


import io.netty.handler.ssl.SslContext;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.junit.Assert;
import org.junit.Test;

import com.kixeye.kixmpp.KixmppJid;
import com.kixeye.kixmpp.client.KixmppClient;
import com.kixeye.kixmpp.client.module.muc.MucJoin;
import com.kixeye.kixmpp.client.module.muc.MucKixmppClientModule;
import com.kixeye.kixmpp.client.module.muc.MucListener;
import com.kixeye.kixmpp.client.module.muc.MucMessage;
import com.kixeye.kixmpp.client.module.presence.Presence;
import com.kixeye.kixmpp.client.module.presence.PresenceKixmppClientModule;
import com.kixeye.kixmpp.client.module.presence.PresenceListener;
import com.kixeye.kixmpp.server.module.auth.InMemoryAuthenticationService;
import com.kixeye.kixmpp.server.module.auth.SaslKixmppServerModule;
import com.kixeye.kixmpp.server.module.muc.MucKixmppServerModule;

/**
 * Tests the {@link KixmppServer}
 * 
 * @author ebahtijaragic
 */
public class KixmppServerTest {
	@Test
	public void testSimpleUsingKixmpp() throws Exception {
		try (KixmppServer server = new KixmppServer("testChat")) {
			Assert.assertNotNull(server.start().get(2, TimeUnit.SECONDS));
			
			((InMemoryAuthenticationService)server.module(SaslKixmppServerModule.class).getAuthenticationService()).addUser("testUser", "testPassword");
			server.module(MucKixmppServerModule.class).addService("conference").addRoom("someRoom");
			
			try (KixmppClient client = new KixmppClient(SslContext.newClientContext())) {
				final LinkedBlockingQueue<Presence> presences = new LinkedBlockingQueue<>();
				final LinkedBlockingQueue<MucJoin> mucJoins = new LinkedBlockingQueue<>();
				final LinkedBlockingQueue<MucMessage> mucMessages = new LinkedBlockingQueue<>();

				Assert.assertNotNull(client.connect("localhost", server.getBindAddress().getPort(), server.getDomain()).get(2, TimeUnit.SECONDS));

				client.module(PresenceKixmppClientModule.class).addPresenceListener(new PresenceListener() {
					public void handle(Presence presence) {
						presences.offer(presence);
					}
				});
				
				client.module(MucKixmppClientModule.class).addJoinListener(new MucListener<MucJoin>() {
					public void handle(MucJoin event) {
						mucJoins.offer(event);
					}
				});
				
				client.module(MucKixmppClientModule.class).addMessageListener(new MucListener<MucMessage>() {
					public void handle(MucMessage event) {
						mucMessages.offer(event);
					}
				});
				
				Assert.assertNotNull(client.login("testUser", "testPassword", "testResource").get(2, TimeUnit.SECONDS));
				client.module(PresenceKixmppClientModule.class).updatePresence(new Presence());
				
				Assert.assertNotNull(presences.poll(2, TimeUnit.SECONDS));
				
				client.module(MucKixmppClientModule.class).joinRoom(KixmppJid.fromRawJid("someRoom@conference.testChat"), "testNick");
				
				MucJoin mucJoin = mucJoins.poll(2, TimeUnit.SECONDS);
				
				Assert.assertNotNull(mucJoin);
				
				client.module(MucKixmppClientModule.class).sendRoomMessage(mucJoin.getRoomJid(), "someMessage", "testNick");

				MucMessage mucMessage = mucMessages.poll(2, TimeUnit.SECONDS);

				Assert.assertNotNull(mucMessage);
				Assert.assertEquals("someMessage", mucMessage.getBody());
			}
		}
	}
	
	@Test
	public void testSimpleUsingSmack() throws Exception {
		try (KixmppServer server = new KixmppServer("testChat")) {
			Assert.assertNotNull(server.start().get(2, TimeUnit.SECONDS));
			
			((InMemoryAuthenticationService)server.module(SaslKixmppServerModule.class).getAuthenticationService()).addUser("testUser", "testPassword");
			server.module(MucKixmppServerModule.class).addService("conference").addRoom("someRoom");
			
			XMPPConnection connection = new XMPPTCPConnection(new ConnectionConfiguration("localhost", server.getBindAddress().getPort(), server.getDomain()));
				
			try {
				connection.connect();
				
				connection.login("testUser", "testPassword");
				
				final LinkedBlockingQueue<Message> messages = new LinkedBlockingQueue<>();
				
				PacketListener messageListener = new PacketListener() {
					public void processPacket(Packet packet) throws NotConnectedException {
						messages.offer((Message)packet);
					}
				};
				
				MultiUserChat chat = new MultiUserChat(connection, "someRoom@conference.testChat");
				chat.addMessageListener(messageListener);
				chat.join("testNick");
				
				chat.sendMessage("hello!");
				
				Message message = messages.poll(2, TimeUnit.SECONDS);
				
				Assert.assertNotNull(message);

				if (null == message.getBody() || "".equals(message.getBody().trim())) {
					message = messages.poll(2, TimeUnit.SECONDS);
	
					Assert.assertNotNull(message);
					
					Assert.assertEquals("hello!", message.getBody());
				}
			} finally {
				connection.disconnect();
			}
		}
	}
}
