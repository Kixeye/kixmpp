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

import com.kixeye.kixmpp.KixmppJid;
import com.kixeye.kixmpp.server.module.muc.MucRole;
import com.kixeye.kixmpp.server.module.muc.MucRoom;

public class RoomBroadcastPresenceTask extends RoomTask {

	private KixmppJid from;
	private MucRole role;
	private String type;

	public RoomBroadcastPresenceTask() {
	}

	public RoomBroadcastPresenceTask(MucRoom room, String gameId, String roomId, KixmppJid from, MucRole role, String type) {
		super(room, gameId, roomId);
		this.from = from;
		this.role = role;
		this.type = type;
	}

	@Override
	public void run() {
		getRoom().receivePresence(from, role, type);
	}

	/**
	 * @return the from
	 */
	public KixmppJid getFrom() {
		return from;
	}

	/**
	 * @param from the from to set
	 */
	public void setFrom(KixmppJid from) {
		this.from = from;
	}
}
