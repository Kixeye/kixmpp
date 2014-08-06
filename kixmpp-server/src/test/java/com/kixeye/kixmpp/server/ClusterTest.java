package com.kixeye.kixmpp.server;

/*
 * #%L
 * KIXMPP
 * %%
 * Copyright (C) 2014 KIXEYE, Inc
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

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
