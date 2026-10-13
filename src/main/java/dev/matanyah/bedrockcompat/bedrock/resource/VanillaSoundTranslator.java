package dev.matanyah.bedrockcompat.bedrock.resource;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPack;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPackConfiguration;
import dev.matanyah.bedrockcompat.resource.GeneratedResourceSink;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Translates Bedrock vanilla sound replacements into Java sound events and virtual OGG resources. */
public final class VanillaSoundTranslator implements BedrockResourceTranslator {
	/** Java fallbacks for a Bedrock level event with no imported sound override. */
	public static List<String> particleEventFallbacks(String event) { return SoundEventMappings.javaEvents(event); }
	private final BedrockPackContentReader reader;

	public VanillaSoundTranslator(BedrockPackContentReader reader) {
		this.reader = reader;
	}

	@Override
	public void translate(BedrockPack pack, BedrockPackConfiguration configuration, GeneratedResourceSink sink)
		throws IOException {
		Map<String, byte[]> content = new LinkedHashMap<>();
		reader.visitSounds(pack, configuration.selectedSubpack(), (path, data) ->
			content.put(path.replace('\\', '/').toLowerCase(Locale.ROOT), data));
		copyOggFiles(content, sink);
		JsonObject definitions = soundDefinitions(content);
		if (definitions == null) return;
        Map<String, JsonObject> converted = convertDefinitions(definitions);
        JsonObject nativeEvents=new JsonObject();
        converted.forEach((name,definition)->nativeEvents.add(
            dev.matanyah.bedrockcompat.bedrock.entity.NativeSoundEvents.id(name).getPath(),definition.deepCopy()));
        // Particle sound_effect names refer to level events, not necessarily definition IDs.
        byte[] metadata=content.get("sounds.json");
        if(metadata!=null)try {
            var root=JsonParser.parseString(new String(metadata,StandardCharsets.UTF_8)).getAsJsonObject();
            var events=object(object(root,"individual_event_sounds"),"events");
            if(events!=null)for(var event:events.entrySet())addReference(nativeEvents,
                List.of(dev.matanyah.bedrockcompat.bedrock.entity.NativeSoundEvents.id(event.getKey()).getPath()),event.getValue(),converted);
            var named=object(root,"individual_named_sounds");
            if(named!=null)for(var event:named.entrySet())addReference(nativeEvents,
                List.of(dev.matanyah.bedrockcompat.bedrock.entity.NativeSoundEvents.id(event.getKey()).getPath()),event.getValue(),converted);
        } catch(RuntimeException exception){throw new IOException("Invalid sounds.json",exception);}
        if(!nativeEvents.isEmpty())sink.put(Identifier.fromNamespaceAndPath("bedrock_native","sounds.json"),
            nativeEvents.toString().getBytes(StandardCharsets.UTF_8));
		JsonObject javaEvents = new JsonObject();
		for (Map.Entry<String, JsonObject> definition : converted.entrySet()) {
			for (String javaEvent : SoundEventMappings.javaEvents(definition.getKey())) {
				javaEvents.add(javaEvent, definition.getValue().deepCopy());
			}
		}
		applyContextualMappings(content.get("sounds.json"), converted, javaEvents);
		if (!javaEvents.isEmpty()) {
			sink.put(Identifier.fromNamespaceAndPath("minecraft", "sounds.json"),
				javaEvents.toString().getBytes(StandardCharsets.UTF_8));
		}
	}

	private static void copyOggFiles(Map<String, byte[]> content, GeneratedResourceSink sink) {
		for (Map.Entry<String, byte[]> entry : content.entrySet()) {
			String path = entry.getKey();
			if (!path.startsWith("sounds/") || !path.endsWith(".ogg") || !Identifier.isValidPath(path)) continue;
			sink.put(Identifier.fromNamespaceAndPath("minecraft", path), entry.getValue());
		}
	}

