package com.kixeye.kixmpp.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.Collection;
import java.util.Collections;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.jdom2.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.Environment;
import reactor.core.Reactor;
import reactor.core.composable.Deferred;
import reactor.core.composable.Promise;
import reactor.core.composable.spec.Promises;
import reactor.core.spec.Reactors;
import reactor.event.Event;
import reactor.tuple.Tuple;

import com.kixeye.kixmpp.KixmppCodec;
import com.kixeye.kixmpp.KixmppStreamEnd;
import com.kixeye.kixmpp.KixmppStreamStart;
import com.kixeye.kixmpp.server.module.KixmppModule;

/**
 * A XMPP server.
 * 
 * @author ebahtijaragic
 */
public class KixmppServer implements AutoCloseable {
	private static final Logger logger = LoggerFactory.getLogger(KixmppServer.class);
	
	public static final int DEFAULT_PORT = 5222;
	
    private final ConcurrentHashMap<KixmppServerOption<?>, Object> serverOptions = new ConcurrentHashMap<KixmppServerOption<?>, Object>();
    
	private final ServerBootstrap bootstrap;
	
	private final Environment environment;
	private final Reactor reactor;

	private final KixmppHandlerRegistry handlerRegistry;
	
	private final SslContext sslContext;

	private final Set<String> modulesToRegister = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
	private final ConcurrentHashMap<String, KixmppModule> modules = new ConcurrentHashMap<>();
	
	private final AtomicReference<ChannelFuture> channelFuture = new AtomicReference<>();
	private final AtomicReference<Channel> channel = new AtomicReference<>();
	private AtomicReference<State> state = new AtomicReference<>(State.STOPPED);
	private static enum State {
		STARTING,
		STARTED,
		
		STOPPING,
		STOPPED
	}
	
	/**
	 * Creates a new {@link KixmppServer} with the given ssl engine.
	 * 
	 * @param sslContext
	 */
	public KixmppServer(SslContext sslContext) {
		this(new NioEventLoopGroup(), new NioEventLoopGroup(), new Environment(), Environment.WORK_QUEUE, sslContext);
	}
	
	/**
	 * Creates a new {@link KixmppClient}.
	 * 
	 * @param workerGroup
	 * @param bossGroup
	 * @param environment
	 * @param dispatcher
	 * @param sslContext
	 */
	public KixmppServer(EventLoopGroup workerGroup, EventLoopGroup bossGroup, Environment environment, String dispatcher, SslContext sslContext) {
		this(workerGroup, bossGroup, environment, Reactors.reactor(environment, dispatcher), sslContext);
	}
	
	/**
	 * Creates a new {@link KixmppClient}.
	 * 
	 * @param workerGroup
	 * @param environment
	 * @param reactor
	 * @param sslContext
	 */
	public KixmppServer(EventLoopGroup workerGroup, EventLoopGroup bossGroup, Environment environment, Reactor reactor, SslContext sslContext) {
		bootstrap = new ServerBootstrap()
			.group(bossGroup, workerGroup)
			.channel(NioServerSocketChannel.class)
			.childHandler(new ChannelInitializer<SocketChannel>() {
				protected void initChannel(SocketChannel ch) throws Exception {
					ch.pipeline().addLast(new KixmppCodec());
					ch.pipeline().addLast(new KixmppServerMessageHandler());
				}
			});

		this.handlerRegistry = new KixmppHandlerRegistry(reactor);
		this.environment = environment;
		this.reactor = reactor;
		this.sslContext = sslContext;
	}
	
	/**
	 * Starts the server.
	 * 
	 * @throws Exception
	 */
	public Promise<KixmppServer> start() throws Exception {
		return start(DEFAULT_PORT);
	}
	
	/**
	 * Starts the server.
	 * 
	 * @param port
	 * @throws Exception
	 */
	public Promise<KixmppServer> start(final int port) throws Exception {
		checkAndSetState(State.STARTING, State.STOPPED);
		
		logger.info("Starting Kixmpp Server on port [{}]...", port);

		// register all modules
		for (String moduleClassName : modulesToRegister) {
			installModule(moduleClassName);
		}
		
		final Deferred<KixmppServer, Promise<KixmppServer>> deferred = Promises.defer(environment, reactor.getDispatcher());

		channelFuture.set(bootstrap.bind(port));
		
		channelFuture.get().addListener(new GenericFutureListener<Future<? super Void>>() {
			@Override
			public void operationComplete(Future<? super Void> future) throws Exception {
				if (future.isSuccess()) {
					logger.info("Kixmpp Server listening on port [{}]", port);
					
					channel.set(channelFuture.get().channel());
					state.set(State.STARTED);
					channelFuture.set(null);
					deferred.accept(KixmppServer.this);
				} else {
					logger.error("Unable to start Kixmpp Server on port [{}]", port, future.cause());
					
					state.set(State.STOPPED);
					deferred.accept(future.cause());
				}
			}
		});
		
		return deferred.compose();
	}
	
