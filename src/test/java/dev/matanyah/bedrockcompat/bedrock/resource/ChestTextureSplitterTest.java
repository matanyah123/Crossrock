package dev.matanyah.bedrockcompat.bedrock.resource;

import dev.matanyah.bedrockcompat.resource.InMemoryGeneratedResourceStore;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChestTextureSplitterTest {
	@Test
	void splitsBedrockDoubleChestIntoJavaHalves() throws Exception {
		BufferedImage source = coordinates(128, 64);
		InMemoryGeneratedResourceStore store = new InMemoryGeneratedResourceStore();

		assertTrue(ChestTextureSplitter.translate("textures/entity/chest/double_normal.png", encode(source), store));
		BufferedImage left = output(store, "textures/entity/chest/normal_left.png");
		BufferedImage right = output(store, "textures/entity/chest/normal_right.png");
		assertEquals(64, left.getWidth());
		assertEquals(64, right.getHeight());

		assertEquals(source.getRGB(57, 18), left.getRGB(29, 14));
		assertEquals(source.getRGB(29, 13), left.getRGB(29, 0));
		assertEquals(source.getRGB(87, 42), right.getRGB(14, 33));
		assertEquals(source.getRGB(44, 32), right.getRGB(14, 19));
		assertEquals(source.getRGB(0, 0), left.getRGB(0, 0));
		assertEquals(source.getRGB(0, 0), right.getRGB(0, 0));
	}

	@Test
	void convertsSingleChestFacesAndRenamesBedrockDefaultCopperChest() throws Exception {
		byte[] image = encode(coordinates(64, 64));
		InMemoryGeneratedResourceStore store = new InMemoryGeneratedResourceStore();
		assertTrue(ChestTextureSplitter.translate("textures/entity/chest/copper_default.png", image, store));
		assertTrue(store.snapshot().resources().containsKey(id("textures/entity/chest/copper.png")));
		assertTrue(ChestTextureSplitter.translate("textures/entity/chest/normal.png", image, store));
		BufferedImage normal = output(store, "textures/entity/chest/normal.png");
		assertEquals(coordinates(64, 64).getRGB(14, 13), normal.getRGB(28, 0));
		assertEquals(coordinates(64, 64).getRGB(55, 18), normal.getRGB(14, 14));
	}

	private static BufferedImage coordinates(int width, int height) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
			image.setRGB(x, y, 0xFF000000 | x << 8 | y);
		}
		return image;
	}

	private static BufferedImage output(InMemoryGeneratedResourceStore store, String path) throws Exception {
		return ImageIO.read(new ByteArrayInputStream(store.snapshot().resources().get(id(path))));
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("minecraft", path);
	}

	private static byte[] encode(BufferedImage image) throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(image, "PNG", output);
		return output.toByteArray();
	}
}
