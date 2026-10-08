package dev.matanyah.bedrockcompat.bedrock.resource;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPack;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPackConfiguration;
import dev.matanyah.bedrockcompat.resource.GeneratedResourceSink;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class VanillaTextureTranslator implements BedrockResourceTranslator {
	private static final String DEFAULT_FLIPBOOKS =
		"/bedrock_compat/mappings/mojang_vanilla_flipbook_textures.json";
	private static final byte[] IMPLICIT_FLIPBOOK_ANIMATION =
		"{\"animation\":{\"frametime\":10}}".getBytes(StandardCharsets.UTF_8);
	private static final Map<String, byte[]> DEFAULT_FLIPBOOK_ANIMATIONS = loadDefaultFlipbookAnimations();
	private final BedrockPackContentReader reader;

	public VanillaTextureTranslator(BedrockPackContentReader reader) {
		this.reader = reader;
	}

	@Override
	public void translate(BedrockPack pack, BedrockPackConfiguration configuration, GeneratedResourceSink sink) throws IOException {
		Map<String, byte[]> content = new LinkedHashMap<>();
		reader.visit(pack, configuration.selectedSubpack(), (path, data) -> {
			String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);
			content.put(normalized, data);
		});
		Map<String, byte[]> animations = parseFlipbookAnimations(content);
		for (Map.Entry<String, byte[]> entry : content.entrySet()) {
			String normalized = entry.getKey();
			if (!normalized.endsWith(".png") && !normalized.endsWith(".tga")) continue;
			String mappingPath = normalized.endsWith(".tga")
				? normalized.substring(0, normalized.length() - 4) + ".png" : normalized;
			byte[] image = normalized.endsWith(".tga") ? decodeTga(entry.getValue()) : entry.getValue();
			if (image == null) continue;
			BedTextureSplitter.translate(mappingPath, image, sink);
			if (ChestTextureSplitter.translate(mappingPath, image, sink)) continue;
			for (String javaPath : TexturePathMappings.map(mappingPath)) {
				putTexture(sink, javaPath, image, animations.get(normalized));
			}
		}
		translateTerrainAliases(content, animations, sink);
		BedrockBlockModelGenerator.generate(content, sink);
	}

	private static void translateTerrainAliases(Map<String, byte[]> content, Map<String, byte[]> animations,
		GeneratedResourceSink sink) throws IOException {
		byte[] metadata = content.get("textures/terrain_texture.json");
		if (metadata == null) return;
		try {
			JsonObject root = JsonParser.parseString(new String(metadata, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonObject textureData = root.getAsJsonObject("texture_data");
			if (textureData == null) return;
			for (Map.Entry<String, JsonElement> tile : textureData.entrySet()) {
				ResolvedTexture resolved = resolveTexture(tile.getValue(), content);
				if (resolved == null) continue;
				String logicalPath = "textures/blocks/" + tile.getKey().toLowerCase(Locale.ROOT) + ".png";
				for (String javaPath : TexturePathMappings.map(logicalPath)) {
					putTexture(sink, javaPath, resolved.data(), animations.get(resolved.path()));
				}
			}
		} catch (RuntimeException exception) {
			throw new IOException("Invalid textures/terrain_texture.json", exception);
		}
	}

	private static ResolvedTexture resolveTexture(JsonElement definition, Map<String, byte[]> content) {
		if (definition == null || definition.isJsonNull()) return null;
		if (definition.isJsonPrimitive() && definition.getAsJsonPrimitive().isString()) {
			String path = definition.getAsString().replace('\\', '/').toLowerCase(Locale.ROOT);
			if (!path.endsWith(".png")) path += ".png";
			byte[] data = content.get(path);
			return data == null ? null : new ResolvedTexture(path, data);
		}
		if (definition.isJsonArray()) {
			for (JsonElement candidate : definition.getAsJsonArray()) {
				ResolvedTexture resolved = resolveTexture(candidate, content);
				if (resolved != null) return resolved;
			}
			return null;
		}
		if (!definition.isJsonObject()) return null;
		JsonObject object = definition.getAsJsonObject();
		if (object.has("path")) return resolveTexture(object.get("path"), content);
		if (object.has("textures")) return resolveTexture(object.get("textures"), content);
		if (object.has("variations")) return resolveTexture(object.get("variations"), content);
		return null;
	}

	private static Map<String, byte[]> parseFlipbookAnimations(Map<String, byte[]> content) throws IOException {
		Map<String, byte[]> animations = new LinkedHashMap<>(DEFAULT_FLIPBOOK_ANIMATIONS);
		byte[] metadata = content.get("textures/flipbook_textures.json");
		if (metadata == null) return Map.copyOf(animations);
		try {
			animations.putAll(parseFlipbookDefinitions(metadata));
			return Map.copyOf(animations);
		} catch (RuntimeException exception) {
			throw new IOException("Invalid textures/flipbook_textures.json", exception);
		}
	}

	private static Map<String, byte[]> loadDefaultFlipbookAnimations() {
		try (InputStream input = VanillaTextureTranslator.class.getResourceAsStream(DEFAULT_FLIPBOOKS)) {
			if (input == null) throw new IllegalStateException("Missing bundled Bedrock vanilla flipbook mappings");
			return Map.copyOf(parseFlipbookDefinitions(input.readAllBytes()));
		} catch (IOException | RuntimeException exception) {
			throw new ExceptionInInitializerError(exception);
		}
	}

	private static Map<String, byte[]> parseFlipbookDefinitions(byte[] metadata) {
		JsonElement parsed = JsonParser.parseString(new String(metadata, StandardCharsets.UTF_8));
		if (!parsed.isJsonArray()) throw new IllegalArgumentException("flipbook root is not an array");
		Map<String, byte[]> animations = new LinkedHashMap<>();
		for (JsonElement element : parsed.getAsJsonArray()) {
			if (!element.isJsonObject()) continue;
			JsonObject flipbook = element.getAsJsonObject();
			JsonElement pathElement = flipbook.get("flipbook_texture");
			if (pathElement == null || !pathElement.isJsonPrimitive()) continue;
			String path = pathElement.getAsString().replace('\\', '/').toLowerCase(Locale.ROOT);
			if (!path.endsWith(".png")) path += ".png";
			JsonObject animation = new JsonObject();
			animation.addProperty("frametime", positiveInteger(flipbook.get("ticks_per_frame"), 1));
			if (flipbook.has("blend_frames")) {
				animation.addProperty("interpolate", flipbook.get("blend_frames").getAsBoolean());
			}
			JsonElement frames = flipbook.get("frames");
			if (frames != null && frames.isJsonArray() && !frames.getAsJsonArray().isEmpty()) {
				animation.add("frames", frames.deepCopy());
			}
			JsonObject javaMetadata = new JsonObject();
			javaMetadata.add("animation", animation);
			animations.put(path, javaMetadata.toString().getBytes(StandardCharsets.UTF_8));
		}
		return animations;
	}

	private static int positiveInteger(JsonElement value, int fallback) {
		if (value == null) return fallback;
		int parsed = value.getAsInt();
		return parsed > 0 ? parsed : fallback;
	}

	/** Decodes raw/RLE true-colour and grayscale TGA, with both origin flags. */
	static byte[] decodeTga(byte[] data) throws IOException {
		// Some Bedrock subpacks store PNG bytes under .tga paths. Detect the actual
		// format before decoding; these resources need no pixel transformation.
		byte[] png={(byte)137,80,78,71,13,10,26,10};
		if(data.length>=24&&java.util.Arrays.equals(data,0,8,png,0,8)) {
			var header=java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.BIG_ENDIAN);
			int w=header.getInt(16),h=header.getInt(20);
			if(w<1||h<1||w>8192||h>8192||(long)w*h>16_777_216)return null;
			return data;
		}
		if (data.length < 18) return null;
		int idLength = Byte.toUnsignedInt(data[0]);
		int colorMapType = Byte.toUnsignedInt(data[1]);
		int imageType = Byte.toUnsignedInt(data[2]);
		boolean gray=imageType==3||imageType==11,rle=imageType==10||imageType==11;
		if (colorMapType != 0 || !java.util.Set.of(2,3,10,11).contains(imageType)) return null;
		int width = Short.toUnsignedInt((short) ((data[12] & 0xFF) | ((data[13] & 0xFF) << 8)));
		int height = Short.toUnsignedInt((short) ((data[14] & 0xFF) | ((data[15] & 0xFF) << 8)));
		int bits = Byte.toUnsignedInt(data[16]);
		if (width < 1 || height < 1 || width > 8192 || height > 8192 || (long)width*height>16_777_216
			|| (!gray && bits != 24 && bits != 32) || (gray && bits != 8)) return null;
		int bytesPerPixel = bits / 8;
		long expected = 18L + idLength + (long) width * height * bytesPerPixel;
		if (!rle && expected > data.length) return null;
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		boolean topOrigin = (data[17] & 0x20) != 0;
		int offset = 18 + idLength;
		int pixel=0,total=width*height;
		boolean rightOrigin=(data[17]&0x10)!=0;
		while(pixel<total) {
			int count=1;boolean repeat=false;
			if(rle) {
				if(offset>=data.length)return null;
				int packet=Byte.toUnsignedInt(data[offset++]);count=(packet&127)+1;repeat=(packet&128)!=0;
			}
			if(pixel+count>total)return null;
			int rgba=0;
			for(int i=0;i<count;i++) {
				if(!repeat||i==0) {
					if(offset+bytesPerPixel>data.length)return null;
					int blue=Byte.toUnsignedInt(data[offset++]);
					int green=gray?blue:Byte.toUnsignedInt(data[offset++]);
					int red=gray?blue:Byte.toUnsignedInt(data[offset++]);
					int alpha=gray||bits==24?255:Byte.toUnsignedInt(data[offset++]);
					rgba=alpha<<24|red<<16|green<<8|blue;
				}
				int x=pixel%width,y=pixel/width;
				image.setRGB(rightOrigin?width-1-x:x,topOrigin?y:height-1-y,rgba);pixel++;
			}
		}
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			if (!ImageIO.write(image, "PNG", output)) return null;
			return output.toByteArray();
		}
	}

	private static void putTexture(GeneratedResourceSink sink, String javaPath, byte[] image, byte[] animation) {
		if (!Identifier.isValidPath(javaPath)) return;
		byte[] javaImage = JavaTextureOpacity.conformToVanilla(javaPath, image);
		sink.put(Identifier.fromNamespaceAndPath("minecraft", javaPath), javaImage);
		if (animation == null && looksLikeSpriteSheet(image)) {
			animation = vanillaAnimation(javaPath);
			if (animation == null) animation = IMPLICIT_FLIPBOOK_ANIMATION;
		}
		animation = sanitizeAnimation(animation, image);
		if (animation != null) {
			String metadataPath = javaPath + ".mcmeta";
			if (Identifier.isValidPath(metadataPath)) {
				sink.put(Identifier.fromNamespaceAndPath("minecraft", metadataPath), animation);
			}
		}
	}

	private static byte[] vanillaAnimation(String javaPath) {
		String resource = "/assets/minecraft/" + javaPath + ".mcmeta";
		try (InputStream input = VanillaTextureTranslator.class.getResourceAsStream(resource)) {
			if (input != null) return input.readAllBytes();
		} catch (IOException ignored) {
			// Fall through to the implicit sequential animation used for undeclared sprite sheets.
		}
		return null;
	}

	private static byte[] sanitizeAnimation(byte[] metadata, byte[] image) {
		if (metadata == null) return null;
		int frameCount = spriteFrameCount(image);
		if (frameCount < 1) return metadata;
		try {
			JsonObject root = JsonParser.parseString(new String(metadata, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonObject animation = root.getAsJsonObject("animation");
			if (animation == null || !animation.has("frames") || !animation.get("frames").isJsonArray()) return metadata;
			JsonArray validFrames = new JsonArray();
			for (JsonElement frame : animation.getAsJsonArray("frames")) {
				int index;
				if (frame.isJsonPrimitive() && frame.getAsJsonPrimitive().isNumber()) index = frame.getAsInt();
				else if (frame.isJsonObject() && frame.getAsJsonObject().has("index")) {
					index = frame.getAsJsonObject().get("index").getAsInt();
				} else continue;
				if (index >= 0 && index < frameCount) validFrames.add(frame.deepCopy());
			}
			if (validFrames.isEmpty()) animation.remove("frames");
			else animation.add("frames", validFrames);
			return root.toString().getBytes(StandardCharsets.UTF_8);
		} catch (RuntimeException ignored) {
			return metadata;
		}
	}

	private static boolean looksLikeSpriteSheet(byte[] png) {
		return spriteFrameCount(png) > 1;
	}

	private static int spriteFrameCount(byte[] png) {
		if (png.length < 24 || png[0] != (byte) 0x89 || png[1] != 'P' || png[2] != 'N' || png[3] != 'G') return 0;
		int width = integerAt(png, 16);
		int height = integerAt(png, 20);
		return width > 0 && height >= width && height % width == 0 ? height / width : 0;
	}

	private static int integerAt(byte[] bytes, int offset) {
		return (bytes[offset] & 0xFF) << 24 | (bytes[offset + 1] & 0xFF) << 16
			| (bytes[offset + 2] & 0xFF) << 8 | bytes[offset + 3] & 0xFF;
	}

	private record ResolvedTexture(String path, byte[] data) {
	}

	static String mapPath(String path) {
		return TexturePathMappings.map(path).stream().findFirst().orElse(null);
	}
}
