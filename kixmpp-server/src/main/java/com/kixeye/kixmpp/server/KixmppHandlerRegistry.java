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

import io.netty.channel.Channel;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.jdom2.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.Reactor;
import reactor.event.Event;
import reactor.event.registry.Registration;
import reactor.event.selector.Selectors;
import reactor.function.Consumer;
import reactor.tuple.Tuple;

import com.kixeye.kixmpp.KixmppStreamEnd;
import com.kixeye.kixmpp.KixmppStreamStart;

/**
 * A stanza handler that uses {@link Reactor}.
 * 
 * @author ebahtijaragic
 */
public class KixmppHandlerRegistry {
	private static final Logger logger = LoggerFactory.getLogger(KixmppHandlerRegistry.class);
	
	private final ConcurrentHashMap<Tuple, ConcurrentLinkedQueue<Registration<Consumer<Event<Tuple>>>>> consumers = new ConcurrentHashMap<>();

	private final Reactor reactor;

	/**
	 * @param reactor
	 */
	public KixmppHandlerRegistry(Reactor reactor) {
		this.reactor = reactor;
	}
	
	/**
	 * Registers a stream handler.
	 * 
	 * @param handler
	 */
	public void register(KixmppStreamHandler handler) {
		Tuple startKey = Tuple.of("stream:stream", "http://etherx.jabber.org/streams", "start");
		Tuple endKey = Tuple.of("stream:stream", "http://etherx.jabber.org/streams", "end");

		synchronized (consumers) {
			ConcurrentLinkedQueue<Registration<Consumer<Event<Tuple>>>> consumerQueue = consumers.get(startKey);
			
			if (consumerQueue == null) {
				ConcurrentLinkedQueue<Registration<Consumer<Event<Tuple>>>> newConsumerQueue = new ConcurrentLinkedQueue<>();
				
				consumerQueue = consumers.putIfAbsent(startKey, newConsumerQueue);
				
	            if (consumerQueue == null) {
	            	consumerQueue = newConsumerQueue;
	            }
	        }
			
			consumerQueue.offer(reactor.on(Selectors.$(startKey), new StreamStartHandlerForwardingConsumer(handler)));
			
			consumerQueue = consumers.get(endKey);
			
			if (consumerQueue == null) {
				ConcurrentLinkedQueue<Registration<Consumer<Event<Tuple>>>> newConsumerQueue = new ConcurrentLinkedQueue<>();
				
				consumerQueue = consumers.putIfAbsent(endKey, newConsumerQueue);
				
	            if (consumerQueue == null) {
	            	consumerQueue = newConsumerQueue;
	            }
	        }
			
			consumerQueue.offer(reactor.on(Selectors.$(endKey), new StreamEndHandlerForwardingConsumer(handler)));
		}
	}
	
	/**
	 * Unregisters a stream handler.
	 * 
	 * @param qualifiedName
	 * @param namespace
	 * @param handler
	 */
	public void unregister(KixmppStreamHandler handler) {
		Tuple key = Tuple.of("stream:stream", "http://etherx.jabber.org/streams", "start");

		synchronized (consumers) {
			ConcurrentLinkedQueue<Registration<Consumer<Event<Tuple>>>> consumerQueue = consumers.get(key);
			
			if (consumerQueue != null) {
				Iterator<Registration<Consumer<Event<Tuple>>>> iterator = consumerQueue.iterator();
				
				while (iterator.hasNext()) {
					Registration<Consumer<Event<Tuple>>> registration = iterator.next();
					
					if (registration.getObject() == handler) {
						iterator.remove();
						registration.cancel();
					}
				}
			}
			
			consumerQueue = consumers.get(key);
			
			if (consumerQueue != null) {
				Iterator<Registration<Consumer<Event<Tuple>>>> iterator = consumerQueue.iterator();
				
				while (iterator.hasNext()) {
					Registration<Consumer<Event<Tuple>>> registration = iterator.next();
					
					if (registration.getObject() == handler) {
						iterator.remove();
						registration.cancel();
					}
				}
			}
		}
	}

