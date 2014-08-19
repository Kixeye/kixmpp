package com.kixeye.kixmpp.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;

import org.fusesource.hawtdispatch.Task;
import org.jdom2.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.Striped;
import com.kixeye.kixmpp.KixmppCodec;
import com.kixeye.kixmpp.KixmppJid;
import com.kixeye.kixmpp.KixmppStanzaRejectedException;
import com.kixeye.kixmpp.KixmppStreamEnd;
import com.kixeye.kixmpp.KixmppStreamStart;
import com.kixeye.kixmpp.handler.KixmppEventEngine;
import com.kixeye.kixmpp.interceptor.KixmppStanzaInterceptor;
import com.kixeye.kixmpp.p2p.ClusterClient;
import com.kixeye.kixmpp.p2p.discovery.ConstNodeDiscovery;
import com.kixeye.kixmpp.p2p.discovery.NodeDiscovery;
import com.kixeye.kixmpp.p2p.listener.ClusterListener;
import com.kixeye.kixmpp.p2p.node.NodeId;
import com.kixeye.kixmpp.server.cluster.mapreduce.MapReduceTracker;
import com.kixeye.kixmpp.server.cluster.message.ClusterTask;
import com.kixeye.kixmpp.server.cluster.message.MapReduceRequest;
import com.kixeye.kixmpp.server.cluster.message.MapReduceResponse;
import com.kixeye.kixmpp.server.cluster.message.RoomBroadcastTask;
import com.kixeye.kixmpp.server.cluster.message.RoomTask;
import com.kixeye.kixmpp.server.module.KixmppServerModule;
import com.kixeye.kixmpp.server.module.auth.SaslKixmppServerModule;
import com.kixeye.kixmpp.server.module.bind.BindKixmppServerModule;
import com.kixeye.kixmpp.server.module.disco.DiscoKixmppServerModule;
import com.kixeye.kixmpp.server.module.features.FeaturesKixmppServerModule;
import com.kixeye.kixmpp.server.module.muc.MucKixmppServerModule;
import com.kixeye.kixmpp.server.module.muc.MucRoom;
import com.kixeye.kixmpp.server.module.muc.MucService;
import com.kixeye.kixmpp.server.module.presence.PresenceKixmppServerModule;
import com.kixeye.kixmpp.server.module.roster.RosterKixmppServerModule;
import com.kixeye.kixmpp.server.module.session.SessionKixmppServerModule;
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

/**
 * A XMPP server.
 * 
 * @author ebahtijaragic
 */
public class KixmppServer implements AutoCloseable, ClusterListener {
	private static final Logger logger = LoggerFactory.getLogger(KixmppServer.class);
	
	public static final InetSocketAddress DEFAULT_SOCKET_ADDRESS = new InetSocketAddress(5222);
    public static final InetSocketAddress DEFAULT_CLUSTER_ADDRESS = new InetSocketAddress(8100);
    public static final int CUSTOM_MESSAGE_START = 16;

	private final InetSocketAddress bindAddress;
	private final String domain;
	
	private final ServerBootstrap bootstrap;
	
	private final KixmppEventEngine eventEngine;
	
	private final Set<String> modulesToRegister = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
	private final ConcurrentHashMap<String, KixmppServerModule> modules = new ConcurrentHashMap<>();

	private final Set<KixmppStanzaInterceptor> interceptors = Collections.newSetFromMap(new ConcurrentHashMap<KixmppStanzaInterceptor, Boolean>());

	private final AtomicReference<ChannelFuture> channelFuture = new AtomicReference<>();
	private final AtomicReference<Channel> channel = new AtomicReference<>();
	private AtomicReference<State> state = new AtomicReference<>(State.STOPPED);
	
	private final DefaultChannelGroup channels;
	private final ConcurrentHashMap<KixmppJid, Channel> jidChannel = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Set<Channel>> usernameChannel = new ConcurrentHashMap<>();
	private final Striped<Lock> usernameChannelStripes = Striped.lock(Runtime.getRuntime().availableProcessors() * 4);

    private static enum State {
		STARTING,
		STARTED,
		
		STOPPING,
		STOPPED
	}

    private final ClusterClient cluster;
    private final MapReduceTracker mapReduce;
    private final ScheduledExecutorService scheduledExecutorService;

