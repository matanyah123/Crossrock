package dev.matanyah.bedrockcompat.bedrock.resource;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.matanyah.bedrockcompat.resource.GeneratedResourceSink;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/** Converts Bedrock block texture assignments while retaining Java's native blockstates and model geometry. */
final class BedrockBlockModelGenerator {
	private static final String[] POINTED_DRIPSTONE_MODELS = {
		"pointed_dripstone", "pointed_dripstone_down_base", "pointed_dripstone_down_frustum",
		"pointed_dripstone_down_middle", "pointed_dripstone_down_tip", "pointed_dripstone_down_tip_merge",
		"pointed_dripstone_up_base", "pointed_dripstone_up_frustum", "pointed_dripstone_up_middle",
		"pointed_dripstone_up_tip", "pointed_dripstone_up_tip_merge"
	};

	private BedrockBlockModelGenerator() {
	}

	static void generate(Map<String, byte[]> content, GeneratedResourceSink sink) throws IOException {
		byte[] metadata = content.get("blocks.json");
		try {
			JsonObject blocks = metadata == null ? new JsonObject()
				: JsonParser.parseString(new String(metadata, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonObject terrain = terrainData(content);
			// Texture-only packs can declare dedicated double-slab atlas tiles without
			// repeating vanilla blocks.json. Explicit block face assignments win.
			if (terrain != null) for (var tile : terrain.entrySet()) {
				String name = tile.getKey();
				if (!name.endsWith("_slab") || !name.contains("double_")
					|| blocks.has(name) || blocks.has("minecraft:" + name)) continue;
				JsonObject faces = new JsonObject();
				faces.addProperty("up", terrain.has(name + "_top") ? name + "_top" : name);
				faces.addProperty("down", terrain.has(name + "_bottom") ? name + "_bottom" : name);
				faces.addProperty("side", terrain.has(name + "_side") ? name + "_side" : name);
				JsonObject block = new JsonObject(); block.add("textures", faces); blocks.add(name, block);
			}
			generateGrassModel(blocks, terrain, content, sink);
			generateBottomTopModel(blocks, terrain, "mycelium", "mycelium", sink);
			generateBottomTopModel(blocks, terrain, "podzol", "podzol", sink);
			generateBottomTopModel(blocks, terrain, "warped_nylium", "warped_nylium", sink);
			generateBottomTopModel(blocks, terrain, "crimson_nylium", "crimson_nylium", sink);
			generateDirtPathModel(blocks, terrain, sink);
			generateSixSidedCube(blocks, terrain, "crafting_table", "crafting_table", sink);
			generateOrientable(blocks, terrain, "furnace", "furnace", sink);
			generateOrientable(blocks, terrain, "lit_furnace", "furnace_on", sink);
			generateOrientable(blocks, terrain, "blast_furnace", "blast_furnace", sink);
			generateOrientable(blocks, terrain, "lit_blast_furnace", "blast_furnace_on", sink);
			generateBeeContainer(blocks, terrain, "beehive", "beehive", sink);
			generateBeeContainer(blocks, terrain, "bee_nest", "bee_nest", sink);
			generateAzaleaShapes(blocks, terrain, sink);
			generateFullBlockShape(blocks, terrain, sink);
			patchHopperModels(blocks, terrain, sink);
			generateDoubleSlabs(blocks, terrain, sink);
		} catch (RuntimeException exception) {
			throw new IOException("Invalid blocks.json", exception);
		}
	}

	private static void generateDoubleSlabs(JsonObject blocks, JsonObject terrain, GeneratedResourceSink sink) throws IOException {
		try (InputStream stream = BedrockBlockModelGenerator.class.getResourceAsStream(
			"/bedrock_compat/mappings/java_26_2_slab_states.json")) {
			if (stream == null) throw new IOException("Missing vanilla slab state mapping");
			JsonObject states = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
			for (var entry : blocks.entrySet()) {
				String name = entry.getKey().replace("minecraft:", "");
				if (!name.endsWith("_slab") || !name.contains("double_") || !entry.getValue().isJsonObject()) continue;
				String slab = name.replace("double_", "");
				if (name.equals("double_stone_slab")) slab = "smooth_stone_slab";
				if (slab.equals("normal_stone_slab")) slab = "stone_slab";
				if (!states.has(slab)) continue; // Never manufacture states for unknown Java blocks.
				JsonElement textures = entry.getValue().getAsJsonObject().get("textures");
				JsonObject slots = new JsonObject();
				boolean complete = true;
				for (String face : new String[]{"down", "up", "north", "south", "west", "east"}) {
					String texture = javaTexture(textureForFace(textures, face), terrain);
					if (texture == null) { complete = false; break; }
					slots.addProperty(face, texture);
				}
				if (!complete) continue;
				slots.addProperty("particle", slots.get("north").getAsString());
				String model = "crossrock/" + slab + "_double";
				putParentModel(sink, model, "minecraft:block/cube", slots);
				JsonObject state = states.getAsJsonObject(slab).deepCopy();
				state.getAsJsonObject("variants").getAsJsonObject("type=double").addProperty("model", "minecraft:block/" + model);
				sink.put(Identifier.withDefaultNamespace("blockstates/" + slab + ".json"), state.toString().getBytes(StandardCharsets.UTF_8));
			}
		}
	}

	private static JsonObject terrainData(Map<String, byte[]> content) {
		byte[] metadata = content.get("textures/terrain_texture.json");
		if (metadata == null) return null;
		JsonObject root = JsonParser.parseString(new String(metadata, StandardCharsets.UTF_8)).getAsJsonObject();
		return root.getAsJsonObject("texture_data");
	}

	private static void generateGrassModel(JsonObject blocks, JsonObject terrain, Map<String,byte[]> content, GeneratedResourceSink sink) {
		JsonObject block = block(blocks, "grass");
		var definition=terrain!=null&&terrain.has("grass_side")?terrain.get("grass_side"):new com.google.gson.JsonPrimitive("textures/blocks/grass_side");
		var resolved=VanillaTextureTranslator.resolveTexture(definition,content);
		if (block == null && resolved == null) return;
		JsonElement textures = block==null?null:block.get("textures");
		String down = block==null?"minecraft:block/dirt":javaTexture(textureForFace(textures, "down"), terrain);
		String up = block==null?"minecraft:block/grass_block_top":javaTexture(textureForFace(textures, "up"), terrain);
		String side = block==null?"minecraft:block/grass_block_side_overlay":javaTexture(textureForFace(textures, "side"), terrain);
		if (down == null || up == null || side == null) return;
		String model = """
			{"parent":"minecraft:block/block","textures":{"particle":"%3$s","down":"%1$s","up":"%2$s","side":"%3$s"},"elements":[{"from":[0,0,0],"to":[16,16,16],"faces":{"down":{"texture":"#down","cullface":"down","tintindex":0},"up":{"texture":"#up","cullface":"up","tintindex":0},"north":{"texture":"#side","cullface":"north","tintindex":0},"south":{"texture":"#side","cullface":"south","tintindex":0},"west":{"texture":"#side","cullface":"west","tintindex":0},"east":{"texture":"#side","cullface":"east","tintindex":0}}}]}
			""".formatted(down, up, side);
		JsonObject layered = JsonParser.parseString(model).getAsJsonObject();
		layered.getAsJsonObject("textures").addProperty("base", "minecraft:block/grass_block_side");
		layered.getAsJsonObject("textures").addProperty("side", "minecraft:block/grass_block_side_overlay");
		JsonObject faces = layered.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("faces");
		faces.getAsJsonObject("down").remove("tintindex");
		JsonObject base = new JsonObject();
		base.add("from", JsonParser.parseString("[0,0,0]"));base.add("to", JsonParser.parseString("[16,16,16]"));
		JsonObject baseFaces = new JsonObject();base.add("faces",baseFaces);
		for (String face : new String[]{"north","south","east","west"}) {
			JsonObject f=faces.getAsJsonObject(face).deepCopy();f.remove("tintindex");f.addProperty("texture","#base");baseFaces.add(face,f);
		}
		layered.getAsJsonArray("elements").add(base);
		if(resolved!=null)GrassTintGeometry.clipOverlay(layered,resolved.data());
		putModel(sink, "grass_block", layered.toString());
	}

	private static void generateBottomTopModel(JsonObject blocks, JsonObject terrain, String bedrockName,
		String javaName, GeneratedResourceSink sink) {
		JsonObject block = block(blocks, bedrockName);
		if (block == null) return;
		JsonElement textures = block.get("textures");
		String bottom = javaTexture(textureForFace(textures, "down"), terrain);
		String top = javaTexture(textureForFace(textures, "up"), terrain);
		String side = javaTexture(textureForFace(textures, "side"), terrain);
		if (bottom == null || top == null || side == null) return;
		putParentModel(sink, javaName, "minecraft:block/cube_bottom_top",
			Map.of("particle", side, "bottom", bottom, "top", top, "side", side));
	}

	private static void generateDirtPathModel(JsonObject blocks, JsonObject terrain, GeneratedResourceSink sink) {
		JsonObject block = block(blocks, "grass_path");
		if (block == null) return;
		JsonElement textures = block.get("textures");
		String down = javaTexture(textureForFace(textures, "down"), terrain);
		String up = javaTexture(textureForFace(textures, "up"), terrain);
		String side = javaTexture(textureForFace(textures, "side"), terrain);
		if (down == null || up == null || side == null) return;
		String model = """
			{"parent":"minecraft:block/block","textures":{"particle":"%3$s","down":"%1$s","up":"%2$s","side":"%3$s"},"elements":[{"from":[0,0,0],"to":[16,15,16],"faces":{"down":{"uv":[0,0,16,16],"texture":"#down","cullface":"down"},"up":{"uv":[0,0,16,16],"texture":"#up"},"north":{"uv":[0,1,16,16],"texture":"#side","cullface":"north"},"south":{"uv":[0,1,16,16],"texture":"#side","cullface":"south"},"west":{"uv":[0,1,16,16],"texture":"#side","cullface":"west"},"east":{"uv":[0,1,16,16],"texture":"#side","cullface":"east"}}}]}
			""".formatted(down, up, side);
		putModel(sink, "dirt_path", model);
	}

	private static void generateSixSidedCube(JsonObject blocks, JsonObject terrain, String bedrockName,
		String javaName, GeneratedResourceSink sink) {
		JsonObject block = block(blocks, bedrockName);
		if (block == null) return;
		JsonElement textures = block.get("textures");
		JsonObject slots = new JsonObject();
		for (String face : new String[]{"down", "up", "north", "south", "west", "east"}) {
			String texture = javaTexture(textureForFace(textures, face), terrain);
			if (texture == null) return;
			slots.addProperty(face, texture);
		}
		slots.addProperty("particle", slots.get("north").getAsString());
		putParentModel(sink, javaName, "minecraft:block/cube", slots);
	}

	private static void generateOrientable(JsonObject blocks, JsonObject terrain, String bedrockName,
		String javaName, GeneratedResourceSink sink) {
		JsonObject block = block(blocks, bedrockName);
		if (block == null) return;
		JsonElement textures = block.get("textures");
		String front = javaTexture(findNamedHorizontalTexture(textures, "front"), terrain);
		String side = javaTexture(findNamedHorizontalTexture(textures, "side"), terrain);
		String top = javaTexture(textureForFace(textures, "up"), terrain);
		if (front == null || side == null || top == null) return;
		putParentModel(sink, javaName, "minecraft:block/orientable",
			Map.of("particle", side, "front", front, "side", side, "top", top));
	}

	private static void generateBeeContainer(JsonObject blocks, JsonObject terrain, String bedrockName,
		String javaName, GeneratedResourceSink sink) {
		JsonObject block = block(blocks, bedrockName);
		if (block == null) return;
		JsonElement textures = block.get("textures");
		String bottom = javaTexture(textureForFace(textures, "down"), terrain);
		String top = javaTexture(textureForFace(textures, "up"), terrain);
		String side = javaTexture(findNamedHorizontalTexture(textures, "side"), terrain);
		String front = javaTexture(findNamedHorizontalTexture(textures, "front"), terrain);
		if (bottom == null || top == null || side == null || front == null) return;
		putParentModel(sink, javaName + "_empty", "minecraft:block/orientable_with_bottom",
			Map.of("bottom", bottom, "top", top, "side", side, "front", front, "particle", side));
		String honey = javaTexture(javaName + "_front_honey", terrain);
		putParentModel(sink, javaName + "_honey", "minecraft:block/orientable_with_bottom",
			Map.of("bottom", bottom, "top", top, "side", side, "front", honey, "particle", side));
	}

	private static void generateAzaleaShapes(JsonObject blocks, JsonObject terrain, GeneratedResourceSink sink) {
		for (String name : new String[]{"brown_mushroom", "red_mushroom", "crimson_fungus", "warped_fungus"}) {
			JsonObject block = block(blocks, name);
			if (block == null || !"azalea".equals(string(block.get("blockshape")))) continue;
			JsonElement textures = block.get("textures");
			String top = javaTexture(textureForFace(textures, "up"), terrain);
			String side = javaTexture(textureForFace(textures, "north"), terrain);
			String plant = javaTexture(textureForFace(textures, "east"), terrain);
			if (top == null || side == null || plant == null) continue;
			putParentModel(sink, name, "minecraft:block/template_azalea",
				Map.of("particle", plant, "plant", plant, "top", top, "side", side));
		}
	}

	private static void generateFullBlockShape(JsonObject blocks, JsonObject terrain, GeneratedResourceSink sink) {
		JsonObject block = block(blocks, "pointed_dripstone");
		if (block == null || !"block".equals(string(block.get("blockshape")))) return;
		String texture = javaTexture(textureForFace(block.get("textures"), "all"), terrain);
		if (texture == null) return;
		for (String model : POINTED_DRIPSTONE_MODELS) {
			putParentModel(sink, model, "minecraft:block/cube_all", Map.of("all", texture));
		}
	}

	private static void patchHopperModels(JsonObject blocks, JsonObject terrain, GeneratedResourceSink sink)
		throws IOException {
		JsonObject block = block(blocks, "hopper");
		if (block == null) return;
		JsonElement textures = block.get("textures");
		String inside = javaTexture(textureForFace(textures, "down"), terrain);
		String top = javaTexture(textureForFace(textures, "up"), terrain);
		String side = javaTexture(findNamedHorizontalTexture(textures, "outside"), terrain);
		if (inside == null || top == null || side == null) return;
		Map<String, String> replacements = Map.of("particle", side, "inside", inside, "top", top, "side", side);
		patchVanillaModel(sink, "hopper", replacements);
		patchVanillaModel(sink, "hopper_side", replacements);
	}

	private static void patchVanillaModel(GeneratedResourceSink sink, String name, Map<String, String> replacements)
		throws IOException {
		String resource = "/assets/minecraft/models/block/" + name + ".json";
		try (InputStream input = BedrockBlockModelGenerator.class.getResourceAsStream(resource)) {
			if (input == null) return;
			JsonObject model = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonObject textures = model.getAsJsonObject("textures");
			if (textures == null) {
				textures = new JsonObject();
				model.add("textures", textures);
			}
			for (Map.Entry<String, String> replacement : replacements.entrySet()) {
				textures.addProperty(replacement.getKey(), replacement.getValue());
			}
			putModel(sink, name, model.toString());
		}
	}

	private static JsonObject block(JsonObject blocks, String name) {
		JsonElement value = blocks.get(name);
		if (value == null) value = blocks.get("minecraft:" + name);
		return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
	}

	private static String textureForFace(JsonElement textures, String face) {
		if (textures == null) return null;
		if (textures.isJsonPrimitive() && textures.getAsJsonPrimitive().isString()) return textures.getAsString();
		if (!textures.isJsonObject()) return null;
		JsonObject object = textures.getAsJsonObject();
		JsonElement value = object.get(face);
		if (value == null && !face.equals("up") && !face.equals("down")) value = object.get("side");
		if (value == null) value = object.get("all");
		if (value == null && object.size() == 1) value = object.entrySet().iterator().next().getValue();
		return string(value);
	}

	private static String findNamedHorizontalTexture(JsonElement textures, String fragment) {
		if (textures == null || !textures.isJsonObject()) return textureForFace(textures, "side");
		JsonObject object = textures.getAsJsonObject();
		for (String face : new String[]{"north", "south", "west", "east"}) {
			String value = string(object.get(face));
			if (value != null && value.toLowerCase(Locale.ROOT).contains(fragment)) return value;
		}
		return textureForFace(textures, "side");
	}

	private static String javaTexture(String tile, JsonObject terrain) {
		if (tile == null || tile.isBlank()) return null;
		String physical = terrainPath(tile, terrain);
		String path = physical == null ? "textures/blocks/" + tile + ".png" : physical;
		for (String javaPath : TexturePathMappings.map(path)) {
			if (javaPath.startsWith("textures/") && javaPath.endsWith(".png")) {
				return "minecraft:" + javaPath.substring("textures/".length(), javaPath.length() - ".png".length());
			}
		}
		return null;
	}

	private static String terrainPath(String tile, JsonObject terrain) {
		if (terrain == null) return null;
		String path = firstPath(terrain.get(tile));
		if (path == null) return null;
		path = path.replace('\\', '/').toLowerCase(Locale.ROOT);
		return path.endsWith(".png") ? path : path + ".png";
	}

	private static String firstPath(JsonElement definition) {
		if (definition == null || definition.isJsonNull()) return null;
		if (definition.isJsonPrimitive() && definition.getAsJsonPrimitive().isString()) return definition.getAsString();
		if (definition.isJsonArray()) {
			for (JsonElement candidate : definition.getAsJsonArray()) {
				String path = firstPath(candidate);
				if (path != null) return path;
			}
			return null;
		}
		if (!definition.isJsonObject()) return null;
		JsonObject object = definition.getAsJsonObject();
		if (object.has("path")) return firstPath(object.get("path"));
		if (object.has("textures")) return firstPath(object.get("textures"));
		return null;
	}

	private static String string(JsonElement value) {
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
			? value.getAsString() : null;
	}

	private static void putParentModel(GeneratedResourceSink sink, String name, String parent,
		Map<String, String> textures) {
		JsonObject slots = new JsonObject();
		textures.forEach(slots::addProperty);
		putParentModel(sink, name, parent, slots);
	}

	private static void putParentModel(GeneratedResourceSink sink, String name, String parent, JsonObject textures) {
		JsonObject model = new JsonObject();
		model.addProperty("parent", parent);
		model.add("textures", textures);
		putModel(sink, name, model.toString());
	}

	private static void putModel(GeneratedResourceSink sink, String name, String json) {
		sink.put(Identifier.fromNamespaceAndPath("minecraft", "models/block/" + name + ".json"),
			json.getBytes(StandardCharsets.UTF_8));
	}
}
