package ru.progrevgoev.yasm.entity.girls;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import ru.progrevgoev.yasm.Main;
import ru.progrevgoev.yasm.entity.EntityGirl;
import ru.progrevgoev.yasm.scene.common.StripScene;
import ru.progrevgoev.yasm.scene.girls.example.ExampleGreetingScene;

public class EntityExampleGirl extends EntityGirl {
    public EntityExampleGirl(EntityType<? extends EntityExampleGirl> type, Level level) {
        super(type, level);
        getSceneManager().register(new StripScene());
        getSceneManager().register(new ExampleGreetingScene());
    }

    @Override
    public ResourceLocation getModelId() {
        return ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "models/girls/example_girl/model.gltf");
    }
}
