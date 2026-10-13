package dev.matanyah.bedrockcompat.behavior;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

public final class BehaviorIdentities {
    private static final ResourceKey<EntityType<?>> ENTITY_KEY=ResourceKey.create(Registries.ENTITY_TYPE,Identifier.fromNamespaceAndPath("bedrock_compat","actor"));
    private static final ResourceKey<Item> ITEM_KEY=ResourceKey.create(Registries.ITEM,Identifier.fromNamespaceAndPath("bedrock_compat","item"));
    public static final EntityType<BehaviorEntity> ENTITY=Registry.register(BuiltInRegistries.ENTITY_TYPE,ENTITY_KEY,EntityType.Builder.<BehaviorEntity>of(BehaviorEntity::new,MobCategory.MISC).sized(.6f,1.8f).clientTrackingRange(10).build(ENTITY_KEY));
    public static final Item ITEM=Registry.register(BuiltInRegistries.ITEM,ITEM_KEY,new Item(new Item.Properties().setId(ITEM_KEY)));
    public static void register(){FabricDefaultAttributeRegistry.register(ENTITY,Mob.createMobAttributes());}
    public static String itemIdentifier(ItemStack stack){if(!stack.is(ITEM))return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getStringOr("crossrock:identifier","");}
    public static ItemStack item(BehaviorDefinitions definitions,String id,int count){
        var definition=definitions.find("minecraft:item",id).orElseThrow(()->new IllegalArgumentException("Unknown Bedrock item "+id));
        ItemStack stack=new ItemStack(ITEM,count);CompoundTag tag=new CompoundTag();tag.putString("crossrock:identifier",id);tag.putString("crossrock:pack",definition.pack());stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));stack.set(DataComponents.ITEM_MODEL,Identifier.parse(id));
        stack.set(DataComponents.ITEM_NAME,net.minecraft.network.chat.Component.translatable("item."+id+".name"));return stack;
    }
}
