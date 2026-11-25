package ru.progrevgoev.yasm.interfaces;

import net.minecraft.resources.ResourceLocation;
import ru.progrevgoev.yasm.scene.SceneContext;

/** A server-authoritative, tick-driven scene. Implementations should be deterministic on the server. */
public interface IScene {
    ResourceLocation id();

    default boolean canStart(SceneContext context) { return true; }
    default void onStart(SceneContext context) {}
    default void tick(SceneContext context) {}
    default boolean isFinished(SceneContext context) { return false; }
    default void onStop(SceneContext context) {}
}