	/**
	 * Stops the server.
	 * 
	 * @return
	 */
	public Promise<KixmppServer> stop() {
		checkAndSetState(State.STOPPING, State.STARTED, State.STARTING);

		logger.info("Stopping Kixmpp Server...");
		
		for (Entry<String, KixmppModule> entry : modules.entrySet()) {
			entry.getValue().uninstall(this);
		}
		
		final Deferred<KixmppServer, Promise<KixmppServer>> deferred = Promises.defer(environment, reactor.getDispatcher());

		ChannelFuture serverChannelFuture = channelFuture.get();
		
		if (serverChannelFuture != null) {
			serverChannelFuture.cancel(true);
		}
		
		Channel serverChannel = channel.get();
		
		if (serverChannel != null) {
			serverChannel.disconnect().addListener(new GenericFutureListener<Future<? super Void>>() {
				public void operationComplete(Future<? super Void> future) throws Exception {
					logger.info("Stopped Kixmpp Server");
					
					state.set(State.STOPPED);
					
					handlerRegistry.unregisterAll();
					
					deferred.accept(KixmppServer.this);
				}
			});
		} else {
			logger.info("Stopped Kixmpp Server");
			
			state.set(State.STOPPED);
			
			deferred.accept(KixmppServer.this);
		}
		
		return deferred.compose();
	}

	/**
	 * @see java.lang.AutoCloseable#close()
	 */
	public void close() throws Exception {
		stop();
	}
	
	/**
	 * Sets the servers {@link KixmppServerOption}s.
	 * 
	 * @param option
	 * @param value
	 * @return
	 */
    public <T> KixmppServer serverOption(KixmppServerOption<T> option, T value) {
    	if (value == null) {
    		serverOptions.remove(option);
    	} else {
    		serverOptions.put(option, value);
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
    public <T> KixmppServer channelOption(ChannelOption<T> option, T value) {
    	bootstrap.option(option, value);
    	return this;
    }
    
    /**
	 * Sets Netty child {@link ChannelOption}s.
	 * 
	 * @param option
	 * @param value
	 * @return
	 */
    public <T> KixmppServer childChannelOption(ChannelOption<T> option, T value) {
    	bootstrap.childOption(option, value);
    	return this;
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
    	if (!(state.get() == State.STARTED)) {
			throw new IllegalStateException(String.format("The current state is [%s] but must be [STARTED]", state.get()));
    	}
    	
    	T module = (T)modules.get(moduleClass.getName());
    	
    	if (module == null) {
    		module = (T)installModule(moduleClass.getName());
    	}

    	return module;
    }
    
    /**
     * Returns a collections of active modules.
     * 
     * @return
     */
    public Collection<KixmppModule> modules() {
    	return modules.values();
    }

    /**
     * Gets the handler registry.
     * 
     * @return
     */
    public KixmppHandlerRegistry getHandlerRegistry() {
    	return handlerRegistry;
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
	 * Message handler for the {@link KixmppServer}
	 * 
	 * @author ebahtijaragic
	 */
	private final class KixmppServerMessageHandler extends ChannelDuplexHandler {
		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
			if (msg instanceof Element) {
				Element stanza = (Element)msg;

				reactor.notify(Tuple.of(stanza.getQualifiedName(), stanza.getNamespaceURI()), Event.wrap(Tuple.of(ctx.channel(), stanza)));
			} else if (msg instanceof KixmppStreamStart) {
				KixmppStreamStart streamStart = (KixmppStreamStart)msg;

				reactor.notify(Tuple.of("stream:stream", "http://etherx.jabber.org/streams", "start"), Event.wrap(Tuple.of(ctx.channel(), streamStart)));
			} else if (msg instanceof KixmppStreamEnd) {
				KixmppStreamEnd streamEnd = (KixmppStreamEnd)msg;

				reactor.notify(Tuple.of("stream:stream", "http://etherx.jabber.org/streams", "end"), Event.wrap(Tuple.of(ctx.channel(), streamEnd)));
			} else {
				logger.error("Unknown message type [{}] from Channel [{}]", msg, ctx.channel());
			}
		}
		
		@Override
		public void channelActive(ChannelHandlerContext ctx) throws Exception {
			logger.debug("Channel [{}] connected.", ctx.channel());
		}
		
		@Override
		public void channelInactive(ChannelHandlerContext ctx) throws Exception {
			logger.debug("Channel [{}] disconnected.", ctx.channel());
		}
	}
}
