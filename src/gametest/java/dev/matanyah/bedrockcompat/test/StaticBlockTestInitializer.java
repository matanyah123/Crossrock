package dev.matanyah.bedrockcompat.test;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.item.*;

/** Test-only external runtime identity: production translation never creates gameplay objects. */
public final class StaticBlockTestInitializer implements ModInitializer {
    public static Block BLOCK;
    public static Item ITEM;
    public static Block STATEFUL;
    public static Item ICON;
    @Override public void onInitialize() {
        Identifier id=Identifier.fromNamespaceAndPath("bedrock_compat_test","geometry_probe");
        BLOCK=Registry.register(BuiltInRegistries.BLOCK,id,new Block(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK,id))));
        ITEM=Registry.register(BuiltInRegistries.ITEM,id,new BlockItem(BLOCK,new Item.Properties().setId(ResourceKey.create(Registries.ITEM,id)).useBlockDescriptionPrefix()));
        Identifier stateful=Identifier.fromNamespaceAndPath("bedrock_compat_test","stateful_probe");
        STATEFUL=Registry.register(BuiltInRegistries.BLOCK,stateful,new Stateful(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK,stateful))));
        Registry.register(BuiltInRegistries.ITEM,stateful,new BlockItem(STATEFUL,new Item.Properties().setId(ResourceKey.create(Registries.ITEM,stateful)).useBlockDescriptionPrefix()));
        Identifier icon=Identifier.fromNamespaceAndPath("bedrock_compat_test","icon_probe");
        ICON=Registry.register(BuiltInRegistries.ITEM,icon,new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM,icon))));
    }
    private static final class Stateful extends Block {
        Stateful(BlockBehaviour.Properties properties){super(properties);registerDefaultState(defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED,false).setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,net.minecraft.core.Direction.NORTH));}
        @Override protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block,net.minecraft.world.level.block.state.BlockState> builder){builder.add(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED,net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);}
    }

}
