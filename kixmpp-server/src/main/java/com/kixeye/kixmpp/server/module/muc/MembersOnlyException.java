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

import com.kixeye.kixmpp.KixmppException;
import com.kixeye.kixmpp.KixmppJid;

/**
 * A user attempted to join a members-only MUC room for which they are not a member.
 *
 * @author dturner@kixeye.com
 */
public class MembersOnlyException extends KixmppException {
	private static final long serialVersionUID = 8605187410416156957L;

	public MembersOnlyException(MucRoom mucRoom, KixmppJid jid) {
        super(jid + " cannot join room " + mucRoom.getRoomJid() + " because they are not a member and the room is members only.");
    }
}
