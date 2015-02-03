package com.kixeye.kixmpp.server.module.muc;

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
import com.kixeye.kixmpp.KixmppJid;
import org.jdom2.Element;

import java.util.Map;
import java.util.UUID;

public class DefaultMucRoomMessageHandler implements MucRoomMessageHandler{

	private Element createMessage(String id, KixmppJid from, KixmppJid to, String type, String bodyText) {
		Element message = new Element("message");

		message.setAttribute("to", to.getFullJid());
		message.setAttribute("from", from.getFullJid());
		message.setAttribute("type", type);
		message.setAttribute("id", id);

		Element body = new Element("body");
		body.addContent(bodyText);

		message.addContent(body);

		return message;
	}

	@Override
	public void handleMessage(Map<KixmppJid, MucRole> jidRoles, KixmppJid fromAddress, MucRoom mucRoom, String... messages) {
		for (MucRoom.Client client : mucRoom.getClients()) {
			for (String message : messages) {
				Element stanza = createMessage(UUID.randomUUID().toString(),
						fromAddress,
						client.getAddress(),
						"groupchat",
						message);
				client.getChannel().writeAndFlush(stanza);
			}
		}
	}
}
