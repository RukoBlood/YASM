package ru.progrevgoev.yasm.scene.girls.example;

import net.minecraft.resources.ResourceLocation;
import ru.progrevgoev.yasm.Main;
import ru.progrevgoev.yasm.scene.IScene;

public final class ExampleGreetingScene implements IScene {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "example_girl/greeting");
    @Override public ResourceLocation id() { return ID; }
    // TODO: implement this character's unique greeting sequence.
}
