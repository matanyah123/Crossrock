package dev.matanyah.bedrockcompat.bedrock.resource;

import dev.matanyah.bedrockcompat.resource.GeneratedResourceSink;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * Converts Bedrock's 64x64 bed entity sheet into Minecraft 26.2's block sprites.
 * The boxes and transforms invert GeyserMC PackConverter's MIT-licensed bed conversion.
 */
final class BedTextureSplitter {
	private static final String PREFIX = "textures/entity/bed/";
	private static final Map<String, String> COLORS = Map.ofEntries(
		Map.entry("black", "black"), Map.entry("blue", "blue"), Map.entry("brown", "brown"),
		Map.entry("cyan", "cyan"), Map.entry("gray", "gray"), Map.entry("green", "green"),
		Map.entry("light_blue", "light_blue"), Map.entry("silver", "light_gray"),
		Map.entry("light_gray", "light_gray"), Map.entry("lime", "lime"),
		Map.entry("magenta", "magenta"), Map.entry("orange", "orange"), Map.entry("pink", "pink"),
		Map.entry("purple", "purple"), Map.entry("red", "red"), Map.entry("white", "white"),
		Map.entry("yellow", "yellow")
	);

	private BedTextureSplitter() {
	}

	static boolean translate(String path, byte[] png, GeneratedResourceSink sink) throws IOException {
		String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);
		if (!normalized.startsWith(PREFIX) || !normalized.endsWith(".png")) return false;
		String bedrockColor = normalized.substring(PREFIX.length(), normalized.length() - 4);
		if (bedrockColor.contains("/") || bedrockColor.endsWith("_mers")) return false;
		String javaColor = COLORS.get(bedrockColor);
		if (javaColor == null) return false;

		BufferedImage source = ImageIO.read(new ByteArrayInputStream(png));
		if (source == null || source.getWidth() < 64 || source.getHeight() < 64
			|| source.getWidth() % 64 != 0 || source.getHeight() % 64 != 0) return false;

		put(sink, javaColor + "_bed_head_up", simple(source, new Box(6, 6, 16, 16), Transform.NONE));
		put(sink, javaColor + "_bed_foot_up", simple(source, new Box(6, 22, 16, 16), Transform.NONE));
		put(sink, javaColor + "_bed_head_down", simple(source, new Box(28, 6, 16, 16), Transform.ROTATE_180));
		put(sink, javaColor + "_bed_foot_down", simple(source, new Box(28, 22, 16, 16), Transform.ROTATE_180));
		put(sink, javaColor + "_bed_foot_south", multipart(source,
			part(22, 0, 16, 6, Transform.FLIP_Y, 0, 7),
			part(0, 47, 3, 3, Transform.ROTATE_90, 0, 13), part(6, 47, 3, 3, Transform.ROTATE_90, 3, 13),
			part(12, 47, 3, 3, Transform.ROTATE_270, 10, 13), part(18, 47, 3, 3, Transform.ROTATE_270, 13, 13)));
		put(sink, javaColor + "_bed_foot_west", multipart(source,
			part(0, 22, 6, 16, Transform.ROTATE_270, 0, 7),
			part(3, 47, 3, 3, Transform.ROTATE_180, 7, 13),
			part(3, 44, 3, 3, Transform.ROTATE_180, 10, 13), part(6, 44, 3, 3, Transform.FLIP_X, 13, 13)));
		put(sink, javaColor + "_bed_foot_east", multipart(source,
			part(22, 22, 6, 16, Transform.ROTATE_90, 0, 7),
			part(18, 44, 3, 3, Transform.FLIP_X, 0, 13), part(15, 44, 3, 3, Transform.ROTATE_180, 3, 13),
			part(15, 47, 3, 3, Transform.ROTATE_180, 6, 13)));
		put(sink, javaColor + "_bed_head_east", multipart(source,
			part(22, 6, 6, 16, Transform.ROTATE_90, 0, 7),
			part(15, 41, 3, 3, Transform.ROTATE_180, 7, 13),
			part(18, 38, 3, 3, Transform.FLIP_Y, 10, 13), part(18, 41, 3, 3, Transform.ROTATE_90, 13, 13)));
		put(sink, javaColor + "_bed_head_west", multipart(source,
			part(0, 6, 6, 16, Transform.ROTATE_270, 0, 7),
			part(3, 38, 3, 3, Transform.FLIP_Y, 0, 13), part(6, 38, 3, 3, Transform.FLIP_Y, 3, 13),
			part(3, 41, 3, 3, Transform.ROTATE_180, 6, 13)));
		putModels(sink, javaColor);

