package com.kixeye.kixmpp.date;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * Some helpful date utils.
 * 
 * @author ebahtijaragic
 */
public final class XmppDateUtils {
	private XmppDateUtils() { }

    private static final DateTimeFormatter xmppDateTimeFormatter = DateTimeFormat.forPattern("YYYY-MM-DD'T'hh:mm:ss[.sss]Z");
    
    /**
     * Format a long from epoch in UTC to a string.
     * 
     * @param dateTime
     * @return
     */
	public static String format(long dateTime) {
		return xmppDateTimeFormatter.print(dateTime);
	}
    
    /**
     * Format a DateTime to a string.
     * 
     * @param dateTime
     * @return
     */
	public static String format(DateTime dateTime) {
		return dateTime.toString(xmppDateTimeFormatter);
	}
	
	/**
     * Parse a string into a DateTime.
     * 
     * @param dateTime
     * @return
     */
	public static DateTime parse(String dateTime) {
		return DateTime.parse(dateTime, xmppDateTimeFormatter);
	}
}
