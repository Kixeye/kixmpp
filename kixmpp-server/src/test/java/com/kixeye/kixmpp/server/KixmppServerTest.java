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

import org.junit.Assert;
import org.junit.Test;

import com.kixeye.kixmpp.client.KixmppClient;
import com.kixeye.kixmpp.client.module.muc.MucJoin;
import com.kixeye.kixmpp.client.module.muc.MucKixmppModule;
import com.kixeye.kixmpp.client.module.muc.MucListener;
import com.kixeye.kixmpp.client.module.muc.MucMessage;
import com.kixeye.kixmpp.client.module.presence.Presence;
import com.kixeye.kixmpp.client.module.presence.PresenceKixmppModule;
import com.kixeye.kixmpp.client.module.presence.PresenceListener;
import com.kixeye.kixmpp.server.module.auth.KixmppSaslModule;

/**
 * Tests the {@link KixmppServer}
 * 
 * @author ebahtijaragic
 */
public class KixmppServerTest {
	@Test
	public void testSimple() throws Exception {
		try (KixmppServer server = new KixmppServer("testChat", SslContext.newClientContext())) {
			Assert.assertNotNull(server.start().await(2, TimeUnit.SECONDS));
			
			server.module(KixmppSaslModule.class).addUser("testUser", "testPassword");
			
			try (KixmppClient client = new KixmppClient(SslContext.newClientContext())) {
				final LinkedBlockingQueue<Presence> presences = new LinkedBlockingQueue<>();
				final LinkedBlockingQueue<MucJoin> mucJoins = new LinkedBlockingQueue<>();
				final LinkedBlockingQueue<MucMessage> mucMessages = new LinkedBlockingQueue<>();

				Assert.assertNotNull(client.connect("localhost", server.getBindAddress().getPort(), server.getDomain()).await(2, TimeUnit.SECONDS));

				client.module(PresenceKixmppModule.class).addPresenceListener(new PresenceListener() {
					public void handle(Presence presence) {
						presences.offer(presence);
					}
				});
				
				client.module(MucKixmppModule.class).addJoinListener(new MucListener<MucJoin>() {
					public void handle(MucJoin event) {
						mucJoins.offer(event);
					}
				});
				
				client.module(MucKixmppModule.class).addMessageListener(new MucListener<MucMessage>() {
					public void handle(MucMessage event) {
						mucMessages.offer(event);
					}
				});
				
				Assert.assertNotNull(client.login("testUser", "testPassword", "testResource").await(2, TimeUnit.SECONDS));
				client.module(PresenceKixmppModule.class).updatePresence(new Presence());
				
				Assert.assertNotNull(presences.poll(2, TimeUnit.SECONDS));
				
				client.module(MucKixmppModule.class).joinRoom("someRoom@conference.testChat", "testNick");
				
				MucJoin mucJoin = mucJoins.poll(2, TimeUnit.SECONDS);
				
				Assert.assertNotNull(mucJoin);
				
				client.module(MucKixmppModule.class).sendRoomMessage(mucJoin.getRoomJid(), "someMessage");

				MucMessage mucMessage = mucMessages.poll(2, TimeUnit.SECONDS);

				Assert.assertNotNull(mucMessage);
				Assert.assertEquals("someMessage", mucMessage.getBody());
			}
		}
	}
}
