package ru.rexchange.tools;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

public class DateUtils {
	private static HashMap<String, SimpleDateFormat> dateFormats = new HashMap<>();
	//todo попробовать расширить год до yyyy
	private static DateFormat tf = new SimpleDateFormat("yy.MM.dd HH:mm:ss.SSS");
	private static DateFormat tfm = new SimpleDateFormat("yy.MM.dd HH.mm");
	private static DateFormat df = new SimpleDateFormat("yy.MM.dd");

	private static SimpleDateFormat getDateFormat(String formatString) {
		SimpleDateFormat dateFormat = (SimpleDateFormat) dateFormats.get(formatString);
		if (dateFormat == null) {
			dateFormat = new SimpleDateFormat(formatString);
			dateFormats.put(formatString, dateFormat);
		}

		return dateFormat;
	}

	public static String formatDateTime(Date date, String formatString) {
		return date == null ? "" : getDateFormat(formatString).format(date);
	}

	public static String formatTime(long unixTime) {
		return tf.format(new Date(unixTime));
	}

	public static String formatTimeMin(long unixTime) {
		return tfm.format(new Date(unixTime));
	}

	public static String formatTimeMin(Date date) {
		return tfm.format(date);
	}

	public static String formatDate(long unixTime) {
		return df.format(new Date(unixTime));
	}

	public static String formatDate(Date time) {
		return df.format(time);
	}

	public static long currentTimeMillis() {
		return new Date().getTime();
	}

	public static Date getDateUTC(String date) throws ParseException {
		return org.apache.commons.lang3.time.DateUtils.parseDate(date + " UTC", "dd.MM.yyyy z");
	}

	public static Date parseDateTime(String date, String formatString) throws ParseException {
		return getDateFormat(formatString).parse(date);
	}

	public static Date parseDateTimeUTC(String date, String formatString) throws ParseException {
		return org.apache.commons.lang3.time.DateUtils.parseDate(date + " UTC", formatString + " z");
		//return org.apache.commons.lang3.time.DateUtils.parseDate(date, formatString);
	}

	public static Date addHours(Date date, int hours) {
		return new Date(date.getTime() + hours * TimeUtils.HOUR_IN_MS);
	}

	public static Date addPeriods(Date date, int periods, long periodLengthMs) {
		return new Date(date.getTime() + periods * periodLengthMs);
	}
}
