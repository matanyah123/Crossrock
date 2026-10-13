package dev.matanyah.bedrockcompat.bedrock.resource;

import dev.matanyah.bedrockcompat.bedrock.model.BedrockModule;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPack;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPackType;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPackConfiguration;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockSubpack;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockVersion;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockTranslationPipelineTest {
	@TempDir
	Path temporaryDirectory;

    @Test
    void translatesItemAtlasAliasesWithoutFlatteningAuxVariants() throws Exception {
        Path folder = temporaryDirectory.resolve("item-atlas");
        Files.createDirectories(folder.resolve("textures/art"));
        Files.write(folder.resolve("textures/art/icon.png"), solidPng(0xff12ab34));
        Files.writeString(folder.resolve("textures/item_texture.json"), """
            {"texture_data":{"diamond":{"textures":"textures/art/icon"},
             "stick":{"textures":["textures/art/icon"]},
             "apple":{"textures":["textures/art/icon","textures/art/missing"]},
             "emerald":{"textures":"textures/art/missing"}}}
            """);
        var result = pipeline().generate(List.of(pack("Atlas","atlas",folder,"manifest.json")));
        assertTrue(result.errors().isEmpty(), result.errors().toString());
        assertArrayEquals(solidPng(0xff12ab34), result.resources().resources().get(id("textures/item/diamond.png")));
        assertArrayEquals(solidPng(0xff12ab34), result.resources().resources().get(id("textures/item/stick.png")));
        assertFalse(result.resources().resources().containsKey(id("textures/item/apple.png")));
        assertFalse(result.resources().resources().containsKey(id("textures/item/emerald.png")));
    }

    @Test
    void translatesCubeFacesWildcardAndSeparateCarriedTextures() throws Exception {
        Path folder = temporaryDirectory.resolve("cube-assets");
        Files.createDirectories(folder.resolve("textures/art"));
        Files.write(folder.resolve("textures/art/world.png"), solidPng(0xffaabbcc));
        Files.write(folder.resolve("textures/art/held.png"), solidPng(0xff123456));
        Files.writeString(folder.resolve("textures/terrain_texture.json"), """
            {"texture_data":{"world":{"textures":"textures/art/world"},"held":{"textures":"textures/art/held"}}}
            """);
        Files.writeString(folder.resolve("textures/flipbook_textures.json"), """
            [{"flipbook_texture":"textures/art/world","ticks_per_frame":7,"frames":[0]}]
            """);
        Files.writeString(folder.resolve("blocks.json"), """
            {"minecraft:iron_block":{"textures":{"*":"world","up":"held"},"carried_textures":"held"},
             "diamond_block":{"textures":"world"},"oak_stairs":{"textures":"world"},
             "gold_block":{"textures":{"up":"world"}},"fixture:unknown":{"textures":"world"}}
            """);
        var result = pipeline().generate(List.of(pack("Cubes","cubes",folder,"manifest.json")));
        assertTrue(result.errors().isEmpty(), result.errors().toString());
        var model = com.google.gson.JsonParser.parseString(text(result,"models/block/iron_block.json")).getAsJsonObject();
        assertEquals("minecraft:block/crossrock_assets/textures/art/held",model.getAsJsonObject("textures").get("up").getAsString());
        assertEquals("minecraft:block/crossrock_assets/textures/art/world",model.getAsJsonObject("textures").get("north").getAsString());
        assertTrue(text(result,"textures/block/crossrock_assets/textures/art/world.png.mcmeta").contains("\"frametime\":7"));
        assertTrue(text(result,"items/iron_block.json").contains("block/crossrock_carried/iron_block"));
        assertTrue(text(result,"models/block/crossrock_carried/iron_block.json").contains("minecraft:block/cube"));
        assertTrue(result.resources().resources().containsKey(id("models/block/diamond_block.json")));
        for (String path : List.of("models/block/oak_stairs.json","models/block/gold_block.json","items/diamond_block.json","blockstates/iron_block.json"))
            assertFalse(result.resources().resources().containsKey(id(path)), path);
    }

    @Test
    void itemAtlasUsesSelectedSubpackAndHigherPackPriority() throws Exception {
        Path lower = temporaryDirectory.resolve("lower-atlas");
        Path higher = temporaryDirectory.resolve("higher-atlas");
        for (Path folder : List.of(lower,higher)) {
            Files.createDirectories(folder.resolve("textures/art"));
            Files.writeString(folder.resolve("textures/item_texture.json"), "{\"texture_data\":{\"diamond\":{\"textures\":\"textures/art/icon\"}}}");
            Files.write(folder.resolve("textures/art/icon.png"), solidPng(0xffaabbcc));
        }
        Files.createDirectories(higher.resolve("subpacks/selected/textures/art"));
        Files.write(higher.resolve("subpacks/selected/textures/art/icon.png"),solidPng(0xff102030));
        var result = pipeline().generateConfigured(List.of(
            new BedrockTranslationPipeline.ConfiguredPack(pack("Higher","higher",higher,"manifest.json"),new BedrockPackConfiguration(Optional.of("selected"),Map.of())),
            new BedrockTranslationPipeline.ConfiguredPack(pack("Lower","lower",lower,"manifest.json"),new BedrockPackConfiguration(Optional.empty(),Map.of()))));
        assertArrayEquals(solidPng(0xff102030),result.resources().resources().get(id("textures/item/diamond.png")));
    }

	@Test
	void readsSelectedSubpackBlockAndPresentationMetadata() throws Exception {
		Path folder=temporaryDirectory.resolve("presentation");
		Files.createDirectories(folder.resolve("subpacks/test/textures/ui"));
		Files.writeString(folder.resolve("subpacks/test/blocks.json"),"{\"oak_double_slab\":{\"textures\":\"custom_double\"}}");
		Files.writeString(folder.resolve("subpacks/test/splashes.json"),"{\"splashes\":[\"Subpack splash\"]}");
		Files.write(folder.resolve("subpacks/test/textures/ui/heart.png"),png(9,9));
		Files.writeString(folder.resolve("subpacks/test/textures/ui/heart.json"),"{\"base_size\":[9,9]}");
		var selected=pack("Presentation","presentation",folder,"manifest.json");
		var configuration=new BedrockPackConfiguration(Optional.of("test"),Map.of());
		var pipeline=new BedrockTranslationPipeline(List.of(new VanillaTextureTranslator(new BedrockPackContentReader()),
			new BedrockPresentationTranslator(new BedrockPackContentReader())));
		var result=pipeline.generateConfigured(List.of(new BedrockTranslationPipeline.ConfiguredPack(selected,configuration)));
		assertTrue(result.errors().isEmpty(),result.errors().toString());
		assertEquals("Subpack splash",text(result,"texts/splashes.txt"));
		assertTrue(result.resources().resources().containsKey(Identifier.withDefaultNamespace("blockstates/oak_slab.json")));
		assertTrue(result.resources().resources().containsKey(Identifier.withDefaultNamespace("textures/gui/sprites/hud/heart/full.png")));
	}

	@Test
	void loadsBundledGeyserTextureMappings() {
		assertTrue(TexturePathMappings.mappingCount() > 1_000);
		assertEquals("textures/block/acacia_planks.png",
			VanillaTextureTranslator.mapPath("textures/blocks/planks_acacia.png"));
		assertEquals("textures/item/baked_potato.png",
			VanillaTextureTranslator.mapPath("textures/items/potato_baked.png"));
	}

	@Test
	void discoversAttachableFoldersAndHigherSelectedPackReplacesDefinition() throws IOException {
		Path low=temporaryDirectory.resolve("props-low"),high=temporaryDirectory.resolve("props-high");
		for(Path dir:List.of(low,high)) {
			Files.createDirectories(dir.resolve("attachables"));
			Files.writeString(dir.resolve("attachables/arbitrary-name.json"),
				"{\"minecraft:attachable\":{\"description\":{\"identifier\":\"example:prop\",\"geometry\":{\"default\":\"geometry."
				+ (dir.equals(high)?"high":"low") + "\"}}}}");
		}
		var pipeline=new BedrockTranslationPipeline(List.of(new NativeEntityResourceTranslator(new BedrockPackContentReader())));
		var result=pipeline.generate(List.of(pack("High","high",high,"manifest.json"),pack("Low","low",low,"manifest.json")));
		assertTrue(result.errors().isEmpty());
		String key=java.util.HexFormat.of().formatHex("attachable.example:prop".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		var json=result.resources().resources().get(Identifier.fromNamespaceAndPath("bedrock_native","entity_definitions/"+key+".json"));
		assertTrue(new String(json,java.nio.charset.StandardCharsets.UTF_8).contains("geometry.high"));
		assertEquals(1,result.resources().resources().size());
	}

	@Test
	void mapsStandardArmorLayersAndPreservesPackPriority() throws IOException {
		for (String material : List.of("chain", "diamond", "gold", "iron", "netherite")) {
			String javaMaterial = material.equals("chain") ? "chainmail" : material;
			assertEquals("textures/entity/equipment/humanoid/" + javaMaterial + ".png",
				VanillaTextureTranslator.mapPath("textures/models/armor/" + material + "_1.png"));
			assertEquals("textures/entity/equipment/humanoid_leggings/" + javaMaterial + ".png",
				VanillaTextureTranslator.mapPath("textures/models/armor/" + material + "_2.png"));
		}
		Path low = temporaryDirectory.resolve("armor-low"), high = temporaryDirectory.resolve("armor-high");
		Files.createDirectories(low.resolve("textures/models/armor"));
		Files.createDirectories(high.resolve("textures/models/armor"));
		byte[] lowImage = png(64, 32), highImage = png(128, 64);
		Files.write(low.resolve("textures/models/armor/diamond_1.png"), lowImage);
		Files.write(low.resolve("textures/models/armor/diamond_2.png"), lowImage);
		Files.write(high.resolve("textures/models/armor/diamond_1.png"), highImage);
		var result = pipeline().generate(List.of(pack("High", "high", high, "manifest.json"),
			pack("Low", "low", low, "manifest.json")));
		assertArrayEquals(highImage, result.resources().resources().get(id("textures/entity/equipment/humanoid/diamond.png")));
		assertArrayEquals(lowImage, result.resources().resources().get(id("textures/entity/equipment/humanoid_leggings/diamond.png")));
		assertEquals(List.of(), result.errors());
	}

	@Test
	void translatesBlockAndItemTexturesFromFolder() throws IOException {
		Path packDirectory = temporaryDirectory.resolve("folder-pack");
		Files.createDirectories(packDirectory.resolve("textures/blocks"));
		Files.createDirectories(packDirectory.resolve("textures/items"));
		Files.createDirectories(packDirectory.resolve("textures/entity"));
		Files.write(packDirectory.resolve("textures/blocks/stone.png"), new byte[]{1});
		Files.write(packDirectory.resolve("textures/items/apple.png"), new byte[]{2});
		Files.write(packDirectory.resolve("textures/entity/cow.png"), new byte[]{3});

		var result = pipeline().generate(List.of(pack("Folder", "folder", packDirectory, "manifest.json")));

		assertArrayEquals(new byte[]{1}, result.resources().resources().get(id("textures/block/stone.png")));
		assertArrayEquals(new byte[]{2}, result.resources().resources().get(id("textures/item/apple.png")));
		assertArrayEquals(new byte[]{3}, result.resources().resources().get(id("textures/entity/cow.png")));
		assertEquals(List.of(), result.errors());
	}

	@Test
	void convertsModernArmorStandGeometryToEmfRootParts() throws IOException {
		Path dir = temporaryDirectory.resolve("armor-model");
		Files.createDirectories(dir.resolve("models/entity"));
		Files.writeString(dir.resolve("models/entity/armor.geo.json"), """
			{"format_version":"1.12.0","minecraft:geometry":[{"description":{"identifier":"geometry.hidden","texture_width":64,"texture_height":64},"bones":[
			{"name":"baseplate","cubes":[{"origin":[-6,0,-6],"size":[12,1,12],"uv":[0,32]}]},
			{"name":"body","parent":"baseplate","pivot":[0,24,0],"cubes":[{"origin":[-2,20,-1],"size":[4,4,2],"uv":{"north":{"uv":[0,0],"uv_size":[4,4]}}}]},
			{"name":"head","parent":"body","pivot":[0,24,0],"cubes":[{"origin":[-1,24,-1],"size":[2,2,2],"uv":[8,0]}]}
			]}]}
			""");
		Files.createDirectories(dir.resolve("entity"));
		Files.writeString(dir.resolve("entity/armor.entity.json"), """
			{"minecraft:client_entity":{"description":{"identifier":"minecraft:armor_stand","geometry":{"default":"geometry.hidden"}}}}
			""");
		var result = new BedrockTranslationPipeline(List.of(new BedrockEntityModelTranslator(new BedrockPackContentReader())))
			.generate(List.of(pack("Armor", "armor", dir, "manifest.json")));
		String jem = text(result, "emf/cem/armor_stand.jem");
		assertTrue(jem.contains("\"part\":\"base\""));
		assertTrue(jem.contains("\"part\":\"body\""));
		assertTrue(jem.contains("\"part\":\"head\""));
		assertTrue(jem.contains("\"uvNorth\""));
	}

	@Test
	void topSelectedPackWinsAndArchiveStreamsAreClosed() throws IOException {
		Path lowArchive = temporaryDirectory.resolve("low.mcpack");
		try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(lowArchive))) {
			writeZipEntry(zip, "manifest.json", new byte[]{0});
			writeZipEntry(zip, "textures/blocks/stone.png", new byte[]{1});
			writeZipEntry(zip, "textures/items/apple.png", new byte[]{4});
		}
		Path highDirectory = temporaryDirectory.resolve("high");
		Files.createDirectories(highDirectory.resolve("textures/blocks"));
		Files.write(highDirectory.resolve("textures/blocks/stone.png"), new byte[]{9});

		var result = pipeline().generate(List.of(
			pack("High", "high", highDirectory, "manifest.json"),
			pack("Low", "low", lowArchive, "manifest.json")
		));

		assertArrayEquals(new byte[]{9}, result.resources().resources().get(id("textures/block/stone.png")));
		assertArrayEquals(new byte[]{4}, result.resources().resources().get(id("textures/item/apple.png")));
		Files.delete(lowArchive);
	}

	@Test
	void mapsBedrockConcreteNamesFromPackSubdirectory() throws IOException {
		Path archive = temporaryDirectory.resolve("better-concrete.mcpack");
		try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
			writeZipEntry(zip, "Better Concrete/manifest.json", new byte[]{0});
			writeZipEntry(zip, "Better Concrete/textures/blocks/concrete_black.png", new byte[]{7});
			writeZipEntry(zip, "Better Concrete/textures/blocks/concrete_silver.png", new byte[]{8});
		}

		var result = pipeline().generate(List.of(pack("Concrete", "concrete", archive,
			"Better Concrete/manifest.json")));

		assertArrayEquals(new byte[]{7}, result.resources().resources().get(id("textures/block/black_concrete.png")));
		assertArrayEquals(new byte[]{8}, result.resources().resources().get(id("textures/block/light_gray_concrete.png")));
	}

	@Test
	void selectedSubpackOverridesBaseAndOtherSubpacksAreIgnored() throws IOException {
		Path packDirectory = temporaryDirectory.resolve("subpack");
		Files.createDirectories(packDirectory.resolve("textures/blocks"));
		Files.createDirectories(packDirectory.resolve("subpacks/lite/textures/blocks"));
		Files.createDirectories(packDirectory.resolve("subpacks/full/textures/blocks"));
		Files.write(packDirectory.resolve("textures/blocks/stone.png"), new byte[]{1});
		Files.write(packDirectory.resolve("subpacks/lite/textures/blocks/stone.png"), new byte[]{2});
		Files.write(packDirectory.resolve("subpacks/full/textures/blocks/stone.png"), new byte[]{3});
		BedrockPack pack = pack("Subpack", "subpack", packDirectory, "manifest.json");

		var result = pipeline().generateConfigured(List.of(new BedrockTranslationPipeline.ConfiguredPack(pack,
			new BedrockPackConfiguration(Optional.of("lite"), Map.of()))));

		assertArrayEquals(new byte[]{2}, result.resources().resources().get(id("textures/block/stone.png")));
	}

	@Test
	void resolvesTerrainTextureAliasesInsteadOfOnlyPhysicalFilenames() throws IOException {
		Path packDirectory = temporaryDirectory.resolve("terrain-alias");
		Files.createDirectories(packDirectory.resolve("textures/blocks"));
		Files.write(packDirectory.resolve("textures/blocks/grass_top.png"), new byte[]{42});
		Files.writeString(packDirectory.resolve("textures/terrain_texture.json"), """
			{"texture_data":{"grass_side":{"textures":{"path":"textures/blocks/grass_top","overlay_color":"#70a558"}}}}
			""");

		var result = pipeline().generate(List.of(pack("Aliases", "aliases", packDirectory, "manifest.json")));

		assertArrayEquals(new byte[]{42}, result.resources().resources().get(id("textures/block/grass_block_top.png")));
		assertArrayEquals(new byte[]{42}, result.resources().resources().get(id("textures/block/grass_block_side_overlay.png")));
	}

	@Test
	void translatesBedrockBlockFaceRemappingThatReusesVanillaTextures() throws IOException {
		Path packDirectory = temporaryDirectory.resolve("block-faces");
		Files.createDirectories(packDirectory.resolve("textures"));
		Files.writeString(packDirectory.resolve("textures/terrain_texture.json"), """
			{"texture_data":{"grass_side":{"textures":{"path":"textures/blocks/grass_top"}},"grass_top":{"textures":"textures/blocks/grass_top"}}}
			""");
		Files.writeString(packDirectory.resolve("blocks.json"), """
			{"grass":{"textures":{"down":"grass_side","side":"grass_side","up":"grass_top"}}}
			""");

		var result = pipeline().generate(List.of(pack("Faces", "faces", packDirectory, "manifest.json")));
		byte[] model = result.resources().resources().get(id("models/block/grass_block.json"));

		assertTrue(new String(model).contains("\"side\":\"minecraft:block/grass_block_side_overlay\""));
		var parsed = com.google.gson.JsonParser.parseString(new String(model)).getAsJsonObject();
		var faces = parsed.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("faces");
		assertFalse(faces.getAsJsonObject("down").has("tintindex"));
		assertFalse(parsed.getAsJsonArray("elements").get(1).getAsJsonObject()
			.getAsJsonObject("faces").getAsJsonObject("north").has("tintindex"));
		assertTrue(new String(model).contains("\"tintindex\":0"));
	}

	@Test
	void translatesBedrockBlockModelsWhileKeepingVanillaBlockstates() throws IOException {
		Path packDirectory = temporaryDirectory.resolve("block-models");
		Files.createDirectories(packDirectory.resolve("textures"));
		Files.writeString(packDirectory.resolve("textures/terrain_texture.json"), """
			{"texture_data":{
			  "crafting_table_bottom":{"textures":"textures/blocks/crafting_table_bottom"},
			  "crafting_table_top":{"textures":"textures/blocks/crafting_table_top"},
			  "crafting_table_front":{"textures":"textures/blocks/crafting_table_front"},
			  "crafting_table_side":{"textures":"textures/blocks/crafting_table_side"},
			  "furnace_front_off":{"textures":"textures/blocks/furnace_front_off"},
			  "furnace_side":{"textures":"textures/blocks/furnace_side"},
			  "furnace_top":{"textures":"textures/blocks/furnace_top"},
			  "mushroom_brown_3d_top":{"textures":"textures/blocks/mushroom_brown_3d_top"},
			  "mushroom_brown_3d_side":{"textures":"textures/blocks/mushroom_brown_3d_side"},
			  "mushroom_brown_3d":{"textures":"textures/blocks/mushroom_brown_3d"},
			  "dripstone_block_3d":{"textures":"textures/blocks/dripstone_block_3d"},
			  "hopper_inside":{"textures":"textures/blocks/hopper_inside"},
			  "hopper_top":{"textures":"textures/blocks/hopper_top"},
			  "hopper_outside_north":{"textures":"textures/blocks/hopper_outside_north"}
			}}
			""");
		Files.writeString(packDirectory.resolve("blocks.json"), """
			{
			  "crafting_table":{"textures":{"down":"crafting_table_bottom","up":"crafting_table_top","north":"crafting_table_front","south":"crafting_table_front","west":"crafting_table_side","east":"crafting_table_side"}},
			  "furnace":{"textures":{"down":"furnace_top","up":"furnace_top","north":"furnace_side","south":"furnace_front_off","west":"furnace_side","east":"furnace_side"}},
			  "brown_mushroom":{"blockshape":"azalea","textures":{"up":"mushroom_brown_3d_top","north":"mushroom_brown_3d_side","east":"mushroom_brown_3d"}},
			  "pointed_dripstone":{"blockshape":"block","textures":"dripstone_block_3d"},
			  "hopper":{"textures":{"down":"hopper_inside","up":"hopper_top","north":"hopper_outside_north","south":"hopper_outside_north","west":"hopper_outside_north","east":"hopper_outside_north"}}
			}
			""");

		var result = pipeline().generate(List.of(pack("Models", "models", packDirectory, "manifest.json")));
		String crafting = text(result, "models/block/crafting_table.json");
		String furnace = text(result, "models/block/furnace.json");
		String mushroom = text(result, "models/block/brown_mushroom.json");
		String dripstone = text(result, "models/block/pointed_dripstone_up_tip.json");
		String hopper = text(result, "models/block/hopper.json");

		assertTrue(crafting.contains("minecraft:block/cube"));
		assertTrue(crafting.contains("minecraft:block/crafting_table_front"));
		assertTrue(furnace.contains("minecraft:block/orientable"));
		assertTrue(furnace.contains("minecraft:block/furnace_front"));
		assertTrue(mushroom.contains("minecraft:block/template_azalea"));
		assertTrue(mushroom.contains("\"plant\":\"minecraft:block/mushroom_brown_3d\""));
		assertTrue(dripstone.contains("minecraft:block/cube_all"));
		assertTrue(hopper.contains("\"elements\""));
		assertTrue(hopper.contains("minecraft:block/hopper_outside_north"));
	}

	@Test
	void convertsBedrockFlipbooksToJavaAnimationMetadata() throws IOException {
		Path packDirectory = temporaryDirectory.resolve("animations");
		Files.createDirectories(packDirectory.resolve("textures/blocks"));
		Files.write(packDirectory.resolve("textures/blocks/amethyst_block.png"), new byte[]{1, 2, 3});
		Files.writeString(packDirectory.resolve("textures/flipbook_textures.json"), """
			[{"flipbook_texture":"textures/blocks/amethyst_block","atlas_tile":"amethyst_block",
			  "ticks_per_frame":8,"blend_frames":true,"frames":[0,1,2,1]}]
			""");

		var result = pipeline().generate(List.of(pack("Animation", "animation", packDirectory, "manifest.json")));
		byte[] metadata = result.resources().resources().get(id("textures/block/amethyst_block.png.mcmeta"));
		String json = new String(metadata, java.nio.charset.StandardCharsets.UTF_8);

		assertTrue(json.contains("\"frametime\":8"));
		assertTrue(json.contains("\"interpolate\":true"));
		assertTrue(json.contains("\"frames\":[0,1,2,1]"));
	}

	@Test
	void keepsFlipbookMetadataOnRenamedJavaTextures() throws IOException {
		Path packDirectory = temporaryDirectory.resolve("renamed-animation");
		Files.createDirectories(packDirectory.resolve("textures/blocks"));
		Files.write(packDirectory.resolve("textures/blocks/pumpkin_face_on.png"), png16By48());
		Files.writeString(packDirectory.resolve("textures/flipbook_textures.json"), """
			[{"flipbook_texture":"textures/blocks/pumpkin_face_on","atlas_tile":"pumpkin_face_flipbook",
			  "ticks_per_frame":8,"blend_frames":true,"frames":[0,0,1,0,1,2]}]
			""");

		var result = pipeline().generate(List.of(pack("Pumpkin", "pumpkin", packDirectory, "manifest.json")));
		String metadata = text(result, "textures/block/jack_o_lantern.png.mcmeta");

		assertTrue(metadata.contains("\"frametime\":8"));
		assertTrue(metadata.contains("\"frames\":[0,0,1,0,1,2]"));
	}

	@Test
	void restoresBedrockVanillaAnimationMetadataForImplicitSpriteSheets() throws IOException {
		Path packDirectory = temporaryDirectory.resolve("implicit-animation");
		Files.createDirectories(packDirectory.resolve("textures/blocks"));
		Files.write(packDirectory.resolve("textures/blocks/stonecutter2_saw.png"), png16By48());

		var result = pipeline().generate(List.of(pack("Implicit", "implicit", packDirectory, "manifest.json")));
		byte[] metadata = result.resources().resources().get(id("textures/block/stonecutter_saw.png.mcmeta"));

		assertTrue(new String(metadata, java.nio.charset.StandardCharsets.UTF_8).contains("\"frametime\":1"));
	}

	@Test
	void animatesAnUndeclaredTallTextureAsASequentialFlipbook() throws IOException {
		Path packDirectory = temporaryDirectory.resolve("static-tall");
		Files.createDirectories(packDirectory.resolve("textures/blocks"));
		Files.write(packDirectory.resolve("textures/blocks/custom_panel.png"), png16By48());

		var result = pipeline().generate(List.of(pack("Static", "static", packDirectory, "manifest.json")));

		assertTrue(result.resources().resources().containsKey(id("textures/block/custom_panel.png")));
		String metadata = text(result, "textures/block/custom_panel.png.mcmeta");
		assertTrue(metadata.contains("\"frametime\":10"));
	}

	@Test
	void resolvesTerrainVariationsToTheirDeclaredFlipbookTiming() throws IOException {
		Path packDirectory = temporaryDirectory.resolve("animated-variation");
		Files.createDirectories(packDirectory.resolve("textures/blocks"));
		byte[] animationImage = png16By48();
		Files.write(packDirectory.resolve("textures/blocks/diamond_ore_0.png"), animationImage);
		Files.writeString(packDirectory.resolve("textures/terrain_texture.json"), """
			{"texture_data":{"diamond_ore":{"textures":[{"variations":[
			  {"path":"textures/blocks/diamond_ore_0"}
			]}]}}}
			""");
		Files.writeString(packDirectory.resolve("textures/flipbook_textures.json"), """
			[{"flipbook_texture":"textures/blocks/diamond_ore_0","atlas_tile":"diamond_ore_flipbook",
			  "ticks_per_frame":7,"blend_frames":true,"frames":[0,1,2]}]
			""");

		var result = pipeline().generate(List.of(pack("Variation", "variation", packDirectory, "manifest.json")));

		assertArrayEquals(animationImage,
			result.resources().resources().get(id("textures/block/diamond_ore.png")));
		String metadata = text(result, "textures/block/diamond_ore.png.mcmeta");
		assertTrue(metadata.contains("\"frametime\":7"));
		assertTrue(metadata.contains("\"interpolate\":true"));
		assertTrue(metadata.contains("\"frames\":[0,1,2]"));
	}

	@Test
	void makesBedrockMaterialAlphaOpaqueWhenTheVanillaBlockIsOpaque() throws IOException {
		Path packDirectory = temporaryDirectory.resolve("material-alpha");
		Files.createDirectories(packDirectory.resolve("textures/blocks"));
		byte[] nearlyTransparent = solidPng(0x01CC0011);
		Files.write(packDirectory.resolve("textures/blocks/redstone_block.png"), nearlyTransparent);
		Files.write(packDirectory.resolve("textures/blocks/glass.png"), nearlyTransparent);

		var result = pipeline().generate(List.of(pack("Alpha", "alpha", packDirectory, "manifest.json")));

		assertEquals(255, firstPixelAlpha(result.resources().resources().get(id("textures/block/redstone_block.png"))));
		assertEquals(1, firstPixelAlpha(result.resources().resources().get(id("textures/block/glass.png"))));
	}

	@Test
	void usesBedrockVanillaTimingInsteadOfJavaTimingForImplicitAnimations() throws IOException {
		Path packDirectory = temporaryDirectory.resolve("short-lava");
		Files.createDirectories(packDirectory.resolve("textures/blocks"));
		Files.write(packDirectory.resolve("textures/blocks/lava_still.png"), png16By48());

		var result = pipeline().generate(List.of(pack("Lava", "lava", packDirectory, "manifest.json")));
		String metadata = text(result, "textures/block/lava_still.png.mcmeta");

		assertTrue(metadata.contains("\"frametime\":16"));
		assertFalse(metadata.contains("\"frames\""));
	}

	@Test
	void packFlipbookDefinitionOverridesBedrockVanillaTiming() throws IOException {
		Path packDirectory = temporaryDirectory.resolve("custom-lava-speed");
		Files.createDirectories(packDirectory.resolve("textures/blocks"));
		Files.write(packDirectory.resolve("textures/blocks/lava_still.png"), png16By48());
		Files.writeString(packDirectory.resolve("textures/flipbook_textures.json"), """
			[{"flipbook_texture":"textures/blocks/lava_still","ticks_per_frame":28,
			  "blend_frames":true,"frames":[0,1,2]}]
			""");

		var result = pipeline().generate(List.of(pack("Lava", "lava", packDirectory, "manifest.json")));
		String metadata = text(result, "textures/block/lava_still.png.mcmeta");

		assertTrue(metadata.contains("\"frametime\":28"));
		assertTrue(metadata.contains("\"interpolate\":true"));
		assertTrue(metadata.contains("\"frames\":[0,1,2]"));
	}

	@Test
	void splitsBedrockBedSheetsIntoMinecraft262BlockTextures() throws IOException {
		Path packDirectory = temporaryDirectory.resolve("beds");
		Files.createDirectories(packDirectory.resolve("textures/entity/bed"));
		Files.write(packDirectory.resolve("textures/entity/bed/black.png"), coordinatePng(128));
		Files.write(packDirectory.resolve("textures/entity/bed/silver.png"), coordinatePng(64));

		var result = pipeline().generate(List.of(pack("Beds", "beds", packDirectory, "manifest.json")));
		Map<Identifier, byte[]> resources = result.resources().resources();

		assertTrue(resources.containsKey(id("textures/block/black_bed_head_up.png")));
		assertTrue(resources.containsKey(id("textures/block/black_bed_foot_south.png")));
		assertTrue(resources.containsKey(id("textures/block/bed_down.png")));
		assertTrue(resources.containsKey(id("textures/block/bed_head_north.png")));
		assertTrue(resources.containsKey(id("textures/block/light_gray_bed_head_up.png")));
		assertFalse(resources.containsKey(id("textures/block/silver_bed_head_up.png")));

		BufferedImage headUp = ImageIO.read(new ByteArrayInputStream(
			resources.get(id("textures/block/black_bed_head_up.png"))));
		assertEquals(32, headUp.getWidth());
		assertEquals(32, headUp.getHeight());
		assertEquals(coordinateColor(12, 12), headUp.getRGB(0, 0));

		BufferedImage footSouth = ImageIO.read(new ByteArrayInputStream(
			resources.get(id("textures/block/black_bed_foot_south.png"))));
		assertEquals(coordinateColor(44, 11), footSouth.getRGB(0, 14));
	}

	private static BedrockTranslationPipeline pipeline() {
		return new BedrockTranslationPipeline(List.of(new VanillaTextureTranslator(new BedrockPackContentReader())));
	}

	private static BedrockPack pack(String name, String uuid, Path source, String location) {
		BedrockVersion version = new BedrockVersion(List.of(1, 0, 0));
		return new BedrockPack(name, "", uuid, version, Optional.empty(),
			List.of(new BedrockModule(uuid + "-module", "resources", version, "")), List.of(), source,
			location, BedrockPackType.RESOURCE, Optional.empty(), List.of());
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("minecraft", path);
	}

	private static String text(BedrockTranslationPipeline.GenerationResult result, String path) {
		byte[] data = result.resources().resources().get(id(path));
		return data == null ? "" : new String(data, java.nio.charset.StandardCharsets.UTF_8);
	}

	private static void writeZipEntry(ZipOutputStream zip, String name, byte[] data) throws IOException {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(data);
		zip.closeEntry();
	}

	private static byte[] png16By48() {
		return Base64.getDecoder().decode(
			"iVBORw0KGgoAAAANSUhEUgAAABAAAAAwCAYAAABo1P7KAAAAJUlEQVR42u3MMQ0AAAgDsP1vGjCHgJC0zCkAAAAAAAAAAAAA3g0X8AABcbj3egAAAABJRU5ErkJggg==");
	}

	private static byte[] solidPng(int argb) throws IOException {
		BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, argb);
		}
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ImageIO.write(image, "png", output);
			return output.toByteArray();
		}
	}

	private static byte[] png(int width, int height) throws IOException {
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", output);
			return output.toByteArray();
		}
	}

	private static byte[] coordinatePng(int size) throws IOException {
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
			image.setRGB(x, y, coordinateColor(x, y));
		}
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ImageIO.write(image, "png", output);
			return output.toByteArray();
		}
	}

	private static int coordinateColor(int x, int y) {
		return 0xFF000000 | x << 8 | y;
	}

	private static int firstPixelAlpha(byte[] png) throws IOException {
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
		return image.getRGB(0, 0) >>> 24;
	}
}
