package ru.progrevgoev.yasm.util;

import net.minecraft.resources.ResourceLocation;
import ru.progrevgoev.yasm.Main;

public final class ResourceUtils {
    private ResourceUtils() {}

    public static ResourceLocation yasm(String path) {
        return ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, path);
    }
}
