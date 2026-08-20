package com.andrewbristowx.emimobcontrol.mixin;

import com.andrewbristowx.emimobcontrol.system.PassiveMobControlService;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MobFinalizeSpawnMixin {
    @Inject(method = "finalizeSpawn", at = @At("HEAD"), cancellable = true)
    private void chaina$blockVanillaPassiveNaturalSpawn(
            ServerLevelAccessor level,
            DifficultyInstance difficulty,
            MobSpawnType spawnType,
            SpawnGroupData spawnData,
            CallbackInfoReturnable<SpawnGroupData> cir) {
        Mob self = (Mob) (Object) this;
        if (PassiveMobControlService.shouldBlockNaturalSpawn(self, spawnType)) {
            self.discard();
            cir.setReturnValue(spawnData);
        }
    }
}
