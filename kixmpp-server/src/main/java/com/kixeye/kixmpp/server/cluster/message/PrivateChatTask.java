package com.kixeye.kixmpp.server.cluster.message;

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

import java.util.Set;

import org.jdom2.Element;

import com.kixeye.kixmpp.KixmppJid;

/**
 * Distributes a private message.
 * 
 * @author ebahtijaragic
 */
public class PrivateChatTask extends ClusterTask {
    private String fromJid;
    private String toJid;
    private String body;

    public PrivateChatTask() {
    }

    public PrivateChatTask(KixmppJid fromJid, KixmppJid toJid, String body) {
        this.fromJid = fromJid.getFullJid();
        this.toJid = toJid.getFullJid();
        this.body = body;
    }

	/**
	 * @return the fromJid
	 */
	public String getFromJid() {
		return fromJid;
	}

	/**
	 * @param fromJid the fromJid to set
	 */
	public void setFromJid(String fromJid) {
		this.fromJid = fromJid;
	}

	/**
	 * @return the toJid
	 */
	public String getToJid() {
		return toJid;
	}

	/**
	 * @param toJid the toJid to set
	 */
	public void setToJid(String toJid) {
		this.toJid = toJid;
	}

	/**
	 * @return the body
	 */
	public String getBody() {
		return body;
	}

	/**
	 * @param body the body to set
	 */
	public void setBody(String body) {
		this.body = body;
	}

	/**
	 * @see org.fusesource.hawtdispatch.Task#run()
	 */
	public void run() {
		KixmppJid toJid = KixmppJid.fromRawJid(this.fromJid);
		KixmppJid fromJid = KixmppJid.fromRawJid(this.fromJid);
		
		Set<Channel> channels = getKixmppServer().getChannels(toJid.getNode());
		
		for (Channel channel : channels) {
			Element messageElement = new Element("message");
			messageElement.setAttribute("type", "chat");
			messageElement.setAttribute("from", fromJid.getFullJid());
			messageElement.setAttribute("to", toJid.getFullJid());
			
			Element bodyElement = new Element("body");
			bodyElement.setText(body);
			
			messageElement.addContent(bodyElement);
			
			channel.writeAndFlush(messageElement);
		}
	}
}
