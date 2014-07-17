package com.kixeye.kixmpp.client;

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

/**
 * A stanza handler that uses {@link Reactor}.
 * 
 * @author ebahtijaragic
 */
public class KixmppStanzaHandlerRegistry {
	private final ConcurrentHashMap<Tuple, ConcurrentLinkedQueue<Registration<Consumer<Event<Element>>>>> consumsers = new ConcurrentHashMap<>();

	private final String clientId;
	private final Reactor reactor;

	/**
	 * @param reactor
	 */
	public KixmppStanzaHandlerRegistry(String clientId, Reactor reactor) {
		this.clientId = clientId;
		this.reactor = reactor;
	}

	/**
	 * Registers a stanza handler.
	 * 
	 * @param qualifiedName
	 * @param namespace
	 * @param handler
	 */
	public void register(String qualifiedName, String namespace, KixmppStanzaHandler handler) {
		Tuple key = Tuple.of(clientId, qualifiedName, namespace);
		ConcurrentLinkedQueue<Registration<Consumer<Event<Element>>>> consumerQueue = consumsers.get(key);
		
		if (consumerQueue == null) {
			ConcurrentLinkedQueue<Registration<Consumer<Event<Element>>>> newConsumerQueue = new ConcurrentLinkedQueue<>();
			
			consumerQueue = consumsers.putIfAbsent(key, newConsumerQueue);
			
            if (consumerQueue == null) {
            	consumerQueue = newConsumerQueue;
            }
        }
		
		consumerQueue.offer(reactor.on(Selectors.$(Tuple.of(clientId, qualifiedName, namespace)), new HandlerForwardingConsumer(handler)));
	}

	/**
	 * Registers a stanza handler.
	 * 
	 * @param qualifiedName
	 * @param namespace
	 * @param handler
	 */
	public void unregister(String qualifiedName, String namespace, KixmppStanzaHandler handler) {
		Tuple key = Tuple.of(clientId, qualifiedName, namespace);
		
		ConcurrentLinkedQueue<Registration<Consumer<Event<Element>>>> consumerQueue = consumsers.remove(key);
		
		if (consumerQueue != null) {
			Registration<?> registration = null;
			
			while ((registration = consumerQueue.poll()) != null) {
				if (registration.getObject() == handler) {
					registration.cancel();
				}
			}
		}
	}
	
	/**
	 * Unregisters all the handlers.
	 */
	public void unregisterAll() {
		for (Entry<Tuple, ConcurrentLinkedQueue<Registration<Consumer<Event<Element>>>>> entry : consumsers.entrySet()) {
			Registration<?> registration = null;
			
			while ((registration = entry.getValue().poll()) != null) {
				registration.cancel();
			}
		}
	}
	
	/**
	 * A consumer that forwards the stanza to a handler.
	 * 
	 * @author ebahtijaragic
	 */
	private static class HandlerForwardingConsumer implements Consumer<Event<Element>> {
		private static final Logger logger = LoggerFactory.getLogger(HandlerForwardingConsumer.class);
		
		private final KixmppStanzaHandler handler;
		
		/**
		 * @param handler
		 */
		public HandlerForwardingConsumer(KixmppStanzaHandler handler) {
			this.handler = handler;
		}

		public void accept(Event<Element> t) {
			try {
				handler.handle(t.getData());
			} catch (Exception e) {
				logger.error("Error while executing handler", e);
			}
		}
	}
}
