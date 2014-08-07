package com.kixeye.kixmpp.server.module.muc;

/**
 * TODO:replace this with some description
 *
 * @author dturner@kixeye.com
 */
public class MucRoomSettings {

    private boolean membersOnly = false;

    public MucRoomSettings(MucRoomSettings settings) {
        membersOnly = settings.isMembersOnly();
    }

    public MucRoomSettings() {
    }

    public MucRoomSettings membersOnly(boolean membersOnly){
        this.membersOnly = membersOnly;
        return this;
    }

    public boolean isMembersOnly() {
        return membersOnly;
    }
}
