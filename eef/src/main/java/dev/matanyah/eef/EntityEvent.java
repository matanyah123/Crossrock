package dev.matanyah.eef;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.Optional;

public record EntityEvent(
	long gameTick,
	EntityEventKind kind,
	Entity source,
	Entity target,
	BlockPos position,
	Identifier detail,
	EventReliability reliability,
	Map<String, Object> context
) {
	public EntityEvent {
		context = context == null ? Map.of() : Map.copyOf(context);
	}

	/** Resolve a dotted resource-pack path from the event envelope or its nested context. */
	public Optional<Object> value(String path) {
		if (path == null || path.isBlank()) return Optional.empty();
		Object current;
		String[] parts = path.split("\\.");
		current = switch (parts[0]) {
			case "event" -> Map.of(
				"kind", kind.name().toLowerCase(java.util.Locale.ROOT),
				"tick", gameTick,
				"reliability", reliability.name(),
				"detail", detail == null ? "" : detail.toString());
			default -> context;
		};
		int start = parts[0].equals("event") ? 1 : 0;
		for (int index = start; index < parts.length; index++) {
			if (!(current instanceof Map<?, ?> map) || !map.containsKey(parts[index])) return Optional.empty();
			current = map.get(parts[index]);
		}
		return Optional.ofNullable(current);
	}

	public String entityKey() {
		if (source != null) return source.getUUID().toString();
		return value("self.id").map(String::valueOf).orElse("none");
	}
}
