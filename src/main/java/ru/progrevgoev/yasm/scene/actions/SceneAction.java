package ru.progrevgoev.yasm.scene.actions;

import ru.progrevgoev.yasm.scene.SceneContext;

/** A small unit of work used by scripted scenes. */
public interface SceneAction {
    default void start(SceneContext context) {}
    void tick(SceneContext context);
    boolean isComplete(SceneContext context);
    default void stop(SceneContext context) {}
}
