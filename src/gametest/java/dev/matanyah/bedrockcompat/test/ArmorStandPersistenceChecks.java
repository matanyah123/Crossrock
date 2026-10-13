package dev.matanyah.bedrockcompat.test;

import dev.matanyah.bedrockcompat.entity.ArmorStandCompatibility;
import dev.matanyah.bedrockcompat.entity.BedrockArmorStandAccess;
import dev.matanyah.bedrockcompat.entity.BedrockArmorStandState;
import net.minecraft.core.Rotations;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Real entity codecs and mixins, not a unit test of the independent state record. */
final class ArmorStandPersistenceChecks {
    private ArmorStandPersistenceChecks() {}

    /** Called on the integrated server thread while the fixture pack is enabled. */
    static void run(MinecraftServer server) {
        try {
            for (boolean originalArms : new boolean[]{false, true}) {
                ArmorStand source = new ArmorStand(server.overworld(), 0, 99, 0);
                source.setShowArms(originalArms);
                source.setHeadPose(new Rotations(12, 34, 56));
                source.setBodyPose(new Rotations(7, 8, 9));
                source.setLeftArmPose(new Rotations(10, 20, 30));
                source.setRightArmPose(new Rotations(40, 50, 60));
                source.setLeftLegPose(new Rotations(70, 80, 90));
                source.setRightLegPose(new Rotations(100, 110, 120));
                ArmorStandCompatibility.reconcile(source);
                state(source).poseIndex(9);
                byte[] active = save(source);

                ArmorStand restored = load(server, active);
                check(state(restored).active() && restored.showArms(), "Active stand lost server arms on load");
                check(state(restored).originalArms() == originalArms, "Load recaptured compatibility arms as original");
                checkPose(source, restored);

                // Read the same active save under a disabled pack policy, as happens
                // when a chunk is first loaded after the resource selection changed.
                ArmorStandCompatibility.setEnabled(server, false);
                ArmorStand disabled = load(server, active);
                check(!state(disabled).active(), "Disabled policy retained a saved active flag");
                check(disabled.showArms() == originalArms, "Disabled load did not restore original arms");
                checkPose(source, disabled);
                byte[] inactive = save(disabled);
                ArmorStand disabledAgain = load(server, inactive);
                check(!state(disabledAgain).active() && disabledAgain.showArms() == originalArms,
                    "Inactive re-save changed vanilla arms");
                checkPose(source, disabledAgain);

                ArmorStand untouched = new ArmorStand(server.overworld(), 0, 99, 0);
                var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, server.registryAccess());
                untouched.saveWithoutId(output);
                var tag = output.buildResult();
                check(!tag.contains(BedrockArmorStandState.ACTIVE_KEY)
                    && !tag.contains(BedrockArmorStandState.POSE_KEY)
                    && !tag.contains(BedrockArmorStandState.ORIGINAL_ARMS_KEY),
                    "Untouched vanilla stand acquired compatibility NBT");

                ArmorStandCompatibility.setEnabled(server, true);
                ArmorStand reactivated = load(server, inactive);
                check(state(reactivated).active() && reactivated.showArms(), "Re-enabled stand did not activate");
                check(state(reactivated).originalArms() == originalArms, "Re-enable lost original arms");
                checkPose(source, reactivated);
            }
        } catch (IOException exception) {
            throw new AssertionError("Armor-stand compressed NBT round trip failed", exception);
        } finally {
            ArmorStandCompatibility.setEnabled(server, true);
        }
    }

    private static byte[] save(ArmorStand stand) throws IOException {
        var problems = new ProblemReporter.Collector();
        var output = TagValueOutput.createWithContext(problems, stand.registryAccess());
        stand.saveWithoutId(output);
        check(problems.isEmpty(), "Entity save codec errors: " + problems.getReport());
        var bytes = new ByteArrayOutputStream();
        NbtIo.writeCompressed(output.buildResult(), bytes);
        return bytes.toByteArray();
    }

    private static ArmorStand load(MinecraftServer server, byte[] bytes) throws IOException {
        var tag = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
        var problems = new ProblemReporter.Collector();
        ArmorStand stand = new ArmorStand(server.overworld(), 0, 99, 0);
        stand.load(TagValueInput.create(problems, server.registryAccess(), tag));
        check(problems.isEmpty(), "Entity load codec errors: " + problems.getReport());
        return stand;
    }

    private static BedrockArmorStandState state(ArmorStand stand) {
        return ((BedrockArmorStandAccess) stand).bedrockCompatState();
    }

    private static void checkPose(ArmorStand expected, ArmorStand actual) {
        check(state(actual).poseIndex() == 9, "Independent Bedrock pose did not survive save/load");
        check(expected.getHeadPose().equals(actual.getHeadPose())
            && expected.getBodyPose().equals(actual.getBodyPose())
            && expected.getLeftArmPose().equals(actual.getLeftArmPose())
            && expected.getRightArmPose().equals(actual.getRightArmPose())
            && expected.getLeftLegPose().equals(actual.getLeftLegPose())
            && expected.getRightLegPose().equals(actual.getRightLegPose()),
            "Compatibility activation or persistence replaced Java limb rotations");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
