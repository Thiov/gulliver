package gulliver.mixin.client;

import gulliver.client.VisualSize;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
//#if MC >= 1.21.2 && MC < 1.21.11
//$$ import org.spongepowered.asm.mixin.injection.Redirect;
//#endif
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The player model in the inventory (and the horse, llama and creative
 * screens) has to look the same at every size. Vanilla keeps it constant
 * by dividing the model's own scale back out, but only knows its own scale
 * attribute, never the mod's: a size-8 player filled the screen, a 1/8
 * player was a speck, and on 1.20.1, which has no such division at all,
 * the model covered the whole window.
 *
 * Every preview is therefore bracketed with VisualSize.beginPreview /
 * endPreview, which makes the renderers use the size the screen can
 * normalise (the hitbox's, not the animated one; on 1.20.1, 1.0). On
 * 1.21.10 the screen divides by LivingEntity.getScale() only, so that
 * call takes the mod's size too. Held items follow the body they are
 * held by (MixinItemInHandLayer).
 */
@Mixin(InventoryScreen.class)
public abstract class MixinInventoryScreenPreview {

    //#if MC >= 26.1
    @Inject(method = "extractEntityInInventoryFollowsMouse", at = @At("HEAD"), require = 0)
    private static void gulliver$previewBegin(net.minecraft.client.gui.GuiGraphicsExtractor graphics,
                                              int x0, int y0, int x1, int y1, int size, float offsetY,
                                              float mouseX, float mouseY, LivingEntity entity, CallbackInfo ci) {
        VisualSize.beginPreview();
    }

    @Inject(method = "extractEntityInInventoryFollowsMouse", at = @At("RETURN"), require = 0)
    private static void gulliver$previewEnd(net.minecraft.client.gui.GuiGraphicsExtractor graphics,
                                            int x0, int y0, int x1, int y1, int size, float offsetY,
                                            float mouseX, float mouseY, LivingEntity entity, CallbackInfo ci) {
        VisualSize.endPreview();
    }
    //#elif MC >= 1.21.10
    //$$ @Inject(method = "renderEntityInInventoryFollowsMouse", at = @At("HEAD"), require = 0)
    //$$ private static void gulliver$previewBegin(net.minecraft.client.gui.GuiGraphics graphics,
    //$$                                           int x0, int y0, int x1, int y1, int size, float offsetY,
    //$$                                           float mouseX, float mouseY, LivingEntity entity, CallbackInfo ci) {
    //$$     VisualSize.beginPreview();
    //$$ }
    //$$
    //$$ @Inject(method = "renderEntityInInventoryFollowsMouse", at = @At("RETURN"), require = 0)
    //$$ private static void gulliver$previewEnd(net.minecraft.client.gui.GuiGraphics graphics,
    //$$                                         int x0, int y0, int x1, int y1, int size, float offsetY,
    //$$                                         float mouseX, float mouseY, LivingEntity entity, CallbackInfo ci) {
    //$$     VisualSize.endPreview();
    //$$ }
    //#else
    //$$ @Inject(method = "renderEntityInInventory", at = @At("HEAD"), require = 0)
    //$$ private static void gulliver$previewBegin(net.minecraft.client.gui.GuiGraphics graphics, int x, int y, int size,
    //$$                                           org.joml.Quaternionf rotation, org.joml.Quaternionf cameraRotation,
    //$$                                           LivingEntity entity, CallbackInfo ci) {
    //$$     VisualSize.beginPreview();
    //$$ }
    //$$
    //$$ @Inject(method = "renderEntityInInventory", at = @At("RETURN"), require = 0)
    //$$ private static void gulliver$previewEnd(net.minecraft.client.gui.GuiGraphics graphics, int x, int y, int size,
    //$$                                         org.joml.Quaternionf rotation, org.joml.Quaternionf cameraRotation,
    //$$                                         LivingEntity entity, CallbackInfo ci) {
    //$$     VisualSize.endPreview();
    //$$ }
    //#endif

    //#if MC >= 1.21.2 && MC < 1.21.11
    //$$ @Redirect(method = "renderEntityInInventoryFollowsMouse",
    //$$           at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getScale()F"),
    //$$           require = 0)
    //$$ private static float gulliver$previewScale(LivingEntity entity) {
    //$$     return entity.getScale() * VisualSize.logical(entity);
    //$$ }
    //#endif
}
