package dev.matanyah.bedrockcompat.bedrock.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public record BedrockPackConfiguration(Optional<String> selectedSubpack, Map<String, Object> settingValues) {
	public static final BedrockPackConfiguration EMPTY = new BedrockPackConfiguration(Optional.empty(), Map.of());

	public BedrockPackConfiguration {
		selectedSubpack = selectedSubpack == null ? Optional.empty() : selectedSubpack;
		settingValues = Map.copyOf(new LinkedHashMap<>(settingValues));
	}

	public static BedrockPackConfiguration defaults(BedrockPack pack) {
		Map<String, Object> values = new LinkedHashMap<>();
		for (BedrockPackSetting setting : pack.settings()) {
			if (setting instanceof BedrockPackSetting.Toggle toggle) values.put(toggle.name(), toggle.defaultValue());
			else if (setting instanceof BedrockPackSetting.Slider slider) values.put(slider.name(), slider.defaultValue());
			else if (setting instanceof BedrockPackSetting.Multiselect multi) values.put(multi.name(), multi.defaults());
			else if (setting instanceof BedrockPackSetting.Dropdown dropdown) values.put(dropdown.name(), dropdown.defaultValue());
		}
		Optional<String> subpack = pack.subpacks().stream()
			.reduce((first, second) -> second.memoryPerformanceTier() >= first.memoryPerformanceTier() ? second : first)
			.map(BedrockSubpack::folderName);
		return new BedrockPackConfiguration(subpack, values);
	}

	public BedrockPackConfiguration mergedWithDefaults(BedrockPack pack) {
		BedrockPackConfiguration defaults = defaults(pack);
		Map<String, Object> merged = new LinkedHashMap<>(defaults.settingValues());
		merged.putAll(settingValues);
		Optional<String> subpack = selectedSubpack.filter(folder -> pack.subpacks().stream()
			.anyMatch(candidate -> candidate.folderName().equals(folder)));
		return new BedrockPackConfiguration(subpack.isPresent() ? subpack : defaults.selectedSubpack(), merged);
	}
}
