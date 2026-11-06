package ru.progrevgoev.yasm.scene.actions;

import ru.progrevgoev.yasm.scene.SceneContext;

/** Scene-owned navigation step; this is not a standalone companion command/AI mode. */
public final class NavigateToBedAction implements SceneAction {
    @Override public void start(SceneContext context) {
        // TODO: search valid bed positions server-side and start entity navigation.
    }
    @Override public void tick(SceneContext context) {
        // TODO: track navigation success, timeout and path failure.
    }
    @Override public boolean isComplete(SceneContext context) { return false; }
}
