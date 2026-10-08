package dev.matanyah.bedrockcompat.bedrock.resource;

import dev.matanyah.bedrockcompat.resource.GeneratedResourceSink;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/** Converts Bedrock's combined double-chest sheet to Java's left and right sheets. */
final class ChestTextureSplitter {
	private static final String PREFIX = "textures/entity/chest/";
	private static final Map<String, String> SINGLE_CHESTS = Map.ofEntries(
		Map.entry("normal", "normal"), Map.entry("trapped", "trapped"),
		Map.entry("ender", "ender"), Map.entry("christmas", "christmas"),
		Map.entry("copper_default", "copper"), Map.entry("copper_exposed", "copper_exposed"),
		Map.entry("copper_weathered", "copper_weathered"), Map.entry("copper_oxidized", "copper_oxidized")
	);
	private static final Map<String, ChestNames> DOUBLE_CHESTS = Map.ofEntries(
		Map.entry("double_normal", new ChestNames("normal_left", "normal_right")),
		Map.entry("trapped_double", new ChestNames("trapped_left", "trapped_right")),
		Map.entry("christmas_double", new ChestNames("christmas_left", "christmas_right")),
		Map.entry("copper_default_double", new ChestNames("copper_left", "copper_right")),
		Map.entry("copper_exposed_double", new ChestNames("copper_exposed_left", "copper_exposed_right")),
		Map.entry("copper_weathered_double", new ChestNames("copper_weathered_left", "copper_weathered_right")),
		Map.entry("copper_oxidized_double", new ChestNames("copper_oxidized_left", "copper_oxidized_right"))
	);

	private ChestTextureSplitter() {
	}

	static boolean translate(String path, byte[] png, GeneratedResourceSink sink) throws IOException {
		String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);
		if (!normalized.startsWith(PREFIX) || !normalized.endsWith(".png")) return false;
		String name = normalized.substring(PREFIX.length(), normalized.length() - 4);
		ChestNames outputs = DOUBLE_CHESTS.get(name);
		BufferedImage source = ImageIO.read(new ByteArrayInputStream(png));
		if (source == null) return false;
		String singleOutput = SINGLE_CHESTS.get(name);
		if (singleOutput != null) return translateSingle(source, singleOutput, sink);
		if (outputs == null || source.getWidth() % 128 != 0) return false;
		int scale = source.getWidth() / 128;
		if (scale < 1 || source.getHeight() != 64 * scale) return false;

		BufferedImage left = new BufferedImage(64 * scale, 64 * scale, BufferedImage.TYPE_INT_ARGB);
		BufferedImage right = new BufferedImage(64 * scale, 64 * scale, BufferedImage.TYPE_INT_ARGB);
		copy(source, 0, 0, 6, 6, left, 0, 0, scale, Transform.NONE);
		copy(source, 0, 0, 6, 6, right, 0, 0, scale, Transform.NONE);

		copy(source, 44, 14, 14, 5, left, 29, 14, scale, Transform.ROTATE_180);
		copy(source, 44, 33, 14, 10, left, 29, 33, scale, Transform.ROTATE_180);
		copy(source, 29, 0, 15, 14, left, 29, 0, scale, Transform.FLIP_Y);
		copy(source, 29, 14, 15, 5, left, 43, 14, scale, Transform.ROTATE_180);
		copy(source, 29, 19, 15, 14, left, 29, 19, scale, Transform.FLIP_Y);
		copy(source, 29, 33, 15, 10, left, 43, 33, scale, Transform.ROTATE_180);
		copy(source, 59, 0, 15, 14, left, 14, 0, scale, Transform.FLIP_Y);
		copy(source, 59, 19, 15, 14, left, 14, 19, scale, Transform.FLIP_Y);
		copy(source, 58, 14, 15, 5, left, 14, 14, scale, Transform.ROTATE_180);
		copy(source, 58, 33, 15, 10, left, 14, 33, scale, Transform.ROTATE_180);

