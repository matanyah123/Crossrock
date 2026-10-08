package dev.matanyah.eef;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Derives pack-visible day-cycle transition semantics from two absolute world times. */
public final class EefTimeTransition {
	private EefTimeTransition() {}

	public static Map<String,Object> between(long before, long after) {
		long beforeCycle = Math.floorMod(before, 24000L), afterCycle = Math.floorMod(after, 24000L);
		long delta = after - before, cycleDelta = Math.floorMod(afterCycle - beforeCycle, 24000L);
		boolean skipped = delta < 0 || delta > 100 || cycleDelta > 100;
		boolean beforeDay = beforeCycle < 12000, afterDay = afterCycle < 12000;
		Map<String,Object> value = new LinkedHashMap<>();
		value.put("before", before); value.put("after", after); value.put("delta", delta);
		value.put("before_time_of_day", beforeCycle); value.put("after_time_of_day", afterCycle);
		value.put("cycle_delta", cycleDelta); value.put("skipped", skipped);
		value.put("crossed_to_day", skipped && !beforeDay && afterDay);
		value.put("crossed_to_night", skipped && beforeDay && !afterDay);
		return Collections.unmodifiableMap(value);
	}
}
