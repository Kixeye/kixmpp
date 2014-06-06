package com.kixeye.kixmpp;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import org.jdom2.Element;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the {@link KixmppCodec}
 * 
 * @author ebahtijaragic
 */
public class KixmppCodecTest {
	@Test
	public void testSampleXmppClientSession() throws Exception {
		final ArrayList<Element> elements = new ArrayList<>();
		
		ChannelInboundHandlerAdapter handler = new ChannelInboundHandlerAdapter() {
			@Override
			public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
				elements.add((Element)msg);
			}
		};
		
		EmbeddedChannel channel = new EmbeddedChannel(
				new KixmppCodec(),
				handler);
		
		// write a packer per line
		try (InputStream inputStream = this.getClass().getResourceAsStream("/sampleXmppClientSession.xml")) {
			BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
			
			String line = null;
			while ((line = reader.readLine()) != null) {
				channel.writeInbound(channel.alloc().buffer().writeBytes(line.getBytes(StandardCharsets.UTF_8)));
			}
		}
		
		Assert.assertEquals(5, elements.size());

		Assert.assertEquals("starttls", elements.get(0).getName());
		Assert.assertEquals("auth", elements.get(1).getName());
		Assert.assertEquals("iq", elements.get(2).getName());
		Assert.assertEquals("iq", elements.get(3).getName());
		Assert.assertEquals("iq", elements.get(4).getName());
	}
	
	@Test
	public void testSampleXmppServerSession() throws Exception {
		final ArrayList<Element> elements = new ArrayList<>();
		
		ChannelInboundHandlerAdapter handler = new ChannelInboundHandlerAdapter() {
			@Override
			public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
				elements.add((Element)msg);
			}
		};
		
		EmbeddedChannel channel = new EmbeddedChannel(
				new KixmppCodec(),
				handler);

		// write a packet per line
		try (InputStream inputStream = this.getClass().getResourceAsStream("/sampleXmppServerSession.xml")) {
			BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
			
			String line = null;
			while ((line = reader.readLine()) != null) {
				channel.writeInbound(channel.alloc().buffer().writeBytes(line.getBytes(StandardCharsets.UTF_8)));
			}
		}
		
		Assert.assertEquals(6, elements.size());

		Assert.assertEquals("features", elements.get(0).getName());
		Assert.assertEquals("proceed", elements.get(1).getName());
		Assert.assertEquals("success", elements.get(2).getName());
		Assert.assertEquals("iq", elements.get(3).getName());
		Assert.assertEquals("iq", elements.get(4).getName());
		Assert.assertEquals("iq", elements.get(5).getName());
	}
}
