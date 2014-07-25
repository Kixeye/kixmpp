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


import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.base64.Base64;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import org.jdom2.Attribute;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.output.XMLOutputter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.Environment;
import reactor.core.Reactor;
import reactor.core.composable.Deferred;
import reactor.core.composable.Promise;
import reactor.core.composable.spec.Promises;
import reactor.core.spec.Reactors;
import reactor.event.Event;
import reactor.event.registry.Registration;
import reactor.event.selector.Selectors;
import reactor.function.Consumer;
import reactor.tuple.Tuple;

import com.kixeye.kixmpp.KixmppCodec;
import com.kixeye.kixmpp.client.module.KixmppModule;
import com.kixeye.kixmpp.client.module.muc.MucKixmppModule;
import com.kixeye.kixmpp.client.module.presence.PresenceKixmppModule;

/**
 * A XMPP client.
 * 
 * @author ebahtijaragic
 */
public class KixmppClient implements AutoCloseable {
	private static final Logger logger = LoggerFactory.getLogger(KixmppClient.class);
	
	private final String clientId = UUID.randomUUID().toString().replace("-", "");
	
    private final ConcurrentHashMap<KixmppClientOption<?>, Object> clientOptions = new ConcurrentHashMap<KixmppClientOption<?>, Object>();
	private final Bootstrap bootstrap;
	
	private final KixmppStanzaHandlerRegistry handlerRegistry;
	
	private final Environment environment;
	private final Reactor reactor;
	
	private final ConcurrentLinkedQueue<Registration<?>> consumerRegistrations = new ConcurrentLinkedQueue<>();

	private final SslContext sslContext;
	
	private final Set<KixmppStanzaInterceptor> incomingStanzaInterceptors = Collections.newSetFromMap(new ConcurrentHashMap<KixmppStanzaInterceptor, Boolean>());
	private final Set<KixmppStanzaInterceptor> outgoingStanzaInterceptors = Collections.newSetFromMap(new ConcurrentHashMap<KixmppStanzaInterceptor, Boolean>());

	private final Set<String> modulesToRegister = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
	private final ConcurrentHashMap<String, KixmppModule> modules = new ConcurrentHashMap<>();
	
	private Deferred<KixmppClient, Promise<KixmppClient>> deferredLogin;
	private Deferred<KixmppClient, Promise<KixmppClient>> deferredDisconnect;
	
	private String username;
	private String password;
	private String resource;
	private String domain;
	private String jid;
	
	private AtomicReference<Channel> channel = new AtomicReference<>(null);
	
	private AtomicReference<State> state = new AtomicReference<>(State.DISCONNECTED);
	private static enum State {
		CONNECTING,
		CONNECTED,
		
		LOGGING_IN,
		SECURING,
		LOGGED_IN,
		
		DISCONNECTING,
		DISCONNECTED
	}
	
	/**
	 * Creates a new {@link KixmppClient} with the given ssl engine.
	 * 
	 * @param sslContext
	 */
	public KixmppClient(SslContext sslContext) {
		this(new NioEventLoopGroup(), new Environment(), Environment.WORK_QUEUE, sslContext);
	}

	/**
	 * Creates a new {@link KixmppClient}.
	 * 
	 * @param eventLoopGroup
	 * @param environment
	 * @param dispatcher
	 * @param sslContext
	 */
	public KixmppClient(EventLoopGroup eventLoopGroup, Environment environment, String dispatcher, SslContext sslContext) {
		this(eventLoopGroup, environment, Reactors.reactor(environment, dispatcher), sslContext);
	}
	
	/**
	 * Creates a new {@link KixmppClient}.
	 * 
	 * @param eventLoopGroup
	 * @param environment
	 * @param reactor
	 * @param sslContext
	 */
	public KixmppClient(EventLoopGroup eventLoopGroup, Environment environment, Reactor reactor, SslContext sslContext) {
		bootstrap = new Bootstrap()
			.group(eventLoopGroup)
			.channel(NioSocketChannel.class)
			.option(ChannelOption.TCP_NODELAY, false)
			.option(ChannelOption.SO_KEEPALIVE, true)
			.handler(new KixmppClientChannelInitializer());
		
		this.environment = environment;
		this.reactor = reactor;
		this.sslContext = sslContext;
		this.handlerRegistry = new KixmppStanzaHandlerRegistry(clientId, reactor);
		
		// set modules to be registered
		this.modulesToRegister.add(MucKixmppModule.class.getName());
		this.modulesToRegister.add(PresenceKixmppModule.class.getName());
	}
	
