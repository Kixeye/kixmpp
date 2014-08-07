package com.kixeye.kixmpp.server.module.muc;

import com.kixeye.kixmpp.KixmppException;
import com.kixeye.kixmpp.KixmppJid;

/**
 * A user attempted to join a members-only MUC room for which they are not a member.
 *
 * @author dturner@kixeye.com
 */
public class MembersOnlyException extends KixmppException {
    public MembersOnlyException(MucRoom mucRoom, KixmppJid jid) {
        super(jid + " cannot join room " + mucRoom.getRoomJid() + " because they are not a member and the room is members only.");
    }
}
