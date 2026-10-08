package dev.matanyah.eef.client;

import dev.matanyah.eef.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Interprets generic command requests against the currently loaded client resource packs. */
public final class EefCommandClient {
	private static final int PAGE_SIZE = 10;
	private static boolean catalogDirty = true;
	private static Object lastConnection;
	private EefCommandClient() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(EefCommandPayload.TYPE, (payload, context) ->
			context.client().execute(() -> receive(payload, context.client())));
		ClientTickEvents.END_CLIENT_TICK.register(EefCommandClient::syncCatalog);
	}
	static void catalogChanged() { catalogDirty = true; }
	private static void syncCatalog(Minecraft client) {
		Object connection = client.getConnection();
		if (connection != lastConnection) { lastConnection = connection; catalogDirty = true; }
		if (!catalogDirty || connection == null || !ClientPlayNetworking.canSend(EefCommandCatalogPayload.TYPE)) return;
		List<EefCommandCatalogPayload.Entry> catalog = EntityEventFeaturesClient.RULE_ENGINE.ruleCatalog().stream()
			.limit(2048).map(rule -> new EefCommandCatalogPayload.Entry(rule.id(), rule.outcomes().size())).toList();
		ClientPlayNetworking.send(new EefCommandCatalogPayload(catalog));
		catalogDirty = false;
	}

	private static void receive(EefCommandPayload payload, Minecraft client) {
		switch (payload.operation()) {
			case "help" -> help(client);
			case "play" -> play(payload, client, false);
			case "play_look" -> play(payload, client, true);
			case "list" -> list(payload.page(), client);
			case "info" -> info(payload.rule(), client);
			default -> error(client, "Unknown EEF command operation: " + payload.operation());
		}
	}

	private static void play(EefCommandPayload payload, Minecraft client, boolean useCrosshair) {
		if (client.level == null || client.player == null) { error(client, "No world is loaded."); return; }
		List<EefRule> rules = EntityEventFeaturesClient.RULE_ENGINE.findManualRules(payload.rule());
		if (rules.isEmpty()) { error(client, "No EEF trigger matches '" + payload.rule() + "'. Use /eef list."); return; }
		if (rules.size() > 1) {
			error(client, "Trigger name is ambiguous. Matches: " + String.join(", ", rules.stream().limit(8).map(EefRule::id).toList()));
			return;
		}
		List<Integer> entityIds = useCrosshair
			? (client.crosshairPickEntity == null ? List.of() : List.of(client.crosshairPickEntity.getId()))
			: payload.entityIds();
		if (useCrosshair && entityIds.isEmpty()) { error(client, "Look directly at an entity, then run /eef play again."); return; }
		int played = 0, missing = 0;
		EefRuleEngine.ManualPlayResult last = null;
		for (int entityId : entityIds) {
			Entity entity = client.level.getEntity(entityId);
			if (entity == null) { missing++; continue; }
			last = EntityEventFeaturesClient.RULE_ENGINE.playManual(rules.getFirst().id(), payload.variant(), event(client, entity));
			if (last.status() == EefRuleEngine.ManualPlayStatus.PLAYED) played++;
			else if (last.status() == EefRuleEngine.ManualPlayStatus.INVALID_VARIANT) break;
		}
		if (last != null && last.status() == EefRuleEngine.ManualPlayStatus.INVALID_VARIANT) {
			error(client, "Variant must be between 1 and " + last.variantCount() + " (or omit it for weighted selection)."); return;
		}
		if (played == 0) { error(client, "No selected entities were available on this client."); return; }
		String selected = payload.variant() == 0 && played > 1 ? "weighted selection"
			: last.outcomeId() + " / variant " + last.variant();
		success(client, "Played " + last.ruleId() + " (" + selected + ") on " + played
			+ " entit" + (played == 1 ? "y" : "ies") + (missing == 0 ? "." : "; " + missing + " not client-visible."));
	}

	private static void help(Minecraft client) {
		chat(client, Component.literal("EEF developer commands").withStyle(ChatFormatting.GOLD));
		chat(client, Component.literal("/eef play <trigger> [variant]  (looked-at entity)").withStyle(ChatFormatting.YELLOW));
		chat(client, Component.literal("/eef <targets> play <trigger> [variant]").withStyle(ChatFormatting.YELLOW));
		chat(client, Component.literal("/eef list [page]").withStyle(ChatFormatting.YELLOW));
		chat(client, Component.literal("/eef info <trigger>").withStyle(ChatFormatting.YELLOW));
		chat(client, Component.literal("Omit variant for weighted selection; /eef info shows available numbers.")
			.withStyle(ChatFormatting.GRAY));
	}

	private static void list(int requestedPage, Minecraft client) {
		List<EefRuleEngine.RuleSummary> rules = EntityEventFeaturesClient.RULE_ENGINE.ruleCatalog();
		if (rules.isEmpty()) { error(client, "No EEF rules are loaded. Enable an EEF resource pack or reload resources."); return; }
		int pages = Math.max(1, (rules.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		int page = Math.max(1, Math.min(requestedPage, pages));
		chat(client, Component.literal("EEF triggers — page " + page + "/" + pages + " (" + rules.size() + ")").withStyle(ChatFormatting.GOLD));
		for (EefRuleEngine.RuleSummary rule : rules.subList((page - 1) * PAGE_SIZE, Math.min(page * PAGE_SIZE, rules.size())))
			chat(client, Component.literal("  " + rule.id()).withStyle(ChatFormatting.YELLOW)
				.append(Component.literal("  [" + rule.event().name().toLowerCase() + ", " + rule.outcomes().size() + " variant(s)]")
					.withStyle(ChatFormatting.GRAY)));
		if (page < pages) chat(client, Component.literal("Use /eef list " + (page + 1) + " for the next page.").withStyle(ChatFormatting.GRAY));
	}

	private static void info(String query, Minecraft client) {
		List<EefRule> matches = EntityEventFeaturesClient.RULE_ENGINE.findManualRules(query);
		if (matches.isEmpty()) { error(client, "No EEF trigger matches '" + query + "'."); return; }
		if (matches.size() > 1) {
			error(client, "Ambiguous trigger. Matches: " + String.join(", ", matches.stream().limit(10).map(EefRule::id).toList())); return;
		}
		EefRule rule = matches.getFirst();
		chat(client, Component.literal(rule.id()).withStyle(ChatFormatting.GOLD)
			.append(Component.literal(" — " + rule.event().name().toLowerCase()).withStyle(ChatFormatting.GRAY)));
		for (int index = 0; index < rule.outcomes().size(); index++) {
			EefOutcome outcome = rule.outcomes().get(index);
			chat(client, Component.literal("  " + (index + 1) + ". " + outcome.id()).withStyle(ChatFormatting.YELLOW)
				.append(Component.literal("  weight " + outcome.weight()).withStyle(ChatFormatting.GRAY)));
		}
		chat(client, Component.literal("Play: /eef <targets> play " + rule.id() + " [variant]").withStyle(ChatFormatting.GRAY));
	}

	private static EntityEvent event(Minecraft client, Entity entity) {
		Map<String,Object> context = new LinkedHashMap<>();
		context.put("self", EefClientContext.entity(entity));
		context.put("observer", EefClientContext.entity(client.player));
		context.put("world", EefClientContext.world(client.level));
		context.put("distance", Map.of("observer_to_self", client.player.distanceTo(entity)));
		return new EntityEvent(client.level.getGameTime(), EntityEventKind.ENTITY_OBSERVED, entity, null,
			entity.blockPosition(), null, EventReliability.CLIENT_OBSERVED, context);
	}

	private static void success(Minecraft client, String message) { chat(client, Component.literal(message).withStyle(ChatFormatting.GREEN)); }
	private static void error(Minecraft client, String message) { chat(client, Component.literal(message).withStyle(ChatFormatting.RED)); }
	private static void chat(Minecraft client, Component message) { client.gui.chatListener().handleSystemMessage(message, false); }
}