	/**
	 * Connects to the hostname and port.
	 * 
	 * @param hostname
	 * @param port
	 */
	public Promise<KixmppClient> connect(String hostname, int port, String domain) {
		checkAndSetState(State.CONNECTING, State.DISCONNECTED);
		
		this.domain = domain;
		
		setUp();
		
		final Deferred<KixmppClient, Promise<KixmppClient>> deferred = Promises.defer(environment, reactor.getDispatcher());
		
		bootstrap.connect(hostname, port).addListener(new GenericFutureListener<Future<? super Void>>() {
			public void operationComplete(Future<? super Void> future) throws Exception {
				if (future.isSuccess()) {
					if (state.compareAndSet(State.CONNECTING, State.CONNECTED)) {
						channel.set(((ChannelFuture)future).channel());
						deferred.accept(KixmppClient.this);
					}
				} else {
					state.set(State.DISCONNECTED);
					deferred.accept(future.cause());
				}
			}
		});
		
		return deferred.compose();
	}
	
	/**
	 * Logs the user into the XMPP server.
	 * 
	 * @param username
	 * @param password
	 * @param resource
	 * @throws InterruptedException 
	 */
	public Promise<KixmppClient> login(String username, String password, String resource) throws InterruptedException {
		checkAndSetState(State.LOGGING_IN, State.CONNECTED);
		
		this.username = username;
		this.password = password;
		this.resource = resource;

		deferredLogin = Promises.defer(environment, reactor.getDispatcher());
		
		KixmppCodec.sendXmppStreamRootStart(channel.get(), null, domain);
		
		return deferredLogin.compose();
	}
	
	/**
	 * Disconnects from the current server.
	 */ 
	public Promise<KixmppClient> disconnect() {
		if (state.get() == State.DISCONNECTED) {
			final Deferred<KixmppClient, Promise<KixmppClient>> deferred = Promises.defer(environment, Environment.WORK_QUEUE);
			deferred.accept(this);
			
			return deferred.compose();
		} else if (state.get() == State.DISCONNECTING) {
			return deferredDisconnect.compose();
		}
		
		checkAndSetState(State.DISCONNECTING, State.CONNECTED, State.LOGGED_IN, State.LOGGING_IN);

		deferredDisconnect = Promises.defer(environment, reactor.getDispatcher());
		
		cleanUp();
		
		final Channel currentChannel = channel.get();

		if (currentChannel != null) {
			KixmppCodec.sendXmppStreamRootStop(channel.get()).addListener(new GenericFutureListener<Future<? super Void>>() {
				public void operationComplete(Future<? super Void> arg0) throws Exception {
					currentChannel.close().addListener(new GenericFutureListener<Future<? super Void>>() {
						public void operationComplete(Future<? super Void> arg0) throws Exception {
							deferredDisconnect.accept(KixmppClient.this);
							state.set(State.DISCONNECTED);
						}
					});
				}
			});
		} else {
			deferredDisconnect.accept(new KixmppException("No channel available to close."));
		}

		return deferredDisconnect.compose();
	}

	/**
	 * @see java.lang.AutoCloseable#close()
	 */
	public void close() throws Exception {
		disconnect();
	}
	
	/**
	 * Sets the client's {@link KixmppClientOption}s.
	 * 
	 * @param option
	 * @param value
	 * @return
	 */
    public <T> KixmppClient clientOption(KixmppClientOption<T> option, T value) {
    	if (value == null) {
    		clientOptions.remove(option);
    	} else {
    		clientOptions.put(option, value);
    	}
    	return this;
    }

	/**
	 * Sets Netty {@link ChannelOption}s.
	 * 
	 * @param option
	 * @param value
	 * @return
	 */
    public <T> KixmppClient channelOption(ChannelOption<T> option, T value) {
    	bootstrap.option(option, value);
    	return this;
    }
    
    /**
     * Adds an incoming stanza interceptor.
     * 
     * @param interceptor
     */
    public boolean addIncomingStanzaInterceptor(KixmppStanzaInterceptor interceptor) {
    	return incomingStanzaInterceptors.add(interceptor);
    }
    
    /**
     * Removes an incoming stanza interceptor.
     * 
     * @param interceptor
     */
    public boolean removeIncomingStanzaInterceptor(KixmppStanzaInterceptor interceptor) {
    	return incomingStanzaInterceptors.remove(interceptor);
    }
    