    /**
	 * Creates a new {@link KixmppServer} with the given ssl engine.
	 * 
	 * @param domain
	 */
	public KixmppServer(String domain) {
		this(new NioEventLoopGroup(), new NioEventLoopGroup(), new KixmppEventEngine(), DEFAULT_SOCKET_ADDRESS, domain, DEFAULT_CLUSTER_ADDRESS, new ConstNodeDiscovery() );
	}
	
	/**
	 * Creates a new {@link KixmppServer} with the given ssl engine.
	 * 
	 * @param bindAddress
	 * @param domain
	 */
	public KixmppServer(InetSocketAddress bindAddress, String domain) {
		this(new NioEventLoopGroup(), new NioEventLoopGroup(), new KixmppEventEngine(), bindAddress, domain, DEFAULT_CLUSTER_ADDRESS, new ConstNodeDiscovery());
	}
	
	/**
	 * Creates a new {@link KixmppServer} with the given ssl engine.
	 * 
	 * @param bindAddress
	 * @param domain
	 */
	public KixmppServer(InetSocketAddress bindAddress, String domain, InetSocketAddress clusterAddress, NodeDiscovery clusterDiscovery) {
		this(new NioEventLoopGroup(), new NioEventLoopGroup(), new KixmppEventEngine(), bindAddress, domain, clusterAddress, clusterDiscovery );
	}
	
	/**
	 * Creates a new {@link KixmppServer}.
	 * 
	 * @param workerGroup
	 * @param eventEngine
	 * @param bindAddress
	 * @param domain
	 */
	public KixmppServer(EventLoopGroup workerGroup, EventLoopGroup bossGroup, KixmppEventEngine eventEngine, InetSocketAddress bindAddress, String domain, InetSocketAddress clusterAddress, NodeDiscovery clusterDiscovery) {
		this.bootstrap = new ServerBootstrap()
			.group(bossGroup, workerGroup)
			.channel(NioServerSocketChannel.class)
			.childHandler(new ChannelInitializer<SocketChannel>() {
				protected void initChannel(SocketChannel ch) throws Exception {
					ch.pipeline().addLast(new KixmppCodec());
					ch.pipeline().addLast(new KixmppServerMessageHandler());
				}
			});

		this.scheduledExecutorService = Executors.newScheduledThreadPool( Runtime.getRuntime().availableProcessors() );
        this.cluster = new ClusterClient( this, clusterAddress.getHostName(), clusterAddress.getPort(), clusterDiscovery, 300000, scheduledExecutorService );
        this.cluster.getMessageRegistry().addCustomMessage(1, RoomBroadcastTask.class);
        this.mapReduce = new MapReduceTracker(this, scheduledExecutorService);
        this.channels = new DefaultChannelGroup("All Channels", GlobalEventExecutor.INSTANCE);

		this.bindAddress = bindAddress;
		this.domain = domain.toLowerCase();
		this.eventEngine = eventEngine;

		this.modulesToRegister.add(FeaturesKixmppServerModule.class.getName());
		this.modulesToRegister.add(SaslKixmppServerModule.class.getName());
		this.modulesToRegister.add(BindKixmppServerModule.class.getName());
		this.modulesToRegister.add(SessionKixmppServerModule.class.getName());
		this.modulesToRegister.add(PresenceKixmppServerModule.class.getName());
		this.modulesToRegister.add(MucKixmppServerModule.class.getName());
		this.modulesToRegister.add(RosterKixmppServerModule.class.getName());
		this.modulesToRegister.add(DiscoKixmppServerModule.class.getName());
	}
	
	/**
	 * Starts the server.
	 * 
	 * @throws Exception
	 */
	public ListenableFuture<KixmppServer> start() throws Exception {
		checkAndSetState(State.STARTING, State.STOPPED);
		
		logger.info("Starting Kixmpp Server on [{}]...", bindAddress);

		// register all modules
		for (String moduleClassName : modulesToRegister) {
			installModule(moduleClassName);
		}
		
		final SettableFuture<KixmppServer> responseFuture = SettableFuture.create();

		channelFuture.set(bootstrap.bind(bindAddress));

		channelFuture.get().addListener(new GenericFutureListener<Future<? super Void>>() {
			@Override
			public void operationComplete(Future<? super Void> future) throws Exception {
				if (future.isSuccess()) {
					logger.info("Kixmpp Server listening on [{}]", bindAddress);
					
					channel.set(channelFuture.get().channel());
					state.set(State.STARTED);
					channelFuture.set(null);
					responseFuture.set(KixmppServer.this);
				} else {
					logger.error("Unable to start Kixmpp Server on [{}]", bindAddress, future.cause());
					
					state.set(State.STOPPED);
					responseFuture.setException(future.cause());
				}
			}
		});
		
		return responseFuture;
	}
	
