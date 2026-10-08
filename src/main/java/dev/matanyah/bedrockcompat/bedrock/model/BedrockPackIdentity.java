package dev.matanyah.bedrockcompat.bedrock.model;

public final class BedrockPackIdentity {
	private BedrockPackIdentity() {
	}

	public static String selectionId(BedrockPack pack) {
		if (!pack.uuid().isBlank()) return pack.uuid();
		return pack.source().toAbsolutePath() + "!/" + pack.locationInSource();
	}
}
