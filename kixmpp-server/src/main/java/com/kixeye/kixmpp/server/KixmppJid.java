package com.kixeye.kixmpp.server;

/**
 * A JID.
 * 
 * @author ebahtijaragic
 */
public class KixmppJid {
	private final String node;
	private final String domain;
	private final String resource;
	
	/**
	 * @param node
	 * @param domain
	 */
	public KixmppJid(String node, String domain) {
		this(node, domain, domain);
	}

	/**
	 * @param node
	 * @param domain
	 * @param resource
	 */
	public KixmppJid(String node, String domain, String resource) {
		assert node != null && !node.isEmpty() : "Argument 'node' cannot be null or empty";
		assert domain != null && !domain.isEmpty() : "Argument 'domain' cannot be null or empty";

		this.node = node;
		this.domain = domain;
		this.resource = resource;
	}
	
	/**
	 * Creates a {@link KixmppJid} from a raw jid.
	 * 
	 * @param jid
	 * @return
	 */
	public static KixmppJid fromRawJid(String jid) {
		String[] jidSplit = jid.split("/", 2);
		String[] domainSplit = jidSplit[0].split("@", 2);
		
		return new KixmppJid(domainSplit[0], domainSplit[1], jidSplit.length == 2 ? jidSplit[1] : null);
	}

	/**
	 * Returns a clone of this JID with a different node.
	 * 
	 * @param node
	 * @return
	 */
	public KixmppJid withNode(String node) {
		return new KixmppJid(node, domain, resource);
	}

	/**
	 * Returns a clone of this JID with a different domain.
	 * 
	 * @param domain
	 * @return
	 */
	public KixmppJid withDomain(String domain) {
		return new KixmppJid(node, domain, resource);
	}

	/**
	 * Returns a clone of this JID with a different resource.
	 * 
	 * @param resource
	 * @return
	 */
	public KixmppJid withResource(String resource) {
		return new KixmppJid(node, domain, resource);
	}
	
	/**
	 * Returns a clone of this JID without the resource.
	 * 
	 * @return
	 */
	public KixmppJid withoutResource() {
		return new KixmppJid(node, domain);
	}

	/**
	 * @return the node
	 */
	public String getNode() {
		return node;
	}

	/**
	 * @return the domain
	 */
	public String getDomain() {
		return domain;
	}

	/**
	 * @return the resource
	 */
	public String getResource() {
		return resource;
	}
	
	/**
	 * Gets the base JID (node)@(domain).
	 * 
	 * @return
	 */
	public String getBaseJid() {
		return node + "@" + domain;
	}
	
	/**
	 * Gets the full JID (node)@(domain)[/(resource)].
	 * 
	 * @return
	 */
	public String getFullJid() {
		if (resource == null) {
			return node + "@" + domain;
		} else {
			return node + "@" + domain + "/" + resource;
		}
	}
	
	/**
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return getFullJid();
	}

	/**
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((domain == null) ? 0 : domain.hashCode());
		result = prime * result + ((node == null) ? 0 : node.hashCode());
		result = prime * result
				+ ((resource == null) ? 0 : resource.hashCode());
		return result;
	}

	/**
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		KixmppJid other = (KixmppJid) obj;
		if (domain == null) {
			if (other.domain != null)
				return false;
		} else if (!domain.equals(other.domain))
			return false;
		if (node == null) {
			if (other.node != null)
				return false;
		} else if (!node.equals(other.node))
			return false;
		if (resource == null) {
			if (other.resource != null)
				return false;
		} else if (!resource.equals(other.resource))
			return false;
		return true;
	}

}
