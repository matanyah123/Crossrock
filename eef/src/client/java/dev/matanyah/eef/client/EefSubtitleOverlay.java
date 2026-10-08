package dev.matanyah.eef.client;

import dev.matanyah.eef.EntityEventFeatures;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Bottom-center subtitle cue renderer independent of Minecraft's title timer. */
public final class EefSubtitleOverlay {
	private static Component message;
	private static long expiresAtMillis;
	private EefSubtitleOverlay() {}

	public static void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.SUBTITLES,
			Identifier.fromNamespaceAndPath(EntityEventFeatures.MOD_ID, "subtitles"), EefSubtitleOverlay::render);
	}

	public static synchronized void show(Component value, int durationTicks) {
		if (!EntityEventFeaturesClient.CONFIG.subtitlesEnabled()) return;
		message = value;
		expiresAtMillis = System.currentTimeMillis() + Math.max(1, durationTicks) * 50L;
	}

	public static synchronized void clear() { message = null; expiresAtMillis = 0; }

	private static synchronized void render(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker deltaTracker) {
		if (!EntityEventFeaturesClient.CONFIG.subtitlesEnabled() || message == null) return;
		if (System.currentTimeMillis() > expiresAtMillis) { clear(); return; }
		Minecraft client = Minecraft.getInstance();
		int width = client.font.width(message);
		int x = (graphics.guiWidth() - width) / 2;
		int y = graphics.guiHeight() - 58;
		graphics.fill(x - 3, y - 2, x + width + 3, y + client.font.lineHeight + 2, 0xA0000000);
		graphics.text(client.font, message, x, y, 0xFFFFFFFF, true);
	}
}
