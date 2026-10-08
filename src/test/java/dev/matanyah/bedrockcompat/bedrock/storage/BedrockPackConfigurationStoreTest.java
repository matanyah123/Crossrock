package dev.matanyah.bedrockcompat.bedrock.storage;

import dev.matanyah.bedrockcompat.bedrock.model.BedrockPackConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockPackConfigurationStoreTest {
	@TempDir Path directory;

	@Test
	void roundTripsTypedSettingsAndPreservesVanillaOptions() throws Exception {
		Path options = directory.resolve("options.txt");
		Files.writeString(options, "version:5000\nresourcePacks:[\"vanilla\"]\n");
		BedrockPackConfigurationStore store = new BedrockPackConfigurationStore(options);
		store.save(Map.of("pack", new BedrockPackConfiguration(Optional.of("full"),
			Map.of("toggle", true, "slider", 2.5, "dropdown", "blue", "multi", java.util.List.of("one", "two"), "empty", java.util.List.of()))));

		BedrockPackConfiguration loaded = store.load().get("pack");
		assertEquals(Optional.of("full"), loaded.selectedSubpack());
		assertEquals(true, loaded.settingValues().get("toggle"));
		assertEquals(2.5, loaded.settingValues().get("slider"));
		assertEquals("blue", loaded.settingValues().get("dropdown"));
		assertEquals(java.util.List.of("one", "two"), loaded.settingValues().get("multi"));
		assertEquals(java.util.List.of(), loaded.settingValues().get("empty"));
		assertTrue(Files.readString(options).contains("resourcePacks:[\"vanilla\"]"));
	}
}
