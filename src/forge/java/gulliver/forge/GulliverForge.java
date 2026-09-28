package gulliver.forge;

import gulliver.Gulliver;
import gulliver.common.CommonEvents;
import gulliver.init.GulliverEffects;
import gulliver.init.GulliverGameRules;
import gulliver.init.GulliverPotions;
import gulliver.platform.Services;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.RegisterEvent;

@Mod(Gulliver.MOD_ID)
public final class GulliverForge {
    public GulliverForge(FMLJavaModLoadingContext context) {
        Services.set(new ForgePlatform());
        Gulliver.init();
        ForgeNetworking.init();

        BusGroup modBus = context.getModBusGroup();
        RegisterEvent.getBus(modBus).addListener(GulliverForge::onRegister);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            GulliverForgeClient.init(modBus);
        }

        PlayerInteractEvent.RightClickBlock.BUS.addListener(e -> {
            return cancel(e, CommonEvents.onUseBlock(e.getEntity(), e.getLevel(), e.getHand(), e.getHitVec()));
        });
        PlayerInteractEvent.EntityInteractSpecific.BUS.addListener(e -> {
            return cancel(e, CommonEvents.onUseEntity(e.getEntity(), e.getLevel(), e.getHand(), e.getTarget()));
        });
        PlayerInteractEvent.RightClickItem.BUS.addListener(e -> {
            return cancel(e, CommonEvents.onUseItem(e.getEntity(), e.getLevel(), e.getHand()));
        });
        AttackEntityEvent.BUS.addListener(e -> {
            CommonEvents.onAttackEntity(e.getEntity(), e.getEntity().level(), e.getTarget());
            return false;
        });
        PlayerEvent.Clone.BUS.addListener(e -> {
            if (e.getOriginal() instanceof ServerPlayer oldP && e.getEntity() instanceof ServerPlayer newP) {
                CommonEvents.onPlayerClone(oldP, newP, !e.isWasDeath());
            }
        });
        PlayerEvent.PlayerRespawnEvent.BUS.addListener(e -> {
            if (e.getEntity() instanceof ServerPlayer p) CommonEvents.onPlayerRespawn(p, e.isEndConquered());
        });
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(e -> {
            if (e.getEntity() instanceof ServerPlayer p) CommonEvents.onPlayerJoin(p);
        });
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(e -> {
            if (e.getEntity() instanceof ServerPlayer p) CommonEvents.onPlayerLeave(p);
        });
        PlayerEvent.PlayerChangedDimensionEvent.BUS.addListener(e -> {
            if (e.getEntity() instanceof ServerPlayer p) CommonEvents.onPlayerChangedDimension(p);
        });
        PlayerEvent.BreakSpeed.BUS.addListener(e -> {
            e.setNewSpeed(CommonEvents.scaleBreakSpeed(e.getEntity(), e.getNewSpeed()));
            return false;
        });
        RegisterCommandsEvent.BUS.addListener(e -> CommonEvents.registerCommands(e.getDispatcher()));
    }

    private static void onRegister(RegisterEvent event) {
        event.register(Registries.MOB_EFFECT, helper -> GulliverEffects.register(helper::register));
        event.register(Registries.POTION, helper -> GulliverPotions.register(helper::register));
        //#if MC >= 1.21.11
        event.register(Registries.GAME_RULE, helper -> GulliverGameRules.register(helper::register));
        //#endif
    }

    /** Cancels the interaction (returning the given result) unless it is PASS. */
    private static boolean cancel(PlayerInteractEvent event, InteractionResult result) {
        if (result == InteractionResult.PASS) return false;
        event.setCancellationResult(result);
        return true;
    }
}