	/**
	 * Registers a stanza handler.
	 * 
	 * @param qualifiedName
	 * @param namespace
	 * @param handler
	 */
	public void register(String qualifiedName, String namespace, KixmppStanzaHandler handler) {
		synchronized (consumers) {
			Tuple key = Tuple.of(qualifiedName, namespace);
			ConcurrentLinkedQueue<Registration<Consumer<Event<Tuple>>>> consumerQueue = consumers.get(key);
			
			if (consumerQueue == null) {
				ConcurrentLinkedQueue<Registration<Consumer<Event<Tuple>>>> newConsumerQueue = new ConcurrentLinkedQueue<>();
				
				consumerQueue = consumers.putIfAbsent(key, newConsumerQueue);
				
	            if (consumerQueue == null) {
	            	consumerQueue = newConsumerQueue;
	            }
	        }
			
			consumerQueue.offer(reactor.on(Selectors.$(key), new StanzaHandlerForwardingConsumer(handler)));
		}
	}

	/**
	 * Unregisters a stanza handler.
	 * 
	 * @param qualifiedName
	 * @param namespace
	 * @param handler
	 */
	public void unregister(String qualifiedName, String namespace, KixmppStanzaHandler handler) {
		synchronized (consumers) {
			Tuple key = Tuple.of(qualifiedName, namespace);
			
			ConcurrentLinkedQueue<Registration<Consumer<Event<Tuple>>>> consumerQueue = consumers.get(key);
			
			if (consumerQueue != null) {
				Iterator<Registration<Consumer<Event<Tuple>>>> iterator = consumerQueue.iterator();
				
				while (iterator.hasNext()) {
					Registration<Consumer<Event<Tuple>>> registration = iterator.next();
					
					if (registration.getObject() == handler) {
						iterator.remove();
						registration.cancel();
					}
				}
			}
		}
	}
	
	/**
	 * Unregisters all the handlers.
	 */
	public void unregisterAll() {
		synchronized (consumers) {
			for (Entry<Tuple, ConcurrentLinkedQueue<Registration<Consumer<Event<Tuple>>>>> entry : consumers.entrySet()) {
				Registration<?> registration = null;
				
				while ((registration = entry.getValue().poll()) != null) {
					registration.cancel();
				}
			}
			
			consumers.clear();
		}
	}
	
	/**
	 * A consumer that forwards the stanza to a handler.
	 * 
	 * @author ebahtijaragic
	 */
	private static class StanzaHandlerForwardingConsumer implements Consumer<Event<Tuple>> {
		private final KixmppStanzaHandler handler;
		
		/**
		 * @param handler
		 */
		public StanzaHandlerForwardingConsumer(KixmppStanzaHandler handler) {
			this.handler = handler;
		}

		public void accept(Event<Tuple> t) {
			try {
				handler.handle((Channel)t.getData().toArray()[0], (Element)t.getData().toArray()[1]);
			} catch (Exception e) {
				logger.error("Error while executing handler", e);
			}
		}
	}
	
	/**
	 * A consumer that forwards the stream start to a handler.
	 * 
	 * @author ebahtijaragic
	 */
	private static class StreamStartHandlerForwardingConsumer implements Consumer<Event<Tuple>> {
		private final KixmppStreamHandler handler;
		
		/**
		 * @param handler
		 */
		public StreamStartHandlerForwardingConsumer(KixmppStreamHandler handler) {
			this.handler = handler;
		}

		public void accept(Event<Tuple> t) {
			try {
				handler.handleStreamStart((Channel)t.getData().toArray()[0], (KixmppStreamStart)t.getData().toArray()[1]);
			} catch (Exception e) {
				logger.error("Error while executing handler", e);
			}
		}
	}
	
	/**
	 * A consumer that forwards the stream end to a handler.
	 * 
	 * @author ebahtijaragic
	 */
	private static class StreamEndHandlerForwardingConsumer implements Consumer<Event<Tuple>> {
		private final KixmppStreamHandler handler;
		
		/**
		 * @param handler
		 */
		public StreamEndHandlerForwardingConsumer(KixmppStreamHandler handler) {
			this.handler = handler;
		}

		public void accept(Event<Tuple> t) {
			try {
				handler.handleStreamEnd((Channel)t.getData().toArray()[0], (KixmppStreamEnd)t.getData().toArray()[1]);
			} catch (Exception e) {
				logger.error("Error while executing handler", e);
			}
		}
	}
}
