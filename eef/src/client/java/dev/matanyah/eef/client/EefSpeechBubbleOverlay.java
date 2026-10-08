package dev.matanyah.eef.client;

import dev.matanyah.eef.EntityEventFeatures;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Crisp screen-space speech bubbles projected from a moving world entity. */
public final class EefSpeechBubbleOverlay {
	private static final Identifier BUBBLE = Identifier.fromNamespaceAndPath(EntityEventFeatures.MOD_ID, "speech_bubble/bubble");
	private static final Identifier TAIL = Identifier.fromNamespaceAndPath(EntityEventFeatures.MOD_ID, "speech_bubble/tail");
	private static final Identifier ARROW_UP = Identifier.fromNamespaceAndPath(EntityEventFeatures.MOD_ID, "speech_bubble/up");
	private static final Identifier ARROW_DOWN = Identifier.fromNamespaceAndPath(EntityEventFeatures.MOD_ID, "speech_bubble/down");
	private static final Identifier ARROW_LEFT = Identifier.fromNamespaceAndPath(EntityEventFeatures.MOD_ID, "speech_bubble/left");
	private static final Identifier ARROW_RIGHT = Identifier.fromNamespaceAndPath(EntityEventFeatures.MOD_ID, "speech_bubble/right");
	private static final int MAX_TEXT_WIDTH = 160;
	private static final int ARROW_SIZE = 8;
	private static final int ARROW_GAP = 2;
	private static final float MAX_OFFSCREEN_NDC = 3.0f;
	private static final Map<UUID, Bubble> ACTIVE = new LinkedHashMap<>();
	private record Edges(boolean up, boolean down, boolean left, boolean right) {
		boolean any() { return up || down || left || right; }
	}

	private record Bubble(Entity entity, Component message, long expiresAtMillis) {}

	private EefSpeechBubbleOverlay() {}

	public static void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.SUBTITLES,
			Identifier.fromNamespaceAndPath(EntityEventFeatures.MOD_ID, "speech_bubbles"), EefSpeechBubbleOverlay::render);
	}

	public static synchronized void show(Component message, int durationTicks, Entity speaker) {
		if (!EntityEventFeaturesClient.CONFIG.speechBubblesEnabled() || speaker == null) return;
		ACTIVE.put(speaker.getUUID(), new Bubble(speaker, message,
			System.currentTimeMillis() + Math.max(1, durationTicks) * 50L));
	}

	public static synchronized void clear() { ACTIVE.clear(); }

	private static synchronized void render(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker deltaTracker) {
		if (!EntityEventFeaturesClient.CONFIG.speechBubblesEnabled() || ACTIVE.isEmpty()) return;
		long now = System.currentTimeMillis();
		ACTIVE.values().removeIf(bubble -> bubble.expiresAtMillis() < now || bubble.entity().isRemoved());
		if (ACTIVE.isEmpty()) return;
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null || client.gui.screen() != null) return;
		Camera camera = client.gameRenderer.mainCamera();
		Matrix4f projection = camera.getViewRotationProjectionMatrix(new Matrix4f());
		float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
		for (Bubble bubble : List.copyOf(ACTIVE.values())) {
			Entity entity = bubble.entity();
			double distance = entity.distanceTo(client.player);
			if (entity.level() != client.level || distance > 48.0) continue;
			Vec3 anchor = entity.getPosition(partial).add(0.0, entity.getBbHeight() + 0.35, 0.0);
			Vec3 cameraPos = camera.position();
			Vector4f clip = projection.transform(new Vector4f((float)(anchor.x - cameraPos.x),
				(float)(anchor.y - cameraPos.y), (float)(anchor.z - cameraPos.z), 1.0f));
			if (clip.w <= 0.01f) continue;
			float ndcX = clip.x / clip.w, ndcY = clip.y / clip.w;
			double graceDistance = EntityEventFeaturesClient.CONFIG.bubbleGraceDistance();
			float graceRatio = (float)(graceDistance / dev.matanyah.eef.EefConfig.MAX_BUBBLE_GRACE_DISTANCE);
			float offscreenLimit = 1.0f + (MAX_OFFSCREEN_NDC - 1.0f) * graceRatio;
			if (ndcX < -offscreenLimit || ndcX > offscreenLimit || ndcY < -offscreenLimit || ndcY > offscreenLimit) continue;
			Edges edges = edges(ndcX, ndcY);
			if (edges.any() && distance > graceDistance) continue;
			int screenX = Math.round((ndcX + 1.0f) * graphics.guiWidth() * 0.5f);
			int screenY = Math.round((1.0f - ndcY) * graphics.guiHeight() * 0.5f);
			draw(graphics, client.font, bubble.message(), screenX, screenY, edges);
		}
	}

	private static Edges edges(float x, float y) {
		return new Edges(y > 1, y < -1, x < -1, x > 1);
	}

	private static void draw(GuiGraphicsExtractor graphics, Font font, Component message, int anchorX, int anchorY, Edges edges) {
		List<FormattedCharSequence> lines = font.split(message, MAX_TEXT_WIDTH);
		if (lines.isEmpty()) lines = List.of(FormattedCharSequence.EMPTY);
		int textWidth = 0;
		for (FormattedCharSequence line : lines) textWidth = Math.max(textWidth, font.width(line));
		int width = Math.max(20, textWidth + 14);
		int height = Math.max(20, lines.size() * font.lineHeight + 12);
		int outside = ARROW_SIZE + ARROW_GAP;
		int minX = edges.left() ? outside + 2 : 2;
		int maxX = graphics.guiWidth() - width - (edges.right() ? outside + 2 : 2);
		int minY = edges.up() ? outside + 2 : 2;
		int maxY = graphics.guiHeight() - height - 11 - (edges.down() ? outside : 0);
		int x = Math.max(minX, Math.min(maxX, anchorX + 10));
		int y = Math.max(minY, Math.min(maxY, anchorY - height - 7));
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BUBBLE, x, y, width, height);
		int tailX = Math.max(2, Math.min(graphics.guiWidth() - 11, x + 2));
		int tailY = Math.min(graphics.guiHeight() - 9, y + height - 2);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, TAIL, tailX, tailY, 9, 9);
		int centerX = x + width / 2 - ARROW_SIZE / 2;
		int centerY = y + height / 2 - ARROW_SIZE / 2;
		if (edges.up()) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW_UP,
			centerX, y - outside, ARROW_SIZE, ARROW_SIZE);
		if (edges.down()) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW_DOWN,
			centerX, y + height + ARROW_GAP, ARROW_SIZE, ARROW_SIZE);
		if (edges.left()) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW_LEFT,
			x - outside, centerY, ARROW_SIZE, ARROW_SIZE);
		if (edges.right()) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW_RIGHT,
			x + width + ARROW_GAP, centerY, ARROW_SIZE, ARROW_SIZE);
		int textX = x + 7;
		int textY = y + 6;
		for (FormattedCharSequence line : lines) {
			graphics.text(font, line, textX, textY, 0xFF1D3440, false);
			textY += font.lineHeight;
		}
	}
}
