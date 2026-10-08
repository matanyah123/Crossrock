package dev.matanyah.bedrockcompat.mixin;

import dev.matanyah.bedrockcompat.entity.BedrockArmorStandAccess;
import dev.matanyah.bedrockcompat.entity.BedrockArmorStandState;
import dev.matanyah.bedrockcompat.entity.ArmorStandNetworking;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Persist separate Bedrock pose state without replacing Java's Pose compound. */
@Mixin(ArmorStand.class)
public abstract class ArmorStandStateMixin implements BedrockArmorStandAccess {
    @Unique private final BedrockArmorStandState bedrockCompat$state = new BedrockArmorStandState();
    @Override public BedrockArmorStandState bedrockCompatState() { return bedrockCompat$state; }

    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void bedrockCompat$cycle(Player player, InteractionHand hand, Vec3 hit,
                                    CallbackInfoReturnable<InteractionResult> callback) {
        ArmorStand stand = (ArmorStand)(Object)this;
        if (!bedrockCompat$state.active() || !player.isSecondaryUseActive()
                || hand != InteractionHand.MAIN_HAND || player.isSpectator() || !player.mayBuild() || stand.isMarker()) return;
        if (!stand.level().isClientSide()) {
            bedrockCompat$state.cycle();
            ArmorStandNetworking.changed(stand);
        }
        callback.setReturnValue(InteractionResult.SUCCESS);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void bedrockCompat$save(ValueOutput output, CallbackInfo callback) {
        if (!bedrockCompat$state.active() && bedrockCompat$state.poseIndex() == 0) return;
        output.putBoolean(BedrockArmorStandState.ACTIVE_KEY, bedrockCompat$state.active());
        output.putInt(BedrockArmorStandState.POSE_KEY, bedrockCompat$state.poseIndex());
        output.putBoolean(BedrockArmorStandState.ORIGINAL_ARMS_KEY, bedrockCompat$state.originalArms());
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void bedrockCompat$load(ValueInput input, CallbackInfo callback) {
        boolean wasActive=bedrockCompat$state.active(), originalArms=bedrockCompat$state.originalArms();
        bedrockCompat$state.restore(input.getBooleanOr(BedrockArmorStandState.ACTIVE_KEY, false),
            input.getIntOr(BedrockArmorStandState.POSE_KEY, 0),
            input.getBooleanOr(BedrockArmorStandState.ORIGINAL_ARMS_KEY, false));
        ArmorStand stand=(ArmorStand)(Object)this;
        if(!stand.level().isClientSide()) {
            if(wasActive&&!bedrockCompat$state.active())stand.setShowArms(originalArms);
            dev.matanyah.bedrockcompat.entity.ArmorStandCompatibility.reconcile(stand);
            ArmorStandNetworking.changed(stand);
        }
    }
}
