package ru.progrevgoev.yasm.scene.common;

import net.minecraft.resources.ResourceLocation;
import ru.progrevgoev.yasm.Main;
import ru.progrevgoev.yasm.scene.IScene;
import ru.progrevgoev.yasm.scene.SceneContext;

/** Shared scene implementation placeholder. Character-specific animation/material IDs belong in model config. */
public final class StripScene implements IScene {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "common/strip");

    @Override
    public ResourceLocation id() { return ID; }

    @Override
    public boolean canStart(SceneContext context) {
        // TODO: require explicit player initiation and validate scene eligibility on the server.
        return false;
    }

    @Override
    public void onStart(SceneContext context) {
        // TODO: trigger character's configured strip animation and material visibility overrides.
    }
}
