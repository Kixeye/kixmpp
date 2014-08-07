package com.kixeye.kixmpp.server.cluster.message;


import java.util.UUID;

public abstract class MapReduceResponse extends ClusterTask {

    // serialized fields
    private UUID transactionId;

    public MapReduceResponse() {
    }

    public MapReduceResponse(UUID transactionId) {
        this.transactionId = transactionId;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MapReduceResponse)) return false;

        MapReduceResponse that = (MapReduceResponse) o;

        if (!transactionId.equals(that.transactionId)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return transactionId.hashCode();
    }
}
