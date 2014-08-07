package com.kixeye.kixmpp.server.cluster.message;

import com.kixeye.kixmpp.KixmppJid;
import com.kixeye.kixmpp.p2p.node.NodeId;

import java.util.UUID;

public abstract class MapReduceRequest extends ClusterTask {

    // serialized fields
    private KixmppJid targetJID;
    private UUID transactionId;

    // local-only fields
    private transient NodeId senderId;

    public MapReduceRequest() {
    }

    public MapReduceRequest(KixmppJid targetJID) {
        this.targetJID = targetJID;
    }

    public void setSenderId(NodeId senderId) {
        this.senderId = senderId;
    }


    public NodeId getSenderId() {
        return senderId;
    }

    public void setTransactionId(UUID transactionId) {
        this.transactionId = transactionId;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public KixmppJid getTargetJID() {
        return targetJID;
    }

    public abstract void addResponse(MapReduceResponse response);

    public abstract void onComplete(boolean timedOut);


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MapReduceRequest)) return false;

        MapReduceRequest that = (MapReduceRequest) o;

        if (!transactionId.equals(that.transactionId)) return false;

        return true;
    }


    @Override
    public int hashCode() {
        return transactionId.hashCode();
    }
}
