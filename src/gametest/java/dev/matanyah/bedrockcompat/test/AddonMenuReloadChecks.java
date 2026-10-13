package dev.matanyah.bedrockcompat.test;

import dev.matanyah.bedrockcompat.bedrock.discovery.BedrockPackRepository;
import dev.matanyah.bedrockcompat.client.BedrockOptionsState;
import dev.matanyah.bedrockcompat.client.resource.VirtualCompatibilityPackRuntime;
import dev.matanyah.bedrockcompat.client.ui.AddonsScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

final class AddonMenuReloadChecks {
    static void run(ClientGameTestContext context) {
        context.runOnClient(mc -> {
            var expected=new dev.matanyah.bedrockcompat.bedrock.model.BedrockPackConfiguration(
                java.util.Optional.of("test-subpack"),java.util.Map.of("toggle",false,"choice","custom","amount",.75));
            BedrockOptionsState.setConfiguration("persistence-regression",expected);
            var selections=BedrockOptionsState.selectedIds();
            mc.options.save();
            try {
                var path=mc.gameDirectory.toPath().resolve("options.txt");
                var reloaded=new dev.matanyah.bedrockcompat.bedrock.storage.BedrockPackConfigurationStore(path).load();
                if(!expected.equals(reloaded.get("persistence-regression")))
                    throw new AssertionError("Vanilla options save discarded pack settings or selected subpack");
                BedrockOptionsState.initialize(path);
                if(!selections.equals(BedrockOptionsState.selectedIds()))
                    throw new AssertionError("Pack selections changed after options reload");
            } catch(java.io.IOException e) {throw new IllegalStateException(e);}
        });
        context.runOnClient(mc -> {
            long generation = VirtualCompatibilityPackRuntime.reloadGeneration();
            var resources = VirtualCompatibilityPackRuntime.currentSnapshot();
            var errors = VirtualCompatibilityPackRuntime.generationErrors();
            var parent = new net.minecraft.client.gui.screens.TitleScreen();
            for (int i=0;i<2;i++) {
                long start = System.nanoTime();
                var screen = new AddonsScreen(parent, new BedrockPackRepository(mc.gameDirectory.toPath()));
                mc.setScreenAndShow(screen);
                BedrockOptionsState.setSelectedIds(BedrockOptionsState.selectedIds());
                screen.onClose();
                if (VirtualCompatibilityPackRuntime.reloadGeneration()!=generation
                    || VirtualCompatibilityPackRuntime.currentSnapshot()!=resources
                    || VirtualCompatibilityPackRuntime.generationErrors()!=errors)
                    throw new AssertionError("Opening/closing add-ons regenerated resources or replaced reload errors");
                dev.matanyah.bedrockcompat.BedrockCompat.LOGGER.info("Metadata-only add-ons menu open/close: {} ms", (System.nanoTime()-start)/1_000_000d);
            }
        });
    }
}
