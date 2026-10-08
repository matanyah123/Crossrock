package dev.matanyah.bedrockcompat.bedrock.resource;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPack;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPackConfiguration;
import dev.matanyah.bedrockcompat.resource.GeneratedResourceSink;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Adapts Bedrock attachables to Punchy's resource-pack Model Parts API. Punchy remains an optional
 * dependency: these resources are inert when it is not installed.
 */
public final class PunchyItemTranslator implements BedrockResourceTranslator {
	private static final String ATTACHABLE_PREFIX = "attachables/";
	private static final String MODEL_PREFIX = "models/entity/";
	private static final String ANIMATION_PREFIX = "animations/";
	private final BedrockPackContentReader reader;

	public PunchyItemTranslator(BedrockPackContentReader reader) {
		this.reader = reader;
	}

	@Override
	public void translate(BedrockPack pack, BedrockPackConfiguration configuration, GeneratedResourceSink sink)
		throws IOException {
		Map<String, byte[]> content = new LinkedHashMap<>();
		reader.visitAll(pack, configuration.selectedSubpack(), (path, data) -> content.put(normalize(path), data));

		Map<String, JsonObject> geometries = indexGeometries(content);
		Map<String, JsonObject> animations = indexAnimations(content);
		for (Map.Entry<String, byte[]> entry : content.entrySet()) {
			if (!entry.getKey().startsWith(ATTACHABLE_PREFIX) || !entry.getKey().endsWith(".json")) continue;
			translateAttachable(entry.getValue(), geometries, animations, sink);
		}
	}

	private static void translateAttachable(byte[] data, Map<String, JsonObject> geometries,
		Map<String, JsonObject> animations, GeneratedResourceSink sink) {
		JsonObject description;
		try {
			JsonObject root = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonObject attachable = object(root, "minecraft:attachable");
			description = attachable == null ? null : object(attachable, "description");
		} catch (RuntimeException ignored) {
			return;
		}
		if (description == null) return;
		String itemId = string(description.get("identifier"));
		if (itemId == null || !validIdentifier(itemId)) return;
		String itemPath = itemId.substring(itemId.indexOf(':') + 1);
		String safeName = sanitize(itemId);

		JsonObject geometryAliases = object(description, "geometry");
		JsonObject geometry = preferredGeometry(geometryAliases, geometries);
		if (geometry == null) return;

		String base = "punchy/model_parts_items/";
		String geometryPath = base + "geo/bedrock_compat_" + safeName + ".geo.json";
		JsonObject geometryFile = new JsonObject();
		geometryFile.addProperty("format_version", "1.12.0");
		JsonArray geometryArray = new JsonArray();
		geometryArray.add(geometry.deepCopy());
		geometryFile.add("minecraft:geometry", geometryArray);
		sink.put(id(geometryPath), bytes(geometryFile));

		Map<String, String> aliases = stringMap(object(description, "animations"));
		List<AnimationCandidate> candidates = animationCandidates(description, aliases, animations, geometry);
		String animationPath = base + "animations/bedrock_compat_" + safeName + ".animation.json";
		if (!candidates.isEmpty()) {
			JsonObject animationFile = new JsonObject();
			animationFile.addProperty("format_version", "1.8.0");
			JsonObject clips = new JsonObject();
			for (AnimationCandidate candidate : candidates) {
				clips.add(candidate.id(), candidate.clip().deepCopy());
			}
			animationFile.add("animations", clips);
			sink.put(id(animationPath), bytes(animationFile));
		}

		JsonObject definition = new JsonObject();
		JsonArray items = new JsonArray();
		items.add(itemId);
		definition.add("items", items);
		definition.addProperty("geo", "minecraft:" + geometryPath);
		// Point Punchy at the exact path emitted by VanillaTextureTranslator, including pack-specific textures.
		definition.addProperty("texture", textureReference(description, itemPath));
		if (!candidates.isEmpty()) {
			JsonArray files = new JsonArray();
			files.add("minecraft:" + animationPath);
			definition.add("animations", files);
			JsonArray selectors = new JsonArray();
			JsonObject idle = new JsonObject();
			idle.addProperty("type", "idle");
			idle.addProperty("name", candidates.getFirst().id());
			selectors.add(idle);
			definition.add("modelPartsAnimation", selectors);
		}
		sink.put(id(base + "definitions/bedrock_compat_" + safeName + ".json"), bytes(definition));
	}

