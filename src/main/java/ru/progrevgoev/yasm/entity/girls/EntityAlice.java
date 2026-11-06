package ru.progrevgoev.yasm.entity.girls;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import ru.progrevgoev.yasm.Main;
import ru.progrevgoev.yasm.entity.EntityGirl;
import ru.progrevgoev.yasm.scene.common.StripScene;
import ru.progrevgoev.yasm.scene.girls.alice.AliceGreetingScene;

public class EntityAlice extends EntityGirl {
    public EntityAlice(EntityType<? extends EntityAlice> type, Level level) {
        super(type, level);
        getSceneManager().register(new StripScene());
        getSceneManager().register(new AliceGreetingScene());
    }

    @Override
    public ResourceLocation getModelId() {
        return ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "models/girls/alice/model.gltf");
    }
}
