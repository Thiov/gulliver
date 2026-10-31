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
 *
 * A monster that snatched its prey uses both hands (applyMob): they swing
 * down to where the prey stood, carry it up to the face as it is lifted,
 * and push it into the mouth with every bite. That pose goes on after the
 * model's own setupAnim, since zombies, skeletons and the like set their
 * arms after HumanoidModel's.
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

    /** Arm pitch reaching down towards prey on the ground in front. */
    private static final float GRAB_X_ROT = -0.45F;
    /** Each arm's yaw towards the middle while grabbing, and at the mouth. */
    private static final float GRAB_Y_ROT = 0.3F;
    private static final float MOB_MOUTH_Y_ROT = 0.55F;
    /** Ticks the arms take to swing down to the prey. */
    private static final float GRIP_TICKS = 4.0F;

    /**
     * How far a monster's hands have closed on its prey (0..1), or 0 when
     * it isn't eating: they swing down over the first few ticks of the meal.
     */
    public static float mobGrip(Entity e, float partialTick) {
        if (e instanceof net.minecraft.world.entity.player.Player) return 0.0F;
        if (((gulliver.access.IGulliverShoulderInternal) e).gulliver$getHandEntity() == null) return 0.0F;
        int ticks = ((gulliver.access.IGulliverShoulderInternal) e).gulliver$getChewTicks();
        if (ticks < 0) return 0.0F;
        return Mth.clamp((ticks + partialTick + 1.0F) / GRIP_TICKS, 0.05F, 1.0F);
    }

    /** Both of a monster's arms: down to the prey, up to the mouth with it, a push with every bite. */
    public static void applyMob(ModelPart rightArm, ModelPart leftArm, float grip, float lift, float bite) {
        if (grip <= 0.0F) return;
        float x = Mth.lerp(lift, GRAB_X_ROT, MOUTH_X_ROT) + bite * 0.14F;
        float y = Mth.lerp(lift, GRAB_Y_ROT, MOB_MOUTH_Y_ROT);
        rightArm.xRot = Mth.lerp(grip, rightArm.xRot, x);
        rightArm.yRot = Mth.lerp(grip, rightArm.yRot, -y);
        rightArm.zRot = Mth.lerp(grip, rightArm.zRot, 0.0F);
        leftArm.xRot = Mth.lerp(grip, leftArm.xRot, x);
        leftArm.yRot = Mth.lerp(grip, leftArm.yRot, y);
        leftArm.zRot = Mth.lerp(grip, leftArm.zRot, 0.0F);
    }

    //#if MC >= 1.21.2
    /** After any model's setupAnim: a monster's eating pose on a humanoid model. */
    public static void afterSetupAnim(Object model, Object state) {
        if (model instanceof net.minecraft.client.model.HumanoidModel<?> humanoid
                && state instanceof gulliver.access.IGlideRenderState g && g.gulliver$getMobGrip() > 0.0F) {
            applyMob(humanoid.rightArm, humanoid.leftArm, g.gulliver$getMobGrip(), g.gulliver$getEatLift(), g.gulliver$getEatBite());
        }
    }
    //#endif

    public static void apply(ModelPart rightArm, float lift, float bite) {
        if (lift <= 0.0F) return;
        rightArm.xRot = Mth.lerp(lift, rightArm.xRot, MOUTH_X_ROT) + bite * 0.14F;
        rightArm.yRot = Mth.lerp(lift, rightArm.yRot, MOUTH_Y_ROT);
        rightArm.zRot = Mth.lerp(lift, rightArm.zRot, 0.0F);
    }
}
