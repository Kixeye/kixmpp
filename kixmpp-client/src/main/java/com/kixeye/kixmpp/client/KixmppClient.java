package com.kixeye.kixmpp.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.base64.Base64;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.nio.charset.StandardCharsets;
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

/**
 * A KIXMPP client.
 * 
 * @author ebahtijaragic
 */
public class KixmppClient implements AutoCloseable {
	private static final Logger logger = LoggerFactory.getLogger(KixmppClient.class);
	
	private final String clientId = UUID.randomUUID().toString().replace("-", "");
	
    private final ConcurrentHashMap<KixmppClientOption<?>, Object> clientOptions = new ConcurrentHashMap<KixmppClientOption<?>, Object>();
	private final Bootstrap bootstrap;
	
	private final Environment environment;
	private final Reactor reactor;
	
	private final ConcurrentLinkedQueue<Registration<?>> consumerRegistrations = new ConcurrentLinkedQueue<>();

	private final SslContext sslContext;
	
	private Deferred<KixmppClient, Promise<KixmppClient>> deferredLogin;
	
	private String username;
	private String password;
	private String resource;
	private String domain;
	private String jid;
	
	private AtomicReference<ChannelFuture> future = new AtomicReference<>(null);
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
		bootstrap = new Bootstrap()
			.group(eventLoopGroup)
			.channel(NioSocketChannel.class)
			.option(ChannelOption.TCP_NODELAY, false)
			.option(ChannelOption.SO_KEEPALIVE, true)
			.handler(new KixmppClientChannelInitializer());
		
		this.environment = environment;
		this.reactor = Reactors.reactor(environment, dispatcher);
		this.sslContext = sslContext;
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
	}
	
	/**
	 * Connects to the hostname and port.
	 * 
	 * @param hostname
	 * @param port
	 */
	public Promise<KixmppClient> connect(String hostname, int port,  String domain) {
		checkAndSetState(State.DISCONNECTED, State.CONNECTING);
		
		this.domain = domain;
		
		registerConsumers();
		
		final Deferred<KixmppClient, Promise<KixmppClient>> deferred = Promises.defer(environment, Environment.WORK_QUEUE);
		
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
		checkAndSetState(State.CONNECTED, State.LOGGING_IN);
		
		this.username = username;
		this.password = password;
		this.resource = resource;

		deferredLogin = Promises.defer(environment, Environment.WORK_QUEUE);
		
		KixmppCodec.sendXmppStreamRootStart(channel.get(), domain);
		
		return deferredLogin.compose();
	}
	
	/**
	 * Disconnects from the current server.
	 */ 
	public void disconnect() {
		State previousState = state.getAndSet(State.DISCONNECTING);
		
		if (previousState != State.DISCONNECTING) {
			try {
				unregisterConsumers();
				
				try {
					ChannelFuture currentFuture = future.get();
					
					if (currentFuture != null) {
						currentFuture.cancel(false);
					}
				} finally {
					Channel currentChannel = channel.get();
					
					if (currentChannel != null) {
						currentChannel.close();
					}
				}
			} finally {
				state.set(State.DISCONNECTED);
			}
		}
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
     * Checks the state and sets it.
     * 
     * @param expected
     * @param update
     * @throws IllegalStateException
     */
    private void checkAndSetState(State expected, State update) throws IllegalStateException {
    	if (!state.compareAndSet(expected, update)) {
			throw new IllegalStateException(String.format("The current state is [%s] but must be [%s]", state.get(), expected));
		}
    }
    
    /**
     * Registers all the consumers.
     */
    private void registerConsumers() {
    	if (state.get() == State.CONNECTING) {
    		consumerRegistrations.offer(reactor.on(Selectors.$(Tuple.of(clientId, "stream:features", "http://etherx.jabber.org/streams")), streamFeaturesConsumer));

    		consumerRegistrations.offer(reactor.on(Selectors.$(Tuple.of(clientId, "proceed", "urn:ietf:params:xml:ns:xmpp-tls")), tlsResponseConsumer));
    		
    		consumerRegistrations.offer(reactor.on(Selectors.$(Tuple.of(clientId, "success", "urn:ietf:params:xml:ns:xmpp-sasl")), authResultConsumer));
    		consumerRegistrations.offer(reactor.on(Selectors.$(Tuple.of(clientId, "failure", "urn:ietf:params:xml:ns:xmpp-sasl")), authResultConsumer));

    		consumerRegistrations.offer(reactor.on(Selectors.$(Tuple.of(clientId, "iq", "jabber:client")), iqResultConsumer));
    		
    		consumerRegistrations.offer(reactor.on(Selectors.$(Tuple.of(clientId, "bind")), iqBindResultConsumer));
    		consumerRegistrations.offer(reactor.on(Selectors.$(Tuple.of(clientId, "session")), iqSessionResultConsumer));
    	}
    }
    
    /**
     * Unregisters all consumers.
     */
    private void unregisterConsumers() {
    	if (state.get() == State.DISCONNECTING) {
    		Registration<?> registration = null;
    		
    		while ((registration = consumerRegistrations.poll()) != null) {
    			registration.cancel();
    		}
    	}
    }
    
    /**
     * Performs auth.
     */
    private void performAuth() {
    	byte[] authToken = ("\0" + username + "\0" + password).getBytes(StandardCharsets.UTF_8);
		
		Element auth = new Element("auth", "urn:ietf:params:xml:ns:xmpp-sasl");
		auth.setAttribute("mechanism", "PLAIN");
		auth.setText(Base64.encode(channel.get().alloc().buffer().writeBytes(authToken)).toString(StandardCharsets.UTF_8));
		
		channel.get().writeAndFlush(auth);
    }
    
    /**
     * Handles stream features
     */
    private final Consumer<Event<Element>> streamFeaturesConsumer = new Consumer<Event<Element>>() {
		public void accept(Event<Element> event) {
			Element features = event.getData();
			
			Element startTls = features.getChild("starttls", Namespace.getNamespace("urn:ietf:params:xml:ns:xmpp-tls"));
			
			if (startTls != null && startTls.getChild("required", startTls.getNamespace()) != null) {
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
							
							KixmppCodec.sendXmppStreamRootStart(channel.get(), domain);
						} else {
							deferredLogin.accept(new KixmppAuthException("tls failed"));
						}
					}
				});
				channel.get().pipeline().addAfter("loggingHandler", "sslHandler", handler);
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
					jid = bind.getChildText("jid");
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

				// no need to keep reference around
				deferredLogin = null;
			} else {
				// fail
				deferredLogin.accept(new KixmppAuthException(new XMLOutputter().outputString(iqResponse)));
			}
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
			ch.pipeline().addLast("loggingHandler", new LoggingHandler(LogLevel.DEBUG));
			ch.pipeline().addLast("secondLoggingHandler", new LoggingHandler(LogLevel.DEBUG));
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
			Element stanza = (Element)msg;

			System.out.println(stanza.getName());
			
			reactor.notify(Tuple.of(clientId, stanza.getQualifiedName(), stanza.getNamespaceURI()), Event.wrap(stanza));
		}
		
		@Override
		public void channelInactive(ChannelHandlerContext ctx) throws Exception {
			KixmppClient.this.disconnect();
		}
	}
}
