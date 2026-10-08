package dev.matanyah.bedrockcompat.bedrock.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockPackSelectionStoreTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void savesSelectionAsOptionsArrayWithoutRemovingVanillaOptions() throws IOException {
		Path options = temporaryDirectory.resolve("options.txt");
		Files.writeString(options, "version:5000\nresourcePacks:[\"vanilla\"]\n");
		BedrockPackSelectionStore store = new BedrockPackSelectionStore(options);

		store.save(List.of("pack-b", "pack-a"));

		String content = Files.readString(options);
		assertTrue(content.contains("version:5000"));
		assertTrue(content.contains("resourcePacks:[\"vanilla\"]"));
		assertTrue(content.contains("bedrockCompatPacks:[\"pack-b\",\"pack-a\"]"));
		assertEquals(List.of("pack-b", "pack-a"), store.load());
	}

	@Test
	void replacesExistingSelectionAndRemovesDuplicates() throws IOException {
		Path options = temporaryDirectory.resolve("options.txt");
		Files.writeString(options, "bedrockCompatPacks:[\"old\"]\n");
		BedrockPackSelectionStore store = new BedrockPackSelectionStore(options);

		store.save(List.of("new", "new", "second"));

		assertEquals(List.of("new", "second"), store.load());
		assertEquals(1, Files.readAllLines(options).stream()
			.filter(line -> line.startsWith(BedrockPackSelectionStore.OPTION_KEY + ":")).count());
	}
}
