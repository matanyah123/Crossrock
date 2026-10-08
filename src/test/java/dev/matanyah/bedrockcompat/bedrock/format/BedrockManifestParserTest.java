package dev.matanyah.bedrockcompat.bedrock.format;

import dev.matanyah.bedrockcompat.bedrock.model.BedrockPack;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPackType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockManifestParserTest {
	private final BedrockManifestParser parser = new BedrockManifestParser();

    @Test
    void parsesLocalizedMultiselectDefaults() {
        var pack = parser.parse("""
            {"format_version":3,"header":{"name":"Test","version":"1.0.0"},
             "settings":[{"type":"multiselect","name":"demo:effects","text":"effects",
              "defaults":["rain"],"options":[{"name":"rain","text":"rain_label"},{"name":"snow","text":"Snow"}]}]}
            """.getBytes(StandardCharsets.UTF_8), Path.of("test.mcpack"), "", null,
            Map.of("effects", "Effects", "rain_label", "Rain"));
        var setting = (dev.matanyah.bedrockcompat.bedrock.model.BedrockPackSetting.Multiselect) pack.settings().getFirst();
        assertEquals("Effects", setting.text());
        assertEquals("Rain", setting.options().getFirst().text());
        assertEquals(java.util.List.of("rain"),
            dev.matanyah.bedrockcompat.bedrock.model.BedrockPackConfiguration.defaults(pack).settingValues().get("demo:effects"));
        assertTrue(pack.warnings().stream().noneMatch(w -> w.contains("setting")));
    }

	@Test
	void parsesBedrockHeaderModulesAndDependencies() {
		String json = """
			{
			  "format_version": 2,
			  "header": {
			    "name": "Test resources",
			    "description": "A real manifest shape",
			    "uuid": "12345678-1234-1234-1234-1234567890ab",
			    "version": [1, 2, 3],
			    "min_engine_version": [1, 21, 0]
			  },
			  "modules": [{
			    "type": "resources",
			    "uuid": "abcdefab-1234-1234-1234-abcdefabcdef",
			    "version": [1, 2, 3]
			  }],
			  "dependencies": [{
			    "uuid": "87654321-4321-4321-4321-ba0987654321",
			    "version": [2, 0, 0]
			  }, {
			    "module_name": "@minecraft/server",
			    "version": "1.12.0"
			  }]
			}
			""";

		BedrockPack pack = parser.parse(json.getBytes(StandardCharsets.UTF_8), Path.of("test"), "manifest.json", null);

		assertEquals("Test resources", pack.displayName());
		assertEquals("1.2.3", pack.version().toString());
		assertEquals("1.21.0", pack.minimumEngineVersion().orElseThrow().toString());
		assertEquals(BedrockPackType.RESOURCE, pack.type());
		assertEquals(1, pack.modules().size());
		assertEquals(2, pack.dependencies().size());
		assertEquals("@minecraft/server", pack.dependencies().get(1).moduleName());
		assertTrue(pack.warnings().isEmpty());
	}

	@Test
	void toleratesMissingOptionalFieldsAndReportsWarnings() {
		String json = """
			{"format_version": 2, "header": {"name": "Minimal"}, "modules": [{"type": "data"}]}
			""";

		BedrockPack pack = parser.parse(json.getBytes(StandardCharsets.UTF_8), Path.of("test"), "manifest.json", null);

		assertEquals(BedrockPackType.BEHAVIOR, pack.type());
		assertFalse(pack.minimumEngineVersion().isPresent());
		assertFalse(pack.warnings().isEmpty());
	}

	@Test
	void rejectsMissingHeaderWithoutCrashingCaller() {
		assertThrows(IllegalArgumentException.class,
			() -> parser.parse("{}".getBytes(StandardCharsets.UTF_8), Path.of("test"), "manifest.json", null));
	}

	@Test
	void resolvesLocalizedHeaderTextAndAcceptsFormatThree() {
		String json = """
			{"format_version":3,"header":{"name":"pack.name","description":"pack.description","uuid":"12345678-1234-1234-1234-1234567890ab","version":"1.9.0"},"modules":[{"type":"resources","uuid":"abcdefab-1234-1234-1234-abcdefabcdef","version":"1.9.0"}]}
			""";

		BedrockPack pack = parser.parse(json.getBytes(StandardCharsets.UTF_8), Path.of("test"), "manifest.json", null,
			Map.of("pack.name", "Actions & Stuff 1.9", "pack.description", "By Studio"));

		assertEquals("Actions & Stuff 1.9", pack.displayName());
		assertEquals("By Studio", pack.description());
		assertTrue(pack.warnings().isEmpty());
	}

	@Test
	void parsesSubpacksAndEveryBedrockSettingType() {
		String json = """
			{"format_version":3,
			 "header":{"name":"Pack","description":"","uuid":"12345678-1234-1234-1234-1234567890ab","version":[1,0,0]},
			 "modules":[{"type":"resources","uuid":"abcdefab-1234-1234-1234-abcdefabcdef","version":[1,0,0]}],
			 "subpacks":[{"folder_name":"full","name":"sub.full","memory_performance_tier":3}],
			 "settings":[
			   {"type":"label","text":"setting.info"},
			   {"type":"toggle","name":"enabled","text":"Enabled","default":true},
			   {"type":"slider","name":"amount","text":"Amount","min":0,"max":10,"step":0.5,"default":2.5},
			   {"type":"dropdown","name":"mode","text":"Mode","options":[{"name":"a","text":"Alpha"},{"name":"b","text":"Beta"}],"default":"b"}
			 ]}
			""";

		BedrockPack pack = parser.parse(json.getBytes(StandardCharsets.UTF_8), Path.of("test"), "manifest.json", null,
			Map.of("sub.full", "Full Experience", "setting.info", "Information"));

		assertEquals("full", pack.subpacks().getFirst().folderName());
		assertEquals("Full Experience", pack.subpacks().getFirst().name());
		assertEquals(4, pack.settings().size());
		assertEquals("Information", pack.settings().getFirst().text());
		assertTrue(pack.warnings().isEmpty());
	}
}