    /**
     * Adds an outgoing stanza interceptor.
     * 
     * @param interceptor
     */
    public boolean addOutgoingStanzaInterceptor(KixmppStanzaInterceptor interceptor) {
    	return outgoingStanzaInterceptors.add(interceptor);
    }
    
    /**
     * Removes an outgoing stanza interceptor.
     * 
     * @param interceptor
     */
    public boolean removeOutgoingStanzaInterceptor(KixmppStanzaInterceptor interceptor) {
    	return outgoingStanzaInterceptors.remove(interceptor);
    }
    
    /**
     * Gets the handler registry.
     * 
     * @return
     */
    public KixmppStanzaHandlerRegistry getHandlerRegistry() {
    	return handlerRegistry;
    }
    
    /**
     * @param moduleClass
     * @return true if module is installed
     */
    public boolean hasActiveModule(Class<?> moduleClass) {
    	return modules.containsKey(moduleClass.getName());
    }
    
    /**
     * Gets or installs a module.
     * 
     * @param moduleClass
     * @return
     */
    @SuppressWarnings("unchecked")
	public <T extends KixmppModule> T module(Class<T> moduleClass) {
    	if (!(state.get() == State.CONNECTED || state.get() == State.LOGGED_IN)) {
			throw new IllegalStateException(String.format("The current state is [%s] but must be [CONNECTED or LOGGED_IN]", state.get()));
    	}
    	
    	T module = (T)modules.get(moduleClass.getName());
    	
    	if (module == null) {
    		module = (T)installModule(moduleClass.getName());
    	}

    	return module;
    }
    
    /**
     * Writes a stanza to the channel.
     * 
     * @param element
     */
    public void sendStanza(Element element) {
    	channel.get().writeAndFlush(element);
    }
    
    /**
     * Checks the state and sets it.
     * 
     * @param update
     * @param expectedStates
     * @throws IllegalStateException
     */
    private void checkAndSetState(State update, State... expectedStates) throws IllegalStateException {
    	if (expectedStates != null) {
    		boolean wasSet = false;
    		
    		for (State expectedState : expectedStates) {
    			if (state.compareAndSet(expectedState, update)) {
    				wasSet = true;
    				break;
    			}
    		}
    		
    		if (!wasSet) {
    			throw new IllegalStateException(String.format("The current state is [%s] but must be [%s]", state.get(), expectedStates));
    		}
    	} else {
    		if (!state.compareAndSet(null, update)) {
    			throw new IllegalStateException(String.format("The current state is [%s] but must be [null]", state.get()));
			}
    	}
    }
    
    /**
     * Registers all the consumers and modules.
     */
    private void setUp() {
    	if (state.get() == State.CONNECTING) {
    		// this client deals with the following stanzas
    		consumerRegistrations.offer(reactor.on(Selectors.$(Tuple.of(clientId, "stream:features", "http://etherx.jabber.org/streams")), streamFeaturesConsumer));

    		consumerRegistrations.offer(reactor.on(Selectors.$(Tuple.of(clientId, "proceed", "urn:ietf:params:xml:ns:xmpp-tls")), tlsResponseConsumer));
    		
    		consumerRegistrations.offer(reactor.on(Selectors.$(Tuple.of(clientId, "success", "urn:ietf:params:xml:ns:xmpp-sasl")), authResultConsumer));
    		consumerRegistrations.offer(reactor.on(Selectors.$(Tuple.of(clientId, "failure", "urn:ietf:params:xml:ns:xmpp-sasl")), authResultConsumer));

    		consumerRegistrations.offer(reactor.on(Selectors.$(Tuple.of(clientId, "iq", "jabber:client")), iqResultConsumer));
    		
    		consumerRegistrations.offer(reactor.on(Selectors.$(Tuple.of(clientId, "bind")), iqBindResultConsumer));
    		consumerRegistrations.offer(reactor.on(Selectors.$(Tuple.of(clientId, "session")), iqSessionResultConsumer));
    		
    		// register all modules
    		for (String moduleClassName : modulesToRegister) {
    			installModule(moduleClassName);
    		}
    	}
    }
    
