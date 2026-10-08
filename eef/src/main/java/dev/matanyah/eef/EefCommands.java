package dev.matanyah.eef;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Vanilla-selector command surface; all pack-specific lookup happens on the client. */
public final class EefCommands {
	private static final Map<UUID,List<EefCommandCatalogPayload.Entry>> CATALOGS = new ConcurrentHashMap<>();
	private EefCommands() {}
	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(EefCommandCatalogPayload.TYPE, (payload, context) ->
			context.server().execute(() -> CATALOGS.put(context.player().getUUID(), payload.rules().stream()
				.filter(entry -> !entry.id().isBlank()).distinct().limit(2048).toList())));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CATALOGS.remove(handler.player.getUUID()));
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
			Commands.literal("eef")
				.executes(context -> simple(context.getSource(), new EefCommandPayload("help", List.of(), "", 0, 0)))
				.then(Commands.literal("help")
					.executes(context -> simple(context.getSource(), new EefCommandPayload("help", List.of(), "", 0, 0))))
				.then(Commands.literal("play")
					.then(Commands.argument("trigger", StringArgumentType.string()).suggests(EefCommands::suggestTriggers)
						.executes(context -> playLooking(context.getSource(), StringArgumentType.getString(context, "trigger"), 0))
						.then(Commands.argument("variant", IntegerArgumentType.integer(1)).suggests(EefCommands::suggestVariants)
							.executes(context -> playLooking(context.getSource(), StringArgumentType.getString(context, "trigger"),
								IntegerArgumentType.getInteger(context, "variant"))))))
				.then(Commands.argument("targets", EntityArgument.entities())
					.then(Commands.literal("play")
						.then(Commands.argument("trigger", StringArgumentType.string()).suggests(EefCommands::suggestTriggers)
							.executes(context -> play(context.getSource(), EntityArgument.getEntities(context, "targets"),
								StringArgumentType.getString(context, "trigger"), 0))
							.then(Commands.argument("variant", IntegerArgumentType.integer(1)).suggests(EefCommands::suggestVariants)
								.executes(context -> play(context.getSource(), EntityArgument.getEntities(context, "targets"),
									StringArgumentType.getString(context, "trigger"), IntegerArgumentType.getInteger(context, "variant")))))))
				.then(Commands.literal("list")
					.executes(context -> simple(context.getSource(), new EefCommandPayload("list", List.of(), "", 0, 1)))
					.then(Commands.argument("page", IntegerArgumentType.integer(1))
						.executes(context -> simple(context.getSource(), new EefCommandPayload("list", List.of(), "", 0,
							IntegerArgumentType.getInteger(context, "page"))))))
				.then(Commands.literal("info").then(Commands.argument("trigger", StringArgumentType.string()).suggests(EefCommands::suggestTriggers)
					.executes(context -> simple(context.getSource(), new EefCommandPayload("info", List.of(),
						StringArgumentType.getString(context, "trigger"), 0, 0)))))
		));
	}
	private static int playLooking(CommandSourceStack source, String trigger, int variant) {
		return simple(source, new EefCommandPayload("play_look", List.of(), trigger, variant, 0));
	}
	private static int play(net.minecraft.commands.CommandSourceStack source, Collection<? extends Entity> targets,
		String trigger, int variant) {
		ServerPlayer player;
		try { player = source.getPlayerOrException(); }
		catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) { source.sendFailure(Component.literal("/eef must be run by a player.")); return 0; }
		if (!ServerPlayNetworking.canSend(player, EefCommandPayload.TYPE)) { source.sendFailure(Component.literal("Your client does not support EEF commands.")); return 0; }
		ServerPlayNetworking.send(player, new EefCommandPayload("play", targets.stream().limit(256).map(Entity::getId).toList(), trigger, variant, 0));
		source.sendSuccess(() -> Component.literal("EEF playback request sent for " + targets.size() + " target(s)."), false);
		return targets.size();
	}
	private static int simple(net.minecraft.commands.CommandSourceStack source, EefCommandPayload payload) {
		ServerPlayer player;
		try { player = source.getPlayerOrException(); }
		catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) { source.sendFailure(Component.literal("/eef must be run by a player.")); return 0; }
		if (!ServerPlayNetworking.canSend(player, EefCommandPayload.TYPE)) { source.sendFailure(Component.literal("Your client does not support EEF commands.")); return 0; }
		ServerPlayNetworking.send(player, payload); return 1;
	}
	private static CompletableFuture<Suggestions> suggestTriggers(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
		return SharedSuggestionProvider.suggest(catalog(context).stream().map(EefCommandCatalogPayload.Entry::id), builder);
	}
	private static CompletableFuture<Suggestions> suggestVariants(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
		String query;
		try { query = StringArgumentType.getString(context, "trigger"); }
		catch (IllegalArgumentException ignored) { return builder.buildFuture(); }
		List<EefCommandCatalogPayload.Entry> matches = catalog(context).stream()
			.filter(entry -> normalize(entry.id()).equals(normalize(query)) || normalize(entry.id()).contains(normalize(query))).toList();
		if (matches.size() != 1) return builder.buildFuture();
		return SharedSuggestionProvider.suggest(java.util.stream.IntStream.rangeClosed(1, matches.getFirst().variants())
			.mapToObj(Integer::toString), builder);
	}
	private static List<EefCommandCatalogPayload.Entry> catalog(CommandContext<CommandSourceStack> context) {
		try { return CATALOGS.getOrDefault(context.getSource().getPlayerOrException().getUUID(), List.of()); }
		catch (com.mojang.brigadier.exceptions.CommandSyntaxException ignored) { return List.of(); }
	}
	private static String normalize(String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9:.]+", "_").replaceAll("_+", "_");
	}
}
