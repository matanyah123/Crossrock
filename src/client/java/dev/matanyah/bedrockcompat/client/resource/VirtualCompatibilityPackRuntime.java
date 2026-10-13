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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class VirtualCompatibilityPackRuntime {
	private static final BedrockTranslationPipeline PIPELINE = new BedrockTranslationPipeline(translators());
	private static final BedrockCapabilityAnalyzer CAPABILITY_ANALYZER = new BedrockCapabilityAnalyzer();
	private static GeneratedResourceSnapshot snapshot = GeneratedResourceSnapshot.EMPTY;
	private static List<String> generationErrors = List.of();
	private static List<String> reloadErrors = List.of();
	private static long reloadGeneration;
	private static Path gameDirectory;
	private static boolean autoActivationAttempted;
	private static boolean reloadPending;

	private VirtualCompatibilityPackRuntime() {
	}

	public static BedrockTranslationPipeline.GenerationResult generate(List<BedrockTranslationPipeline.ConfiguredPack> packs) {return PIPELINE.generateConfigured(packs);}

	private static List<BedrockResourceTranslator> translators() {
		List<BedrockResourceTranslator> translators = new ArrayList<>();
		translators.add(new VanillaTextureTranslator(new BedrockPackContentReader()));
		translators.add(new dev.matanyah.bedrockcompat.bedrock.resource.BedrockPresentationTranslator(new BedrockPackContentReader()));
		translators.add(new dev.matanyah.bedrockcompat.bedrock.resource.BedrockBedModelTranslator(new BedrockPackContentReader()));
		translators.add(new VanillaSoundTranslator(new BedrockPackContentReader()));
		translators.add(new BedrockLanguageTranslator(new BedrockPackContentReader()));
		translators.add(new dev.matanyah.bedrockcompat.bedrock.resource.BedrockUiTranslator(new BedrockPackContentReader()));
		translators.add(new dev.matanyah.bedrockcompat.bedrock.resource.BedrockEnvironmentTranslator(new BedrockPackContentReader()));
		translators.add(new dev.matanyah.bedrockcompat.bedrock.resource.NativeEntityResourceTranslator(new BedrockPackContentReader()));
		if (FabricLoader.getInstance().isModLoaded("punchy")) {
			translators.add(new PunchyItemTranslator(new BedrockPackContentReader()));
		}
		translators.add(new dev.matanyah.bedrockcompat.bedrock.resource.BedrockBlockAssetTranslator(new BedrockPackContentReader(),dev.matanyah.bedrockcompat.client.block.JavaBlockStates::variants));
		translators.add(new dev.matanyah.bedrockcompat.bedrock.resource.BedrockItemAssetTranslator(new BedrockPackContentReader(),dev.matanyah.bedrockcompat.client.block.JavaBlockStates::item));
		return translators;
	}

	public static synchronized void initialize(Path directory) {
		gameDirectory = directory;
		reloadPending = true;
	}

	public static void selectionChanged() {
		markReloadPending();
	}

	public static void sourceFilesChanged() {
		markReloadPending();
	}

	public static void applyPendingChanges() {
		synchronized (VirtualCompatibilityPackRuntime.class) {
			if (!reloadPending) return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null) return;
		var repository = minecraft.getResourcePackRepository();
		repository.reload();
		boolean alreadySelected = repository.getSelectedIds().contains(VirtualCompatibilityPackProvider.PACK_ID);
		boolean shouldActivate;
		boolean shouldReload;
		synchronized (VirtualCompatibilityPackRuntime.class) {
			shouldActivate = !BedrockOptionsState.selectedIds().isEmpty() && !autoActivationAttempted;
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

    /** Called after vanilla has committed its list, before its normal output callback. */
    public static void commitPackScreen(net.minecraft.server.packs.repository.PackRepository repository,
            java.util.function.Consumer<net.minecraft.server.packs.repository.PackRepository> vanillaOutput) {
        boolean pending,activate;
        synchronized(VirtualCompatibilityPackRuntime.class) {
            pending=reloadPending;reloadPending=false;
            activate=pending&&!BedrockOptionsState.selectedIds().isEmpty()&&!autoActivationAttempted;
            if(activate||repository.getSelectedIds().contains(VirtualCompatibilityPackProvider.PACK_ID))autoActivationAttempted=true;
        }
        if(activate)repository.addPack(VirtualCompatibilityPackProvider.PACK_ID);
        var mc=Minecraft.getInstance();
        var before=List.copyOf(mc.options.resourcePacks);
        vanillaOutput.accept(repository);
        // Vanilla already starts one reload if its own selection changed. A
        // Bedrock-only configuration change needs one explicitly, never a second.
        if(pending&&before.equals(mc.options.resourcePacks)&&repository.getSelectedIds().contains(VirtualCompatibilityPackProvider.PACK_ID))
            mc.reloadResourcePacks();
    }

	private static synchronized void markReloadPending() {
		reloadPending = true;
	}

	public static synchronized GeneratedResourceSnapshot snapshotForReload() {
		rebuild();
		reloadPending = false;
		return snapshot;
	}

	/** Icon/metadata requests must never generate assets or replace reload diagnostics. */
	public static synchronized GeneratedResourceSnapshot currentSnapshot() { return snapshot; }
	public static synchronized long reloadGeneration() { return reloadGeneration; }
	public static synchronized void resourceReloadCompleted(List<String> nativeErrors) {
		List<String> errors = new ArrayList<>(generationErrors);
		errors.addAll(nativeErrors);
		reloadErrors = List.copyOf(errors);
	}

	public static synchronized List<String> generationErrors() {
		return reloadErrors;
	}

	private static synchronized void rebuild() {
		if (gameDirectory == null) return;
		List<PackLoadResult> results = new BedrockPackRepository(gameDirectory).scan();
		List<String> discoveryErrors = new ArrayList<>();
		Map<String, BedrockPack> packsById = new HashMap<>();
		for (PackLoadResult result : results) {
			result.error().ifPresent(error -> discoveryErrors.add(result.source() + ": " + error));
			result.pack().ifPresent(pack -> pack.warnings().forEach(warning -> discoveryErrors.add(pack.displayName() + ": " + warning)));
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
		snapshot = generated.resources();
		List<String> allDiagnostics = new ArrayList<>(generated.errors());
		allDiagnostics.addAll(discoveryErrors);
		allDiagnostics.addAll(capabilityDiagnostics);
		generationErrors = List.copyOf(allDiagnostics);
		reloadErrors = generationErrors;
		reloadGeneration++;
		if (!generationErrors.isEmpty()) BedrockCompat.LOGGER.warn("Bedrock resource generation completed with errors: {}", generationErrors);
		BedrockCompat.LOGGER.debug("Generated {} virtual Bedrock compatibility resources", snapshot.resources().size());
	}
}
