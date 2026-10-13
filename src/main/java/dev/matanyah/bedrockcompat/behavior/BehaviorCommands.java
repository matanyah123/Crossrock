package dev.matanyah.bedrockcompat.behavior;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.network.chat.Component;

/** Administrative infrastructure commands; no Bedrock commands or gameplay subsystems emulated. */
public final class BehaviorCommands {
    public static void register(){CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->dispatcher.register(
        Commands.literal("crossrock").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
        .then(Commands.literal("status").executes(c->{var r=BehaviorRuntime.get(c.getSource().getServer());c.getSource().sendSuccess(()->Component.literal("Crossrock generation "+r.revision()+": "+r.profile().packs().size()+" packs, "+r.profile().definitions().registry("minecraft:entity").size()+" actor identities, "+r.profile().definitions().registry("minecraft:item").size()+" item identities. Scripts/gameplay deferred."),false);return 1;}))
        .then(Commands.literal("summon").then(Commands.argument("identifier",IdentifierArgument.id()).executes(c->{var source=c.getSource();var defs=BehaviorRuntime.get(source.getServer()).profile().definitions();String id=IdentifierArgument.getId(c,"identifier").toString();if(defs.find("minecraft:entity",id).isEmpty()){source.sendFailure(Component.literal("Unknown active Bedrock entity: "+id));return 0;}var entity=BehaviorIdentities.ENTITY.create(source.getLevel(),EntitySpawnReason.COMMAND);if(entity==null)return 0;entity.initialize(id,defs);entity.setPos(source.getPosition());source.getLevel().addFreshEntity(entity);return 1;})))
        .then(Commands.literal("give").then(Commands.argument("identifier",IdentifierArgument.id()).executes(c->{var source=c.getSource();var player=source.getPlayerOrException();String id=IdentifierArgument.getId(c,"identifier").toString();var defs=BehaviorRuntime.get(source.getServer()).profile().definitions();if(defs.find("minecraft:item",id).isEmpty()){source.sendFailure(Component.literal("Unknown active Bedrock item: "+id));return 0;}var stack=BehaviorIdentities.item(defs,id,1);if(!player.getInventory().add(stack))player.drop(stack,false);return 1;})))
    ));}
}
