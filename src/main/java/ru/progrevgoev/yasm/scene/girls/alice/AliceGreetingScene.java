package ru.progrevgoev.yasm.scene.girls.alice;

import net.minecraft.resources.ResourceLocation;
import ru.progrevgoev.yasm.Main;
import ru.progrevgoev.yasm.scene.Scene;

public final class AliceGreetingScene implements Scene {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "alice/greeting");
    @Override public ResourceLocation id() { return ID; }
    // TODO: implement Alice's unique greeting sequence.
}
