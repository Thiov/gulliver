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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.RegisterEvent;

/** Forge 1.20.1 entrypoint (EventBus 6); also runs on NeoForge 1.20.1. */
@Mod(Gulliver.MOD_ID)
public final class GulliverForge {
    public GulliverForge() {
        Services.set(new ForgePlatform());
        Gulliver.init();
        GulliverGameRules.register();
        ForgeNetworking.init();

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(GulliverForge::onRegister);
        modBus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(GulliverPotions::registerLegacyMixes));
        if (FMLEnvironment.dist == Dist.CLIENT) {
            GulliverForgeClient.init(modBus);
        }

        IEventBus bus = MinecraftForge.EVENT_BUS;
        bus.addListener((PlayerInteractEvent.RightClickBlock e) ->
                cancel(e, CommonEvents.onUseBlock(e.getEntity(), e.getLevel(), e.getHand(), e.getHitVec())));
        bus.addListener((PlayerInteractEvent.EntityInteract e) ->
                cancel(e, CommonEvents.onUseEntity(e.getEntity(), e.getLevel(), e.getHand(), e.getTarget())));
        bus.addListener((PlayerInteractEvent.RightClickItem e) ->
                cancel(e, CommonEvents.onUseItem(e.getEntity(), e.getLevel(), e.getHand())));
        bus.addListener((AttackEntityEvent e) ->
                CommonEvents.onAttackEntity(e.getEntity(), e.getEntity().level(), e.getTarget()));
        bus.addListener((PlayerEvent.Clone e) -> {
            if (e.getOriginal() instanceof ServerPlayer oldP && e.getEntity() instanceof ServerPlayer newP) {
                CommonEvents.onPlayerClone(oldP, newP, !e.isWasDeath());
            }
        });
        bus.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) CommonEvents.onPlayerRespawn(p, e.isEndConquered());
        });
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) CommonEvents.onPlayerJoin(p);
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) CommonEvents.onPlayerLeave(p);
        });
        bus.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) CommonEvents.onPlayerChangedDimension(p);
        });
        bus.addListener((PlayerEvent.BreakSpeed e) ->
                e.setNewSpeed(CommonEvents.scaleBreakSpeed(e.getEntity(), e.getNewSpeed())));
        bus.addListener((RegisterCommandsEvent e) -> CommonEvents.registerCommands(e.getDispatcher()));
    }

    private static void onRegister(RegisterEvent event) {
        event.register(Registries.MOB_EFFECT, helper -> GulliverEffects.register(helper::register));
        event.register(Registries.POTION, helper -> GulliverPotions.register(helper::register));
    }

    private static void cancel(PlayerInteractEvent event, InteractionResult result) {
        if (result == InteractionResult.PASS || !event.isCancelable()) return;
        event.setCanceled(true);
        event.setCancellationResult(result);
    }
}
