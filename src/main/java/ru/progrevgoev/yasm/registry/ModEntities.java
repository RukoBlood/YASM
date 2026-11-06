package ru.progrevgoev.yasm.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import ru.progrevgoev.yasm.Main;
import ru.progrevgoev.yasm.entity.girls.EntityAlice;
import ru.progrevgoev.yasm.entity.girls.EntityExampleGirl;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Main.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<EntityExampleGirl>> EXAMPLE_GIRL =
            ENTITY_TYPES.register("example_girl", () -> EntityType.Builder
                    .of(EntityExampleGirl::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(8)
                    .build("example_girl"));

    public static final DeferredHolder<EntityType<?>, EntityType<EntityAlice>> ALICE =
            ENTITY_TYPES.register("alice", () -> EntityType.Builder
                    .of(EntityAlice::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(8)
                    .build("alice"));

    private ModEntities() {}
}
