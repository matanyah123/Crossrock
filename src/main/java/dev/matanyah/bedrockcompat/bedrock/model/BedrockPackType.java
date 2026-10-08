package dev.matanyah.bedrockcompat.bedrock.model;

import java.util.Collection;
import java.util.Locale;

public enum BedrockPackType {
	RESOURCE("Resource"),
	BEHAVIOR("Behavior"),
	MIXED("Mixed"),
	OTHER("Other"),
	UNKNOWN("Unknown");

	private final String displayName;

	BedrockPackType(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return displayName;
	}

	public static BedrockPackType fromModules(Collection<BedrockModule> modules) {
		boolean resource = false;
		boolean behavior = false;
		boolean other = false;
		for (BedrockModule module : modules) {
			String type = module.type().toLowerCase(Locale.ROOT);
			resource |= type.equals("resources");
			behavior |= type.equals("data");
			other |= !type.isBlank() && !type.equals("resources") && !type.equals("data");
		}
		if (resource && behavior) return MIXED;
		if (resource) return RESOURCE;
		if (behavior) return BEHAVIOR;
		if (other) return OTHER;
		return UNKNOWN;
	}
}
