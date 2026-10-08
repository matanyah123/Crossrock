package dev.matanyah.bedrockcompat.bedrock.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public record BedrockPack(
	String displayName,
	String description,
	String uuid,
	BedrockVersion version,
	Optional<BedrockVersion> minimumEngineVersion,
	List<BedrockModule> modules,
	List<BedrockDependency> dependencies,
	List<BedrockSubpack> subpacks,
	List<BedrockPackSetting> settings,
	Path source,
	String locationInSource,
	BedrockPackType type,
	Optional<byte[]> icon,
	List<String> warnings
) {
	public BedrockPack(String displayName, String description, String uuid, BedrockVersion version,
		Optional<BedrockVersion> minimumEngineVersion, List<BedrockModule> modules,
		List<BedrockDependency> dependencies, Path source, String locationInSource, BedrockPackType type,
		Optional<byte[]> icon, List<String> warnings) {
		this(displayName, description, uuid, version, minimumEngineVersion, modules, dependencies,
			List.of(), List.of(), source, locationInSource, type, icon, warnings);
	}

	public BedrockPack {
		minimumEngineVersion = minimumEngineVersion == null ? Optional.empty() : minimumEngineVersion;
		modules = List.copyOf(modules);
		dependencies = List.copyOf(dependencies);
		subpacks = List.copyOf(subpacks);
		settings = List.copyOf(settings);
		icon = icon.map(byte[]::clone);
		warnings = List.copyOf(warnings);
	}

	@Override
	public Optional<byte[]> icon() {
		return icon.map(byte[]::clone);
	}
}
