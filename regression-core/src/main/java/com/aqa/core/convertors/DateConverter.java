package com.aqa.core.convertors;

import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static java.lang.String.valueOf;
import static java.time.ZoneId.SHORT_IDS;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.Objects.nonNull;

public class DateConverter {
    static String ADJUST_TIME_PATTERN = "(now[-+]{1}\\d{1,}[dmhwM]{1}(\\/[dmhwM]){0,1}(\\s{0,1}\\([A-Z]{3}\\)){0,1}(\\s{0,1}\\(.*?\\)){0,1})";
    private final static String TIME_COUNT_PATTERN = "\\d{1,}";
    private final static String TIME_PERIOD_PATTERN = "(?<=\\d)([dmhwM]((\\/[dmhwM]){0,1}))";
    private final static String TIME_ZONE_PATTERN = "(?<=\\()(UTC|EST|PST|ECT)";
    private final static String DATE_TIME_FORMAT = "(?<=\\()(?!UTC)(?!PST)(?!EST)(?!ECT)(.*?)(?=\\))";
    private final static String EPOCH_FORMAT = "epoch";
    private final static String AS_STRING_FORMAT = "(asString)";
    public final static String DEFAULT_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";

    public static ZonedDateTime adjustTime(final String timeToAdjust) {
        final String timeZone = getTimeZone(timeToAdjust);
        return nonNull(timeZone) ? adjustByPeriod(timeToAdjust, ZoneId.of(timeZone, SHORT_IDS))
                : adjustByPeriod(timeToAdjust, ZoneOffset.UTC);
    }

    public static boolean isDateTimeAdjustable(final String table) {
        return Pattern.compile(ADJUST_TIME_PATTERN).matcher(table).find();
    }

    public static String convertByDateTimeVariables(final String templateString) {
        return templateString.contains(AS_STRING_FORMAT) ? getAsString(templateString)
                : convertToDateTime(templateString);
    }

    public static String convertToDateTime(final String templateString) {
        final ZonedDateTime dateTime = adjustTime(templateString);
        final Matcher m = Pattern.compile(DATE_TIME_FORMAT).matcher(templateString);
        return m.find() ? replaceWithDateTime(templateString, validateDateTimeByFormat(m.group(0), dateTime))
                : replaceWithDateTime(templateString, dateTime.toString());

    }

    private static long getTimeCount(final String time) {
        Matcher m = Pattern.compile(TIME_COUNT_PATTERN).matcher(time);
        m.find();
        return Long.parseLong(m.group(0));
    }

    private static String getPeriod(final String time) {
        Matcher m = Pattern.compile(TIME_PERIOD_PATTERN).matcher(time);
        m.find();
        return m.group(0);
    }

    private static String getTimeZone(final String time) {
        Matcher m = Pattern.compile(TIME_ZONE_PATTERN).matcher(time);
        if (m.find()) {
            return m.group(0);
        }
        return null;
    }

    private static ZonedDateTime adjustByPeriod(final String timeToAdjust, final ZoneId timeZone) {
        if (timeToAdjust.contains("+")) {
            return adjustUpByPeriod(getPeriod(timeToAdjust), getTimeCount(timeToAdjust), ZonedDateTime.now(timeZone));
        } else if (timeToAdjust.contains("-")) {
            return adjustDownByPeriod(getPeriod(timeToAdjust), getTimeCount(timeToAdjust), ZonedDateTime.now(timeZone));
        }
        throw new IllegalArgumentException(format("%s is not adjustable as date", timeToAdjust));
    }

    private static ZonedDateTime adjustDownByPeriod(final String period, final long count, final ZonedDateTime currentTime) {
        switch (period) {
            case "h":
                return currentTime.minusHours(count);
            case "d":
                return currentTime.minusDays(count);
            case "m":
                return currentTime.minusMinutes(count);
            case "d/d":
                return currentTime.minusDays(count).truncatedTo(ChronoUnit.DAYS);
            case "d/h":
                return currentTime.minusDays(count).truncatedTo(ChronoUnit.HOURS);
            case "d/m":
                return currentTime.minusDays(count).truncatedTo(ChronoUnit.MINUTES);
            case "h/d":
                return currentTime.minusHours(count).truncatedTo(ChronoUnit.DAYS);
            case "h/h":
                return currentTime.minusHours(count).truncatedTo(ChronoUnit.HOURS);
            case "h/m":
                return currentTime.minusHours(count).truncatedTo(ChronoUnit.MINUTES);
            case "m/m":
                return currentTime.minusMinutes(count).truncatedTo(ChronoUnit.MINUTES);
            case "w/w":
                return currentTime.minusWeeks(count).with(ChronoField.DAY_OF_WEEK, ChronoField.DAY_OF_WEEK.range().getMinimum()).truncatedTo(ChronoUnit.DAYS);
            case "M/M":
                return currentTime.minusMonths(count).with(ChronoField.DAY_OF_MONTH, ChronoField.DAY_OF_MONTH.range().getMinimum()).truncatedTo(ChronoUnit.DAYS);
        }
        return currentTime;
    }

    private static ZonedDateTime adjustUpByPeriod(final String period, final long count, final ZonedDateTime currentTime) {
        switch (period) {
            case "h":
                return currentTime.plusHours(count);
            case "d":
                return currentTime.plusDays(count);
            case "m":
                return currentTime.plusMinutes(count);
            case "d/d":
                return currentTime.plusDays(count).truncatedTo(ChronoUnit.DAYS);
            case "d/h":
                return currentTime.plusDays(count).truncatedTo(ChronoUnit.HOURS);
            case "d/m":
                return currentTime.plusDays(count).truncatedTo(ChronoUnit.MINUTES);
            case "h/d":
                return currentTime.plusHours(count).truncatedTo(ChronoUnit.DAYS);
            case "h/h":
                return currentTime.plusHours(count).truncatedTo(ChronoUnit.HOURS);
            case "h/m":
                return currentTime.plusHours(count).truncatedTo(ChronoUnit.MINUTES);
            case "m/m":
                return currentTime.plusMinutes(count).truncatedTo(ChronoUnit.MINUTES);
            case "w/w":
                return currentTime.plusWeeks(count).with(ChronoField.DAY_OF_WEEK, ChronoField.DAY_OF_WEEK.range().getMinimum()).truncatedTo(ChronoUnit.DAYS);
            case "M/M":
                return currentTime.plusMonths(count).with(ChronoField.DAY_OF_MONTH, ChronoField.DAY_OF_MONTH.range().getMinimum()).truncatedTo(ChronoUnit.DAYS);
        }
        return currentTime;
    }

    private static String getAsString(final String templateString) {
        return templateString.replace(AS_STRING_FORMAT, "");
    }

    private static String validateDateTimeByFormat(final String format, final ZonedDateTime dateTime) {
        return format.equals(EPOCH_FORMAT) ? valueOf(dateTime.toInstant().toEpochMilli()) : dateTime.format(ofPattern(format));
    }

    private static String replaceWithDateTime(final String stringToReplace, final String dateTime) {
        return stringToReplace.replaceAll(ADJUST_TIME_PATTERN, dateTime);
    }

    public static String currentDateToString() {
        return currentDateToString(DEFAULT_FORMAT);
    }

    public static String currentDateToString(final String format) {
        return dateToString(format, Calendar.getInstance().getTime());
    }

    public static String dateToString(final String format, final Date date) {
        return new SimpleDateFormat(format).format(date);
    }
}
