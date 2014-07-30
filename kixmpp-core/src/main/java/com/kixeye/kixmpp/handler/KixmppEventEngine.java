package com.kixeye.kixmpp.handler;

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
public class KixmppEventEngine {
	private static final Logger logger = LoggerFactory.getLogger(KixmppEventEngine.class);
	
	private final String prefix;
	
	private final ConcurrentHashMap<Tuple, ConcurrentLinkedQueue<Registration<Consumer<Event<Tuple>>>>> consumers = new ConcurrentHashMap<>();

	private final Reactor reactor;
	
	/**
	 * @param reactor
	 */
	public KixmppEventEngine(Reactor reactor) {
		this(null, reactor);
	}

	/**
	 * @param prefix
	 * @param reactor
	 */
	public KixmppEventEngine(String prefix, Reactor reactor) {
		assert reactor != null : "Argument 'reactor' cannot be null";
		
		this.prefix = prefix;
		this.reactor = reactor;
	}
	
	/**
	 * Publishes a stanza.
	 * 
	 * @param channel
	 * @param element
	 */
	public void publish(Channel channel, Element stanza) {
		reactor.notify(getTuple(stanza.getQualifiedName(), stanza.getNamespaceURI()), Event.wrap(Tuple.of(channel, stanza)));
	}
	
	/**
	 * Publishes that a channel has connected.
	 * 
	 * @param channel
	 */
	public void publishConnected(Channel channel) {
		reactor.notify(getTuple("connection", "start"), Event.wrap(Tuple.of(channel)));
	}
	
	/**
	 * Publishes that a channel has disconnected.
	 * 
	 * @param channel
	 */
	public void publishDisconnected(Channel channel) {
		reactor.notify(getTuple("connection", "end"), Event.wrap(Tuple.of(channel)));
	}
	
	/**
	 * Publishes a stream start event.
	 * 
	 * @param channel
	 * @param streamStart
	 */
	public void publish(Channel channel, KixmppStreamStart streamStart) {
		reactor.notify(getTuple("stream:stream", "http://etherx.jabber.org/streams", "start"), Event.wrap(Tuple.of(channel, streamStart)));
	}
	
	/**
	 * Publishes a stream end event.
	 * 
	 * @param channel
	 * @param streamEnd
	 */
	public void publish(Channel channel, KixmppStreamEnd streamEnd) {
		reactor.notify(getTuple("stream:stream", "http://etherx.jabber.org/streams", "end"), Event.wrap(Tuple.of(channel, streamEnd)));
	}
	
	/**
	 * Registers a handler to listen to connection events.
	 * 
	 * @param handler
	 */
	public void register(KixmppConnectionHandler handler) {
		Tuple startKey = getTuple("connection", "start");
		Tuple endKey = getTuple("connection", "end");

		synchronized (consumers) {
			ConcurrentLinkedQueue<Registration<Consumer<Event<Tuple>>>> consumerQueue = consumers.get(startKey);
			
			if (consumerQueue == null) {
				ConcurrentLinkedQueue<Registration<Consumer<Event<Tuple>>>> newConsumerQueue = new ConcurrentLinkedQueue<>();
				
				consumerQueue = consumers.putIfAbsent(startKey, newConsumerQueue);
				
	            if (consumerQueue == null) {
	            	consumerQueue = newConsumerQueue;
	            }
	        }
			
			consumerQueue.offer(reactor.on(Selectors.$(startKey), new ConnectionStartHandlerForwardingConsumer(handler)));
			
			consumerQueue = consumers.get(endKey);
			
			if (consumerQueue == null) {
				ConcurrentLinkedQueue<Registration<Consumer<Event<Tuple>>>> newConsumerQueue = new ConcurrentLinkedQueue<>();
				
				consumerQueue = consumers.putIfAbsent(endKey, newConsumerQueue);
				
	            if (consumerQueue == null) {
	            	consumerQueue = newConsumerQueue;
	            }
	        }
			
			consumerQueue.offer(reactor.on(Selectors.$(endKey), new ConnectionEndHandlerForwardingConsumer(handler)));
		}
	}
	

	/**
	 * Unregisters a connection handler.
	 * 
	 * @param handler
	 */
	public void unregister(KixmppConnectionHandler handler) {
		Tuple startKey = getTuple("connection", "start");
		Tuple endKey = getTuple("connection", "end");

		synchronized (consumers) {
			ConcurrentLinkedQueue<Registration<Consumer<Event<Tuple>>>> consumerQueue = consumers.get(startKey);
			
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
			
			consumerQueue = consumers.get(endKey);
			
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
	 * Registers a stream handler.
	 * 
	 * @param handler
	 */
	public void register(KixmppStreamHandler handler) {
		Tuple startKey = getTuple("stream:stream", "http://etherx.jabber.org/streams", "start");
		Tuple endKey = getTuple("stream:stream", "http://etherx.jabber.org/streams", "end");

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
	 * @param handler
	 */
	public void unregister(KixmppStreamHandler handler) {
		Tuple startKey = getTuple("stream:stream", "http://etherx.jabber.org/streams", "start");
		Tuple endKey = getTuple("stream:stream", "http://etherx.jabber.org/streams", "end");

		synchronized (consumers) {
			ConcurrentLinkedQueue<Registration<Consumer<Event<Tuple>>>> consumerQueue = consumers.get(startKey);
			
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
			
			consumerQueue = consumers.get(endKey);
			
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
			Tuple key = getTuple(qualifiedName, namespace);
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
			Tuple key = getTuple(qualifiedName, namespace);
			
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
	 * Gets a tuple.
	 * 
	 * @param qualifiedName
	 * @param namespace
	 * @return
	 */
	private Tuple getTuple(String qualifiedName, String namespace) {
		if (prefix != null) {
			return Tuple.of(prefix, qualifiedName, namespace);
		} else {
			return Tuple.of(qualifiedName, namespace);
		}
	}
	
	/**
	 * Gets a tuple.
	 * 
	 * @param qualifiedName
	 * @param namespace
	 * @param action
	 * @return
	 */
	private Tuple getTuple(String qualifiedName, String namespace, String action) {
		if (prefix != null) {
			return Tuple.of(prefix, qualifiedName, namespace, action);
		} else {
			return Tuple.of(qualifiedName, namespace, action);
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
	
	/**
	 * A consumer that forwards the connection start to a handler.
	 * 
	 * @author ebahtijaragic
	 */
	private static class ConnectionStartHandlerForwardingConsumer implements Consumer<Event<Tuple>> {
		private final KixmppConnectionHandler handler;
		
		/**
		 * @param handler
		 */
		public ConnectionStartHandlerForwardingConsumer(KixmppConnectionHandler handler) {
			this.handler = handler;
		}

		public void accept(Event<Tuple> t) {
			try {
				handler.handleConnected((Channel)t.getData().toArray()[0]);
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
	private static class ConnectionEndHandlerForwardingConsumer implements Consumer<Event<Tuple>> {
		private final KixmppConnectionHandler handler;
		
		/**
		 * @param handler
		 */
		public ConnectionEndHandlerForwardingConsumer(KixmppConnectionHandler handler) {
			this.handler = handler;
		}

		public void accept(Event<Tuple> t) {
			try {
				handler.handleDisconnected((Channel)t.getData().toArray()[0]);
			} catch (Exception e) {
				logger.error("Error while executing handler", e);
			}
		}
	}
}
