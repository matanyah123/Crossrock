package dev.matanyah.bedrockcompat.resource;

import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record GeneratedResourceSnapshot(Map<Identifier, byte[]> resources) {
	public static final GeneratedResourceSnapshot EMPTY = new GeneratedResourceSnapshot(Map.of());

	public GeneratedResourceSnapshot {
		resources = Map.copyOf(resources);
	}

	public Set<String> namespaces() {
		return resources.keySet().stream().map(Identifier::getNamespace).collect(Collectors.toUnmodifiableSet());
	}
}
