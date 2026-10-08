package dev.matanyah.bedrockcompat.bedrock.model;

import java.nio.file.Path;
import java.util.Optional;

public record PackLoadResult(Path source, String locationInSource, Optional<BedrockPack> pack, Optional<String> error) {
	public PackLoadResult {
		pack = pack == null ? Optional.empty() : pack;
		error = error == null ? Optional.empty() : error;
	}

	public static PackLoadResult loaded(BedrockPack pack) {
		return new PackLoadResult(pack.source(), pack.locationInSource(), Optional.of(pack), Optional.empty());
	}

	public static PackLoadResult failed(Path source, String location, String error) {
		return new PackLoadResult(source, location, Optional.empty(), Optional.of(error));
	}

	public String displayName() {
		return pack.map(BedrockPack::displayName).orElseGet(() -> source.getFileName().toString());
	}
}
