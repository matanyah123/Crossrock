package dev.matanyah.eef.client;

import dev.matanyah.eef.*;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.PackType;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Loads declarative EEF rules from every enabled resource pack on each client reload. */
public final class EefResourceReloader implements SimpleSynchronousResourceReloadListener {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(EntityEventFeatures.MOD_ID, "rules");
	private final EefRuleEngine engine;
	public EefResourceReloader(EefRuleEngine engine) { this.engine = engine; }

	@Override public Identifier getFabricId() { return ID; }
	@Override public void onResourceManagerReload(ResourceManager manager) {
		List<EefRule> all = new ArrayList<>();
		manager.listResources("eef", id -> id.getPath().endsWith(".json")).entrySet().stream()
			.sorted(Comparator.comparing(e -> e.getKey().toString())).forEach(entry -> {
				try (Reader reader = new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8)) {
					all.addAll(EefRuleParser.parse(readerToString(reader)).rules());
				} catch (Exception exception) {
					EntityEventFeatures.LOGGER.warn("Ignoring malformed EEF resource {}: {}", entry.getKey(), exception.getMessage());
				}
			});
		engine.reload(new EefRuleSet("resource_packs", 2, all));
		EntityEventFeaturesClient.rulesReloaded();
		EntityEventFeatures.LOGGER.info("Loaded {} EEF resource-pack rules", all.size());
	}
	private static String readerToString(Reader reader) throws java.io.IOException {
		StringBuilder result = new StringBuilder(); char[] buffer = new char[4096]; int count;
		while ((count = reader.read(buffer)) >= 0) result.append(buffer, 0, count);
		return result.toString();
	}
	public static void register(EefRuleEngine engine) {
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new EefResourceReloader(engine));
	}
}
