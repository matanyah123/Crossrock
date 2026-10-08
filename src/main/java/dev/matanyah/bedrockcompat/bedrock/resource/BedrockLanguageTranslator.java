package dev.matanyah.bedrockcompat.bedrock.resource;

import com.google.gson.JsonObject;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPack;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPackConfiguration;
import dev.matanyah.bedrockcompat.resource.GeneratedResourceSink;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Converts Bedrock key=value language files into Java's locale JSON format. */
public final class BedrockLanguageTranslator implements BedrockResourceTranslator {
	private final BedrockPackContentReader reader;

	public BedrockLanguageTranslator(BedrockPackContentReader reader) {
		this.reader = reader;
	}

	@Override
	public void translate(BedrockPack pack, BedrockPackConfiguration configuration, GeneratedResourceSink sink)
		throws IOException {
		reader.visitLanguages(pack, configuration.selectedSubpack(), (path, data) -> {
			String locale = localeFrom(path);
			if (locale == null) return;
			JsonObject translations = new JsonObject();
			for (String line : new String(data, StandardCharsets.UTF_8).split("\\R")) {
				String trimmed = line.strip();
				if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) continue;
				int separator = trimmed.indexOf('=');
				if (separator <= 0) continue;
				String key = trimmed.substring(0, separator).strip();
				String value = trimmed.substring(separator + 1).strip();
				if (!key.isEmpty()) translations.addProperty(key, value);
			}
			if (!translations.isEmpty()) {
				sink.put(Identifier.fromNamespaceAndPath("minecraft", "lang/" + locale + ".json"),
					translations.toString().getBytes(StandardCharsets.UTF_8));
			}
		});
	}

	private static String localeFrom(String path) {
		String normalized = path.replace('\\', '/');
		if (!normalized.startsWith("texts/") || !normalized.endsWith(".lang")) return null;
		String name = normalized.substring("texts/".length(), normalized.length() - ".lang".length());
		if (name.contains("/") || name.isBlank()) return null;
		return name.toLowerCase(Locale.ROOT).replace('-', '_');
	}
}
