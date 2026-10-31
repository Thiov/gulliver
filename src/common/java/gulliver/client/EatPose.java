package gulliver.client;

import gulliver.common.Appetite;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Third-person pose of a body bringing its hand-held creature to its mouth
 * (Appetite): the right arm swings up and across to the face as the meal
 * rises, then dips with every bite. Shared by the player and humanoid
 * model mixins of every version.
 */
public final class EatPose {
    private EatPose() {}

    /** Arm pitch with the fist under the meal at the mouth (straight forward is -PI/2). */
    private static final float MOUTH_X_ROT = -2.0F;
    /** Arm yaw across the chest towards the face. */
    private static final float MOUTH_Y_ROT = -0.5F;

    /** How far up the hand is (0..1) and how deep the current bite is (0..1), for this frame. */
    public static float lift(Entity e, float partialTick) {
        return Appetite.lift(e, partialTick);
    }

    public static float bite(Entity e, float partialTick) {
        double chomp = Appetite.chomp(e, partialTick);
        return e.getBbHeight() <= 0.0F ? 0.0F : (float) (-chomp / (e.getBbHeight() * 0.02D));
    }

    public static void apply(ModelPart rightArm, float lift, float bite) {
        if (lift <= 0.0F) return;
        rightArm.xRot = Mth.lerp(lift, rightArm.xRot, MOUTH_X_ROT) + bite * 0.14F;
        rightArm.yRot = Mth.lerp(lift, rightArm.yRot, MOUTH_Y_ROT);
        rightArm.zRot = Mth.lerp(lift, rightArm.zRot, 0.0F);
    }
}