    /**
     * Unregisters all consumers and modules.
     */
    private void cleanUp() {
    	if (state.get() == State.DISCONNECTING) {
    		Registration<?> registration = null;
    		
    		while ((registration = consumerRegistrations.poll()) != null) {
    			registration.cancel();
    		}
    		
    		for (Entry<String, KixmppModule> entry : modules.entrySet()) {
    			entry.getValue().uninstall(this);
    		}
    		
    		handlerRegistry.unregisterAll();
    	}
    }
    
    /**
     * Tries to install module.
     * 
     * @param moduleClassName
     * @throws Exception
     */
    private KixmppModule installModule(String moduleClassName) {
    	KixmppModule module = null;
		
		try {
			module = (KixmppModule)Class.forName(moduleClassName).newInstance();
			module.install(this);
			
			modules.put(moduleClassName, module);
		} catch (Exception e) {
			logger.error("Error while installing module", e);
		}
		
		return module;
    }
    
    /**
     * Performs auth.
     */
    private void performAuth() {
    	byte[] authToken = ("\0" + username + "\0" + password).getBytes(StandardCharsets.UTF_8);
		
		Element auth = new Element("auth", "urn:ietf:params:xml:ns:xmpp-sasl");
		auth.setAttribute("mechanism", "PLAIN");
		
		ByteBuf rawCredentials = channel.get().alloc().buffer().writeBytes(authToken);
		ByteBuf encodedCredentials = Base64.encode(rawCredentials);
		String encodedCredentialsString = encodedCredentials.toString(StandardCharsets.UTF_8);
		encodedCredentials.release();
		rawCredentials.release();
		
		auth.setText(encodedCredentialsString);
		
		channel.get().writeAndFlush(auth);
    }
    
    /**
     * Handles stream features
     */
    private final Consumer<Event<Element>> streamFeaturesConsumer = new Consumer<Event<Element>>() {
		public void accept(Event<Element> event) {
			Element features = event.getData();
			
			Element startTls = features.getChild("starttls", Namespace.getNamespace("urn:ietf:params:xml:ns:xmpp-tls"));
			
			Object enableTls = clientOptions.get(KixmppClientOption.ENABLE_TLS);
			
			if ((enableTls != null && (boolean)enableTls) || (startTls != null && startTls.getChild("required", startTls.getNamespace()) != null)) {
				// if its required, always do tls
				startTls = new Element("starttls", "tls", "urn:ietf:params:xml:ns:xmpp-tls");
				
				channel.get().writeAndFlush(startTls);
			} else {
				performAuth();
			}
		}
	};
	
	/**
     * Handles stream features
     */
    private final Consumer<Event<Element>> tlsResponseConsumer = new Consumer<Event<Element>>() {
		public void accept(Event<Element> event) {
			if (state.compareAndSet(State.LOGGING_IN, State.SECURING)) {
				SslHandler handler = sslContext.newHandler(channel.get().alloc());
				handler.handshakeFuture().addListener(new GenericFutureListener<Future<? super Channel>>() {
					public void operationComplete(Future<? super Channel> future) throws Exception {
						if (future.isSuccess()) {
							channel.get().pipeline().replace(KixmppCodec.class, "kixmppCodec", new KixmppCodec());
							
							KixmppCodec.sendXmppStreamRootStart(channel.get(), null, domain);
						} else {
							deferredLogin.accept(new KixmppAuthException("tls failed"));
						}
					}
				});
				
				channel.get().pipeline().addFirst("sslHandler", handler);
			}
		}
	};
	
	/**
     * Handles auth success
     */
    private final Consumer<Event<Element>> authResultConsumer = new Consumer<Event<Element>>() {
		public void accept(Event<Element> event) {
			Element authResponse = event.getData();
			switch (authResponse.getName()) {
				case "success":
					// send bind
					Element bindRequest = new Element("iq");
					bindRequest.setAttribute("type", "set");
					bindRequest.setAttribute("id", "bind");
					
					Element bind = new Element("bind", "urn:ietf:params:xml:ns:xmpp-bind");
					
					if (KixmppClient.this.resource != null) {
						Element resource = new Element("resource");
						resource.setText(KixmppClient.this.resource);
					}
					
					bindRequest.addContent(bind);
		
					channel.get().writeAndFlush(bindRequest);
					break;
				default:
					// fail
					deferredLogin.accept(new KixmppAuthException(new XMLOutputter().outputString(authResponse)));
					break;
			}
		}
	};
	
