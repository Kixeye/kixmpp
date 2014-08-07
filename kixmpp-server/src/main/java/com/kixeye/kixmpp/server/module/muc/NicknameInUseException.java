package com.kixeye.kixmpp.server.module.muc;

import com.kixeye.kixmpp.KixmppException;

/**
 * A user attempted to join a MUC room using a nickname that was already in use.
 *
 * @author dturner@kixeye.com
 */
public class NicknameInUseException extends KixmppException {
    public NicknameInUseException(MucRoom mucRoom, String nickname) {
        super("Nickname " + nickname + " is already in use by another member in room " + mucRoom.getRoomJid());
    }
}
