package com.kixeye.kixmpp.server.module.muc;

import java.util.List;

import com.kixeye.kixmpp.KixmppJid;

/**
 * Provides access to history for {@link MucRoom}s
 * 
 * @author ebahtijaragic
 */
public interface MucHistoryProvider {
	/**
	 * Gets room history.
	 * 
	 * @param roomJid
	 * @param maxChars
	 * @param maxStanzas
	 * @param seconds
	 * @param since
	 * @return
	 */
	public List<MucHistory> getHistory(KixmppJid roomJid, Integer maxChars, Integer maxStanzas, Integer seconds, String since);
}
