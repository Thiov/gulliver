//#if MC < 26.1
//$$ package gulliver.mixin.client;
//$$
//$$ import gulliver.api.IResizeableEntity;
//$$ import net.minecraft.client.Minecraft;
//$$ import net.minecraft.client.renderer.GameRenderer;
//$$ import net.minecraft.world.entity.Entity;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.Constant;
//$$ import org.spongepowered.asm.mixin.injection.ModifyConstant;
//$$
//$$ /**
//$$  * Near clip plane scaled with the viewer (before 26.1 it lives in the
//$$  * projection matrix): a tiny's camera would otherwise clip into walls
//$$  * it is standing right against.
//$$  */
//$$ @Mixin(GameRenderer.class)
//$$ public abstract class MixinGameRendererNearClip {
//$$
//$$     @ModifyConstant(method = "getProjectionMatrix", constant = @Constant(floatValue = 0.05F))
//$$     private float gulliver$scaleNearPlane(float near) {
//$$         Entity cam = Minecraft.getInstance().getCameraEntity();
//$$         if (cam == null) return near;
//$$         float m = ((IResizeableEntity) cam).getSizeMultiplier();
//$$         return m == 1.0F ? near : near * m;
//$$     }
//$$ }
//#endif
