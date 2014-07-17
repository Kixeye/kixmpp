package com.kixeye.kixmpp.client.module;

import com.kixeye.kixmpp.client.KixmppClient;

/**
 * Defines a KixmppModule.
 * 
 * @author ebahtijaragic
 */
public interface KixmppModule {
	/**
	 * Installs the module.
	 * 
	 * @param client
	 */
	public void install(KixmppClient client);
	
	/**
	 * Uninstalls the module.
	 * 
	 * @param client
	 */
	public void uninstall(KixmppClient client);
}
