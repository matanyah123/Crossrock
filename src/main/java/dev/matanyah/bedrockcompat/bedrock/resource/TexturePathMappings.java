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
import java.util.List;
import java.util.Map;

final class TexturePathMappings {
	private static final String RESOURCE = "/bedrock_compat/mappings/geyser_pack_converter_textures.json";
	private static final Map<String, List<String>> BEDROCK_TO_JAVA = load();

	private TexturePathMappings() {
	}

	static List<String> map(String bedrockPath) {
		// Standard Bedrock armor atlases share Java's humanoid UV layout. Do not
		// alias leather: its dye masks need conversion, not a filename-only copy.
		for (String material : List.of("chain", "diamond", "gold", "iron", "netherite")) {
			String javaMaterial = material.equals("chain") ? "chainmail" : material;
			for (int layer = 1; layer <= 2; layer++) {
				if (bedrockPath.equals("textures/models/armor/" + material + "_" + layer + ".png")) {
					return List.of("textures/entity/equipment/"
						+ (layer == 1 ? "humanoid/" : "humanoid_leggings/") + javaMaterial + ".png");
				}
			}
		}
		List<String> mapped = BEDROCK_TO_JAVA.get(bedrockPath);
		if (mapped != null) return mapped;
		if (bedrockPath.startsWith("textures/blocks/")) {
			return List.of("textures/block/" + bedrockPath.substring("textures/blocks/".length()));
		}
		if (bedrockPath.startsWith("textures/items/")) {
			return List.of("textures/item/" + bedrockPath.substring("textures/items/".length()));
		}
		// Most entity, armor, particle, painting, environment, map, and misc paths
		// intentionally retain their Java-relative layout. Only return this fallback
		// for PNGs so unsupported Bedrock metadata never becomes a Java resource.
		if (bedrockPath.startsWith("textures/") && bedrockPath.endsWith(".png")) {
			return List.of(bedrockPath);
		}
		return List.of();
	}

	static int mappingCount() {
		return BEDROCK_TO_JAVA.size();
	}

	private static Map<String, List<String>> load() {
		Map<String, List<String>> reverse = new LinkedHashMap<>();
		try (InputStream input = TexturePathMappings.class.getResourceAsStream(RESOURCE)) {
			if (input == null) throw new IOException("Missing bundled mapping " + RESOURCE);
			JsonObject root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
			indexSection(root.getAsJsonObject("block"), "textures/blocks/", "textures/block/", reverse);
			indexSection(root.getAsJsonObject("item"), "textures/items/", "textures/item/", reverse);
			Map<String, List<String>> immutable = new LinkedHashMap<>();
			reverse.forEach((key, value) -> immutable.put(key, List.copyOf(value)));
			return Map.copyOf(immutable);
		} catch (IOException | RuntimeException exception) {
			BedrockCompat.LOGGER.error("Could not load bundled Bedrock texture path mappings", exception);
			return Map.of();
		}
	}

	private static void indexSection(JsonObject section, String bedrockPrefix, String javaPrefix,
		Map<String, List<String>> reverse) {
		if (section == null) return;
		for (var entry : section.entrySet()) {
			String javaPath = javaPrefix + entry.getKey() + ".png";
			JsonElement value = entry.getValue();
			if (value.isJsonPrimitive()) add(reverse, bedrockPrefix + value.getAsString() + ".png", javaPath);
			else if (value.isJsonArray()) {
				for (JsonElement alias : value.getAsJsonArray()) add(reverse, bedrockPrefix + alias.getAsString() + ".png", javaPath);
			}
		}
	}

	private static void add(Map<String, List<String>> reverse, String bedrockPath, String javaPath) {
		reverse.computeIfAbsent(bedrockPath, ignored -> new ArrayList<>()).add(javaPath);
	}
}
