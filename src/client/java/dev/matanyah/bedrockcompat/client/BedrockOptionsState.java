package dev.matanyah.bedrockcompat.client;

import dev.matanyah.bedrockcompat.BedrockCompat;
import dev.matanyah.bedrockcompat.bedrock.storage.BedrockPackSelectionStore;
import dev.matanyah.bedrockcompat.bedrock.storage.BedrockPackConfigurationStore;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPack;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPackConfiguration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import dev.matanyah.bedrockcompat.client.resource.VirtualCompatibilityPackRuntime;

public final class BedrockOptionsState {
	private static BedrockPackSelectionStore store;
	private static BedrockPackConfigurationStore configurationStore;
	private static List<String> selectedIds = List.of();
	private static Map<String, BedrockPackConfiguration> configurations = Map.of();

	private BedrockOptionsState() {
	}

	public static synchronized void initialize(Path optionsFile) {
		store = new BedrockPackSelectionStore(optionsFile);
		configurationStore = new BedrockPackConfigurationStore(optionsFile);
		try {
			selectedIds = store.load();
			configurations = configurationStore.load();
		} catch (IOException | RuntimeException exception) {
			BedrockCompat.LOGGER.warn("Could not read Bedrock pack selections from {}", optionsFile, exception);
			selectedIds = List.of();
			configurations = Map.of();
		}
	}

	public static synchronized BedrockPackConfiguration configuration(String id, BedrockPack pack) {
		return configurations.getOrDefault(id, BedrockPackConfiguration.EMPTY).mergedWithDefaults(pack);
	}

	public static void setConfiguration(String id, BedrockPackConfiguration configuration) {
		synchronized (BedrockOptionsState.class) {
			if (configuration.equals(configurations.get(id))) return;
			Map<String, BedrockPackConfiguration> changed = new LinkedHashMap<>(configurations);
			changed.put(id, configuration);
			configurations = Map.copyOf(changed);
			persistConfigurations();
		}
		VirtualCompatibilityPackRuntime.selectionChanged();
	}

	public static synchronized List<String> selectedIds() {
		return selectedIds;
	}

	public static void setSelectedIds(List<String> ids) {
		synchronized (BedrockOptionsState.class) {
			if (selectedIds.equals(ids)) return;
			selectedIds = List.copyOf(ids);
			persist();
		}
		VirtualCompatibilityPackRuntime.selectionChanged();
	}

	public static synchronized void persist() {
		if (store == null) return;
		try {
			store.save(selectedIds);
			// Vanilla rewrites options.txt and discards unknown keys. Restore both
			// extension records after every vanilla save, not just the pack order.
			persistConfigurations();
		} catch (IOException | RuntimeException exception) {
			BedrockCompat.LOGGER.error("Could not save Bedrock pack selections to options.txt", exception);
		}
	}

	private static void persistConfigurations() {
		if (configurationStore == null) return;
		try {
			configurationStore.save(configurations);
		} catch (IOException | RuntimeException exception) {
			BedrockCompat.LOGGER.error("Could not save Bedrock pack configuration to options.txt", exception);
		}
	}
}
