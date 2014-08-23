package com.kixeye.kixmpp;

/*
 * #%L
 * KIXMPP Parent
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


import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.jdom2.Content;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaderSAX2Factory;
import org.jdom2.output.XMLOutputter;
import org.jdom2.util.IteratorIterable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An XMPP codec for the client. It implements the following spec: https://datatracker.ietf.org/doc/draft-ietf-xmpp-websocket/?include_text=1.
 * 
 * @author ebahtijaragic
 */
public class KixmppWebSocketCodec extends MessageToMessageCodec<Object, Object> {
	private static final Logger logger  = LoggerFactory.getLogger(KixmppWebSocketCodec.class);
	
	private XMLReaderSAX2Factory readerFactory = new XMLReaderSAX2Factory(false);
	
	@Override
	public boolean acceptInboundMessage(Object msg) throws Exception {
		return msg instanceof WebSocketFrame;
	}
	
	@Override
	public boolean acceptOutboundMessage(Object msg) throws Exception {
		return msg instanceof Element || msg instanceof KixmppStreamStart || 
				msg instanceof KixmppStreamEnd || msg instanceof String || 
				msg instanceof ByteBuf;
	}
	
	@Override
	protected void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
		WebSocketFrame frame = null;
		
		if (msg instanceof Element) {
			Element element = (Element)msg;
			
			if (element.getNamespace() == null || element.getNamespace() == Namespace.NO_NAMESPACE) {
				if ("stream".equals(element.getNamespacePrefix())) {
					element.setNamespace(Namespace.getNamespace("http://etherx.jabber.org/streams"));
				} else {
					element.setNamespace(Namespace.getNamespace("jabber:client"));
					
					IteratorIterable<Content> descendants = element.getDescendants();
					
					while (descendants.hasNext()) {
						Content content = descendants.next();
						
						if (content instanceof Element) {
							Element descendantElement = (Element)content;
							if (descendantElement.getNamespace() == null || descendantElement.getNamespace() == Namespace.NO_NAMESPACE) {
								descendantElement.setNamespace(element.getNamespace());
							}
						}
					}
				}
			}
			
			ByteBuf binaryData = ctx.alloc().buffer();
			new XMLOutputter().output((Element)msg, new ByteBufOutputStream(binaryData));
			
			frame = new TextWebSocketFrame(binaryData);
		} else if (msg instanceof KixmppStreamStart) {
			KixmppStreamStart streamStart = (KixmppStreamStart)msg;
			
			StringWriter writer = new StringWriter();
			
			writer.append("<open xmlns=\"urn:ietf:params:xml:ns:xmpp-framing\"");
			
			if (streamStart.getFrom() != null) {
				writer.append(" from=\"" + streamStart.getFrom().getFullJid() + "\"");
			}
			
			if (streamStart.getTo() != null) {
				writer.append(" to=\"" + streamStart.getTo().getFullJid() + "\"");
			}
			
			writer.append(" version=\"1.0\" />");
			
			frame = new TextWebSocketFrame(writer.toString());
		} else if (msg instanceof KixmppStreamEnd) {
			frame = new TextWebSocketFrame("<close xmlns=\"urn:ietf:params:xml:ns:xmpp-framing\" />");
		} else if (msg instanceof String) {
			frame = new TextWebSocketFrame((String)msg);
		} else if (msg instanceof ByteBuf) {
			frame = new TextWebSocketFrame((ByteBuf)msg);
		}
		
		if (frame != null) {
			if (logger.isDebugEnabled()) {
				logger.debug("Sending: [{}]", frame.content().toString(StandardCharsets.UTF_8));
			}
			
			out.add(frame);
		}
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
		WebSocketFrame frame = (WebSocketFrame)msg;
		
		if (logger.isDebugEnabled()) {
			logger.debug("Received: [{}]", frame.content().toString(StandardCharsets.UTF_8));
		}
		
		SAXBuilder saxBuilder = new SAXBuilder(readerFactory);
		Document document = saxBuilder.build(new ByteBufInputStream(frame.retain().content()));
		
		Element element = document.getRootElement();
		
		if ("open".equals(element.getName())) {
			out.add(new KixmppStreamStart(element, false));
		} else if ("close".equals(element.getName())) {
			out.add(new KixmppStreamEnd());
		} else {
			out.add(element);
		}
	}
}