	/**
	 * Stops the server.
	 * 
	 * @return
	 */
	public ListenableFuture<KixmppServer> stop() {
		checkAndSetState(State.STOPPING, State.STARTED, State.STARTING);

		logger.info("Stopping Kixmpp Server...");

        // shutdown clustering
        cluster.shutdown();
        scheduledExecutorService.shutdown();
		
		for (Entry<String, KixmppServerModule> entry : modules.entrySet()) {
			entry.getValue().uninstall(this);
		}

		final SettableFuture<KixmppServer> responseFuture = SettableFuture.create();

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
					
					eventEngine.unregisterAll();
					
					responseFuture.set(KixmppServer.this);
				}
			});
		} else {
			logger.info("Stopped Kixmpp Server");
			
			state.set(State.STOPPED);

			responseFuture.set(KixmppServer.this);
		}
		
		return responseFuture;
	}

	/**
	 * @see java.lang.AutoCloseable#close()
	 */
	public void close() throws Exception {
		stop();
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
	public <T extends KixmppServerModule> T module(Class<T> moduleClass) {
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
    public Collection<KixmppServerModule> modules() {
    	return modules.values();
    }

    /**
     * Gets the event engine.
     * 
     * @return
     */
    public KixmppEventEngine getEventEngine() {
    	return eventEngine;
    }
    
    /**
	 * @return the bindAddress
	 */
	public InetSocketAddress getBindAddress() {
		return bindAddress;
	}

	/**
	 * @return the domain
	 */
	public String getDomain() {
		return domain;
	}

    /**
     * Adds a stanza interceptor.
     * 
     * @param interceptor
     */
    public boolean addInterceptor(KixmppStanzaInterceptor interceptor) {
    	return interceptors.add(interceptor);
    }
    
    /**
     * Removes a stanza interceptor.
     * 
     * @param interceptor
     */
    public boolean removeInterceptor(KixmppStanzaInterceptor interceptor) {
    	return interceptors.remove(interceptor);
    }
    
    /**
     * Gets the number of channels.
     * 
     * @return
     */
    public int getChannelCount() {
    	return channels.size();
    }
    
    /**
     * Gets a channel that is assigned to this JID.
     * 
     * @param jid
     * @return
     */
    public Channel getChannel(KixmppJid jid) {
    	return jidChannel.get(jid);
    }
    
    /**
     * Gets channel by username.
     * 
     * @param username
     * @return
     */
    @SuppressWarnings("unchecked")
	public Set<Channel> getChannels(String username) {
    	Set<Channel> channels = usernameChannel.get(username);
    	
    	if (channels != null) {
    		return Collections.unmodifiableSet(usernameChannel.get(username));
    	} else {
    		return Collections.EMPTY_SET;
    	}
    }
    
    /**
     * Adds a channel mapping.
     * 
     * @param jid
     * @param channel
     */
    public void addChannelMapping(KixmppJid jid, Channel channel) {
    	jidChannel.put(jid, channel);
    	
    	Lock lock = usernameChannelStripes.get(jid.getNode());
    	
    	try {
    		lock.lock();
    		
    		Set<Channel> channels = usernameChannel.get(jid.getNode());
    		
    		if (channels == null) {
    			channels = new HashSet<>();
    			channels.add(channel);
    		}
    		
        	usernameChannel.put(jid.getNode(), channels);
    	} finally {
    		lock.unlock();
    	}
    }
    
	/**
     * Tries to install module.
     * 
     * @param moduleClassName
     */
	private KixmppServerModule installModule(String moduleClassName) {
		KixmppServerModule module = null;
		
		try {
			module = (KixmppServerModule)Class.forName(moduleClassName).newInstance();
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
				
				boolean rejected = false;
				
				for (KixmppStanzaInterceptor interceptor : interceptors) {
					try {
						interceptor.interceptIncoming(ctx.channel(),(Element)msg);
					} catch (KixmppStanzaRejectedException e) {
						rejected = true;
						
						logger.debug("Incoming stanza interceptor [{}] threw an rejected exception.", interceptor, e);
					} catch (Exception e) {
						logger.error("Incoming stanza interceptor [{}] threw an exception.", interceptor, e);
					}
				}
				
				if (!rejected) {
					eventEngine.publishStanza(ctx.channel(), stanza);
				}
			} else if (msg instanceof KixmppStreamStart) {
				KixmppStreamStart streamStart = (KixmppStreamStart)msg;

				eventEngine.publishStreamStart(ctx.channel(), streamStart);
			} else if (msg instanceof KixmppStreamEnd) {
				KixmppStreamEnd streamEnd = (KixmppStreamEnd)msg;

				eventEngine.publishStreamEnd(ctx.channel(), streamEnd);
			} else {
				logger.error("Unknown message type [{}] from Channel [{}]", msg, ctx.channel());
			}
		}
		
		@Override
		public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
			boolean rejected = false;
			
			if (msg instanceof Element) {
				for (KixmppStanzaInterceptor interceptor : interceptors) {
					try {
						interceptor.interceptOutgoing(ctx.channel(), (Element)msg);
					} catch (KixmppStanzaRejectedException e) {
						rejected = true;
						
						logger.debug("Outgoing stanza interceptor [{}] threw an rejected exception.", interceptor, e);
					} catch (Exception e) {
						logger.error("Outgoing stanza interceptor [{}] threw an exception.", interceptor, e);
					}
				}
			}
			
			if (!rejected) {
				super.write(ctx, msg, promise);
			}
		}
		
		@Override
		public void channelActive(ChannelHandlerContext ctx) throws Exception {
			logger.debug("Channel [{}] connected.", ctx.channel());
			
			channels.add(ctx.channel());
			
			eventEngine.publishConnected(ctx.channel());
		}
		
		@Override
		public void channelInactive(ChannelHandlerContext ctx) throws Exception {
			logger.debug("Channel [{}] disconnected.", ctx.channel());
			
			channels.remove(ctx.channel());

			KixmppJid jid = ctx.channel().attr(BindKixmppServerModule.JID).get();
			
			if (jid != null) {
				jidChannel.remove(jid);
				
				Lock lock = usernameChannelStripes.get(jid.getNode());
				
				try {
					lock.lock();
					usernameChannel.remove(jid.getNode());
				} finally {
					lock.unlock();
				}
			}
			
			eventEngine.publishDisconnected(ctx.channel());
		}
		
		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
			logger.error("Unexpected exception.", cause);
			
			ctx.close();
		}
	}

    public ClusterClient getCluster() {
        return cluster;
    }

    public void sendMapReduceRequest(MapReduceRequest request) {
        mapReduce.sendRequest(request);
    }

    public void registerCustomMessage(int id, Class<?> clazz) {
        cluster.getMessageRegistry().addCustomMessage(CUSTOM_MESSAGE_START + id, clazz);
    }

    @Override
    public void onNodeJoin(ClusterClient cluster, NodeId nodeId) {
        logger.info("Node {} joined cluster", nodeId.toString());
    }

    @Override
    public void onNodeLeft(ClusterClient cluster, NodeId nodeId) {
        logger.info("Node {} left cluster", nodeId.toString());
    }

    @Override
    public void onMessage(ClusterClient cluster, NodeId senderId, Object message) {

        // inject server reference
        if (message instanceof ClusterTask) {
            ((ClusterTask) message).setKixmppServer(this);
        }

        if (message instanceof MapReduceRequest) {
            MapReduceRequest request = (MapReduceRequest) message;
            request.setSenderId(senderId);
            getEventEngine().publishTask(request.getTargetJID(),request);
        } else if (message instanceof MapReduceResponse) {
            MapReduceResponse response = (MapReduceResponse) message;
            mapReduce.processResponse(response);
        } else  if (message instanceof RoomTask) {
            RoomTask roomTask = (RoomTask) message;
            MucService service = module(MucKixmppServerModule.class).getService(roomTask.getServiceSubDomain());
            if (service == null) {
                return;
            }
            MucRoom room = service.getRoom(roomTask.getRoomId());
            if (room == null) {
                return;
            }
            roomTask.setRoom(room);
            getEventEngine().publishTask(room.getRoomJid(),roomTask);
        } else if (message instanceof Task) {
            Task task = (Task) message;
            task.run();
        }
    }
}
