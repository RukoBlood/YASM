package ru.progrevgoev.yasm.scene.actions;

import ru.progrevgoev.yasm.scene.SceneContext;

public final class WaitForPlayerAction implements SceneAction {
    private final double distance;
    public WaitForPlayerAction(double distance) { this.distance = distance; }
    @Override public void tick(SceneContext context) {
        // TODO: wait for the initiating player to approach; validate on the server.
    }
    @Override public boolean isComplete(SceneContext context) { return false; }
    public double distance() { return distance; }
}
