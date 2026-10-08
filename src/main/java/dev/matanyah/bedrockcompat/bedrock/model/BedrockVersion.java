package dev.matanyah.bedrockcompat.bedrock.model;

import java.util.List;
import java.util.stream.Collectors;

public record BedrockVersion(List<Integer> components) {
	public BedrockVersion {
		components = List.copyOf(components);
	}

	public static BedrockVersion unknown() {
		return new BedrockVersion(List.of());
	}

	public boolean isKnown() {
		return !components.isEmpty();
	}

	@Override
	public String toString() {
		return isKnown() ? components.stream().map(String::valueOf).collect(Collectors.joining(".")) : "Unknown";
	}
}
