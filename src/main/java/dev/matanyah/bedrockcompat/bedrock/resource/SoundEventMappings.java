package dev.matanyah.bedrockcompat.bedrock.resource;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.matanyah.bedrockcompat.BedrockCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Reverse index of Geyser's complete Minecraft 26.2 Java-to-Bedrock sound mapping. */
final class SoundEventMappings {
	private static final String RESOURCE = "/bedrock_compat/mappings/geyser_sounds_26_2.json";
	private static final Index INDEX = load();
	private static final Map<String, String> ENTITY_NAMES = Map.of(
		"evocation_illager", "evoker",
		"irongolem", "iron_golem",
		"polar_bear", "polar_bear",
		"polarbear", "polar_bear",
		"villager_v2", "villager",
		"zombie_pigman", "zombified_piglin"
	);

	private SoundEventMappings() {
	}

	static List<String> javaEvents(String bedrockEvent) {
		if (bedrockEvent == null || bedrockEvent.isBlank()) return List.of();
		String normalized = normalize(bedrockEvent);
		LinkedHashSet<String> result = new LinkedHashSet<>(INDEX.bedrockToJava().getOrDefault(normalized, List.of()));
		if (INDEX.javaEvents().contains(normalized)) result.add(normalized);
		return List.copyOf(result);
	}

	static List<String> entityEvents(String bedrockEntity, String event) {
		String entity = ENTITY_NAMES.getOrDefault(normalize(bedrockEntity), normalize(bedrockEntity));
		String normalizedEvent = normalize(event);
		LinkedHashSet<String> result = new LinkedHashSet<>();
		for (String suffix : eventAliases(normalizedEvent)) {
			// A shared Bedrock sound may map to several Java species. An entity-local
			// override must not mute/replace the other species' events as a side effect.
			for(String candidate:javaEvents("mob." + normalize(bedrockEntity) + "." + suffix))
				if(candidate.startsWith("entity."+entity+"."))result.add(candidate);
			String javaEvent = "entity." + entity + "." + javaEventSuffix(suffix);
			if (INDEX.javaEvents().contains(javaEvent)) result.add(javaEvent);
		}
		return List.copyOf(result);
	}

	static List<String> blockEvents(String group, String event) {
		String normalizedGroup = normalize(group);
		String normalizedEvent = normalize(event);
		LinkedHashSet<String> result = new LinkedHashSet<>();
		result.addAll(javaEvents((normalizedEvent.equals("break") ? "dig" : normalizedEvent) + "." + normalizedGroup));
		String javaEvent = "block." + normalizedGroup + "." + normalizedEvent;
		if (INDEX.javaEvents().contains(javaEvent)) result.add(javaEvent);
		return List.copyOf(result);
	}

	static int mappingCount() {
		return INDEX.javaEvents().size();
	}

	private static List<String> eventAliases(String event) {
		return switch (event) {
			case "ambient" -> List.of("ambient", "say", "idle");
			case "hurt" -> List.of("hurt", "hit");
			case "angry" -> List.of("angry", "mad");
			default -> List.of(event);
		};
	}

	private static String javaEventSuffix(String suffix) {
		return switch (suffix) {
			case "say", "idle" -> "ambient";
			case "hit" -> "hurt";
			default -> suffix;
		};
	}

	private static String normalize(String value) {
		String normalized = value.replace('\\', '/').toLowerCase(Locale.ROOT);
		if (normalized.startsWith("minecraft:")) normalized = normalized.substring("minecraft:".length());
		return normalized;
	}

	private static Index load() {
		Map<String, List<String>> reverse = new LinkedHashMap<>();
		Set<String> javaEvents = new LinkedHashSet<>();
		try (InputStream input = SoundEventMappings.class.getResourceAsStream(RESOURCE)) {
			if (input == null) throw new IOException("Missing bundled mapping " + RESOURCE);
			JsonObject root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
			for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
				String javaEvent = normalize(entry.getKey());
				javaEvents.add(javaEvent);
				if (!entry.getValue().isJsonObject()) continue;
				JsonElement playsound = entry.getValue().getAsJsonObject().get("playsound_mapping");
				if (playsound == null || !playsound.isJsonPrimitive() || !playsound.getAsJsonPrimitive().isString()
					|| playsound.getAsString().isBlank()) continue;
				reverse.computeIfAbsent(normalize(playsound.getAsString()), ignored -> new ArrayList<>()).add(javaEvent);
			}
			Map<String, List<String>> immutable = new LinkedHashMap<>();
			reverse.forEach((key, value) -> immutable.put(key, List.copyOf(value)));
			return new Index(Map.copyOf(immutable), Set.copyOf(javaEvents));
		} catch (IOException | RuntimeException exception) {
			BedrockCompat.LOGGER.error("Could not load bundled Bedrock sound event mappings", exception);
			return new Index(Map.of(), Set.of());
		}
	}

	private record Index(Map<String, List<String>> bedrockToJava, Set<String> javaEvents) {
	}
}
