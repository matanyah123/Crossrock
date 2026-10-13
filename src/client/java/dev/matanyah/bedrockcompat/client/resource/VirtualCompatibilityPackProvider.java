package dev.matanyah.bedrockcompat.client.resource;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlagSet;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public final class VirtualCompatibilityPackProvider {
	public static final String PACK_ID = "bedrock_compat/virtual_resources";
	private static final PackLocationInfo LOCATION = new PackLocationInfo(
		PACK_ID,
		Component.translatable("bedrock_compat.virtual_pack.name"),
		PackSource.BUILT_IN,
		Optional.empty()
	);

	private VirtualCompatibilityPackProvider() {
	}

	public static void loadPacks(Consumer<Pack> consumer) {
		Pack.ResourcesSupplier resources = new Pack.ResourcesSupplier() {
			@Override
			public VirtualCompatibilityPackResources openPrimary(PackLocationInfo location) {
				return new VirtualCompatibilityPackResources(location, VirtualCompatibilityPackRuntime.currentSnapshot());
			}

			@Override
			public VirtualCompatibilityPackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
				return new VirtualCompatibilityPackResources(location, VirtualCompatibilityPackRuntime.currentSnapshot());
			}
		};
		Pack.Metadata metadata = new Pack.Metadata(
			Component.translatable("bedrock_compat.virtual_pack.description"),
			PackCompatibility.COMPATIBLE,
			FeatureFlagSet.of(),
			List.of()
		);
		consumer.accept(new Pack(LOCATION, resources, metadata,
			new PackSelectionConfig(false, Pack.Position.TOP, false)));
	}
}
