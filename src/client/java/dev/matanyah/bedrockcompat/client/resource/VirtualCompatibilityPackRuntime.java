package dev.matanyah.bedrockcompat.client.resource;

import dev.matanyah.bedrockcompat.BedrockCompat;
import dev.matanyah.bedrockcompat.bedrock.discovery.BedrockPackRepository;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPack;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPackIdentity;
import dev.matanyah.bedrockcompat.bedrock.model.PackLoadResult;
import dev.matanyah.bedrockcompat.bedrock.resource.BedrockPackContentReader;
import dev.matanyah.bedrockcompat.bedrock.resource.BedrockTranslationPipeline;
import dev.matanyah.bedrockcompat.bedrock.resource.VanillaTextureTranslator;
import dev.matanyah.bedrockcompat.bedrock.resource.VanillaSoundTranslator;
import dev.matanyah.bedrockcompat.bedrock.resource.BedrockLanguageTranslator;
import dev.matanyah.bedrockcompat.bedrock.resource.BedrockResourceTranslator;
import dev.matanyah.bedrockcompat.bedrock.resource.PunchyItemTranslator;
import dev.matanyah.bedrockcompat.client.BedrockOptionsState;
import dev.matanyah.bedrockcompat.resource.GeneratedResourceSnapshot;
import net.minecraft.client.Minecraft;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class VirtualCompatibilityPackRuntime {
	private static final BedrockTranslationPipeline PIPELINE = new BedrockTranslationPipeline(translators());
	private static final BedrockCapabilityAnalyzer CAPABILITY_ANALYZER = new BedrockCapabilityAnalyzer();
	private static GeneratedResourceSnapshot snapshot = GeneratedResourceSnapshot.EMPTY;
	private static List<String> generationErrors = List.of();
	private static Path gameDirectory;
	private static boolean autoActivationAttempted;
	private static boolean reloadPending;

	private VirtualCompatibilityPackRuntime() {
	}

	private static List<BedrockResourceTranslator> translators() {
		List<BedrockResourceTranslator> translators = new ArrayList<>();
		translators.add(new VanillaTextureTranslator(new BedrockPackContentReader()));
		translators.add(new VanillaSoundTranslator(new BedrockPackContentReader()));
		translators.add(new BedrockLanguageTranslator(new BedrockPackContentReader()));
		translators.add(new dev.matanyah.bedrockcompat.bedrock.resource.NativeEntityResourceTranslator(new BedrockPackContentReader()));
		if (FabricLoader.getInstance().isModLoaded("punchy")) {
			translators.add(new PunchyItemTranslator(new BedrockPackContentReader()));
		}
		return translators;
	}

	public static synchronized void initialize(Path directory) {
		gameDirectory = directory;
		rebuild();
	}

	public static void selectionChanged() {
		boolean resourcesChanged = rebuild();
		if (resourcesChanged) markReloadPending();
	}

	public static void sourceFilesChanged() {
		if (rebuild()) markReloadPending();
	}

	public static void applyPendingChanges() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null) return;
		var repository = minecraft.getResourcePackRepository();
		repository.reload();
		boolean alreadySelected = repository.getSelectedIds().contains(VirtualCompatibilityPackProvider.PACK_ID);
		boolean shouldActivate;
		boolean shouldReload;
		synchronized (VirtualCompatibilityPackRuntime.class) {
			shouldActivate = !snapshot.resources().isEmpty() && !autoActivationAttempted;
			if (shouldActivate || alreadySelected) autoActivationAttempted = true;
			shouldReload = reloadPending;
			reloadPending = false;
		}
		boolean activated = shouldActivate && repository.addPack(VirtualCompatibilityPackProvider.PACK_ID);
		if (activated) {
			minecraft.options.updateResourcePacks(repository);
		} else if (shouldReload && repository.getSelectedIds().contains(VirtualCompatibilityPackProvider.PACK_ID)) {
			minecraft.reloadResourcePacks();
		}
	}

	private static synchronized void markReloadPending() {
		reloadPending = true;
	}

	public static synchronized GeneratedResourceSnapshot snapshotForReload() {
		rebuild();
		return snapshot;
	}

	public static synchronized List<String> generationErrors() {
		List<String> errors = new ArrayList<>(generationErrors);
		errors.addAll(dev.matanyah.bedrockcompat.client.entity.NativeEntityResources.errors());
		return List.copyOf(errors);
	}

	private static synchronized boolean rebuild() {
		if (gameDirectory == null) return false;
		List<PackLoadResult> results = new BedrockPackRepository(gameDirectory).scan();
		Map<String, BedrockPack> packsById = new HashMap<>();
		for (PackLoadResult result : results) {
			result.pack().ifPresent(pack -> packsById.putIfAbsent(BedrockPackIdentity.selectionId(pack), pack));
		}
		List<BedrockTranslationPipeline.ConfiguredPack> selected = new ArrayList<>();
		for (String id : BedrockOptionsState.selectedIds()) {
			BedrockPack pack = packsById.get(id);
			if (pack != null) selected.add(new BedrockTranslationPipeline.ConfiguredPack(pack,
				BedrockOptionsState.configuration(id, pack)));
		}
		BedrockTranslationPipeline.GenerationResult generated = PIPELINE.generateConfigured(selected);
		List<String> capabilityDiagnostics = CAPABILITY_ANALYZER.analyze(selected);
		boolean changed = !sameResources(snapshot, generated.resources());
		snapshot = generated.resources();
		List<String> allDiagnostics = new ArrayList<>(generated.errors());
		allDiagnostics.addAll(capabilityDiagnostics);
		generationErrors = List.copyOf(allDiagnostics);
		if (!generationErrors.isEmpty()) BedrockCompat.LOGGER.warn("Bedrock resource generation completed with errors: {}", generationErrors);
		BedrockCompat.LOGGER.debug("Generated {} virtual Bedrock compatibility resources", snapshot.resources().size());
		return changed;
	}

	private static boolean sameResources(GeneratedResourceSnapshot first, GeneratedResourceSnapshot second) {
		if (!first.resources().keySet().equals(second.resources().keySet())) return false;
		for (var entry : first.resources().entrySet()) {
			if (!Arrays.equals(entry.getValue(), second.resources().get(entry.getKey()))) return false;
		}
		return true;
	}
}
