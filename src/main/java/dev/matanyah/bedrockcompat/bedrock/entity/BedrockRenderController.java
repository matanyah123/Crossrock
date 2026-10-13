package dev.matanyah.bedrockcompat.bedrock.entity;

import com.google.gson.*;
import java.util.*;

/** Pack-authored selection logic; no entity-specific names or pack-specific overrides. */
public final class BedrockRenderController {
    private static final Molang.Expression HURT=Molang.compile("q.hurt_time > 0"), FIRE=Molang.compile("q.is_on_fire");
    public record Material(String bonePattern, String identifier) {}
    public record Visibility(String bonePattern, boolean visible) {}
    public record Color(double r,double g,double b,double a) {
        public Color overlay(Color overlay) {
            return new Color(r*(1-overlay.a)+overlay.r*overlay.a,g*(1-overlay.a)+overlay.g*overlay.a,
                b*(1-overlay.a)+overlay.b*overlay.a,a);
        }
    }
    public record Appearance(Color color,double offsetU,double offsetV,double scaleU,double scaleV,
                             boolean fullBright,boolean replaceHurtOverlay) {}
    public record Selection(String geometry, List<String> textures, List<Material> materials,
                            List<Visibility> visibility,Appearance appearance) {}
    private record ColorExpression(Molang.Expression r,Molang.Expression g,Molang.Expression b,Molang.Expression a) {
        Color sample(Molang.Context context){return new Color(finite(r,context),finite(g,context),finite(b,context),finite(a,context));}
    }
    private record Rule(String pattern, Molang.Expression expression) {}
    private final Molang.Expression geometry;
    private final List<Molang.Expression> textures;
    private final List<Rule> materials, visibility;
    private final Map<String,Map<String,Object>> arrays;
    private final ColorExpression color,overlay,hurt,fire;
    private final List<Molang.Expression> uvOffset,uvScale;
    private final boolean ignoreLighting;

    public BedrockRenderController(JsonObject source) {
        geometry = Molang.compile(source.get("geometry").getAsString());
        textures = expressions(source.getAsJsonArray("textures"));
        materials = rules(source.getAsJsonArray("materials"));
        visibility = rules(source.getAsJsonArray("part_visibility"));
        Map<String,Map<String,Object>> compiled = new LinkedHashMap<>();
        if (source.has("arrays")) for (var group : source.getAsJsonObject("arrays").entrySet()) {
            if (!Set.of("geometries", "textures", "materials").contains(group.getKey()))
                throw new IllegalArgumentException("Unknown render-controller array group: " + group.getKey());
            Map<String,List<String>> typedArrays = new LinkedHashMap<>();
            for (var array : group.getValue().getAsJsonObject().entrySet()) {
                String name = array.getKey().toLowerCase(Locale.ROOT);
                if (!name.matches("array\\.[a-z0-9_.]+"))
                    throw new IllegalArgumentException("Invalid render-controller array name: " + name);
                List<String> members = new ArrayList<>();
                for (var member : array.getValue().getAsJsonArray()) members.add(member.getAsString());
                if (typedArrays.putIfAbsent(name, List.copyOf(members)) != null)
                    throw new IllegalArgumentException("Duplicate render-controller array: " + name);
            }
            compiled.put(group.getKey(), new ResourceArrays(typedArrays).compile());
        }
        arrays = Map.copyOf(compiled);
        color=color(source,"color",1);overlay=color(source,"overlay_color",0);
        hurt=source.has("is_hurt_color")?color(source,"is_hurt_color",0):null;
        fire=source.has("on_fire_color")?color(source,"on_fire_color",0):null;
        JsonObject uv=source.has("uv_anim")?source.getAsJsonObject("uv_anim"):new JsonObject();
        uvOffset=uv(uv,"offset",0);uvScale=uv(uv,"scale",1);
        ignoreLighting=source.has("ignore_lighting")&&source.get("ignore_lighting").getAsBoolean();
    }

    /** Bare array members concatenate resources; indexed members remain lazy Molang. */
    private static final class ResourceArrays {
        private static final int MAX_MEMBERS = 65536, MAX_ARRAYS = 4096, MAX_DEPTH = 64;
        private final Map<String,List<String>> source;
        private final Map<String,List<Molang.Expression>> resolved = new LinkedHashMap<>();
        private final Map<String,Integer> depths = new HashMap<>();
        private final Set<String> visiting = new HashSet<>();
        private int expandedMembers;

        private ResourceArrays(Map<String,List<String>> source) {
            this.source = source;
            if (source.size() > MAX_ARRAYS || source.values().stream().mapToLong(List::size).sum() > MAX_MEMBERS)
                throw new IllegalArgumentException("Render-controller array definition budget exceeded");
        }

        private Map<String,Object> compile() {
            source.keySet().forEach(this::resolve);
            return Map.copyOf(resolved);
        }

