package ru.progrevgoev.yasm.scene.actions;

import ru.progrevgoev.yasm.scene.SceneContext;

public final class PlayAnimationAction implements SceneAction {
    private final String animationId;
    public PlayAnimationAction(String animationId) { this.animationId = animationId; }
    @Override public void tick(SceneContext context) {
        // TODO: send animation state to clients and play it through the entity's controller.
    }
    @Override public boolean isComplete(SceneContext context) { return false; }
    public String animationId() { return animationId; }
}
