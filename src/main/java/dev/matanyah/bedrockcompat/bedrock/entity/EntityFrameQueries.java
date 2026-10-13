package dev.matanyah.bedrockcompat.bedrock.entity;

import java.util.*;

/** Value-only adapters for equipment and camera queries, shared by native entity consumers. */
public record EntityFrameQueries(EntityQueryContext entity, Map<String,Item> equipment,
                                 double cameraPitch,double cameraYaw,String graphicsMode) implements Molang.Queries {
    public record Item(String identifier,Set<String> tags,int damage,double remainingUseSeconds) {
        public Item { tags=BedrockItemIds.tags(identifier,tags); }
        public Item(String identifier,Set<String> tags){this(identifier,tags,0,0);}
        public boolean empty(){return identifier.isEmpty()||identifier.equals("minecraft:air");}
    }
    public EntityFrameQueries { equipment=Map.copyOf(equipment); }
    @Override public Object get(String query,List<Object> args) {
        return switch(query) {
            case "camera_rotation" -> {
                if(args.size()!=1)throw new IllegalArgumentException("camera_rotation requires one axis");
                int axis=MolangQueryFunctions.index(args.getFirst(),1,query);
                if(axis!=0&&axis!=1)throw new IllegalArgumentException("camera_rotation axis must be 0 or 1");
                yield axis==0?cameraPitch:cameraYaw;
            }
            case "graphics_mode_is_any" -> args.contains(graphicsMode)?1d:0d;
            case "has_armor_slot" -> {
                int index=args.isEmpty()?0:(int)Molang.number(args.getFirst());
                String slot=switch(index){case 0->"head";case 1->"chest";case 2->"legs";case 3->"feet";case 4->"body";default->"";};
                Item armor=equipment.get("slot.armor."+slot);
                yield armor!=null&&!armor.empty()?1d:0d;
            }
            case "armor_damage_slot" -> {
                if(args.size()!=1)throw new IllegalArgumentException("armor_damage_slot requires one index");
                String slot=switch(MolangQueryFunctions.index(args.getFirst(),4,query)) {
                    case 0->"head";case 1->"chest";case 2->"legs";case 3->"feet";default->"body";
                };
                yield (double)item("slot.armor."+slot).damage;
            }
            case "item_remaining_use_duration" -> {
                if(args.isEmpty()||args.size()>2)throw new IllegalArgumentException("item_remaining_use_duration requires a hand and optional normalization");
                double remaining=item(hand(args.getFirst())).remainingUseSeconds;
                if(args.size()==1)yield remaining;
                double normalization=Molang.number(args.get(1));
                if(!Double.isFinite(normalization)||normalization<=0)throw new IllegalArgumentException("Item duration normalization must be positive");
                yield remaining/normalization;
            }
            case "get_equipped_item_name","is_item_equipped" -> {
                String hand=hand(args.isEmpty()?0d:args.getFirst());
                Item item=item(hand);
                yield query.equals("is_item_equipped")?(item.empty()?0d:1d):
                    item.empty()?"":BedrockItemIds.bedrock(item.identifier).replaceFirst("^minecraft:","");
            }
            case "is_item_name_any" -> {
                if(args.size()<2)throw new IllegalArgumentException("is_item_name_any requires a slot and names");
                String slot=args.getFirst().toString();
                int start=args.get(1) instanceof Number?2:1;
                if(start==2&&Molang.number(args.get(1))!=0)throw new IllegalArgumentException("Equipment slot index must be zero");
                Item item=item(slot);
                String id=item.empty()?"":item.identifier;
                yield args.subList(start,args.size()).stream().anyMatch(name->BedrockItemIds.matches(id,name.toString()))?1d:0d;
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
        if(item==null) {
            if(Set.of("slot.weapon.mainhand","slot.weapon.offhand","slot.armor.head","slot.armor.chest","slot.armor.legs","slot.armor.feet","slot.armor.body").contains(slot))return new Item("minecraft:air",Set.of());
            throw new IllegalArgumentException("Unsupported equipment slot: "+slot);
        }
        return item;
    }
    private static String hand(Object value) {
        if(value.equals("main_hand")||value instanceof Number n&&n.doubleValue()==0)return "slot.weapon.mainhand";
        if(value.equals("off_hand")||value instanceof Number n&&n.doubleValue()==1)return "slot.weapon.offhand";
        throw new IllegalArgumentException("Invalid hand slot: "+value);
    }
}
