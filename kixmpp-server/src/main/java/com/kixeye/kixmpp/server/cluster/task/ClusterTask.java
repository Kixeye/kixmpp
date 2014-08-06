package com.kixeye.kixmpp.server.cluster.task;

import com.kixeye.kixmpp.server.KixmppServer;
import org.fusesource.hawtdispatch.Task;

public abstract class ClusterTask extends Task {

    private transient KixmppServer server;

    public void setKixmppServer(KixmppServer server) {
        this.server = server;
    }

    public KixmppServer getKixmppServer() {
        return server;
    }
}
