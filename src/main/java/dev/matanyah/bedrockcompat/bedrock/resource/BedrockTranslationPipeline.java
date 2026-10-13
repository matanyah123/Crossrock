package dev.matanyah.bedrockcompat.bedrock.resource;

import dev.matanyah.bedrockcompat.bedrock.model.BedrockPack;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPackConfiguration;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPackType;
import dev.matanyah.bedrockcompat.resource.GeneratedResourceSnapshot;
import dev.matanyah.bedrockcompat.resource.InMemoryGeneratedResourceStore;

import java.util.ArrayList;
import java.util.List;

public final class BedrockTranslationPipeline {
	private final List<BedrockResourceTranslator> translators;

	public BedrockTranslationPipeline(List<BedrockResourceTranslator> translators) {
		this.translators = List.copyOf(translators);
	}

	public GenerationResult generate(List<BedrockPack> packsInHighToLowPriorityOrder) {
		return generateConfigured(packsInHighToLowPriorityOrder.stream()
			.map(pack -> new ConfiguredPack(pack, BedrockPackConfiguration.defaults(pack))).toList());
	}

	public GenerationResult generateConfigured(List<ConfiguredPack> packsInHighToLowPriorityOrder) {
		InMemoryGeneratedResourceStore store = new InMemoryGeneratedResourceStore();
		List<String> errors = new ArrayList<>();
		for (ConfiguredPack configuredPack : packsInHighToLowPriorityOrder.reversed()) {
			BedrockPack pack = configuredPack.pack();
			if (pack.type() != BedrockPackType.RESOURCE && pack.type() != BedrockPackType.MIXED) continue;
			for (BedrockResourceTranslator translator : translators) {
				if (translator instanceof BedrockStackResourceTranslator) continue;
				try {
					translator.translate(pack, configuredPack.configuration(), store);
				} catch (Exception exception) {
					String message = exception.getMessage();
					errors.add(pack.displayName() + ": " + exception.getClass().getSimpleName()
						+ (message == null || message.isBlank() ? "" : ": " + message));
				}
			}
		}
		for (var translator : translators) if (translator instanceof BedrockStackResourceTranslator stacked)
			stacked.translateStack(packsInHighToLowPriorityOrder, store, errors);
		BedrockParticleAtlas.generate(store);
		return new GenerationResult(store.snapshot(), errors);
	}

	public record ConfiguredPack(BedrockPack pack, BedrockPackConfiguration configuration) {
	}

	public record GenerationResult(GeneratedResourceSnapshot resources, List<String> errors) {
		public GenerationResult {
			errors = List.copyOf(errors);
		}
	}
}
