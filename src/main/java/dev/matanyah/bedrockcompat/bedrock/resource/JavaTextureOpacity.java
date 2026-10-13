package dev.matanyah.bedrockcompat.bedrock.resource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Adapts Bedrock material alpha to the transparency semantics of the matching vanilla Java texture. */
final class JavaTextureOpacity {
	private static final Map<String, Boolean> VANILLA_OPACITY = new ConcurrentHashMap<>();

	private JavaTextureOpacity() {
	}

	static byte[] conformToVanilla(String javaPath, byte[] png) {
		if (javaPath.equals("textures/block/grass_block_side_overlay.png")) return grassOverlay(png);
		if (!javaPath.startsWith("textures/") || !javaPath.endsWith(".png")
			|| !VANILLA_OPACITY.computeIfAbsent(javaPath, JavaTextureOpacity::isVanillaOpaque)) return png;
		try {
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
			if (image == null || !image.getColorModel().hasAlpha()) return png;
			int width = image.getWidth();
			int height = image.getHeight();
			int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
			boolean changed = false;
			for (int index = 0; index < pixels.length; index++) {
				if ((pixels[index] >>> 24) != 0xFF) {
					pixels[index] |= 0xFF000000;
					changed = true;
				}
			}
			if (!changed) return png;
			BufferedImage opaque = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			opaque.setRGB(0, 0, width, height, pixels, 0, width);
			try (ByteArrayOutputStream output = new ByteArrayOutputStream(png.length)) {
				if (!ImageIO.write(opaque, "png", output)) return png;
				return output.toByteArray();
			}
		} catch (IOException | RuntimeException ignored) {
			return png;
		}
	}

	/** Bedrock grass alpha is a tint mask: alpha=1 dirt is visible but untinted.
	 * Java needs that dirt on the opaque base, never in its tinted cutout layer. */
	private static byte[] grassOverlay(byte[] png) {
		try {
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
			if (image == null) return png;
			for (int y=0;y<image.getHeight();y++) for (int x=0;x<image.getWidth();x++) {
				int pixel=image.getRGB(x,y);
				int alpha=pixel>>>24;
				image.setRGB(x,y,(Math.max(0,alpha-1)*255/254)<<24|pixel&0xffffff);
			}
			try (ByteArrayOutputStream output=new ByteArrayOutputStream()) {
				ImageIO.write(image,"png",output);return output.toByteArray();
			}
		} catch (IOException | RuntimeException ignored) { return png; }
	}

	private static boolean isVanillaOpaque(String javaPath) {
		try (InputStream input = JavaTextureOpacity.class.getResourceAsStream("/assets/minecraft/" + javaPath)) {
			if (input == null) return false;
			BufferedImage image = ImageIO.read(input);
			if (image == null) return false;
			int width = image.getWidth();
			int height = image.getHeight();
			int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
			for (int pixel : pixels) if ((pixel >>> 24) != 0xFF) return false;
			return true;
		} catch (IOException | RuntimeException ignored) {
			return false;
		}
	}
}
