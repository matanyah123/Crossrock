package dev.matanyah.bedrockcompat.bedrock.resource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockModule;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPack;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPackType;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockVersion;
import dev.matanyah.bedrockcompat.resource.InMemoryGeneratedResourceStore;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaSoundTranslatorTest {
    @Test void namedAmbientSoundAliasResolvesDefinition() throws IOException {
        Path directory=temporaryDirectory.resolve("ambient-audio");Files.createDirectories(directory.resolve("sounds"));
        Files.writeString(directory.resolve("sounds/sound_definitions.json"),"{\"sound_definitions\":{\"example:wind\":{\"sounds\":[\"sounds/custom/wind\"]}}}");
        Files.writeString(directory.resolve("sounds.json"),"{\"individual_named_sounds\":{\"wind.loop\":{\"sound\":\"example:wind\",\"volume\":0.5,\"pitch\":0.9}}}");
        var result=pipeline().generate(List.of(pack(directory)));
        var sounds=json(result.resources().resources().get(Identifier.fromNamespaceAndPath("bedrock_native","sounds.json")));
        var sound=sounds.getAsJsonObject(dev.matanyah.bedrockcompat.bedrock.entity.NativeSoundEvents.id("wind.loop").getPath()).getAsJsonArray("sounds").get(0).getAsJsonObject();
        assertEquals("minecraft:custom/wind",sound.get("name").getAsString());assertEquals(.5,sound.get("volume").getAsDouble());assertEquals(.9,sound.get("pitch").getAsDouble());
    }
    @Test void particleLevelEventAliasResolvesImportedDefinition() throws IOException {
        Path directory=temporaryDirectory.resolve("particle-audio");Files.createDirectories(directory.resolve("sounds"));
        Files.writeString(directory.resolve("sounds/sound_definitions.json"),"""
            {"sound_definitions":{"example:pop":{"sounds":["sounds/custom/pop"]}}}
            """);
        Files.writeString(directory.resolve("sounds.json"),"""
            {"individual_event_sounds":{"events":{"pop":{"sound":"example:pop","volume":0.4,"pitch":0.8}}}}
            """);
        var result=pipeline().generate(List.of(pack(directory)));
        var sounds=json(result.resources().resources().get(Identifier.fromNamespaceAndPath("bedrock_native","sounds.json")));
        var event=sounds.getAsJsonObject(dev.matanyah.bedrockcompat.bedrock.entity.NativeSoundEvents.id("pop").getPath());
        var sound=event.getAsJsonArray("sounds").get(0).getAsJsonObject();
        assertEquals("minecraft:custom/pop",sound.get("name").getAsString());
        assertEquals(0.4,sound.get("volume").getAsDouble(),1e-6);
        assertEquals(0.8,sound.get("pitch").getAsDouble(),1e-6);
    }
    @Test void entityLocalMuteDoesNotLeakIntoOtherSpecies() throws IOException {
        Path directory=temporaryDirectory.resolve("muted");Files.createDirectories(directory.resolve("sounds"));
        Files.writeString(directory.resolve("sounds/sound_definitions.json"),"{\"sound_definitions\":{}}");
        Files.writeString(directory.resolve("sounds.json"),"""
            {"entity_sounds":{"entities":{"cow":{"events":{"step":{"volume":0}}}}}}
            """);
        var result=pipeline().generate(List.of(pack(directory)));
        var sounds=json(result.resources().resources().get(id("sounds.json")));
        assertTrue(sounds.getAsJsonObject("entity.cow.step").getAsJsonArray("sounds").isEmpty());
        assertTrue(sounds.keySet().stream().allMatch(k->k.startsWith("entity.cow.")));
    }
	@TempDir
	Path temporaryDirectory;

	@Test
	void loadsCompleteMinecraft262SoundMapping() {
		assertTrue(SoundEventMappings.mappingCount() >= 1_900);
		assertTrue(SoundEventMappings.javaEvents("mob.zombie.say").contains("entity.zombie.ambient"));
		assertTrue(SoundEventMappings.entityEvents("cow", "step").contains("entity.cow.step"));
	}

	@Test
	void readsBrArchiveDefinitionsAndGeneratesJavaSoundsJson() throws IOException {
		Path packDirectory = temporaryDirectory.resolve("sounds-pack");
		Files.createDirectories(packDirectory.resolve("__brarchive"));
		Files.createDirectories(packDirectory.resolve("sounds/custom"));
		byte[] ogg = new byte[]{1, 2, 3, 4};
		Files.write(packDirectory.resolve("sounds/custom/zombie.ogg"), ogg);
		String definitions = """
			{"format_version":"1.20.20","sound_definitions":{"mob.zombie.say":{"sounds":[
			  {"name":"sounds/custom/zombie","volume":0.6,"pitch":0.9}
			]}}}
			""";
		Files.write(packDirectory.resolve("__brarchive/sounds.brarchive"),
			brArchive("sound_definitions.json", definitions.getBytes(StandardCharsets.UTF_8)));

		var result = pipeline().generate(List.of(pack(packDirectory)));

		assertArrayEquals(ogg, result.resources().resources().get(id("sounds/custom/zombie.ogg")));
		JsonObject sounds = json(result.resources().resources().get(id("sounds.json")));
		JsonObject zombie = sounds.getAsJsonObject("entity.zombie.ambient");
		assertTrue(zombie.get("replace").getAsBoolean());
		assertEquals("minecraft:custom/zombie",
			zombie.getAsJsonArray("sounds").get(0).getAsJsonObject().get("name").getAsString());
	}

	@Test
	void mapsContextualEntitySoundsThatReferenceCustomDefinitions() throws IOException {
		Path packDirectory = temporaryDirectory.resolve("context-sounds");
		Files.createDirectories(packDirectory.resolve("sounds/custom"));
		Files.write(packDirectory.resolve("sounds/custom/step.ogg"), new byte[]{5});
		Files.writeString(packDirectory.resolve("sounds/sound_definitions.json"), """
			{"sound_definitions":{"example:custom_step":{"sounds":["sounds/custom/step"]}}}
			""");
		Files.writeString(packDirectory.resolve("sounds.json"), """
			{"entity_sounds":{"entities":{"cow":{"events":{"step":"example:custom_step"}}}}}
			""");

		var result = pipeline().generate(List.of(pack(packDirectory)));
		JsonObject sounds = json(result.resources().resources().get(id("sounds.json")));

		assertEquals("minecraft:custom/step",
			sounds.getAsJsonObject("entity.cow.step").getAsJsonArray("sounds").get(0).getAsString());
	}

	@Test
	void mergesSoundsJsonAcrossSelectedPacksWithTheHigherPackWinning() {
		InMemoryGeneratedResourceStore store = new InMemoryGeneratedResourceStore();
		Identifier id = id("sounds.json");
		store.put(id, "{\"entity.cow.step\":{\"sounds\":[\"minecraft:low\"]},\"entity.pig.step\":{\"sounds\":[\"minecraft:pig\"]}}"
			.getBytes(StandardCharsets.UTF_8));
		store.put(id, "{\"entity.cow.step\":{\"sounds\":[\"minecraft:high\"]}}".getBytes(StandardCharsets.UTF_8));

		JsonObject merged = json(store.snapshot().resources().get(id));
		assertEquals("minecraft:high", merged.getAsJsonObject("entity.cow.step").getAsJsonArray("sounds").get(0).getAsString());
		assertTrue(merged.has("entity.pig.step"));
	}

	private static BedrockTranslationPipeline pipeline() {
		return new BedrockTranslationPipeline(List.of(new VanillaSoundTranslator(new BedrockPackContentReader())));
	}

	private static BedrockPack pack(Path source) {
		BedrockVersion version = new BedrockVersion(List.of(1, 0, 0));
		return new BedrockPack("Sounds", "", "sounds", version, Optional.empty(),
			List.of(new BedrockModule("sounds-module", "resources", version, "")), List.of(), source,
			"manifest.json", BedrockPackType.RESOURCE, Optional.empty(), List.of());
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("minecraft", path);
	}

	private static JsonObject json(byte[] data) {
		return JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
	}

	private static byte[] brArchive(String name, byte[] content) {
		byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
		ByteBuffer archive = ByteBuffer.allocate(16 + 256 + content.length).order(ByteOrder.LITTLE_ENDIAN);
		archive.putLong(0x267052A0B125277DL);
		archive.putInt(1);
		archive.putInt(1);
		archive.put((byte) nameBytes.length);
		archive.put(nameBytes);
		archive.position(16 + 248);
		archive.putInt(0);
		archive.putInt(content.length);
		archive.position(16 + 256);
		archive.put(content);
		return archive.array();
	}
}
