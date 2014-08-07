package com.kixeye.kixmpp.server.cluster.mapreduce;

import com.google.common.cache.*;
import com.kixeye.kixmpp.server.KixmppServer;
import com.kixeye.kixmpp.server.cluster.message.MapReduceRequest;
import com.kixeye.kixmpp.server.cluster.message.MapReduceResponse;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MapReduceTracker tracks pending MapReduceRequests and completes them when all
 * results are available or they timeout.
 */
public class MapReduceTracker implements RemovalListener<UUID,MapReduceTracker.RequestWrapper> {
    private final KixmppServer server;
    private final Cache<UUID,RequestWrapper> requests;


    public MapReduceTracker(KixmppServer server) {
        this.server = server;
        this.requests = CacheBuilder.newBuilder()
                .expireAfterWrite(15, TimeUnit.SECONDS)
                .removalListener(this)
                .build();
    }

    @Override
    public void onRemoval(RemovalNotification<UUID, MapReduceTracker.RequestWrapper> notification) {
        if (notification.getCause() == RemovalCause.EXPIRED) {
            RequestWrapper wrapper = notification.getValue();
            wrapper.request.onComplete(true);
        }
    }


    /**
     * Send MapReduceRequest to all nodes in the cluster, including the
     * local node.
     *
     * @param request
     */
    public void sendRequest(MapReduceRequest request) {
        UUID transId = UUID.randomUUID();
        request.setTransactionId(transId);
        requests.put(transId,new RequestWrapper(request,server.getCluster().getNodeCount()));
        server.getCluster().sendMessageToAll(request, true);
    }


    /**
     * Process 1 of X responses to a request, possibly completing it.
     *
     * @param response
     */
    public void processResponse(MapReduceResponse response) {
        UUID transId = response.getTransactionId();
        RequestWrapper wrapper = requests.getIfPresent(transId);
        wrapper.request.addResponse(response);
        if (wrapper.pendingResponseCount.decrementAndGet() == 0) {
            requests.invalidate(transId);
            wrapper.request.onComplete(false);
        }
    }


    /**
     * Wrapper class to hold the request and pending response count.
     */
    public static class RequestWrapper {
        private MapReduceRequest request;
        private AtomicInteger pendingResponseCount;

        public RequestWrapper(MapReduceRequest request, int pendingResponseCount) {
            this.request = request;
            this.pendingResponseCount = new AtomicInteger(pendingResponseCount);
        }
    }
}