	private static JsonObject soundDefinitions(Map<String, byte[]> content) throws IOException {
		byte[] bytes = content.get("sounds/sound_definitions.json");
		if (bytes == null) bytes = content.get("sound_definitions.json");
		if (bytes == null) return null;
		try {
			JsonObject root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonObject definitions = root.getAsJsonObject("sound_definitions");
			return definitions == null ? root : definitions;
		} catch (RuntimeException exception) {
			throw new IOException("Invalid sound_definitions.json", exception);
		}
	}

	private static Map<String, JsonObject> convertDefinitions(JsonObject definitions) {
		Map<String, JsonObject> result = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : definitions.entrySet()) {
			if (!entry.getValue().isJsonObject()) continue;
			JsonElement sounds = entry.getValue().getAsJsonObject().get("sounds");
			if (sounds == null || !sounds.isJsonArray()) continue;
			JsonObject javaDefinition = new JsonObject();
			javaDefinition.addProperty("replace", true);
			var javaSounds = new com.google.gson.JsonArray();
			for (JsonElement sound : sounds.getAsJsonArray()) {
				JsonElement converted = convertSound(sound);
				if (converted != null) javaSounds.add(converted);
			}
			if (javaSounds.isEmpty()) continue;
			javaDefinition.add("sounds", javaSounds);
			result.put(normalizeEvent(entry.getKey()), javaDefinition);
		}
		return result;
	}

	private static JsonElement convertSound(JsonElement source) {
		if (source.isJsonPrimitive() && source.getAsJsonPrimitive().isString()) {
			String name = javaSoundName(source.getAsString());
			return name == null ? null : new com.google.gson.JsonPrimitive(name);
		}
		if (!source.isJsonObject()) return null;
		JsonObject bedrock = source.getAsJsonObject();
		JsonElement nameValue = bedrock.get("name");
		if (nameValue == null || !nameValue.isJsonPrimitive()) return null;
		String name = javaSoundName(nameValue.getAsString());
		if (name == null) return null;
		JsonObject javaSound = new JsonObject();
		javaSound.addProperty("name", name);
		copyNumber(bedrock, javaSound, "volume");
		copyNumber(bedrock, javaSound, "pitch");
		copyNumber(bedrock, javaSound, "weight");
		if (bedrock.has("stream") && bedrock.get("stream").isJsonPrimitive()) {
			javaSound.addProperty("stream", bedrock.get("stream").getAsBoolean());
		}
		return javaSound;
	}

	private static void copyNumber(JsonObject from, JsonObject to, String key) {
		JsonElement value = from.get(key);
		if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
			to.addProperty(key, value.getAsNumber());
		}
	}

	private static String javaSoundName(String rawName) {
		if (rawName == null || rawName.isBlank()) return null;
		String name = rawName.replace('\\', '/').toLowerCase(Locale.ROOT);
		int colon = name.indexOf(':');
		if (colon >= 0) name = name.substring(colon + 1);
		if (name.startsWith("sounds/")) name = name.substring("sounds/".length());
		if (name.endsWith(".ogg")) name = name.substring(0, name.length() - ".ogg".length());
		return Identifier.isValidPath(name) ? "minecraft:" + name : null;
	}

	private static void applyContextualMappings(byte[] metadata, Map<String, JsonObject> definitions,
		JsonObject output) throws IOException {
		if (metadata == null) return;
		try {
			JsonObject root = JsonParser.parseString(new String(metadata, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonObject individual = object(object(root, "individual_event_sounds"), "events");
			if (individual != null) {
				for (Map.Entry<String, JsonElement> event : individual.entrySet()) {
					addReference(output, SoundEventMappings.javaEvents(event.getKey()), event.getValue(), definitions);
				}
			}
			JsonObject entities = object(object(root, "entity_sounds"), "entities");
			if (entities != null) {
				for (Map.Entry<String, JsonElement> entity : entities.entrySet()) {
					if (!entity.getValue().isJsonObject()) continue;
					JsonObject events = object(entity.getValue().getAsJsonObject(), "events");
					if (events == null) continue;
					for (Map.Entry<String, JsonElement> event : events.entrySet()) {
						addReference(output, SoundEventMappings.entityEvents(entity.getKey(), event.getKey()),
							event.getValue(), definitions);
					}
				}
			}
			JsonObject blocks = object(object(root, "interactive_sounds"), "block_sounds");
			if (blocks != null) {
				for (Map.Entry<String, JsonElement> block : blocks.entrySet()) {
					if (!block.getValue().isJsonObject()) continue;
					JsonObject events = object(block.getValue().getAsJsonObject(), "events");
					if (events == null) continue;
					for (Map.Entry<String, JsonElement> event : events.entrySet()) {
						addReference(output, SoundEventMappings.blockEvents(block.getKey(), event.getKey()),
							event.getValue(), definitions);
					}
				}
			}
		} catch (RuntimeException exception) {
			throw new IOException("Invalid sounds.json", exception);
		}
	}

	private static void addReference(JsonObject output, List<String> javaEvents, JsonElement reference,
		Map<String, JsonObject> definitions) {
		String soundEvent = referencedEvent(reference);
		JsonObject definition = soundEvent == null ? null : definitions.get(normalizeEvent(soundEvent));
		JsonObject options=reference!=null&&reference.isJsonObject()?reference.getAsJsonObject():null;
		for (String javaEvent : javaEvents) {
			// Volume-only entries are meaningful: packs mute vanilla footsteps when
			// their animation timelines provide the correctly timed replacement.
			if(options!=null&&options.has("volume")&&options.get("volume").isJsonPrimitive()
				&&options.getAsJsonPrimitive("volume").isNumber()&&options.get("volume").getAsDouble()==0) {
				JsonObject silent=new JsonObject();silent.addProperty("replace",true);
				silent.add("sounds",new com.google.gson.JsonArray());output.add(javaEvent,silent);continue;
			}
			if(definition==null)continue;
			JsonObject adjusted=definition.deepCopy();
			if(options!=null)for(int i=0;i<adjusted.getAsJsonArray("sounds").size();i++) {
				JsonElement entry=adjusted.getAsJsonArray("sounds").get(i);
				// Context gains multiply definition gains, rather than replacing them.
				if(entry.isJsonPrimitive()&&(options.has("volume")||options.has("pitch"))) {
					JsonObject expanded=new JsonObject();expanded.add("name",entry);
					adjusted.getAsJsonArray("sounds").set(i,expanded);entry=expanded;
				}
				if(!entry.isJsonObject())continue;
				for(String field:List.of("volume","pitch"))if(options.has(field)&&options.get(field).isJsonPrimitive()
					&&options.getAsJsonPrimitive(field).isNumber()) {
					JsonObject sound=entry.getAsJsonObject();double base=sound.has(field)?sound.get(field).getAsDouble():1;
					sound.addProperty(field,base*options.get(field).getAsDouble());
				}
			}
			output.add(javaEvent,adjusted);
		}
	}

	private static String referencedEvent(JsonElement reference) {
		if (reference == null || reference.isJsonNull()) return null;
		if (reference.isJsonPrimitive() && reference.getAsJsonPrimitive().isString()) return reference.getAsString();
		if (!reference.isJsonObject()) return null;
		JsonElement sound = reference.getAsJsonObject().get("sound");
		return sound != null && sound.isJsonPrimitive() ? sound.getAsString() : null;
	}

	private static JsonObject object(JsonObject parent, String key) {
		return parent == null ? null : parent.getAsJsonObject(key);
	}

	private static String normalizeEvent(String event) {
		String normalized = event.toLowerCase(Locale.ROOT);
		return normalized.startsWith("minecraft:") ? normalized.substring("minecraft:".length()) : normalized;
	}
}
