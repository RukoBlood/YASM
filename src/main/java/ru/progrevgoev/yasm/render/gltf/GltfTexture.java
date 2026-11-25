package ru.progrevgoev.yasm.render.gltf;

import java.util.Objects;

/**
 * Immutable, renderer-independent texture data referenced by a glTF material.
 *
 * <p>Instances own encoded image bytes only. GPU resources are created and released by the
 * renderer, which can cache uploads by object identity so that materials sharing one texture
 * also share one GPU texture.</p>
 */
public final class GltfTexture {
    /** glTF magFilter values. A null value means the renderer chooses the default. */
    public enum MagFilter {
        NEAREST,
        LINEAR
    }

    /** glTF minFilter values. A null value means the renderer chooses the default. */
    public enum MinFilter {
        NEAREST,
        LINEAR,
        NEAREST_MIPMAP_NEAREST,
        LINEAR_MIPMAP_NEAREST,
        NEAREST_MIPMAP_LINEAR,
        LINEAR_MIPMAP_LINEAR
    }

    /** glTF wrap modes. REPEAT is the glTF default. */
    public enum WrapMode {
        REPEAT,
        CLAMP_TO_EDGE,
        MIRRORED_REPEAT
    }

    private final String name;
    private final String mimeType;
    private final byte[] data;
    private final int width;
    private final int height;
    private final MagFilter magFilter;
    private final MinFilter minFilter;
    private final WrapMode wrapS;
    private final WrapMode wrapT;

    public GltfTexture(String name, String mimeType, byte[] data, int width, int height,
                       MagFilter magFilter, MinFilter minFilter, WrapMode wrapS, WrapMode wrapT) {
        this.name = name == null || name.isBlank() ? "unnamed_texture" : name;
        this.mimeType = mimeType == null || mimeType.isBlank() ? "image/png" : mimeType;
        this.data = Objects.requireNonNull(data, "data").clone();
        if (this.data.length == 0) {
            throw new IllegalArgumentException("Texture data cannot be empty");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Texture dimensions must be positive");
        }
        this.width = width;
        this.height = height;
        this.magFilter = magFilter;
        this.minFilter = minFilter;
        this.wrapS = Objects.requireNonNull(wrapS, "wrapS");
        this.wrapT = Objects.requireNonNull(wrapT, "wrapT");
    }

    public String name() { return name; }
    public String mimeType() { return mimeType; }
    /** Returns a defensive copy of the encoded image bytes (PNG). */
    public byte[] data() { return data.clone(); }
    public int byteLength() { return data.length; }
    public int width() { return width; }
    public int height() { return height; }
    /** Returns null when the source glTF sampler does not specify a mag filter. */
    public MagFilter magFilter() { return magFilter; }
    /** Returns null when the source glTF sampler does not specify a min filter. */
    public MinFilter minFilter() { return minFilter; }
    public WrapMode wrapS() { return wrapS; }
    public WrapMode wrapT() { return wrapT; }
}
