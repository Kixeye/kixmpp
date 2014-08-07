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
import io.netty.channel.ChannelFuture;
import io.netty.util.Attribute;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.kixeye.kixmpp.KixmppJid;
import com.kixeye.kixmpp.server.KixmppServer;
import com.kixeye.kixmpp.server.module.bind.BindKixmppServerModule;

/**
 * tests for {@link MucRoom}
 *
 * @author dturner@kixeye.com
 */
@SuppressWarnings("unchecked")
public class MucRoomTest {
	@Test
    public void joinRoom_firstTime_noMemberOnly() {
        KixmppServer server = Mockito.mock(KixmppServer.class);
        KixmppJid roomJid = new KixmppJid("testnode", "testdomain");
        MucRoom mucRoom = new MucRoom(server, roomJid, new MucRoomSettings().membersOnly(false));

        Channel channel = Mockito.mock(Channel.class);
        Attribute<KixmppJid> jidAttribute = Mockito.mock(Attribute.class);
        Mockito.when(jidAttribute.get()).thenReturn(new KixmppJid("test.user", "testdomain", "testresource"));
        Mockito.when(channel.attr(BindKixmppServerModule.JID)).thenReturn(jidAttribute);
        Mockito.when(channel.closeFuture()).thenReturn(Mockito.mock(ChannelFuture.class));

        Assert.assertEquals(0, mucRoom.getClients().size());

        mucRoom.join(channel, "nickname");

        Assert.assertEquals(1, mucRoom.getClients().size());
    }

    @Test
    public void joinRoom_multipleConnectionsSameUser_noMemberOnly() {
        KixmppServer server = Mockito.mock(KixmppServer.class);
        KixmppJid roomJid = new KixmppJid("testnode", "testdomain");
        MucRoom mucRoom = new MucRoom(server, roomJid, new MucRoomSettings().membersOnly(false));

        Channel channel = Mockito.mock(Channel.class);
        Attribute<KixmppJid> jidAttribute = Mockito.mock(Attribute.class);
        Mockito.when(jidAttribute.get()).thenReturn(new KixmppJid("test.user", "testdomain", "testresource"));
        Mockito.when(channel.attr(BindKixmppServerModule.JID)).thenReturn(jidAttribute);
        Mockito.when(channel.closeFuture()).thenReturn(Mockito.mock(ChannelFuture.class));

        Assert.assertEquals(0, mucRoom.getClients().size());

        mucRoom.join(channel, "nickname");

        Assert.assertEquals(1, mucRoom.getClients().size());

        Channel channel2 = Mockito.mock(Channel.class);
        Attribute<KixmppJid> jidAttribute2 = Mockito.mock(Attribute.class);
        Mockito.when(jidAttribute2.get()).thenReturn(new KixmppJid("test.user", "testdomain", "testresource2"));
        Mockito.when(channel2.attr(BindKixmppServerModule.JID)).thenReturn(jidAttribute2);
        Mockito.when(channel2.closeFuture()).thenReturn(Mockito.mock(ChannelFuture.class));

        mucRoom.join(channel2, "nickname");

        Assert.assertEquals(2, mucRoom.getClients().size());
    }

    @Test
    public void joinRoom_conflictingNickname_noMemberOnly() {
        KixmppServer server = Mockito.mock(KixmppServer.class);
        KixmppJid roomJid = new KixmppJid("testnode", "testdomain");
        MucRoom mucRoom = new MucRoom(server, roomJid, new MucRoomSettings().membersOnly(false));

        Channel channel = Mockito.mock(Channel.class);
        Attribute<KixmppJid> jidAttribute = Mockito.mock(Attribute.class);
        Mockito.when(jidAttribute.get()).thenReturn(new KixmppJid("test.user1", "testdomain", "testresource"));
        Mockito.when(channel.attr(BindKixmppServerModule.JID)).thenReturn(jidAttribute);
        Mockito.when(channel.closeFuture()).thenReturn(Mockito.mock(ChannelFuture.class));

        Assert.assertEquals(0, mucRoom.getClients().size());

        mucRoom.join(channel, "nickname");

        Assert.assertEquals(1, mucRoom.getClients().size());

        Channel channel2 = Mockito.mock(Channel.class);
        Attribute<KixmppJid> jidAttribute2 = Mockito.mock(Attribute.class);
        Mockito.when(jidAttribute2.get()).thenReturn(new KixmppJid("test.user2", "testdomain", "testresource"));
        Mockito.when(channel2.attr(BindKixmppServerModule.JID)).thenReturn(jidAttribute2);
        Mockito.when(channel2.closeFuture()).thenReturn(Mockito.mock(ChannelFuture.class));

        try {
            mucRoom.join(channel2, "nickname");
            Assert.fail();
        } catch (NicknameInUseException e) {

        }
    }

    @Test(expected = MembersOnlyException.class)
    public void joinRoom_firstTime_memberOnly_noMemberAdded() {
        KixmppServer server = Mockito.mock(KixmppServer.class);
        KixmppJid roomJid = new KixmppJid("testnode", "testdomain");
        MucRoom mucRoom = new MucRoom(server, roomJid, new MucRoomSettings().membersOnly(true));

        Channel channel = Mockito.mock(Channel.class);
        Attribute<KixmppJid> jidAttribute = Mockito.mock(Attribute.class);
        Mockito.when(jidAttribute.get()).thenReturn(new KixmppJid("test.user", "testdomain", "testresource"));
        Mockito.when(channel.attr(BindKixmppServerModule.JID)).thenReturn(jidAttribute);
        Mockito.when(channel.closeFuture()).thenReturn(Mockito.mock(ChannelFuture.class));

        mucRoom.join(channel, "nickname");
    }

    @Test
    public void joinRoom_firstTime_memberOnly_memberAdded() {
        KixmppServer server = Mockito.mock(KixmppServer.class);
        KixmppJid roomJid = new KixmppJid("testnode", "testdomain");
        MucRoom mucRoom = new MucRoom(server, roomJid, new MucRoomSettings().membersOnly(true));

        Channel channel = Mockito.mock(Channel.class);
        Attribute<KixmppJid> jidAttribute = Mockito.mock(Attribute.class);
        Mockito.when(jidAttribute.get()).thenReturn(new KixmppJid("test.user", "testdomain", "testresource"));
        Mockito.when(channel.attr(BindKixmppServerModule.JID)).thenReturn(jidAttribute);
        Mockito.when(channel.closeFuture()).thenReturn(Mockito.mock(ChannelFuture.class));

        Assert.assertEquals(0, mucRoom.getClients().size());

        mucRoom.addMember(new KixmppJid("test.user", "testdomain", "testresource"), "nickname");
        mucRoom.join(channel, "nickname");

        Assert.assertEquals(1, mucRoom.getClients().size());
    }
}
