package com.kixeye.kixmpp.server.module.auth;

/**
 * Authenticates users.
 * 
 * @author ebahtijaragic
 */
public interface AuthenticationService {
	/**
	 * Authenticates a user.
	 * 
	 * @param username
	 * @param password
	 * @return
	 */
	public boolean authenticate(String username, String password);
}
