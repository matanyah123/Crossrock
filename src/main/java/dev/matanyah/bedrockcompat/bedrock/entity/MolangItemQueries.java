package dev.matanyah.bedrockcompat.bedrock.entity;

import java.util.*;

/** The rendered item, not an implicit lookup of its owner's main hand. */
public record MolangItemQueries(Molang.Queries fallback, Set<String> tags, int maxDurability,
                                int damage, boolean enchanted, boolean stackable) implements Molang.Queries {
    public MolangItemQueries { tags=Set.copyOf(tags); }
    @Override public Object get(String name,List<Object> args) {
        return switch(name) {
            case "max_durability" -> (double)maxDurability;
            case "remaining_durability" -> (double)Math.max(0,maxDurability-damage);
            case "is_enchanted" -> enchanted?1d:0d;
            case "is_stackable" -> stackable?1d:0d;
            case "all_tags", "any_tag" -> {
                if(args.isEmpty())throw new IllegalArgumentException(name+" requires one or more tags");
                yield (name.equals("all_tags")?args.stream().allMatch(tags::contains):args.stream().anyMatch(tags::contains))?1d:0d;
            }
            default -> fallback.get(name,args);
        };
    }
}
