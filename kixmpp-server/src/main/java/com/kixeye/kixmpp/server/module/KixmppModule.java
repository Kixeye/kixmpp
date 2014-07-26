package com.kixeye.kixmpp.server.module;

import java.util.List;

import org.jdom2.Element;

import com.kixeye.kixmpp.server.KixmppServer;

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
 * Defines a KixmppModule.
 * 
 * @author ebahtijaragic
 */
public interface KixmppModule {
	/**
	 * Installs the module.
	 * 
	 * @param server
	 */
	public void install(KixmppServer server);
	
	/**
	 * Uninstalls the module.
	 * 
	 * @param server
	 */
	public void uninstall(KixmppServer server);
	
	/**
	 * Gets a list of features added by this module.
	 * 
	 * @return
	 */
	public List<Element> getFeatures();
}
