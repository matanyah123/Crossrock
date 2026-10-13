package dev.matanyah.bedrockcompat.bedrock.ui;

import com.google.gson.JsonObject;
import java.util.*;

/** Small, independently implemented registry; never synthesizes unknown engine templates. */
public final class BedrockUiTemplates {
    public static final String SOURCE = "<compatibility primitive>";
    // Explicitly verified against Mojang's resource_pack/ui/chest_screen.json.
    // File identity matters: arbitrary filenames must not invent an engine namespace.
    public static final Map<String,String> FILE_NAMESPACES = Map.of("ui/chest_screen.json", "chest");
    public static List<BedrockUi.Source> primitives() {
        var doc = new JsonObject(); doc.addProperty("namespace", "common");
        for (String name : List.of("empty_panel", "empty_image", "horizontal_stack_panel", "vertical_stack_panel")) {
            var value = new JsonObject();
            value.addProperty("type", name.endsWith("stack_panel") ? "stack_panel" : name.equals("empty_image") ? "image" : "panel");
            if (name.endsWith("stack_panel")) value.addProperty("orientation", name.startsWith("horizontal") ? "horizontal" : "vertical");
            doc.add(name, value);
        }
        return List.of(new BedrockUi.Source(SOURCE, doc));
    }
    public static String namespace(BedrockUi.Source source) {
        if (source.document().has("namespace")) return source.document().get("namespace").getAsString();
        String result = FILE_NAMESPACES.get(source.file());
        if (result == null) throw new IllegalArgumentException("Missing namespace (no verified engine file identity): " + source.file());
        return result;
    }
    private BedrockUiTemplates() {}
}
