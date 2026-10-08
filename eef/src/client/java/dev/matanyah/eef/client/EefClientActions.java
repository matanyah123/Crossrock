package dev.matanyah.eef.client;

import dev.matanyah.eef.EefAction;
import dev.matanyah.eef.EefActionHandler;
import dev.matanyah.eef.EntityEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/** Safe client actions used by converted resource packs. Unknown actions are ignored. */
public final class EefClientActions implements EefActionHandler {
	@Override public void execute(EefAction action, EntityEvent event) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) return;
		switch (action.type()) {
			case "actionbar", "subtitle" -> {
				Object text = action.parameters().get("text"), key = action.parameters().get("translation_key");
				if (text != null || key != null) {
					Component message = key == null ? Component.literal(String.valueOf(text)) : Component.translatable(String.valueOf(key));
				if (action.type().equals("subtitle")) {
					int duration = integer(action.parameters().get("duration_ticks"), 60);
					EefSubtitleOverlay.show(message, duration);
					EefSpeechBubbleOverlay.show(message, duration, origin(action, event, client.player));
				}
					else client.gui.hud.setOverlayMessage(message, false);
				}
			}
			case "play_sound" -> {
				Object value = action.parameters().get("sound");
				if (value instanceof String id) {
					float radius = number(action.parameters().get("radius"), 16f);
					SoundEvent sound = SoundEvent.createFixedRangeEvent(Identifier.parse(id), radius);
					Entity origin = origin(action, event, client.player);
					float volume = number(action.parameters().get("volume"), 1f);
					float pitch = number(action.parameters().get("pitch"), 1f);
					if (bool(action.parameters().get("follow_entity"), true))
						client.getSoundManager().play(new EntityBoundSoundInstance(sound, SoundSource.VOICE,
							volume, pitch, origin, System.nanoTime()));
					else client.level.playLocalSound(origin.getX(), origin.getY(), origin.getZ(), sound, SoundSource.VOICE,
						volume, pitch, false);
				}
			}
		}
	}
	private static Entity origin(EefAction action, EntityEvent event, Entity observer) {
		String role = String.valueOf(action.parameters().getOrDefault("at", "self"));
		return switch (role) {
			case "target" -> event.target() == null ? observer : event.target();
			case "observer" -> observer;
			case "self" -> event.source() == null ? observer : event.source();
			default -> entityFromContext(event, role, observer);
		};
	}
	private static Entity entityFromContext(EntityEvent event, String role, Entity fallback) {
		Object id = event.value(role + ".id").orElse(null);
		if (id == null || Minecraft.getInstance().level == null) return fallback;
		try {
			java.util.UUID uuid = java.util.UUID.fromString(String.valueOf(id));
			for (Entity entity : Minecraft.getInstance().level.entitiesForRendering()) if (entity.getUUID().equals(uuid)) return entity;
		} catch (IllegalArgumentException ignored) {}
		return fallback;
	}
	private static float number(Object value, float fallback) { return value instanceof Number n ? n.floatValue() : fallback; }
	private static int integer(Object value, int fallback) { return value instanceof Number n ? n.intValue() : fallback; }
	private static boolean bool(Object value, boolean fallback) { return value instanceof Boolean flag ? flag : fallback; }
}
