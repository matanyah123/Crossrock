package dev.matanyah.bedrockcompat.bedrock.entity;

import com.google.gson.*;
import java.util.*;

/** Client entity aliases and script entry points, independent of Java renderer classes. */
public record BedrockClientEntity(String identifier, Map<String,Object> aliases,
                                 List<Molang.Expression> initialize, List<Molang.Expression> preAnimation,
                                 List<Controller> controllers, Map<String,String> animationAliases,
                                 List<BedrockAnimationController.Animation> animate,Molang.Expression scaleX,Molang.Expression scaleY,Molang.Expression scaleZ,
                                 String queryableGeometry,boolean enableAttachables,Molang.Expression heldItemScale) {
    public record Controller(String identifier, Molang.Expression condition) {}

    public static BedrockClientEntity parse(JsonObject source) {
        JsonObject description = source.getAsJsonObject("description");
        String identifier = description.get("identifier").getAsString();
        Map<String,Object> aliases = new LinkedHashMap<>();
        for (String group : List.of("geometry", "textures", "materials", "sound_effects", "particle_effects")) {
            if (!description.has(group)) continue;
            String prefix = switch (group) {case "textures" -> "texture"; case "materials" -> "material";
                case "sound_effects" -> "sound"; case "particle_effects" -> "particle"; default -> group;};
            for (var alias : description.getAsJsonObject(group).entrySet())
                aliases.put(prefix + "." + alias.getKey().toLowerCase(Locale.ROOT), alias.getValue().getAsString());
        }
        JsonObject scripts = description.has("scripts") ? description.getAsJsonObject("scripts") : new JsonObject();
        List<Controller> controllers = new ArrayList<>();
        if (description.has("render_controllers")) for (JsonElement entry : description.getAsJsonArray("render_controllers")) {
            if (entry.isJsonPrimitive()) controllers.add(new Controller(entry.getAsString(), Molang.compile("1")));
            else for (var conditional : entry.getAsJsonObject().entrySet())
                controllers.add(new Controller(conditional.getKey(), Molang.compile(conditional.getValue().getAsString())));
        }
        Map<String,String> animationAliases = new LinkedHashMap<>();
        if(description.has("animations"))description.getAsJsonObject("animations").entrySet().forEach(e->animationAliases.put(e.getKey(),e.getValue().getAsString()));
        return new BedrockClientEntity(identifier, Map.copyOf(aliases), scripts(scripts.get("initialize")),
            scripts(scripts.get("pre_animation")), List.copyOf(controllers), Map.copyOf(animationAliases),
            BedrockAnimationController.animations(scripts.getAsJsonArray("animate")),scale(scripts,"x"),scale(scripts,"y"),scale(scripts,"z"),
            description.has("queryable_geometry")?description.get("queryable_geometry").getAsString().toLowerCase(Locale.ROOT):"default",
            description.has("enable_attachables")&&description.get("enable_attachables").getAsBoolean(),
            Molang.compile(description.has("held_item_scale")?description.get("held_item_scale").getAsString():"1"));
    }

    private static Molang.Expression scale(JsonObject scripts,String axis) {
        var value=scripts.has("scale")?scripts.get("scale"):null;
        if(scripts.has("scale"+axis))value=scripts.get("scale"+axis);
        return Molang.compile(value==null?"1":value.getAsString());
    }

    private static List<Molang.Expression> scripts(JsonElement value) {
        if (value == null) return List.of();
        if (!value.isJsonArray()) return List.of(Molang.compile(value.getAsString()));
        List<Molang.Expression> result = new ArrayList<>();
        for (JsonElement script : value.getAsJsonArray()) result.add(Molang.compile(script.getAsString()));
        return List.copyOf(result);
    }
}
