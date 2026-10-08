package dev.matanyah.bedrockcompat.bedrock.format;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockDependency;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockModule;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPack;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPackType;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPackSetting;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockSubpack;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockVersion;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class BedrockManifestParser {
	public BedrockPack parse(byte[] manifestBytes, Path source, String locationInSource, byte[] iconBytes) {
		return parse(manifestBytes, source, locationInSource, iconBytes, Map.of());
	}

	public BedrockPack parse(byte[] manifestBytes, Path source, String locationInSource, byte[] iconBytes,
		Map<String, String> translations) {
		JsonElement parsed = JsonParser.parseString(new String(manifestBytes, StandardCharsets.UTF_8));
		if (!parsed.isJsonObject()) {
			throw new IllegalArgumentException("manifest.json must contain a JSON object");
		}

		JsonObject root = parsed.getAsJsonObject();
		List<String> warnings = new ArrayList<>();
		int formatVersion = integer(root, "format_version", 0, warnings);
		if (formatVersion <= 0) warnings.add("Missing or invalid format_version");
		else if (formatVersion > 3) warnings.add("Manifest format_version " + formatVersion + " is newer than supported version 3");

		JsonObject header = object(root, "header");
		if (header == null) throw new IllegalArgumentException("manifest.json is missing its header object");

		String name = translated(string(header, "name", "Unnamed pack", warnings), translations);
		String description = translated(string(header, "description", "", warnings), translations);
		String uuid = string(header, "uuid", "", warnings);
		validateUuid(uuid, "header UUID", warnings);
		BedrockVersion version = version(header.get("version"), "header version", warnings);
		Optional<BedrockVersion> minimumEngineVersion = optionalVersion(
			header.get("min_engine_version"), "minimum engine version", warnings);

		List<BedrockModule> modules = parseModules(root.get("modules"), warnings);
		if (modules.isEmpty()) warnings.add("Manifest contains no valid modules");
		List<BedrockDependency> dependencies = parseDependencies(root.get("dependencies"), warnings);
		List<BedrockSubpack> subpacks = parseSubpacks(root.get("subpacks"), translations, warnings);
		List<BedrockPackSetting> settings = parseSettings(root.get("settings"), translations, warnings);

		return new BedrockPack(name, description, uuid, version, minimumEngineVersion, modules, dependencies, subpacks, settings,
			source, locationInSource, BedrockPackType.fromModules(modules), Optional.ofNullable(iconBytes), warnings);
	}

	private static List<BedrockSubpack> parseSubpacks(JsonElement element, Map<String, String> translations,
		List<String> warnings) {
		List<BedrockSubpack> subpacks = new ArrayList<>();
		if (element == null) return subpacks;
		if (!element.isJsonArray()) {
			warnings.add("subpacks is not an array");
			return subpacks;
		}
		for (int index = 0; index < element.getAsJsonArray().size(); index++) {
			JsonElement item = element.getAsJsonArray().get(index);
			if (!item.isJsonObject()) {
				warnings.add("Ignored non-object subpack at index " + index);
				continue;
			}
			JsonObject object = item.getAsJsonObject();
			String folder = string(object, "folder_name", "", warnings);
			if (!safeFolder(folder)) {
				warnings.add("Ignored subpack with unsafe folder_name: " + folder);
				continue;
			}
			String name = translated(string(object, "name", folder, warnings), translations);
			int tier = object.has("memory_performance_tier")
				? integer(object, "memory_performance_tier", 0, warnings)
				: integer(object, "memory_tier", 0, warnings);
			subpacks.add(new BedrockSubpack(folder, name, tier));
		}
		return subpacks;
	}

	private static List<BedrockPackSetting> parseSettings(JsonElement element, Map<String, String> translations,
		List<String> warnings) {
		List<BedrockPackSetting> settings = new ArrayList<>();
		if (element == null) return settings;
		if (!element.isJsonArray()) {
			warnings.add("settings is not an array");
			return settings;
		}
		for (int index = 0; index < element.getAsJsonArray().size(); index++) {
			JsonElement item = element.getAsJsonArray().get(index);
			if (!item.isJsonObject()) {
				warnings.add("Ignored non-object setting at index " + index);
				continue;
			}
			JsonObject object = item.getAsJsonObject();
			String type = string(object, "type", "", warnings);
			String text = translated(string(object, "text", "", null), translations);
			try {
				switch (type) {
					case "label" -> settings.add(new BedrockPackSetting.Label(text));
					case "toggle" -> settings.add(new BedrockPackSetting.Toggle(
						string(object, "name", "", warnings), text, object.get("default").getAsBoolean()));
					case "slider" -> settings.add(new BedrockPackSetting.Slider(
						string(object, "name", "", warnings), text, object.get("min").getAsDouble(),
						object.get("max").getAsDouble(), object.get("step").getAsDouble(), object.get("default").getAsDouble()));
					case "multiselect" -> {
						BedrockPackSetting.Dropdown choices = parseDropdown(object, text, translations, warnings);
						List<String> defaults = new ArrayList<>();
						if (object.has("defaults")) for (JsonElement value : object.getAsJsonArray("defaults")) defaults.add(value.getAsString());
						settings.add(new BedrockPackSetting.Multiselect(choices.name(), text, choices.options(), defaults));
					}
					case "dropdown" -> settings.add(parseDropdown(object, text, translations, warnings));
					default -> warnings.add("Unsupported setting type at index " + index + ": " + type);
				}
			} catch (RuntimeException exception) {
				warnings.add("Ignored invalid " + type + " setting at index " + index);
			}
		}
		return settings;
	}

	private static BedrockPackSetting.Dropdown parseDropdown(JsonObject object, String text,
		Map<String, String> translations, List<String> warnings) {
		List<BedrockPackSetting.Option> options = new ArrayList<>();
		JsonArray values = object.getAsJsonArray("options");
		for (JsonElement item : values) {
			JsonObject option = item.getAsJsonObject();
			options.add(new BedrockPackSetting.Option(string(option, "name", "", warnings),
				translated(string(option, "text", "", null), translations)));
		}
		return new BedrockPackSetting.Dropdown(string(object, "name", "", warnings), text, options,
			string(object, "default", options.isEmpty() ? "" : options.getFirst().name(), warnings));
	}

	private static boolean safeFolder(String folder) {
		return !folder.isBlank() && !folder.equals(".") && !folder.equals("..")
			&& !folder.contains("/") && !folder.contains("\\") && !folder.contains(":");
	}

	private static List<BedrockModule> parseModules(JsonElement element, List<String> warnings) {
		List<BedrockModule> modules = new ArrayList<>();
		if (element == null) return modules;
		if (!element.isJsonArray()) {
			warnings.add("modules is not an array");
			return modules;
		}
		JsonArray array = element.getAsJsonArray();
		for (int index = 0; index < array.size(); index++) {
			JsonElement item = array.get(index);
			if (!item.isJsonObject()) {
				warnings.add("Ignored non-object module at index " + index);
				continue;
			}
			JsonObject module = item.getAsJsonObject();
			String uuid = string(module, "uuid", "", warnings);
			validateUuid(uuid, "module UUID", warnings);
			modules.add(new BedrockModule(
				uuid,
				string(module, "type", "unknown", warnings),
				version(module.get("version"), "module version", warnings),
				string(module, "description", "", null)
			));
		}
		return modules;
	}

	private static List<BedrockDependency> parseDependencies(JsonElement element, List<String> warnings) {
		List<BedrockDependency> dependencies = new ArrayList<>();
		if (element == null) return dependencies;
		if (!element.isJsonArray()) {
			warnings.add("dependencies is not an array");
			return dependencies;
		}
		JsonArray array = element.getAsJsonArray();
		for (int index = 0; index < array.size(); index++) {
			JsonElement item = array.get(index);
			if (!item.isJsonObject()) {
				warnings.add("Ignored non-object dependency at index " + index);
				continue;
			}
			JsonObject dependency = item.getAsJsonObject();
			String uuid = string(dependency, "uuid", "", null);
			String moduleName = string(dependency, "module_name", "", null);
			if (uuid.isBlank() && moduleName.isBlank()) warnings.add("Dependency at index " + index + " has no UUID or module_name");
			if (!uuid.isBlank()) validateUuid(uuid, "dependency UUID", warnings);
			dependencies.add(new BedrockDependency(uuid, moduleName,
				version(dependency.get("version"), "dependency version", warnings)));
		}
		return dependencies;
	}

	private static BedrockVersion version(JsonElement element, String label, List<String> warnings) {
		return optionalVersion(element, label, warnings).orElseGet(BedrockVersion::unknown);
	}

	private static Optional<BedrockVersion> optionalVersion(JsonElement element, String label, List<String> warnings) {
		if (element == null || element.isJsonNull()) return Optional.empty();
		List<Integer> components = new ArrayList<>();
		try {
			if (element.isJsonArray()) {
				for (JsonElement part : element.getAsJsonArray()) components.add(part.getAsInt());
			} else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
				for (String part : element.getAsString().split("\\.")) components.add(Integer.parseInt(part));
			} else {
				throw new IllegalArgumentException();
			}
		} catch (RuntimeException exception) {
			warnings.add("Invalid " + label);
			return Optional.empty();
		}
		if (components.isEmpty() || components.stream().anyMatch(value -> value < 0)) {
			warnings.add("Invalid " + label);
			return Optional.empty();
		}
		return Optional.of(new BedrockVersion(components));
	}

	private static JsonObject object(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
	}

	private static String string(JsonObject object, String key, String fallback, List<String> warnings) {
		JsonElement value = object.get(key);
		if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) return value.getAsString();
		if (warnings != null) warnings.add("Missing or invalid " + key);
		return fallback;
	}

	private static int integer(JsonObject object, String key, int fallback, List<String> warnings) {
		try {
			JsonElement value = object.get(key);
			return value == null ? fallback : value.getAsInt();
		} catch (RuntimeException exception) {
			warnings.add("Invalid " + key);
			return fallback;
		}
	}

	private static void validateUuid(String value, String label, List<String> warnings) {
		if (value.isBlank()) return;
		try {
			UUID.fromString(value);
		} catch (IllegalArgumentException exception) {
			warnings.add("Invalid " + label + ": " + value);
		}
	}

	private static String translated(String value, Map<String, String> translations) {
		return translations.getOrDefault(value, value);
	}
}
