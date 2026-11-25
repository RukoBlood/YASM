package ru.progrevgoev.yasm.render.gltf;

import java.util.Objects;

/**
 * Immutable renderer-independent representation of the supported glTF material properties.
 * Texture indices refer to the source glTF texture array and are resolved by the texture-loading
 * stage. A value of -1 means that no base color texture is assigned.
 */
public final class GltfMaterial {
    public enum AlphaMode {
        OPAQUE,
        MASK,
        BLEND
    }

    private final String name;
    private final float[] baseColorFactor;
    private final int baseColorTextureIndex;
    private final int baseColorTexCoord;
    private final AlphaMode alphaMode;
    private final float alphaCutoff;
    private final boolean doubleSided;

    public GltfMaterial(
            String name,
            float[] baseColorFactor,
            int baseColorTextureIndex,
            int baseColorTexCoord,
            AlphaMode alphaMode,
            float alphaCutoff,
            boolean doubleSided
    ) {
        this.name = name == null || name.isBlank() ? "unnamed_material" : name;
        Objects.requireNonNull(baseColorFactor, "baseColorFactor");
        if (baseColorFactor.length != 4) {
            throw new IllegalArgumentException("Base color factor must contain four components");
        }
        this.baseColorFactor = baseColorFactor.clone();
        for (float component : this.baseColorFactor) {
            if (!Float.isFinite(component) || component < 0.0f || component > 1.0f) {
                throw new IllegalArgumentException("Base color components must be finite and in [0, 1]");
            }
        }

        if (baseColorTextureIndex < -1) {
            throw new IllegalArgumentException("Base color texture index cannot be less than -1");
        }
        if (baseColorTexCoord < 0) {
            throw new IllegalArgumentException("Base color texture coordinate set cannot be negative");
        }
        if (!Float.isFinite(alphaCutoff) || alphaCutoff < 0.0f) {
            throw new IllegalArgumentException("Alpha cutoff must be finite and non-negative");
        }

        this.baseColorTextureIndex = baseColorTextureIndex;
        this.baseColorTexCoord = baseColorTexCoord;
        this.alphaMode = Objects.requireNonNull(alphaMode, "alphaMode");
        this.alphaCutoff = alphaCutoff;
        this.doubleSided = doubleSided;
    }

    public String name() { return name; }
    public float[] baseColorFactor() { return baseColorFactor.clone(); }
    /** Returns -1 when the material has no base color texture. */
    public int baseColorTextureIndex() { return baseColorTextureIndex; }
    public int baseColorTexCoord() { return baseColorTexCoord; }
    public AlphaMode alphaMode() { return alphaMode; }
    public float alphaCutoff() { return alphaCutoff; }
    public boolean doubleSided() { return doubleSided; }
}
