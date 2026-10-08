package dev.matanyah.eef;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.api.ModInitializer;

public final class EntityEventFeatures implements ModInitializer {
	public static final String MOD_ID = "entity_event_features";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public EntityEventFeatures() {
	}

	@Override
	public void onInitialize() {
		EefCommandPayload.register();
		EefCommandCatalogPayload.register();
		EefCommands.register();
		EefDamagePayload.register();
		EefActivityPayload.register();
		EefTradePayload.register();
		EefMerchantContextPayload.register();
		EefLifecyclePayload.register();
		EefServerDamageBridge.register();
		EefServerActivityBridge.register();
		EefServerTradeBridge.register();
		LOGGER.info("Entity Event Features initialized");
	}
}
