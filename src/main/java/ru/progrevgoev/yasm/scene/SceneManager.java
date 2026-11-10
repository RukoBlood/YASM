package ru.progrevgoev.yasm.scene;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import ru.progrevgoev.yasm.entity.EntityGirl;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Owns one active scene per character. Scene execution is server-side only. */
public final class SceneManager {
    private final EntityGirl girl;
    private final Map<ResourceLocation, IScene> scenes = new LinkedHashMap<>();
    private IScene activeScene;
    private Player initiator;

    public SceneManager(EntityGirl girl) {
        this.girl = girl;
    }

    public void register(IScene scene) {
        scenes.put(scene.id(), scene);
    }

    public boolean start(ResourceLocation id, Player player) {
        if (girl.level().isClientSide || activeScene != null) return false;
        IScene scene = scenes.get(id);
        if (scene == null) return false;
        SceneContext context = context();
        if (!scene.canStart(context)) return false;
        initiator = player;
        activeScene = scene;
        activeScene.onStart(context());
        return true;
    }

    public void tick() {
        if (activeScene == null || girl.level().isClientSide) return;
        SceneContext context = context();
        activeScene.tick(context);
        if (activeScene.isFinished(context)) stop();
    }

    public void stop() {
        if (activeScene != null) activeScene.onStop(context());
        activeScene = null;
        initiator = null;
    }

    public Optional<ResourceLocation> activeSceneId() {
        return Optional.ofNullable(activeScene).map(IScene::id);
    }

    private SceneContext context() {
        return new SceneContext(girl, Optional.ofNullable(initiator));
    }
}
