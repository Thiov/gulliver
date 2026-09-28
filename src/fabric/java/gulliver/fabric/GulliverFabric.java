package gulliver.fabric;

import gulliver.Gulliver;
import gulliver.common.CommonEvents;
import gulliver.init.GulliverEffects;
import gulliver.init.GulliverGameRules;
import gulliver.init.GulliverPotions;
import gulliver.platform.Services;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
//#if MC >= 26.1
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
//#else
//$$ import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
//#endif

public final class GulliverFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Services.set(new FabricPlatform());
        Gulliver.init();

        GulliverEffects.register((id, effect) -> Registry.register(BuiltInRegistries.MOB_EFFECT, id, effect));
        GulliverPotions.register((id, potion) -> Registry.register(BuiltInRegistries.POTION, id, potion));
        //#if MC >= 1.21.11
        GulliverGameRules.register((id, rule) -> Registry.register(BuiltInRegistries.GAME_RULE, id, rule));
        //#else
        //$$ GulliverGameRules.register();
        //#endif

        FabricNetworking.registerCommon();

        UseBlockCallback.EVENT.register((player, level, hand, hit) ->
                CommonEvents.onUseBlock(player, level, hand, hit));
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
                CommonEvents.onUseEntity(player, level, hand, entity));
        UseItemCallback.EVENT.register((player, level, hand) -> {
            InteractionResult r = CommonEvents.onUseItem(player, level, hand);
            //#if MC >= 1.21.2
            return r;
            //#else
            //$$ return r == InteractionResult.PASS
            //$$         ? net.minecraft.world.InteractionResultHolder.pass(player.getItemInHand(hand))
            //$$         : net.minecraft.world.InteractionResultHolder.success(player.getItemInHand(hand));
            //#endif
        });
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            CommonEvents.onAttackEntity(player, level, entity);
            return InteractionResult.PASS;
        });
        ServerPlayerEvents.COPY_FROM.register(CommonEvents::onPlayerClone);
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                CommonEvents.onPlayerRespawn(newPlayer, alive));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                CommonEvents.onPlayerJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                CommonEvents.onPlayerLeave(handler.getPlayer()));
        //#if MC >= 26.1
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) ->
                CommonEvents.onPlayerChangedDimension(player));
        //#else
        //$$ ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
        //$$         CommonEvents.onPlayerChangedDimension(player));
        //#endif
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                CommonEvents.registerCommands(dispatcher));
    }
}
