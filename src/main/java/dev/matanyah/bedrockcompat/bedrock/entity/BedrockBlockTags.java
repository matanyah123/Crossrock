package dev.matanyah.bedrockcompat.bedrock.entity;

/** Names used by Bedrock world queries that have direct Java tag equivalents. */
public final class BedrockBlockTags {
    private BedrockBlockTags() {}
    public static String javaTag(String tag) {
        return switch(tag) {
            case "minecraft:is_axe_item_destructible" -> "minecraft:mineable/axe";
            case "minecraft:is_hoe_item_destructible" -> "minecraft:mineable/hoe";
            case "minecraft:is_pickaxe_item_destructible" -> "minecraft:mineable/pickaxe";
            case "minecraft:is_shovel_item_destructible" -> "minecraft:mineable/shovel";
            case "minecraft:stone_tier_destructible" -> "minecraft:needs_stone_tool";
            case "minecraft:iron_tier_destructible" -> "minecraft:needs_iron_tool";
            case "snow" -> "minecraft:snow";
            default -> tag;
        };
    }
}