        private List<Molang.Expression> resolve(String name) {
            if (resolved.containsKey(name)) return resolved.get(name);
            if (!source.containsKey(name)) throw new IllegalArgumentException("Missing render-controller array: " + name);
            if (visiting.contains(name)) throw new IllegalArgumentException("Cyclic render-controller array: " + name);
            if (visiting.size() >= MAX_DEPTH) throw new IllegalArgumentException("Render-controller array nesting exceeds " + MAX_DEPTH);
            visiting.add(name);
            try {
                List<Molang.Expression> members = new ArrayList<>();
                int depth = 1;
                for (String member : source.get(name)) {
                    String reference = member.strip().toLowerCase(Locale.ROOT);
                    if (reference.matches("array\\.[a-z0-9_.]+")) {
                        List<Molang.Expression> nested = resolve(reference);
                        depth = Math.max(depth, depths.get(reference) + 1);
                        if (depth > MAX_DEPTH) throw new IllegalArgumentException("Render-controller array nesting exceeds " + MAX_DEPTH);
                        reserve(nested.size());
                        members.addAll(nested);
                    } else {
                        reserve(1);
                        members.add(Molang.compile(member));
                    }
                }
                List<Molang.Expression> result = List.copyOf(members);
                resolved.put(name, result);
                depths.put(name, depth);
                return result;
            } finally { visiting.remove(name); }
        }

        private void reserve(int count) {
            if (count > MAX_MEMBERS - expandedMembers)
                throw new IllegalArgumentException("Render-controller expanded array budget exceeded");
            expandedMembers += count;
        }
    }

    public Selection select(Molang.Context context, Map<String,Object> aliases) {
        // Non-resource expressions (e.g. visibility comparisons) may use an unambiguous
        // array, but a shared name must never silently choose the last resource group.
        Map<String,Object> bindings = new HashMap<>(aliases);
        Set<String> seen = new HashSet<>(), ambiguous = new HashSet<>();
        for (var group : arrays.values()) for (var array : group.entrySet()) {
            if (!seen.add(array.getKey())) ambiguous.add(array.getKey());
            else bindings.put(array.getKey(), array.getValue());
        }
        ambiguous.forEach(bindings::remove);
        return context.withBindings(bindings, () -> selectBound(context, aliases));
    }

    private <T> T withArrays(Molang.Context context, Map<String,Object> aliases, String type,
                            java.util.function.Supplier<T> action) {
        Map<String,Object> bindings = new HashMap<>(aliases);
        bindings.putAll(arrays.getOrDefault(type, Map.of()));
        return context.withBindings(bindings, action);
    }

    private Selection selectBound(Molang.Context context, Map<String,Object> aliases) {
        String chosenGeometry = withArrays(context, aliases, "geometries", () -> resource(geometry.evaluate(context)));
        List<String> chosenTextures = withArrays(context, aliases, "textures", () ->
            textures.stream().map(e -> resource(e.evaluate(context))).toList());
        List<Material> chosenMaterials = withArrays(context, aliases, "materials", () -> {
            var selected=new ArrayList<Material>();
            // Bedrock controllers may omit materials, inheriting the entity/attachable default.
            if(materials.stream().noneMatch(r->r.pattern.equals("*"))&&aliases.containsKey("material.default"))
                selected.add(new Material("*",resource(aliases.get("material.default"))));
            materials.forEach(r->selected.add(new Material(r.pattern,resource(r.expression.evaluate(context)))));
            return List.copyOf(selected);
        });
        List<Visibility> chosenVisibility = visibility.stream()
            .map(r -> new Visibility(r.pattern, Molang.truth(r.expression.evaluate(context)))).toList();
        Color tint=color.sample(context).overlay(overlay.sample(context));
        if(hurt!=null&&Molang.truth(HURT.evaluate(context)))tint=tint.overlay(hurt.sample(context));
        if(fire!=null&&Molang.truth(FIRE.evaluate(context)))tint=tint.overlay(fire.sample(context));
        return new Selection(chosenGeometry, chosenTextures, chosenMaterials, chosenVisibility,
            new Appearance(tint,finite(uvOffset.get(0),context),finite(uvOffset.get(1),context),
                finite(uvScale.get(0),context),finite(uvScale.get(1),context),ignoreLighting,hurt!=null));
    }

    private static double finite(Molang.Expression expression,Molang.Context context) {
        double value=Molang.number(expression.evaluate(context));
        if(!Double.isFinite(value))throw new IllegalArgumentException("Non-finite render-controller value");
        return value;
    }
    private static ColorExpression color(JsonObject source,String key,double alpha) {
        JsonObject value=source.has(key)?source.getAsJsonObject(key):new JsonObject();
        return new ColorExpression(component(value,"r",1),component(value,"g",1),component(value,"b",1),component(value,"a",alpha));
    }
    private static Molang.Expression component(JsonObject object,String key,double fallback) {
        return Molang.compile(object.has(key)?object.get(key).getAsString():Double.toString(fallback));
    }
    private static List<Molang.Expression> uv(JsonObject object,String key,double fallback) {
        if(!object.has(key))return List.of(Molang.compile(Double.toString(fallback)),Molang.compile(Double.toString(fallback)));
        List<Molang.Expression> result=expressions(object.getAsJsonArray(key));
        if(result.size()!=2)throw new IllegalArgumentException("UV "+key+" requires two components");
        return result;
    }

    private static String resource(Object value) {
        if (!(value instanceof String text) || text.isBlank())
            throw new IllegalArgumentException("Render controller did not resolve a resource identifier");
        return text;
    }
    private static List<Molang.Expression> expressions(JsonArray array) {
        if (array == null) return List.of();
        List<Molang.Expression> out = new ArrayList<>();
        for (JsonElement e : array) out.add(Molang.compile(e.getAsString()));
        return List.copyOf(out);
    }
    private static List<Rule> rules(JsonArray array) {
        if (array == null) return List.of();
        List<Rule> out = new ArrayList<>();
        for (JsonElement e : array) for (var rule : e.getAsJsonObject().entrySet())
            out.add(new Rule(rule.getKey(), Molang.compile(rule.getValue().getAsString())));
        return List.copyOf(out);
    }
}
