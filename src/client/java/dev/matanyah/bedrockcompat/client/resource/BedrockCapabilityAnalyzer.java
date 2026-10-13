package dev.matanyah.bedrockcompat.client.resource;

import dev.matanyah.bedrockcompat.bedrock.model.BedrockPack;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPackConfiguration;
import dev.matanyah.bedrockcompat.bedrock.resource.BedrockPackContentReader;
import dev.matanyah.bedrockcompat.bedrock.resource.BedrockTranslationPipeline;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Reports optional capability gaps without preventing supported resources from loading. */
final class BedrockCapabilityAnalyzer {
	private static final String ESF = "entity_sound_features";
	private final BedrockPackContentReader reader = new BedrockPackContentReader();

	List<String> analyze(List<BedrockTranslationPipeline.ConfiguredPack> selected) {
		List<String> diagnostics = new ArrayList<>();
		Set<String> selectedUuids = selected.stream().map(configured -> configured.pack().uuid().toLowerCase(Locale.ROOT))
			.collect(java.util.stream.Collectors.toSet());
		for (BedrockTranslationPipeline.ConfiguredPack configured : selected) {
			for (var dependency : configured.pack().dependencies()) {
				if (dependency.moduleName() != null && !dependency.moduleName().isBlank()) continue;
				if (!selectedUuids.contains(dependency.uuid().toLowerCase(Locale.ROOT))) {
					diagnostics.add(configured.pack().displayName() + ": required Bedrock dependency "
						+ dependency.uuid() + " is not installed or selected");
				}
			}
			Set<String> paths = new HashSet<>();
			try {
				reader.visitAll(configured.pack(), configured.configuration().selectedSubpack(),
					(path, data) -> paths.add(path.replace('\\', '/').toLowerCase(Locale.ROOT)));
			} catch (IOException exception) {
				diagnostics.add(configured.pack().displayName() + ": capability scan skipped (" + exception.getMessage() + ")");
				continue;
			}
			// Native entity and first-person attachables do not require Punchy.
			// Bedrock entity models and texture selection use the native runtime. Its actual
			// unsupported definitions/queries are reported after reload, not as fake EMF/ETF dependencies.
			boolean scripts = paths.stream().anyMatch(path -> path.startsWith("scripts/") && path.endsWith(".js"));
			if (scripts) {
				diagnostics.add(configured.pack().displayName()
					+ ": Bedrock JavaScript behavior detected; @minecraft/server scripts require a dedicated Java gameplay port");
			}
			boolean customEntities = paths.stream().anyMatch(path -> path.startsWith("entities/") && path.endsWith(".json"));
			if (customEntities) {
				diagnostics.add(configured.pack().displayName()
					+ ": custom Bedrock entity behavior detected; entity components/events are not executed by a resource-pack translator");
			}
			boolean commands = paths.stream().anyMatch(path -> path.startsWith("functions/") && path.endsWith(".mcfunction"));
			if (commands) {
				diagnostics.add(configured.pack().displayName()
					+ ": Bedrock command functions detected; they require an explicit Java command/datapack port");
			}
			boolean gameplayData = paths.stream().anyMatch(path -> path.startsWith("items/") || path.startsWith("recipes/")
				|| path.startsWith("trading/") || path.startsWith("loot_tables/"));
			if (gameplayData) {
				diagnostics.add(configured.pack().displayName()
					+ ": Bedrock gameplay data detected; item/recipe/trade behavior is not enabled by resource translation");
			}
			// ESF is deliberately advisory: ordinary Bedrock sounds are already translated by core.
			// It is only required for conditional/per-entity sound variation properties, which are
			// reported when those OptiFine-style properties are present.
			boolean soundRules = paths.stream().anyMatch(path -> path.startsWith("optifine/random/entity/")
				&& path.endsWith(".properties"));
			if (soundRules && !FabricLoader.getInstance().isModLoaded(ESF)) {
				diagnostics.add(configured.pack().displayName()
					+ ": conditional entity sound rules detected; install Entity Sound Features (ESF)");
			}
		}
		return List.copyOf(diagnostics);
	}
}
