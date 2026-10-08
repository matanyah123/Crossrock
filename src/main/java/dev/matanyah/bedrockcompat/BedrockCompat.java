package dev.matanyah.bedrockcompat;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BedrockCompat implements ModInitializer {
	public static final String MOD_ID = "bedrock_compat";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		dev.matanyah.bedrockcompat.entity.ArmorStandNetworking.register();
		dev.matanyah.bedrockcompat.entity.ArmorStandCompatibility.register();
		LOGGER.info("Crossrock initialized");
	}
}
