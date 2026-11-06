package ru.progrevgoev.yasm;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import ru.progrevgoev.yasm.registry.ModEntities;
import ru.progrevgoev.yasm.network.NetworkBootstrap;

@Mod(Main.MOD_ID)
public final class Main {
    public static final String MOD_ID = "yasm";

    public Main(IEventBus modEventBus) {
        ModEntities.ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(this::onEntityAttributeCreation);
        NetworkBootstrap.register(modEventBus);
    }

    private void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntities.EXAMPLE_GIRL.get(), ru.progrevgoev.yasm.entity.EntityGirl.createAttributes().build());
        event.put(ModEntities.ALICE.get(), ru.progrevgoev.yasm.entity.EntityGirl.createAttributes().build());
    }
}
