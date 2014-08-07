package com.kixeye.kixmpp.server.module.muc;

import com.kixeye.kixmpp.KixmppJid;
import com.kixeye.kixmpp.server.KixmppServer;
import com.kixeye.kixmpp.server.module.bind.BindKixmppServerModule;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.Attribute;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * tests for {@link MucRoom}
 *
 * @author dturner@kixeye.com
 */
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
