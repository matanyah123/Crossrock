package dev.matanyah.bedrockcompat.bedrock.entity;

import java.util.*;

/** Immutable frame data; renderer/animation evaluation never reads mutable world objects. */
public record EntityQueryContext(String name, String biome, Set<String> biomeTags,
                                 Map<String, Object> values) implements Molang.Queries {
    public EntityQueryContext {
        biome=BedrockBiomes.identifier(biome);
        biomeTags=BedrockBiomes.tags(biome,biomeTags);values=Map.copyOf(values);
        Objects.requireNonNull(name);Objects.requireNonNull(biome);
    }
    public EntityQueryContext(String name, String biome, Set<String> biomeTags, Map<String, Object> values,
                              dev.matanyah.bedrockcompat.bedrock.model.BedrockPackConfiguration configuration) {
        this(name, biome, biomeTags, withSettings(values, configuration));
    }
    private static Map<String, Object> withSettings(Map<String, Object> values,
            dev.matanyah.bedrockcompat.bedrock.model.BedrockPackConfiguration configuration) {
        Map<String, Object> result = new LinkedHashMap<>(values);
        result.put("pack_settings", configuration.settingValues());
        return result;
    }
    @Override public Object get(String query,List<Object> args) {
        Object extension=values.get("query_provider");
        if(extension instanceof MolangQueryProvider provider) {
            var result=provider.query(query,args);
            if(result.isPresent())return result.get();
        }
        return switch(query) {
            case "rider_body_x_rotation","rider_body_y_rotation","rider_head_x_rotation","rider_head_y_rotation" -> {
                if(args.size()>1)throw new IllegalArgumentException(query+" takes one rider index");
                double index=args.isEmpty()?0:Molang.number(args.getFirst());
                Object riders=values.get("riders");
                if(!Double.isFinite(index)||index!=Math.rint(index)||index<0||!(riders instanceof List<?> list)||index>=list.size())yield 0d;
                Object rider=list.get((int)index);
                if(!(rider instanceof Map<?,?> fields))yield 0d;
                Object rotation=fields.get(query.substring(6));
                yield rotation==null?0d:rotation;
            }
            case "rotation_to_camera" -> {
                if(args.size()!=1||!(values.get("camera_delta") instanceof List<?> delta))throw new IllegalArgumentException("rotation_to_camera requires an axis and camera data");
                int axis=(int)Molang.number(args.getFirst());
                double x=Molang.number(delta.get(0)),y=Molang.number(delta.get(1)),z=Molang.number(delta.get(2));
                if(axis!=0&&axis!=1)throw new IllegalArgumentException("rotation_to_camera axis must be 0 or 1");
                yield axis==0?-Math.toDegrees(Math.atan2(y,Math.hypot(x,z))):Math.toDegrees(Math.atan2(-x,z));
            }
            case "camera_distance_range_lerp" -> {
                if(args.size()!=2)throw new IllegalArgumentException("camera_distance_range_lerp requires near and far distances");
                double near=Molang.number(args.getFirst()),far=Molang.number(args.get(1));
                double distance=Molang.number(values.getOrDefault("distance_from_camera",0d));
                yield near==far?(distance>=far?1d:0d):Math.clamp((distance-near)/(far-near),0,1);
            }
            case "any","all" -> {
                yield MolangQueryFunctions.get(query,args,(name,arguments)->{throw new IllegalArgumentException(name);});
            }
            case "property","has_property" -> {
                if(args.size()!=1)throw new IllegalArgumentException(query+" requires one argument");
                Object properties=values.get("properties");
                Object property=properties instanceof Map<?,?> map?map.get(args.getFirst().toString()):null;
                if(query.equals("has_property"))yield property!=null?1d:0d;
                if(property==null)throw new IllegalArgumentException("Unsupported Bedrock entity property: "+args.getFirst());
                yield property;
            }
            case "position","position_delta","movement_direction" -> {
                if(args.size()!=1)throw new IllegalArgumentException(query+" requires one axis");
                int index=MolangQueryFunctions.index(args.getFirst(),2,query);
                Object point=values.get(query);
                if(index<0||index>2||!(point instanceof List<?> vector))throw new IllegalArgumentException("Invalid position query");
                yield vector.get(index);
            }
            case "is_riding_any_entity_of_type" -> {
                Object riding=values.get("vehicle_types");
                yield riding instanceof Set<?> types&&args.stream().anyMatch(types::contains)?1d:0d;
            }
            case "is_pack_setting_selected" -> {
                if (args.size() != 2) throw new IllegalArgumentException("is_pack_setting_selected requires two arguments");
                Object settings = values.get("pack_settings");
                Object selected = settings instanceof Map<?, ?> map ? map.get(args.get(0).toString()) : null;
                yield (selected instanceof List<?> list ? list.contains(args.get(1).toString())
                    : selected != null && selected.toString().equals(args.get(1).toString())) ? 1d : 0d;
            }
            case "get_pack_setting" -> {
                if(args.size()!=1)throw new IllegalArgumentException("get_pack_setting requires a slider name");
                Object settings=values.get("pack_settings");
                Object selected=settings instanceof Map<?,?> map?map.get(args.getFirst().toString()):null;
                if(!(selected instanceof Number))throw new IllegalArgumentException("Unsupported Bedrock numeric pack setting: "+args.getFirst());
                yield Molang.number(selected);
            }
            case "is_pack_setting_enabled" -> {
                if(args.size()!=1)throw new IllegalArgumentException("is_pack_setting_enabled requires one argument");
                Object settings=values.get("pack_settings");
                Object enabled=settings instanceof Map<?,?> map?map.get(args.getFirst().toString()):null;
                yield enabled!=null&&(enabled.equals(true)||enabled.toString().equals("true")||enabled.toString().equals("1"))?1d:0d;
            }
            case "get_name" -> name;
            case "is_owner_identifier_any" -> args.contains(values.get("owner_identifier"))?1d:0d;
            case "is_name_any" -> args.stream().anyMatch(name::equals)?1d:0d;
            case "entity_biome_has_any_identifier" -> args.stream().map(Object::toString)
                .map(BedrockBiomes::identifier).anyMatch(biome::equals)?1d:0d;
            case "entity_biome_has_any_tags" -> args.stream().anyMatch(biomeTags::contains)?1d:0d;
            case "entity_biome_has_all_tags" -> args.stream().allMatch(biomeTags::contains)?1d:0d;
            case "in_range" -> args.size()==3 && Molang.number(args.get(0))>=Molang.number(args.get(1))
                && Molang.number(args.get(0))<=Molang.number(args.get(2))?1d:0d;
            default -> {
                if(!values.containsKey(query))throw new IllegalArgumentException("Unsupported Bedrock query: "+query);
                yield values.get(query);
            }
        };
    }
}
