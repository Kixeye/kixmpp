package com.kixeye.kixmpp.server;

import com.kixeye.kixmpp.p2p.discovery.ConstNodeDiscovery;
import com.kixeye.kixmpp.p2p.node.NodeAddress;
import org.junit.Test;

import java.net.InetSocketAddress;


public class ClusterTest {
    public static final InetSocketAddress SERVER_A_SOCKET = new InetSocketAddress(5222);
    public static final InetSocketAddress SERVER_B_SOCKET = new InetSocketAddress(5223);
    public static final InetSocketAddress SERVER_A_CLUSTER = new InetSocketAddress(8100);
    public static final InetSocketAddress SERVER_B_CLUSTER = new InetSocketAddress(8101);
    public static final ConstNodeDiscovery discovery = new ConstNodeDiscovery(
            new NodeAddress(SERVER_A_CLUSTER.getHostName(), SERVER_A_CLUSTER.getPort()),
            new NodeAddress(SERVER_B_CLUSTER.getHostName(), SERVER_B_CLUSTER.getPort())
    );

    @Test
    public void twoNodeCluster() throws Exception {
        KixmppServer serverA = new KixmppServer(SERVER_A_SOCKET, "testChat", SERVER_A_CLUSTER, discovery);
        KixmppServer serverB = new KixmppServer(SERVER_B_SOCKET, "testChat", SERVER_B_CLUSTER, discovery);

        serverA.start().get();
        serverB.start().get();

        Thread.sleep(1000);

        serverA.stop();
        serverB.stop();
    }
}
