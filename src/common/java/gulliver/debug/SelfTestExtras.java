package gulliver.debug;

import gulliver.access.IGulliverEntityInternal;
import gulliver.api.IResizeableEntity;
import gulliver.api.IResizeableLiving;
import gulliver.common.GulliverConfig;
import gulliver.common.GulliverEnvoy;
import gulliver.common.ShoulderHelper;
import gulliver.init.GulliverEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** Behaviour checks added with the 1.1 fixes; run by SelfTest. */
final class SelfTestExtras {
    private SelfTestExtras() {}

    private static void check(List<String> failures, boolean ok, String what) {
        if (!ok) failures.add(what);
        gulliver.Gulliver.LOGGER.info("selftest {} {}", ok ? "ok  " : "FAIL", what);
    }

    static void run(ServerLevel level, List<String> failures) {
        BlockPos at = new BlockPos(8, 0, 8);

        // Trampling by size difference (issue #1): a normal zombie stomps a quarter-size pig.
        Mob zombie = SelfTest.spawnMob(level, EntityType.ZOMBIE, at);
        Mob pig = SelfTest.spawnMob(level, EntityType.PIG, at);
        if (zombie != null && pig != null) {
            ((IResizeableLiving) pig).setBaseSize(0.25F);
            pig.setPos(zombie.getX(), zombie.getY(), zombie.getZ());
            float before = pig.getHealth();
            GulliverEnvoy.stepOnSmallerEntities(zombie, 0.1D);
            check(failures, pig.getHealth() < before, "size-1 zombie tramples a 0.25 pig");
            ((IResizeableLiving) pig).setBaseSize(0.5F);
            pig.invulnerableTime = 0;
            float before2 = pig.getHealth();
            GulliverEnvoy.stepOnSmallerEntities(zombie, 0.1D);
            check(failures, pig.getHealth() == before2, "size-1 zombie leaves a 0.5 pig alone");

            // Damage is scaled once (no re-dispatch): a 0.25 target takes 2x from a sword.
            zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            ((IResizeableLiving) pig).setBaseSize(0.25F);
            pig.setHealth(pig.getMaxHealth());
            pig.invulnerableTime = 0;
            float hp = pig.getHealth();
            pig.hurtServer(level, level.damageSources().mobAttack(zombie), 2.0F);
            float lost = hp - pig.getHealth();
            check(failures, Math.abs(lost - 4.0F) < 0.01F, "0.25 target takes 2x melee damage once (lost " + lost + ")");

            // Carry rules: quite smaller and narrow enough.
            check(failures, ShoulderHelper.canCarry(zombie, pig), "zombie may carry a 0.25 pig");
            ((IResizeableLiving) pig).setBaseSize(0.5F);
            check(failures, !ShoulderHelper.canCarry(zombie, pig), "zombie may not carry a 0.5 pig");
            zombie.discard();
            pig.discard();
        }

        // Tiny + Huge at once cancel out; removing one leaves the other.
        Mob cow = SelfTest.spawnMob(level, EntityType.COW, at);
        if (cow != null) {
            cow.addEffect(new MobEffectInstance(GulliverEffects.tiny(), 200, 0));
            cow.addEffect(new MobEffectInstance(GulliverEffects.huge(), 200, 0));
            for (int i = 0; i < 3; i++) cow.tick();
            float both = ((IGulliverEntityInternal) cow).gulliver$getSizePotionMultiplier();
            check(failures, Math.abs(both - 1.0F) < 1.0E-3F, "tiny+huge cancel out (got " + both + ")");
            cow.removeEffect(GulliverEffects.tiny());
            float huge = ((IGulliverEntityInternal) cow).gulliver$getSizePotionMultiplier();
            check(failures, Math.abs(huge - 4.0F) < 1.0E-3F, "removing tiny leaves huge (got " + huge + ")");
            cow.discard();
        }

        // Spawn sizes from the config now apply to new entities.
        GulliverConfig.SpawnSize ss = GulliverConfig.INSTANCE.spawnSize;
        String old = ss.baseAnimalSize;
        ss.baseAnimalSize = "0.5";
        try {
            Mob sheep = SelfTest.spawnMob(level, EntityType.SHEEP, at);
            if (sheep != null) {
                sheep.tick();
                check(failures, Math.abs(((IResizeableEntity) sheep).getSizeMultiplier() - 0.5F) < 1.0E-3F,
                        "configured animal spawn size applied");
                sheep.discard();
            }
        } finally {
            ss.baseAnimalSize = old;
        }
    }
}
