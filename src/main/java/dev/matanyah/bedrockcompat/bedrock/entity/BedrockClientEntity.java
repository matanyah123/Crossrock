package dev.matanyah.bedrockcompat.bedrock.entity;

import com.google.gson.*;
import java.util.*;

/** Client entity aliases and script entry points, independent of Java renderer classes. */
public record BedrockClientEntity(String identifier, Map<String,Object> aliases,
                                 List<Molang.Expression> initialize, List<Molang.Expression> preAnimation,
                                 List<Controller> controllers, Map<String,String> animationAliases,
                                 List<BedrockAnimationController.Animation> animate,Molang.Expression scaleX,Molang.Expression scaleY,Molang.Expression scaleZ,
                                 String queryableGeometry,boolean enableAttachables,Molang.Expression heldItemScale,
                                 Molang.Expression hideHeldItems, boolean hideArmor, boolean heldItemIgnoresLighting,
                                 Set<String> publicVariables, List<Locator> locators) {
    public record Controller(String identifier, Molang.Expression condition) {}
    public record Locator(String name,String bone,BedrockGeometry.Vec offset) {}

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
        var animate=new ArrayList<>(BedrockAnimationController.animations(scripts.getAsJsonArray("animate")));
        // Before scripts.animate, declarations were also the ordered controller entry points.
        if(description.has("animation_controllers"))for(var entry:description.getAsJsonArray("animation_controllers")) {
            var declarations=new JsonObject();
            if(entry.isJsonPrimitive())declarations.addProperty(entry.getAsString(),entry.getAsString());
            else declarations=entry.getAsJsonObject();
            for(var alias:declarations.entrySet()) {
                if(animationAliases.putIfAbsent(alias.getKey(),alias.getValue().getAsString())!=null)
                    throw new IllegalArgumentException("Duplicate animation/controller alias: "+alias.getKey());
                animate.add(new BedrockAnimationController.Animation(alias.getKey(),Molang.compile("1")));
            }
        }
        var publicVariables=new HashSet<String>();
        if(scripts.has("variables"))for(var entry:scripts.getAsJsonObject("variables").entrySet())
            if(entry.getValue().getAsString().equals("public")) {
                String name=entry.getKey().toLowerCase(Locale.ROOT);
                if(name.startsWith("variable."))name=name.substring(9);
                else if(name.startsWith("v."))name=name.substring(2);
                publicVariables.add(name);
            }
        var locators=new ArrayList<Locator>();
        if(description.has("locators"))for(var locator:description.getAsJsonObject("locators").entrySet())
            for(var bone:locator.getValue().getAsJsonObject().entrySet()) {
                var xyz=bone.getValue().getAsJsonArray();
                if(xyz.size()!=3)throw new IllegalArgumentException("Locator requires three coordinates: "+locator.getKey());
                locators.add(new Locator(locator.getKey(),bone.getKey(),new BedrockGeometry.Vec(
                    xyz.get(0).getAsDouble(),xyz.get(1).getAsDouble(),xyz.get(2).getAsDouble())));
            }
        return new BedrockClientEntity(identifier, Map.copyOf(aliases), scripts(scripts.get("initialize")),
            scripts(scripts.get("pre_animation")), List.copyOf(controllers), Map.copyOf(animationAliases),
            List.copyOf(animate),scale(scripts,"x"),scale(scripts,"y"),scale(scripts,"z"),
            description.has("queryable_geometry")?description.get("queryable_geometry").getAsString().toLowerCase(Locale.ROOT):"default",
            description.has("enable_attachables")&&description.get("enable_attachables").getAsBoolean(),
            Molang.compile(description.has("held_item_scale")?description.get("held_item_scale").getAsString():"1"),
            Molang.compile(scripts.has("hide_held_items")?scripts.get("hide_held_items").getAsString():"0"),
            description.has("hide_armor")&&description.get("hide_armor").getAsBoolean(),
            description.has("held_item_ignores_lighting")&&description.get("held_item_ignores_lighting").getAsBoolean(),
            Set.copyOf(publicVariables),List.copyOf(locators));
    }

    private static Molang.Expression scale(JsonObject scripts,String axis) {
        var value=scripts.has("scale")?scripts.get("scale"):null;
        if(scripts.has("scale"+axis))value=scripts.get("scale"+axis);
        else if(scripts.has("scale"+axis.toUpperCase(Locale.ROOT)))value=scripts.get("scale"+axis.toUpperCase(Locale.ROOT));
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
