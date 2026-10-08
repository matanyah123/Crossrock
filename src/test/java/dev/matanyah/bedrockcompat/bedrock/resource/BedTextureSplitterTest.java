package dev.matanyah.bedrockcompat.bedrock.resource;

import dev.matanyah.bedrockcompat.resource.InMemoryGeneratedResourceStore;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedTextureSplitterTest {
	@Test
	void usesMinecraft262BedUvLayout() throws Exception {
		BufferedImage source = coordinates(64, 64);
		InMemoryGeneratedResourceStore store = new InMemoryGeneratedResourceStore();

		assertTrue(BedTextureSplitter.translate("textures/entity/bed/green.png", encode(source), store));

		BufferedImage top = output(store, "textures/block/green_bed_foot_up.png");
		assertEquals(source.getRGB(6, 22), top.getRGB(0, 0));
		assertEquals(source.getRGB(21, 37), top.getRGB(15, 15));

		BufferedImage south = output(store, "textures/block/green_bed_foot_south.png");
		assertEquals(source.getRGB(22, 5), south.getRGB(0, 7));
		assertEquals(source.getRGB(37, 0), south.getRGB(15, 12));
		assertEquals(source.getRGB(0, 49), south.getRGB(0, 13));
		assertEquals(source.getRGB(18, 49), south.getRGB(15, 15));

		BufferedImage east = output(store, "textures/block/green_bed_head_east.png");
		assertEquals(source.getRGB(17, 41), east.getRGB(7, 15));
		assertEquals(source.getRGB(18, 43), east.getRGB(13, 13));

		BufferedImage footDown = output(store, "textures/block/green_bed_foot_down.png");
		assertEquals(source.getRGB(43, 37), footDown.getRGB(0, 0));
		String footModel = new String(store.snapshot().resources().get(Identifier.fromNamespaceAndPath(
			"minecraft", "models/block/green_bed_foot.json")), StandardCharsets.UTF_8);
		assertTrue(footModel.contains("minecraft:block/green_bed_foot_down"));
	}

	private static BufferedImage coordinates(int width, int height) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
			image.setRGB(x, y, 0xFF000000 | x << 8 | y);
		}
		return image;
	}

	private static BufferedImage output(InMemoryGeneratedResourceStore store, String path) throws Exception {
		byte[] bytes = store.snapshot().resources().get(Identifier.fromNamespaceAndPath("minecraft", path));
		return ImageIO.read(new ByteArrayInputStream(bytes));
	}

	private static byte[] encode(BufferedImage image) throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(image, "PNG", output);
		return output.toByteArray();
	}
}