	private static Map<String, JsonObject> indexGeometries(Map<String, byte[]> content) {
		Map<String, JsonObject> result = new LinkedHashMap<>();
		for (Map.Entry<String, byte[]> entry : content.entrySet()) {
			if (!entry.getKey().startsWith(MODEL_PREFIX) || !entry.getKey().endsWith(".json")) continue;
			JsonObject root = parseObject(entry.getValue());
			if (root == null) continue;
			JsonArray modern = array(root, "minecraft:geometry");
			if (modern != null) {
				for (JsonElement element : modern) {
					if (!element.isJsonObject()) continue;
					JsonObject geometry = element.getAsJsonObject();
					JsonObject description = object(geometry, "description");
					String identifier = description == null ? null : string(description.get("identifier"));
					if (identifier != null) result.put(identifier, geometry);
				}
			}
			for (Map.Entry<String, JsonElement> geometryEntry : root.entrySet()) {
				if (geometryEntry.getKey().startsWith("geometry.") && geometryEntry.getValue().isJsonObject()) {
					result.put(geometryEntry.getKey(), geometryEntry.getValue().getAsJsonObject());
				}
			}
		}
		return result;
	}

	private static Map<String, JsonObject> indexAnimations(Map<String, byte[]> content) {
		Map<String, JsonObject> result = new LinkedHashMap<>();
		for (Map.Entry<String, byte[]> entry : content.entrySet()) {
			if (!entry.getKey().startsWith(ANIMATION_PREFIX) || !entry.getKey().endsWith(".json")) continue;
			JsonObject root = parseObject(entry.getValue());
			JsonObject clips = root == null ? null : object(root, "animations");
			if (clips == null) continue;
			for (Map.Entry<String, JsonElement> clip : clips.entrySet()) {
				if (clip.getValue().isJsonObject()) result.put(clip.getKey(), clip.getValue().getAsJsonObject());
			}
		}
		return result;
	}

