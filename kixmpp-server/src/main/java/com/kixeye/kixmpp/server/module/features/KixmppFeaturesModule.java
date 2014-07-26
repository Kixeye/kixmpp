package com.kixeye.kixmpp.server.module.features;

import io.netty.channel.Channel;

import java.util.List;

import org.jdom2.Element;

import com.kixeye.kixmpp.KixmppCodec;
import com.kixeye.kixmpp.KixmppStreamEnd;
import com.kixeye.kixmpp.KixmppStreamStart;
import com.kixeye.kixmpp.server.KixmppServer;
import com.kixeye.kixmpp.server.KixmppStreamHandler;
import com.kixeye.kixmpp.server.module.KixmppModule;

/**
 * Displays features to the client.
 * 
 * @author ebahtijaragic
 */
public class KixmppFeaturesModule implements KixmppModule {
	private KixmppServer server;
	
	/**
	 * @see com.kixeye.kixmpp.server.module.KixmppModule#install(com.kixeye.kixmpp.server.KixmppServer)
	 */
	public void install(KixmppServer server) {
		this.server = server;
		
		this.server.getHandlerRegistry().register(SERVER_FEATURE_HANDLER);
	}

	/**
	 * @see com.kixeye.kixmpp.server.module.KixmppModule#uninstall(com.kixeye.kixmpp.server.KixmppServer)
	 */
	public void uninstall(KixmppServer server) {
		this.server.getHandlerRegistry().unregister(SERVER_FEATURE_HANDLER);
	}

	/**
	 * @see com.kixeye.kixmpp.server.module.KixmppModule#getFeatures()
	 */
	public List<Element> getFeatures() {
		return null;
	}
	
	private KixmppStreamHandler SERVER_FEATURE_HANDLER = new KixmppStreamHandler() {
		/**
		 * @see com.kixeye.kixmpp.server.KixmppStreamHandler#handleStreamStart(io.netty.channel.Channel, com.kixeye.kixmpp.KixmppStreamStart)
		 */
		public void handleStreamStart(Channel channel, KixmppStreamStart streamStart) {
			KixmppCodec.sendXmppStreamRootStart(channel, server.getDomain(), null);
			
			Element features = new Element("features", "stream", "http://etherx.jabber.org/streams");
			
			for (KixmppModule module : server.modules()) {
				List<Element> featuresList = module.getFeatures();
				
				if (featuresList != null) {
					for (Element featureElement : featuresList) {
						features.addContent(featureElement);
					}
				}
			}
			
			channel.writeAndFlush(features);
		}

		/**
		 * @see com.kixeye.kixmpp.server.KixmppStreamHandler#handleStreamEnd(io.netty.channel.Channel, com.kixeye.kixmpp.KixmppStreamEnd)
		 */
		public void handleStreamEnd(Channel channel, KixmppStreamEnd streamEnd) {
			KixmppCodec.sendXmppStreamRootStop(channel);
		}
	};
}
