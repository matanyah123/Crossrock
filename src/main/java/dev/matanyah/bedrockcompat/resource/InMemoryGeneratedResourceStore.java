package dev.matanyah.bedrockcompat.resource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class InMemoryGeneratedResourceStore implements GeneratedResourceSink {
	private final Map<Identifier, byte[]> resources = new LinkedHashMap<>();

	@Override
	public void put(Identifier id, byte[] data) {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(data, "data");
		byte[] existing = resources.get(id);
		if (existing != null && id.getNamespace().equals("minecraft")
			&& (id.getPath().equals("sounds.json") || id.getPath().startsWith("lang/") && id.getPath().endsWith(".json"))) {
			try {
				JsonObject merged = JsonParser.parseString(new String(existing, StandardCharsets.UTF_8)).getAsJsonObject();
				JsonObject overlay = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
				overlay.entrySet().forEach(entry -> merged.add(entry.getKey(), entry.getValue().deepCopy()));
				resources.put(id, merged.toString().getBytes(StandardCharsets.UTF_8));
				return;
			} catch (RuntimeException ignored) {
				// A malformed generated file is replaced normally and will be reported by Minecraft on reload.
			}
		}
		resources.put(id, data.clone());
	}

	public GeneratedResourceSnapshot snapshot() {
		return new GeneratedResourceSnapshot(resources);
	}

	@Override public void putText(Identifier id, byte[] data, boolean append) {
		byte[] previous = resources.get(id);
		if (append && previous != null) {
			byte[] combined = new byte[previous.length + 1 + data.length];
			System.arraycopy(previous, 0, combined, 0, previous.length);
			combined[previous.length] = '\n';
			System.arraycopy(data, 0, combined, previous.length + 1, data.length);
			put(id, combined);
		} else put(id, data);
	}

	@Override public void putText(Identifier id, byte[] data, boolean append, byte[] base) {
		if (append && base != null && !resources.containsKey(id)) put(id, base);
		putText(id, data, append);
	}
}
