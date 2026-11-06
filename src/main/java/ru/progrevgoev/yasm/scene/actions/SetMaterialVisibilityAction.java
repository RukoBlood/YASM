package ru.progrevgoev.yasm.scene.actions;

import ru.progrevgoev.yasm.scene.SceneContext;

public final class SetMaterialVisibilityAction implements SceneAction {
    private final String materialOrGroup;
    private final boolean visible;
    public SetMaterialVisibilityAction(String materialOrGroup, boolean visible) {
        this.materialOrGroup = materialOrGroup;
        this.visible = visible;
    }
    @Override public void tick(SceneContext context) {
        // TODO: update authoritative scene state and synchronize a visual override to clients.
    }
    @Override public boolean isComplete(SceneContext context) { return true; }
    public String materialOrGroup() { return materialOrGroup; }
    public boolean visible() { return visible; }
}
