package dev.matanyah.bedrockcompat.bedrock.entity;

import java.util.*;

/** Value-only adapters for equipment and camera queries, shared by native entity consumers. */
public record EntityFrameQueries(EntityQueryContext entity, Map<String,Item> equipment,
                                 double cameraPitch,double cameraYaw,String graphicsMode) implements Molang.Queries {
    public record Item(String identifier,Set<String> tags) {
        public Item { tags=Set.copyOf(tags); }
        public boolean empty(){return identifier.isEmpty()||identifier.equals("minecraft:air");}
    }
    public EntityFrameQueries { equipment=Map.copyOf(equipment); }
    @Override public Object get(String query,List<Object> args) {
        return switch(query) {
            case "camera_rotation" -> {
                int axis=args.isEmpty()?0:(int)Molang.number(args.getFirst());
                if(axis!=0&&axis!=1)throw new IllegalArgumentException("camera_rotation axis must be 0 or 1");
                yield axis==0?cameraPitch:cameraYaw;
            }
            case "graphics_mode_is_any" -> args.contains(graphicsMode)?1d:0d;
            case "get_equipped_item_name","is_item_equipped" -> {
                String hand=hand(args.isEmpty()?0d:args.getFirst());
                Item item=item(hand);
                yield query.equals("is_item_equipped")?(item.empty()?0d:1d):
                    item.empty()?"":item.identifier.replaceFirst("^minecraft:","");
            }
            case "is_item_name_any" -> {
                if(args.size()<2)throw new IllegalArgumentException("is_item_name_any requires a slot and names");
                String slot=args.getFirst().toString();
                int start=args.get(1) instanceof Number?2:1;
                if(start==2&&Molang.number(args.get(1))!=0)throw new IllegalArgumentException("Equipment slot index must be zero");
                Item item=item(slot);
                String id=item.empty()?"":item.identifier;
                yield args.subList(start,args.size()).contains(id)?1d:0d;
            }
            case "equipped_item_any_tag","equipped_item_all_tags" -> {
                if(args.size()<2)throw new IllegalArgumentException(query+" requires a slot and tags");
                Item item=item(args.getFirst().toString());
                boolean all=query.equals("equipped_item_all_tags");
                var requested=args.subList(1,args.size());
                yield !item.empty() && (all?requested.stream().allMatch(item.tags::contains):requested.stream().anyMatch(item.tags::contains))?1d:0d;
            }
            case "equipment_count" -> (double)equipment.entrySet().stream()
                .filter(e->e.getKey().startsWith("slot.armor.")&&!e.getValue().empty()).count();
            default -> entity.get(query,args);
        };
    }
    private Item item(String slot) {
        Item item=equipment.get(slot);
        if(item==null)throw new IllegalArgumentException("Unsupported equipment slot: "+slot);
        return item;
    }
    private static String hand(Object value) {
        if(value.equals("main_hand")||value instanceof Number n&&n.doubleValue()==0)return "slot.weapon.mainhand";
        if(value.equals("off_hand")||value instanceof Number n&&n.doubleValue()==1)return "slot.weapon.offhand";
        throw new IllegalArgumentException("Invalid hand slot: "+value);
    }
}