	/**
     * Handles iq bind response
     */
    private final Consumer<Event<Element>> iqBindResultConsumer = new Consumer<Event<Element>>() {
		public void accept(Event<Element> event) {
			Element iqResponse = event.getData();

			Attribute typeAttribute = iqResponse.getAttribute("type");
			
			if (typeAttribute != null && "result".equals(typeAttribute.getValue())) {
				Element bind = iqResponse.getChild("bind", Namespace.getNamespace("urn:ietf:params:xml:ns:xmpp-bind"));
				
				if (bind != null) {
					jid = bind.getChildText("jid", bind.getNamespace());
				}

				// start the session
				Element startSession = new Element("iq");
				startSession.setAttribute("to", domain);
				startSession.setAttribute("type", "set");
				startSession.setAttribute("id", "session");
				
				Element session = new Element("session", "urn:ietf:params:xml:ns:xmpp-session");
				startSession.addContent(session);

				channel.get().writeAndFlush(startSession);
			} else {
				// fail
				deferredLogin.accept(new KixmppAuthException(new XMLOutputter().outputString(iqResponse)));
			}
		}
	};
	
	/**
     * Handles iq bind response
     */
    private final Consumer<Event<Element>> iqSessionResultConsumer = new Consumer<Event<Element>>() {
		public void accept(Event<Element> event) {
			Element iqResponse = event.getData();
			
			Attribute typeAttribute = iqResponse.getAttribute("type");
			
			if (typeAttribute != null && "result".equals(typeAttribute.getValue())) {
				deferredLogin.accept(KixmppClient.this);
				state.set(State.LOGGED_IN);
				
				logger.debug("Logged in as: " + jid);
			} else {
				// fail
				deferredLogin.accept(new KixmppAuthException(new XMLOutputter().outputString(iqResponse)));
			}

			// no need to keep reference around
			deferredLogin = null;
		}
	};
	
	/**
     * Handles iq stanzas
     */
    private final Consumer<Event<Element>> iqResultConsumer = new Consumer<Event<Element>>() {
		public void accept(Event<Element> event) {
			Element iqResponse = event.getData();
			
			Attribute idAttribute = iqResponse.getAttribute("id");
			
			if (idAttribute != null) {
				switch (idAttribute.getValue()) {
					case "bind":
						reactor.notify(Tuple.of(clientId, "bind"), event);
						break;
					case "session":
						reactor.notify(Tuple.of(clientId, "session"), event);
						break;
					default:
						logger.warn("Unsupported IQ stanza: " + new XMLOutputter().outputString(iqResponse));
						break;
				}
			}
		}
	};
	
    /**
     * Channel initializer for the {@link KixmppClient}.
     * 
     * @author ebahtijaragic
     */
	private final class KixmppClientChannelInitializer extends ChannelInitializer<SocketChannel> {
		/**
		 * @see io.netty.channel.ChannelInitializer#initChannel(io.netty.channel.Channel)
		 */
		protected void initChannel(SocketChannel ch) throws Exception {
			// initially only add the codec and out client handler
			ch.pipeline().addLast("kixmppCodec", new KixmppCodec());
			ch.pipeline().addLast("kixmppClientMessageHandler", new KixmppClientMessageHandler());
		}
	}
	
	/**
	 * Message handler for the {@link KixmppClient}
	 * 
	 * @author ebahtijaragic
	 */
	private final class KixmppClientMessageHandler extends ChannelDuplexHandler {
		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
			if (msg instanceof Element) {
				Element stanza = (Element)msg;
				
				for (KixmppStanzaInterceptor interceptor : incomingStanzaInterceptors) {
					try {
						interceptor.interceptStanza((Element)msg);
					} catch (Exception e) {
						logger.error("Incomming stanza interceptor [{}] threw an exception.", interceptor, e);
					}
				}
	
				reactor.notify(Tuple.of(clientId, stanza.getQualifiedName(), stanza.getNamespaceURI()), Event.wrap(stanza));
			}
		}
		
		@Override
		public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
			if (msg instanceof Element) {
				for (KixmppStanzaInterceptor interceptor : outgoingStanzaInterceptors) {
					try {
						interceptor.interceptStanza((Element)msg);
					} catch (Exception e) {
						logger.error("Outgoing stanza interceptor [{}] threw an exception.", interceptor, e);
					}
				}
			}
			
			super.write(ctx, msg, promise);
		}
		
		@Override
		public void channelInactive(ChannelHandlerContext ctx) throws Exception {
			KixmppClient.this.disconnect();
		}
	}
}
