package com.kixeye.kixmpp.server;

import java.util.concurrent.TimeUnit;

import io.netty.handler.ssl.SslContext;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the {@link KixmppServer}
 * 
 * @author ebahtijaragic
 */
public class KixmppServerTest {
	@Test
	public void testSimple() throws Exception {
		try (KixmppServer server = new KixmppServer(SslContext.newClientContext())) {
			Assert.assertNotNull(server.start().await(2, TimeUnit.SECONDS));
			
			Thread.sleep(1000);
		}
	}
}
