package dev.matanyah.bedrockcompat.bedrock.storage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPackConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class BedrockPackConfigurationStore {
	public static final String OPTION_KEY = "bedrockCompatPackConfig";
	private final Path optionsFile;

	public BedrockPackConfigurationStore(Path optionsFile) {
		this.optionsFile = optionsFile;
	}

	public synchronized Map<String, BedrockPackConfiguration> load() throws IOException {
		if (!Files.isRegularFile(optionsFile)) return Map.of();
		Map<String, BedrockPackConfiguration> configurations = new LinkedHashMap<>();
		for (String line : Files.readAllLines(optionsFile, StandardCharsets.UTF_8)) {
			if (!line.startsWith(OPTION_KEY + ":")) continue;
			JsonElement parsed = JsonParser.parseString(line.substring(OPTION_KEY.length() + 1));
			if (!parsed.isJsonObject()) continue;
			for (Map.Entry<String, JsonElement> entry : parsed.getAsJsonObject().entrySet()) {
				if (!entry.getValue().isJsonObject()) continue;
				JsonObject object = entry.getValue().getAsJsonObject();
				Optional<String> subpack = optionalString(object.get("subpack"));
				Map<String, Object> settings = new LinkedHashMap<>();
				JsonElement values = object.get("settings");
				if (values != null && values.isJsonObject()) {
					for (Map.Entry<String, JsonElement> value : values.getAsJsonObject().entrySet()) {
						Object decoded = decode(value.getValue());
						if (decoded != null) settings.put(value.getKey(), decoded);
					}
				}
				configurations.put(entry.getKey(), new BedrockPackConfiguration(subpack, settings));
			}
		}
		return Map.copyOf(configurations);
	}

	public synchronized void save(Map<String, BedrockPackConfiguration> configurations) throws IOException {
		Path parent = optionsFile.getParent();
		if (parent != null) Files.createDirectories(parent);
		List<String> lines = Files.isRegularFile(optionsFile)
			? new ArrayList<>(Files.readAllLines(optionsFile, StandardCharsets.UTF_8)) : new ArrayList<>();
		lines.removeIf(line -> line.startsWith(OPTION_KEY + ":"));
		JsonObject root = new JsonObject();
		for (Map.Entry<String, BedrockPackConfiguration> entry : configurations.entrySet()) {
			JsonObject object = new JsonObject();
			entry.getValue().selectedSubpack().ifPresent(value -> object.addProperty("subpack", value));
			JsonObject settings = new JsonObject();
			entry.getValue().settingValues().forEach((name, value) -> add(settings, name, value));
			object.add("settings", settings);
			root.add(entry.getKey(), object);
		}
		lines.add(OPTION_KEY + ":" + root);
		Path temporary = optionsFile.resolveSibling(optionsFile.getFileName() + ".bedrock_compat_config.tmp");
		Files.write(temporary, lines, StandardCharsets.UTF_8);
		try {
			Files.move(temporary, optionsFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException exception) {
			Files.move(temporary, optionsFile, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static Optional<String> optionalString(JsonElement element) {
		return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
			? Optional.of(element.getAsString()) : Optional.empty();
	}

	private static Object decode(JsonElement element) {
		if (element.isJsonArray()) {
            List<String> values = new ArrayList<>();
            for (JsonElement item : element.getAsJsonArray()) {
                if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) return null;
                values.add(item.getAsString());
            }
            return List.copyOf(values);
        }
        if (!element.isJsonPrimitive()) return null;
		JsonPrimitive value = element.getAsJsonPrimitive();
		if (value.isBoolean()) return value.getAsBoolean();
		if (value.isNumber()) return value.getAsDouble();
		if (value.isString()) return value.getAsString();
		return null;
	}

	private static void add(JsonObject object, String name, Object value) {
		if (value instanceof List<?> list) {
            com.google.gson.JsonArray array = new com.google.gson.JsonArray();
            for (Object item : list) if (item instanceof String string) array.add(string);
            object.add(name, array);
        }
        else if (value instanceof Boolean bool) object.addProperty(name, bool);
		else if (value instanceof Number number) object.addProperty(name, number);
		else if (value instanceof String string) object.addProperty(name, string);
	}
}
