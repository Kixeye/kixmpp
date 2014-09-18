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
import com.kixeye.kixmpp.server.module.muc.MucKixmppServerModule;
import com.kixeye.kixmpp.server.module.muc.MucRoomSettings;

public class CreateRoomTask extends ClusterTask {
	private String serviceName;
	private String roomName;
	
	private MucRoomSettings settings;
	
	private String ownerJid;
	private String ownerNickname;

	public CreateRoomTask() {
		
	}
	
	public CreateRoomTask(String serviceName, String roomName,
			MucRoomSettings settings, KixmppJid ownerJid, String ownerNickname) {
		this.serviceName = serviceName;
		this.roomName = roomName;
		this.settings = settings;
		this.ownerJid = ownerJid == null ? null : ownerJid.toString();
		this.ownerNickname = ownerNickname;
	}

	@Override
	public void run() {
		getKixmppServer().module(MucKixmppServerModule.class).addService(serviceName).addRoom(roomName, settings, ownerJid == null ? null : KixmppJid.fromRawJid(ownerJid), ownerNickname);
	}

}
