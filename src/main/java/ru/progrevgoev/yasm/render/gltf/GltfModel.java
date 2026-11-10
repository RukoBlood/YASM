package ru.progrevgoev.yasm.render.gltf;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/** Immutable renderer-independent representation of the supported glTF data. */
public final class GltfModel {
    private final ResourceLocation source;
    private final List<Mesh> meshes;
    private final List<String> materialSlots;
    private final int nodeCount;
    private final int skinCount;
    private final int animationCount;

    public GltfModel(ResourceLocation source, List<Mesh> meshes, List<String> materialSlots, int nodeCount, int skinCount, int animationCount) {
        this.source = Objects.requireNonNull(source, "source");
        this.meshes = List.copyOf(meshes);
        this.materialSlots = List.copyOf(materialSlots);
        if (nodeCount < 0 || skinCount < 0 || animationCount < 0) {
            throw new IllegalArgumentException("Model counts cannot be negative");
        }
        this.nodeCount = nodeCount;
        this.skinCount = skinCount;
        this.animationCount = animationCount;
    }

    public ResourceLocation source() { return source; }
    public List<Mesh> meshes() { return meshes; }
    public List<String> materialSlots() { return materialSlots; }
    public int nodeCount() { return nodeCount; }
    public int skinCount() { return skinCount; }
    public int animationCount() { return animationCount; }
    public int primitiveCount() { return meshes.stream().mapToInt(m -> m.primitives().size()).sum(); }

    public record Mesh(String name, List<Primitive> primitives) {
        public Mesh {
            name = name == null || name.isBlank() ? "unnamed_mesh" : name;
            primitives = List.copyOf(primitives);
        }
    }

    /** Arrays are flattened: POSITION/NORMAL xyz triples and TEXCOORD_0 uv pairs. */
    public static final class Primitive {
        private final int mode;
        private final String materialSlot;
        private final float[] positions;
        private final float[] normals;
        private final float[] texCoords0;
        private final int[] indices;

        public Primitive(int mode, String materialSlot, float[] positions,
                         float[] normals, float[] texCoords0, int[] indices) {
            if (mode < 0 || mode > 6) throw new IllegalArgumentException("Unsupported primitive mode: " + mode);
            this.mode = mode;
            this.materialSlot = materialSlot == null || materialSlot.isBlank() ? "default" : materialSlot;
            this.positions = Objects.requireNonNull(positions, "positions").clone();
            this.normals = normals == null ? new float[0] : normals.clone();
            this.texCoords0 = texCoords0 == null ? new float[0] : texCoords0.clone();
            this.indices = indices == null ? new int[0] : indices.clone();

            if (this.positions.length == 0 || this.positions.length % 3 != 0) {
                throw new IllegalArgumentException("POSITION must contain xyz triples");
            }
            int vertices = this.positions.length / 3;
            if (this.normals.length != 0 && this.normals.length != this.positions.length) {
                throw new IllegalArgumentException("NORMAL count differs from POSITION count");
            }
            if (this.texCoords0.length != 0 && this.texCoords0.length != vertices * 2) {
                throw new IllegalArgumentException("TEXCOORD_0 count differs from POSITION count");
            }
            for (int index : this.indices) {
                if (index < 0 || index >= vertices) {
                    throw new IllegalArgumentException("Index " + index + " is outside vertex range 0.." + (vertices - 1));
                }
            }
        }

        public int mode() { return mode; }
        public String materialSlot() { return materialSlot; }
        public float[] positions() { return positions.clone(); }
        public float[] normals() { return normals.clone(); }
        public float[] texCoords0() { return texCoords0.clone(); }
        public int[] indices() { return indices.clone(); }
        public int vertexCount() { return positions.length / 3; }
    }
}
