package com.kixeye.kixmpp.server.module.muc;

/**
 * Settings for {@link MucRoom}
 *
 * @author dturner@kixeye.com
 */
public class MucRoomSettings {

    private boolean membersOnly = false;
    private String subject = null;

    public MucRoomSettings(MucRoomSettings settings) {
        membersOnly = settings.isMembersOnly();
    }

    public MucRoomSettings() {
    }

    public MucRoomSettings membersOnly(boolean membersOnly){
        this.membersOnly = membersOnly;
        return this;
    }

    public MucRoomSettings subject(String subject){
        this.subject = subject;
        return this;
    }

    public boolean isMembersOnly() {
        return membersOnly;
    }

    public String getSubject() {
        return subject;
    }
}
