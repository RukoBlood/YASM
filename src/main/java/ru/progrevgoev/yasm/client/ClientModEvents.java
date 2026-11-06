package ru.progrevgoev.yasm.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import ru.progrevgoev.yasm.Main;
import ru.progrevgoev.yasm.entity.girls.EntityAlice;
import ru.progrevgoev.yasm.entity.girls.EntityExampleGirl;
import ru.progrevgoev.yasm.registry.ModEntities;

@EventBusSubscriber(modid = Main.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.EXAMPLE_GIRL.get(), ExampleGirlRenderer::new);
        event.registerEntityRenderer(ModEntities.ALICE.get(), AliceRenderer::new);
    }

    private static final class ExampleGirlRenderer extends EntityRenderer<EntityExampleGirl> {
        private ExampleGirlRenderer(EntityRendererProvider.Context context) { super(context); }
        @Override public ResourceLocation getTextureLocation(EntityExampleGirl entity) {
            return ResourceLocation.withDefaultNamespace("textures/misc/unknown_pack.png");
        }
    }

    private static final class AliceRenderer extends EntityRenderer<EntityAlice> {
        private AliceRenderer(EntityRendererProvider.Context context) { super(context); }
        @Override public ResourceLocation getTextureLocation(EntityAlice entity) {
            return ResourceLocation.withDefaultNamespace("textures/misc/unknown_pack.png");
        }
    }
}
