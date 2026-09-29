package gulliver.neoforge;

import gulliver.Gulliver;
import gulliver.common.CommonEvents;
import gulliver.init.GulliverEffects;
import gulliver.init.GulliverGameRules;
import gulliver.init.GulliverPotions;
import gulliver.platform.Services;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

@Mod(Gulliver.MOD_ID)
public final class GulliverNeoForge {
    public GulliverNeoForge(IEventBus modBus, ModContainer container) {
        Services.set(new NeoForgePlatform());
        Gulliver.init();
        //#if MC < 1.21.11
        //$$ GulliverGameRules.register();
        //#endif

        modBus.addListener(GulliverNeoForge::onRegister);
        modBus.addListener(NeoForgeNetworking::register);
        if (FMLEnvironment.getDist().isClient()) {
            GulliverNeoForgeClient.init(modBus, container);
        }

        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener((PlayerInteractEvent.RightClickBlock e) ->
                apply(e, CommonEvents.onUseBlock(e.getEntity(), e.getLevel(), e.getHand(), e.getHitVec())));
        //#if MC >= 26.2
        bus.addListener((PlayerInteractEvent.EntityInteract e) ->
                apply(e, CommonEvents.onUseEntity(e.getEntity(), e.getLevel(), e.getHand(), e.getTarget())));
        //#else
        //$$ // The "specific" (interactAt) stage comes first: armor stands and
        //$$ // the like consume the click there before EntityInteract would fire.
        //$$ bus.addListener((PlayerInteractEvent.EntityInteractSpecific e) ->
        //$$         apply(e, CommonEvents.onUseEntity(e.getEntity(), e.getLevel(), e.getHand(), e.getTarget())));
        //#endif
        bus.addListener((PlayerInteractEvent.RightClickItem e) ->
                apply(e, CommonEvents.onUseItem(e.getEntity(), e.getLevel(), e.getHand())));
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
        //#if MC >= 1.21.11
        event.register(Registries.GAME_RULE, helper -> GulliverGameRules.register(helper::register));
        //#endif
    }

    private static void apply(PlayerInteractEvent event, InteractionResult result) {
        if (result == InteractionResult.PASS) return;
        if (event instanceof PlayerInteractEvent.RightClickBlock e) {
            e.setCanceled(true);
            e.setCancellationResult(result);
        //#if MC >= 26.2
        } else if (event instanceof PlayerInteractEvent.EntityInteract e) {
        //#else
        //$$ } else if (event instanceof PlayerInteractEvent.EntityInteractSpecific e) {
        //#endif
            e.setCanceled(true);
            e.setCancellationResult(result);
        } else if (event instanceof PlayerInteractEvent.RightClickItem e) {
            e.setCanceled(true);
            e.setCancellationResult(result);
        }
    }
}
