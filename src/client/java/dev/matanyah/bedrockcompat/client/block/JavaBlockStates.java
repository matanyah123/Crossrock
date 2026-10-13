package dev.matanyah.bedrockcompat.client.block;

import dev.matanyah.bedrockcompat.bedrock.resource.BedrockBlockStates;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import java.util.*;

public final class JavaBlockStates {
    private JavaBlockStates() {}
    public static List<BedrockBlockStates.Variant> variants(Identifier id) {
        var block=BuiltInRegistries.BLOCK.getOptional(id).orElse(null);if(block==null)return List.of();
        List<BlockState> states=new ArrayList<>(block.getStateDefinition().getPossibleStates());
        states.remove(block.defaultBlockState());states.addFirst(block.defaultBlockState());
        List<BedrockBlockStates.Variant> result=new ArrayList<>();
        for(var state:states) {
            Map<String,Object> values=new LinkedHashMap<>();List<String> selector=new ArrayList<>();
            for(var property:state.getProperties()) {
                Comparable<?> value=state.getValue(property);
                String serialized=name(property,value);selector.add(property.getName()+"="+serialized);
                values.put(property.getName(),value instanceof Boolean?value:value instanceof Number n?n.doubleValue():serialized);
            }
            Collections.sort(selector);result.add(new BedrockBlockStates.Variant(String.join(",",selector),values));
        }
        return List.copyOf(result);
    }
    public static Optional<Identifier> item(String authored) {
        Identifier id=Identifier.tryParse(authored);if(id==null)return Optional.empty();
        if(dev.matanyah.bedrockcompat.client.behavior.BehaviorClientSession.definitions().find("minecraft:item",authored).isPresent())return Optional.of(id);
        if(BuiltInRegistries.ITEM.getOptional(id).isPresent())return Optional.of(id);
        for(var candidate:BuiltInRegistries.ITEM.keySet())if(dev.matanyah.bedrockcompat.bedrock.entity.BedrockItemIds.matches(candidate.toString(),authored))return Optional.of(candidate);
        return Optional.empty();
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static String name(Property property,Comparable value){return property.getName(value);}
}
