package com.kixeye.kixmpp.server.module.auth;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authenticates users based on an in-memory map.
 * 
 * @author ebahtijaragic
 */
public class InMemoryAuthenticationService implements AuthenticationService {
	private Map<String, String> users = new ConcurrentHashMap<>();
	
	/**
	 * Adds a user.
	 */
	public void addUser(String username, String password) {
		users.put(username, password);
	}
	
	/**
	 * Removes a user.
	 */
	public void removeUser(String username) {
		users.remove(username);
	}
	
	/**
	 * @see com.kixeye.kixmpp.server.module.auth.AuthenticationService#authenticate(java.lang.String, java.lang.String)
	 */
	public boolean authenticate(String username, String password) {
		return password.equals(users.get(username));
	}
}