	private static List<AnimationCandidate> animationCandidates(JsonObject description, Map<String, String> aliases,
		Map<String, JsonObject> animations, JsonObject geometry) {
		Set<String> geometryBones = boneNames(geometry);
		Set<String> firstPersonAliases = new LinkedHashSet<>();
		Set<String> unconditionalAliases = new LinkedHashSet<>();
		JsonObject scripts = object(description, "scripts");
		JsonArray animate = scripts == null ? null : array(scripts, "animate");
		if (animate != null) {
			for (JsonElement element : animate) {
				if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
					unconditionalAliases.add(element.getAsString());
				} else if (element.isJsonObject()) {
					for (Map.Entry<String, JsonElement> conditional : element.getAsJsonObject().entrySet()) {
						String condition = string(conditional.getValue());
						if (isPositiveFirstPerson(condition)) firstPersonAliases.add(conditional.getKey());
					}
				}
			}
		}
		List<AnimationCandidate> result = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		for (Map.Entry<String, String> alias : aliases.entrySet()) {
			JsonObject clip = animations.get(alias.getValue());
			if (clip == null || !seen.add(alias.getValue()) || !touchesModel(clip, geometryBones)) continue;
			int rank = firstPersonAliases.contains(alias.getKey()) ? 0
				: semanticAlias(alias.getKey()) ? 1 : unconditionalAliases.contains(alias.getKey()) ? 2 : 3;
			result.add(new AnimationCandidate(alias.getValue(), clip, rank));
		}
		result.sort(Comparator.comparingInt(AnimationCandidate::rank));
		return result;
	}

	private static boolean touchesModel(JsonObject clip, Set<String> geometryBones) {
		JsonObject bones = object(clip, "bones");
		if (bones == null || bones.isEmpty()) return false;
		for (String bone : bones.keySet()) if (geometryBones.contains(bone)) return true;
		return false;
	}

	private static Set<String> boneNames(JsonObject geometry) {
		Set<String> names = new LinkedHashSet<>();
		JsonArray bones = array(geometry, "bones");
		if (bones == null) return names;
		for (JsonElement element : bones) {
			if (!element.isJsonObject()) continue;
			String name = string(element.getAsJsonObject().get("name"));
			if (name != null) names.add(name);
		}
		return names;
	}

	private static boolean hasRenderableBones(JsonObject geometry) {
		JsonArray bones = array(geometry, "bones");
		if (bones == null) return false;
		for (JsonElement bone : bones) {
			if (bone.isJsonObject()) {
				JsonArray cubes = array(bone.getAsJsonObject(), "cubes");
				if (cubes != null && !cubes.isEmpty()) return true;
			}
		}
		return false;
	}

	private static JsonObject preferredGeometry(JsonObject aliases, Map<String, JsonObject> geometries) {
		if (aliases == null) return null;
		JsonObject best = null;
		int bestScore = Integer.MIN_VALUE;
		for (Map.Entry<String, JsonElement> alias : aliases.entrySet()) {
			String reference = string(alias.getValue());
			JsonObject candidate = reference == null ? null : geometries.get(reference);
			if (candidate == null || !hasRenderableBones(candidate)) continue;
			JsonObject description = object(candidate, "description");
			int width = integer(description, "texture_width", 64);
			int height = integer(description, "texture_height", 64);
			// 1x1 giant cubes in attachables are enchantment/glint helpers, not held-item models.
			int score = Math.min(100, renderableCubeCount(candidate));
			if (width <= 1 || height <= 1) score -= 1_000;
			if (alias.getKey().equals("default")) score += 200;
			if (score > bestScore) {
				best = candidate;
				bestScore = score;
			}
		}
		return bestScore < 0 ? null : best;
	}

	private static int renderableCubeCount(JsonObject geometry) {
		int count = 0;
		JsonArray bones = array(geometry, "bones");
		if (bones == null) return 0;
		for (JsonElement bone : bones) {
			if (!bone.isJsonObject()) continue;
			JsonArray cubes = array(bone.getAsJsonObject(), "cubes");
			if (cubes != null) count += cubes.size();
		}
		return count;
	}

	private static int integer(JsonObject object, String key, int fallback) {
		JsonElement value = object == null ? null : object.get(key);
		try {
			return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static boolean isPositiveFirstPerson(String condition) {
		if (condition == null) return false;
		String compact = condition.toLowerCase(Locale.ROOT).replace(" ", "");
		return (compact.contains("c.is_first_person") || compact.contains("context.is_first_person"))
			&& !compact.contains("!c.is_first_person") && !compact.contains("!context.is_first_person");
	}

	private static boolean semanticAlias(String alias) {
		String value = alias.toLowerCase(Locale.ROOT);
		return value.contains("first") || value.contains("hand") || value.contains("idle");
	}

	private static Map<String, String> stringMap(JsonObject object) {
		Map<String, String> result = new LinkedHashMap<>();
		if (object == null) return result;
		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			String value = string(entry.getValue());
			if (value != null) result.put(entry.getKey(), value);
		}
		return result;
	}

	private static String textureReference(JsonObject description, String itemPath) {
		JsonObject textures = object(description, "textures");
		String bedrockPath = textures == null ? null : string(textures.get("default"));
		if (bedrockPath == null && textures != null) {
			for (JsonElement value : textures.asMap().values()) {
				bedrockPath = string(value);
				if (bedrockPath != null) break;
			}
		}
		if (bedrockPath == null) return "minecraft:item/" + itemPath;
		bedrockPath = bedrockPath.replace('\\', '/').toLowerCase(Locale.ROOT);
		if (!bedrockPath.endsWith(".png")) bedrockPath += ".png";
		List<String> mapped = TexturePathMappings.map(bedrockPath);
		if (mapped.isEmpty()) return "minecraft:item/" + itemPath;
		String javaPath = mapped.getFirst();
		if (javaPath.startsWith("textures/")) javaPath = javaPath.substring("textures/".length());
		if (javaPath.endsWith(".png")) javaPath = javaPath.substring(0, javaPath.length() - ".png".length());
		return "minecraft:" + javaPath;
	}

	private static JsonObject parseObject(byte[] data) {
		try {
			JsonElement parsed = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
			return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private static boolean validIdentifier(String value) {
		return value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");
	}

	private static String sanitize(String value) {
		return value.toLowerCase(Locale.ROOT).replace(':', '_').replace('/', '_');
	}

	private static String normalize(String path) {
		return path.replace('\\', '/').toLowerCase(Locale.ROOT);
	}

	private static JsonObject object(JsonObject parent, String key) {
		JsonElement value = parent == null ? null : parent.get(key);
		return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
	}

	private static JsonArray array(JsonObject parent, String key) {
		JsonElement value = parent == null ? null : parent.get(key);
		return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
	}

	private static String string(JsonElement value) {
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
			? value.getAsString() : null;
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("minecraft", path);
	}

	private static byte[] bytes(JsonObject object) {
		return object.toString().getBytes(StandardCharsets.UTF_8);
	}

	private record AnimationCandidate(String id, JsonObject clip, int rank) {
	}
}