		if (bedrockColor.equals("black")) {
			put(sink, "bed_down", simple(source, new Box(28, 6, 16, 16), Transform.ROTATE_180));
			put(sink, "bed_head_north", multipart(source,
				part(6, 0, 16, 6, Transform.ROTATE_180, 0, 7),
				part(15, 38, 3, 3, Transform.FLIP_X, 0, 13), part(12, 41, 3, 3, Transform.ROTATE_270, 3, 13),
				part(6, 41, 3, 3, Transform.ROTATE_90, 10, 13), part(0, 41, 3, 3, Transform.ROTATE_90, 13, 13)));
		}
		return true;
	}

	private static Part part(int x, int y, int width, int height, Transform transform, int outX, int outY) {
		return new Part(new Box(x, y, width, height), transform, outX, outY);
	}

	private static BufferedImage multipart(BufferedImage source, Part... parts) {
		BufferedImage output = new BufferedImage(scaleX(source, 16), scaleY(source, 16), BufferedImage.TYPE_INT_ARGB);
		for (Part part : parts) {
			BufferedImage piece = simple(source, part.box(), part.transform());
			copy(piece, output, scaleX(source, part.outX()), scaleY(source, part.outY()));
		}
		return output;
	}

	private static BufferedImage simple(BufferedImage source, Box box, Transform transform) {
		BufferedImage crop = source.getSubimage(scaleX(source, box.x()), scaleY(source, box.y()),
			scaleX(source, box.width()), scaleY(source, box.height()));
		return transform(crop, transform);
	}

	private static BufferedImage transform(BufferedImage source, Transform transform) {
		return transformOnce(source, transform);
	}

	private static BufferedImage transformOnce(BufferedImage source, Transform transform) {
		if (transform == Transform.NONE) return source;
		int width = source.getWidth();
		int height = source.getHeight();
		boolean quarterTurn = transform == Transform.ROTATE_90 || transform == Transform.ROTATE_270;
		BufferedImage output = new BufferedImage(quarterTurn ? height : width, quarterTurn ? width : height,
			BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
			int targetX;
			int targetY;
			switch (transform) {
				case FLIP_X -> { targetX = width - 1 - x; targetY = y; }
				case FLIP_Y -> { targetX = x; targetY = height - 1 - y; }
				case ROTATE_90 -> { targetX = height - 1 - y; targetY = x; }
				case ROTATE_180 -> { targetX = width - 1 - x; targetY = height - 1 - y; }
				case ROTATE_270 -> { targetX = y; targetY = width - 1 - x; }
				default -> throw new IllegalStateException("Unexpected transform " + transform);
			}
			output.setRGB(targetX, targetY, source.getRGB(x, y));
		}
		return output;
	}

	private static void copy(BufferedImage source, BufferedImage target, int targetX, int targetY) {
		for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
			target.setRGB(targetX + x, targetY + y, source.getRGB(x, y));
		}
	}

	private static int scaleX(BufferedImage image, int coordinate) {
		return coordinate * image.getWidth() / 64;
	}

	private static int scaleY(BufferedImage image, int coordinate) {
		return coordinate * image.getHeight() / 64;
	}

	private static void put(GeneratedResourceSink sink, String name, BufferedImage image) throws IOException {
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			if (!ImageIO.write(image, "PNG", output)) throw new IOException("No PNG writer is available");
			sink.put(Identifier.fromNamespaceAndPath("minecraft", "textures/block/" + name + ".png"), output.toByteArray());
		}
	}

	private static void putModels(GeneratedResourceSink sink, String color) {
		String head = "{\"parent\":\"minecraft:block/template_bed_head\",\"textures\":{"
			+ "\"east\":\"minecraft:block/" + color + "_bed_head_east\","
			+ "\"up\":\"minecraft:block/" + color + "_bed_head_up\","
			+ "\"west\":\"minecraft:block/" + color + "_bed_head_west\","
			+ "\"down\":\"minecraft:block/" + color + "_bed_head_down\"}}";
		String foot = "{\"parent\":\"minecraft:block/template_bed_foot\",\"textures\":{"
			+ "\"east\":\"minecraft:block/" + color + "_bed_foot_east\","
			+ "\"south\":\"minecraft:block/" + color + "_bed_foot_south\","
			+ "\"up\":\"minecraft:block/" + color + "_bed_foot_up\","
			+ "\"west\":\"minecraft:block/" + color + "_bed_foot_west\","
			+ "\"down\":\"minecraft:block/" + color + "_bed_foot_down\"}}";
		putModel(sink, color + "_bed_head", head);
		putModel(sink, color + "_bed_foot", foot);
	}

	private static void putModel(GeneratedResourceSink sink, String name, String json) {
		sink.put(Identifier.fromNamespaceAndPath("minecraft", "models/block/" + name + ".json"),
			json.getBytes(StandardCharsets.UTF_8));
	}

	private record Box(int x, int y, int width, int height) {
	}

	private record Part(Box box, Transform transform, int outX, int outY) {
	}

	private enum Transform {
		NONE, FLIP_X, FLIP_Y, ROTATE_90, ROTATE_180, ROTATE_270
	}
}
