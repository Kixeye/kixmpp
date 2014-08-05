package com.kixeye.kixmpp.tuple;

import java.util.Arrays;

/**
 * A tuple.
 * 
 * @author ebahtijaragic
 */
public class Tuple {
	private final Object[] values;

	protected Tuple(Object[] values) {
		this.values = values;
	}
	
	public static Tuple from(Object... values) {
		return new Tuple(values);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(values);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Tuple other = (Tuple) obj;
		if (!Arrays.equals(values, other.values))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "Tuple [values=" + Arrays.toString(values) + "]";
	}
}
