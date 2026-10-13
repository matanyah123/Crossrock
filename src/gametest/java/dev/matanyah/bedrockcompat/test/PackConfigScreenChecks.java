package dev.matanyah.bedrockcompat.test;

import dev.matanyah.bedrockcompat.bedrock.model.*;
import dev.matanyah.bedrockcompat.client.ui.BedrockPackConfigScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Real-client checks for responsive labels, typed controls and saved choices. */
final class PackConfigScreenChecks {
    static void run(ClientGameTestContext context) throws Exception {
        String label = "A long pack-provided setting description that should remain readable in a hover tooltip even on a narrow screen";
        var pack = new BedrockPack("Configuration layout test", "Description", "config-layout-test",
            BedrockVersion.unknown(), Optional.empty(), List.of(), List.of(), List.of(),
            List.of(new BedrockPackSetting.Toggle("example", label, true),
                new BedrockPackSetting.Slider("amount","Amount",0,10,1,5),
                new BedrockPackSetting.Dropdown("style","Style",List.of(new BedrockPackSetting.Option("a","Style A"),new BedrockPackSetting.Option("b","Style B")),"a"),
                new BedrockPackSetting.Multiselect("features","Features",List.of(new BedrockPackSetting.Option("x","Feature X")),List.of())),
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
            check(widget instanceof Checkbox,"Toggle did not use a checkbox");
            check(children.children().size()==2,"Setting label is not outside the control");
            var sliderEntry=(net.minecraft.client.gui.components.events.ContainerEventHandler)list.children().get(1);
            check(sliderEntry.children().getFirst() instanceof AbstractSliderButton,"Slider did not use a slider");
            var choiceEntry=(net.minecraft.client.gui.components.events.ContainerEventHandler)list.children().get(2);
            var dropdown=(Button)choiceEntry.children().getFirst();
            check(dropdown.getMessage().getString().startsWith("Style A"),"Dropdown includes its title instead of just the selected value");
            dropdown.onPress(new net.minecraft.client.input.KeyEvent(257,0,0));
            check(screen.children().size()==5,"Dropdown did not open a choices list");
            screen.keyPressed(new net.minecraft.client.input.KeyEvent(264,0,0));
            screen.keyPressed(new net.minecraft.client.input.KeyEvent(257,0,0));
            check(dropdown.getMessage().getString().startsWith("Style B"),"Dropdown did not select the next option");
            dropdown.onPress(new net.minecraft.client.input.KeyEvent(257,0,0));
            var popup=(ObjectSelectionList<?>)screen.children().getLast();
            check(popup.getSelected()==popup.children().get(1),"Dropdown did not retain its selected option");
            screen.keyPressed(new net.minecraft.client.input.KeyEvent(256,0,0));
            var field = AbstractWidget.class.getDeclaredField("tooltip");
            field.setAccessible(true);
            var holder = (WidgetTooltipHolder) field.get(children.children().get(1));
            check(holder.get() != null, "Setting hover tooltip was missing");
            ((Checkbox)widget).onPress(new net.minecraft.client.input.KeyEvent(257,0,0));
        });
        context.waitTicks(3);
        int scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
        context.getInput().setCursorPos(200 * scale, 110 * scale);
        context.waitTicks(15);
        context.takeScreenshot("pack-config-responsive-tooltip");
        context.getInput().setCursorPos(0,0);
        context.runOnClient(mc->{
            var list=(AbstractSelectionList<?>)screen.children().getFirst();
            var entry=(net.minecraft.client.gui.components.events.ContainerEventHandler)list.children().get(2);
            ((Button)entry.children().getFirst()).onPress(new net.minecraft.client.input.KeyEvent(257,0,0));
        });
        context.waitTicks(3);
        context.takeScreenshot("pack-config-dropdown");
        context.runOnClient(mc->screen.keyPressed(new net.minecraft.client.input.KeyEvent(256,0,0)));
        context.runOnClient(mc->screen.onClose());
        context.runOnClient(mc->{
            var saved=dev.matanyah.bedrockcompat.client.BedrockOptionsState.configuration(pack.uuid(),pack);
            check(Boolean.FALSE.equals(saved.settingValues().get("example")),"Checkbox value was not saved");
            check("b".equals(saved.settingValues().get("style")),"Dropdown value was not saved");
        });
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
