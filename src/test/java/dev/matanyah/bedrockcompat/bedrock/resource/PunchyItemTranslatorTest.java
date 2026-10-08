package dev.matanyah.bedrockcompat.bedrock.resource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockModule;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPack;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPackType;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockVersion;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PunchyItemTranslatorTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void emitsPunchyModelPartsDefinitionGeometryAndFirstPersonAnimation() throws IOException {
		Path pack = temporaryDirectory.resolve("pack");
		Files.createDirectories(pack.resolve("attachables"));
		Files.createDirectories(pack.resolve("models/entity"));
		Files.createDirectories(pack.resolve("animations"));
		Files.writeString(pack.resolve("attachables/sword.json"), """
			{"minecraft:attachable":{"description":{"identifier":"minecraft:diamond_sword",
			"geometry":{"default":"geometry.test.sword"},
			"animations":{"fp":"animation.test.sword_idle","third":"animation.test.third"},
			"scripts":{"animate":[{"fp":"c.is_first_person"},{"third":"!c.is_first_person"}]}}}}
			""");
		Files.writeString(pack.resolve("models/entity/items.geo.json"), """
			{"format_version":"1.12.0","minecraft:geometry":[{"description":{"identifier":"geometry.test.sword",
			"texture_width":16,"texture_height":16},"bones":[{"name":"blade","cubes":[{"origin":[0,0,0],
			"size":[1,8,1],"uv":[0,0]}]}]}]}
			""");
		Files.writeString(pack.resolve("animations/items.animation.json"), """
			{"animations":{"animation.test.sword_idle":{"loop":true,"bones":{"blade":{"rotation":[0,0,5]}}},
			"animation.test.third":{"loop":true,"bones":{"blade":{"rotation":[0,0,-5]}}}}}
			""");

		var result = pipeline().generate(List.of(pack(pack)));
		var resources = result.resources().resources();
		String base = "punchy/model_parts_items/";
		JsonObject definition = json(resources.get(id(base + "definitions/bedrock_compat_minecraft_diamond_sword.json")));
		assertEquals("minecraft:diamond_sword", definition.getAsJsonArray("items").get(0).getAsString());
		assertEquals("minecraft:item/diamond_sword", definition.get("texture").getAsString());
		assertEquals("animation.test.sword_idle",
			definition.getAsJsonArray("modelPartsAnimation").get(0).getAsJsonObject().get("name").getAsString());
		assertNotNull(resources.get(id(base + "geo/bedrock_compat_minecraft_diamond_sword.geo.json")));
		JsonObject animation = json(resources.get(id(base + "animations/bedrock_compat_minecraft_diamond_sword.animation.json")));
		assertNotNull(animation.getAsJsonObject("animations").get("animation.test.sword_idle"));
	}

	@Test
	void skipsAttachablesWhoseGeometryCannotBeResolved() throws IOException {
		Path pack = temporaryDirectory.resolve("broken-pack");
		Files.createDirectories(pack.resolve("attachables"));
		Files.writeString(pack.resolve("attachables/missing.json"), """
			{"minecraft:attachable":{"description":{"identifier":"minecraft:stick",
			"geometry":{"default":"geometry.missing"}}}}
			""");

		var resources = pipeline().generate(List.of(pack(pack))).resources().resources();
		assertFalse(resources.keySet().stream().anyMatch(key -> key.getPath().contains("model_parts_items")));
	}

	private static BedrockTranslationPipeline pipeline() {
		return new BedrockTranslationPipeline(List.of(new PunchyItemTranslator(new BedrockPackContentReader())));
	}

	private static BedrockPack pack(Path source) {
		BedrockVersion version = new BedrockVersion(List.of(1, 0, 0));
		return new BedrockPack("Items", "", "items", version, Optional.empty(),
			List.of(new BedrockModule("items-module", "resources", version, "")), List.of(), source,
			"manifest.json", BedrockPackType.RESOURCE, Optional.empty(), List.of());
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("minecraft", path);
	}

	private static JsonObject json(byte[] data) {
		return JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
	}
}
