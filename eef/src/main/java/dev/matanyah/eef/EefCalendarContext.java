package dev.matanyah.eef;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Map;

/** Stable local-calendar fields for declarative real-world date/time predicates. */
public final class EefCalendarContext {
	private EefCalendarContext() {}

	public static Map<String,Object> now() { return at(ZonedDateTime.now()); }
	public static Map<String,Object> at(ZonedDateTime calendar) {
		DayOfWeek weekday = calendar.getDayOfWeek();
		return Map.of(
			"year", calendar.getYear(), "month", calendar.getMonthValue(), "day", calendar.getDayOfMonth(),
			"day_of_week", weekday.name().toLowerCase(Locale.ROOT),
			"hour", calendar.getHour(), "minute", calendar.getMinute(),
			"weekend", weekday == DayOfWeek.SATURDAY || weekday == DayOfWeek.SUNDAY,
			"top_of_hour", calendar.getMinute() == 0);
	}
}