		copy(source, 0, 14, 14, 5, right, 0, 14, scale, Transform.ROTATE_180);
		copy(source, 0, 33, 14, 10, right, 0, 33, scale, Transform.ROTATE_180);
		copy(source, 14, 0, 15, 14, right, 29, 0, scale, Transform.FLIP_Y);
		copy(source, 14, 14, 15, 5, right, 43, 14, scale, Transform.ROTATE_180);
		copy(source, 14, 19, 15, 14, right, 29, 19, scale, Transform.FLIP_Y);
		copy(source, 14, 33, 15, 10, right, 43, 33, scale, Transform.ROTATE_180);
		copy(source, 44, 0, 15, 14, right, 14, 0, scale, Transform.FLIP_Y);
		copy(source, 44, 19, 15, 14, right, 14, 19, scale, Transform.FLIP_Y);
		copy(source, 73, 14, 15, 5, right, 14, 14, scale, Transform.ROTATE_180);
		copy(source, 73, 33, 15, 10, right, 14, 33, scale, Transform.ROTATE_180);

		put(sink, outputs.left(), encode(left));
		put(sink, outputs.right(), encode(right));
		return true;
	}

	private static boolean translateSingle(BufferedImage source, String output, GeneratedResourceSink sink) throws IOException {
		if (source.getWidth() % 64 != 0) return false;
		int scale = source.getWidth() / 64;
		if (scale < 1 || source.getHeight() != 64 * scale) return false;
		BufferedImage java = new BufferedImage(64 * scale, 64 * scale, BufferedImage.TYPE_INT_ARGB);
		copy(source, 0, 0, 6, 6, java, 0, 0, scale, Transform.NONE);
		copy(source, 0, 14, 14, 5, java, 0, 14, scale, Transform.ROTATE_180);
		copy(source, 0, 33, 14, 10, java, 0, 33, scale, Transform.ROTATE_180);
		copy(source, 14, 0, 14, 14, java, 28, 0, scale, Transform.FLIP_Y);
		copy(source, 14, 14, 14, 5, java, 42, 14, scale, Transform.ROTATE_180);
		copy(source, 14, 19, 14, 14, java, 28, 19, scale, Transform.FLIP_Y);
		copy(source, 14, 33, 14, 10, java, 42, 33, scale, Transform.ROTATE_180);
		copy(source, 28, 0, 14, 14, java, 14, 0, scale, Transform.FLIP_Y);
		copy(source, 28, 14, 14, 5, java, 28, 14, scale, Transform.ROTATE_180);
		copy(source, 28, 19, 14, 14, java, 14, 19, scale, Transform.FLIP_Y);
		copy(source, 28, 33, 14, 10, java, 28, 33, scale, Transform.ROTATE_180);
		copy(source, 42, 14, 14, 5, java, 14, 14, scale, Transform.ROTATE_180);
		copy(source, 42, 33, 14, 10, java, 14, 33, scale, Transform.ROTATE_180);
		put(sink, output, encode(java));
		return true;
	}

	private static void copy(BufferedImage source, int sourceX, int sourceY, int width, int height,
		BufferedImage target, int targetX, int targetY, int scale, Transform transform) {
		int scaledWidth = width * scale;
		int scaledHeight = height * scale;
		for (int y = 0; y < scaledHeight; y++) for (int x = 0; x < scaledWidth; x++) {
			int readX = transform == Transform.ROTATE_180 ? scaledWidth - 1 - x : x;
			int readY = transform == Transform.NONE ? y : scaledHeight - 1 - y;
			target.setRGB(targetX * scale + x, targetY * scale + y,
				source.getRGB(sourceX * scale + readX, sourceY * scale + readY));
		}
	}

	private static byte[] encode(BufferedImage image) throws IOException {
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			if (!ImageIO.write(image, "PNG", output)) throw new IOException("No PNG writer is available");
			return output.toByteArray();
		}
	}

	private static void put(GeneratedResourceSink sink, String name, byte[] png) {
		sink.put(Identifier.fromNamespaceAndPath("minecraft", PREFIX + name + ".png"), png);
	}

	private record ChestNames(String left, String right) {
	}

	private enum Transform {
		NONE, FLIP_Y, ROTATE_180
	}
}
