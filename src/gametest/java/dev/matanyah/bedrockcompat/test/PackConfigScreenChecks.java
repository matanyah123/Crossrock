package dev.matanyah.bedrockcompat.test;

import dev.matanyah.bedrockcompat.bedrock.model.*;
import dev.matanyah.bedrockcompat.client.ui.BedrockPackConfigScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Real-client checks for responsive pack settings and their changing tooltips. */
final class PackConfigScreenChecks {
    static void run(ClientGameTestContext context) throws Exception {
        String label = "A long pack-provided setting description that should remain readable in a hover tooltip even on a narrow screen";
        var pack = new BedrockPack("Configuration layout test", "Description", "config-layout-test",
            BedrockVersion.unknown(), Optional.empty(), List.of(), List.of(), List.of(),
            List.of(new BedrockPackSetting.Toggle("example", label, true)),
            Path.of("config-layout-test"), "", BedrockPackType.RESOURCE, Optional.empty(), List.of());
        var previous = context.computeOnClient(mc -> new net.minecraft.client.gui.screens.TitleScreen());
        var screen = context.computeOnClient(mc -> new BedrockPackConfigScreen(previous, pack.uuid(), pack));
        context.setScreen(() -> screen);
        context.waitTicks(3);
        context.runOnClient(mc -> {
            int originalWidth = screen.width, originalHeight = screen.height;
            for (int width : new int[]{320, 640, 960}) {
                screen.resize(width, originalHeight);
                var list = (AbstractSelectionList<?>) screen.children().getFirst();
                check(list.getX() == 18 && list.getWidth() == width - 36, "Pack panel did not resize with padding");
            }
            screen.resize(originalWidth, originalHeight);
        });
        context.waitTicks(3);
        context.runOnClient(mc -> {
            var list = (AbstractSelectionList<?>) screen.children().getFirst();
            var children = (net.minecraft.client.gui.components.events.ContainerEventHandler) list.children().getFirst();
            var widget = (AbstractWidget) children.children().getFirst();
            var field = AbstractWidget.class.getDeclaredField("tooltip");
            field.setAccessible(true);
            var holder = (WidgetTooltipHolder) field.get(widget);
            check(holder.get() != null, "Setting hover tooltip was missing");
            // Updating the selection must also update the tooltip, not keep the old value.
            widget.setMessage(Component.literal(label + ": OFF"));
        });
        context.waitTicks(3);
        context.runOnClient(mc -> {
            var list = (AbstractSelectionList<?>) screen.children().getFirst();
            var entry = (net.minecraft.client.gui.components.events.ContainerEventHandler) list.children().getFirst();
            var cached = entry.getClass().getDeclaredField("tooltipMessage");
            cached.setAccessible(true);
            check(((Component) cached.get(entry)).getString().endsWith(": OFF"), "Tooltip retained a stale setting value");
        });
        int scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
        context.getInput().setCursorPos(200 * scale, 110 * scale);
        context.waitTicks(15);
        context.takeScreenshot("pack-config-responsive-tooltip");
        context.setScreen(() -> previous);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
