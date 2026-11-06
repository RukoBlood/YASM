package ru.progrevgoev.yasm.render.gltf;

import net.minecraft.resources.ResourceLocation;

/** Adapter boundary around jGLTF. Keep jGLTF-specific types out of entity and scene code. */
public final class GltfModelLoader {
    public GltfModel load(ResourceLocation modelLocation) {
        // TODO: resolve model.gltf and relative external URIs through Minecraft ResourceManager.
        // TODO: use jGLTF to parse glTF 2.0, then convert to immutable YASM runtime data.
        // TODO: validate supported primitives, attributes, skins, animations and material profile.
        throw new UnsupportedOperationException("glTF loading is not implemented yet: " + modelLocation);
    }
}
