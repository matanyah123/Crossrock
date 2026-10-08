package dev.matanyah.bedrockcompat.bedrock.resource;

import dev.matanyah.bedrockcompat.bedrock.model.BedrockModule;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPack;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPackType;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockVersion;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockLanguageTranslatorTest {
	@TempDir Path temporaryDirectory;

	@Test
	void convertsLangFilesToJavaLocaleJson() throws Exception {
		Path root = temporaryDirectory.resolve("pack");
		Files.createDirectories(root.resolve("texts"));
		Files.writeString(root.resolve("texts/en_US.lang"), "# comment\nmenu.play=Play\naddon.key=Value=with equals\n");
		BedrockPack pack = new BedrockPack("Lang", "", "lang", new BedrockVersion(List.of(1, 0, 0)),
			Optional.empty(), List.of(new BedrockModule("module", "resources", new BedrockVersion(List.of(1, 0, 0)), "")),
			List.of(), root, "", BedrockPackType.RESOURCE, Optional.empty(), List.of());
		var store = new dev.matanyah.bedrockcompat.resource.InMemoryGeneratedResourceStore();
		new BedrockLanguageTranslator(new BedrockPackContentReader()).translate(pack,
			dev.matanyah.bedrockcompat.bedrock.model.BedrockPackConfiguration.defaults(pack), store);
		byte[] data = store.snapshot().resources().get(Identifier.fromNamespaceAndPath("minecraft", "lang/en_us.json"));
		String json = new String(data);
		assertTrue(json.contains("\"menu.play\":\"Play\""));
		assertTrue(json.contains("\"addon.key\":\"Value=with equals\""));
	}
}
