package ru.progrevgoev.yasm.render.gltf;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/** Immutable renderer-independent representation of the supported glTF data. */
public final class GltfModel {
    private final ResourceLocation source;
    private final List<Mesh> meshes;
    private final List<String> materialSlots;
    private final List<Node> nodes;
    private final List<Scene> scenes;
    private final int defaultSceneIndex;
    private final int skinCount;
    private final int animationCount;

    public GltfModel(ResourceLocation source, List<Mesh> meshes, List<String> materialSlots,
                     List<Node> nodes, List<Scene> scenes, int defaultSceneIndex,
                     int skinCount, int animationCount) {
        this.source = Objects.requireNonNull(source, "source");
        this.meshes = List.copyOf(meshes);
        this.materialSlots = List.copyOf(materialSlots);
        this.nodes = List.copyOf(nodes);
        this.scenes = List.copyOf(scenes);
        if (defaultSceneIndex < -1 || defaultSceneIndex >= this.scenes.size()) {
            throw new IllegalArgumentException("Default scene index is out of range: " + defaultSceneIndex);
        }
        if (skinCount < 0 || animationCount < 0) {
            throw new IllegalArgumentException("Model counts cannot be negative");
        }
        this.defaultSceneIndex = defaultSceneIndex;
        this.skinCount = skinCount;
        this.animationCount = animationCount;
    }

    public ResourceLocation source() { return source; }
    public List<Mesh> meshes() { return meshes; }
    public List<String> materialSlots() { return materialSlots; }
    public List<Node> nodes() { return nodes; }
    public List<Scene> scenes() { return scenes; }
    /** Returns -1 when the glTF asset does not declare a default scene. */
    public int defaultSceneIndex() { return defaultSceneIndex; }
    public int nodeCount() { return nodes.size(); }
    public int skinCount() { return skinCount; }
    public int animationCount() { return animationCount; }
    public int primitiveCount() { return meshes.stream().mapToInt(m -> m.primitives().size()).sum(); }

    public record Mesh(String name, List<Primitive> primitives) {
        public Mesh {
            name = name == null || name.isBlank() ? "unnamed_mesh" : name;
            primitives = List.copyOf(primitives);
        }
    }

    /** A glTF scene stores the indices of its root nodes. */
    public record Scene(String name, List<Integer> rootNodeIndices) {
        public Scene {
            name = name == null || name.isBlank() ? "unnamed_scene" : name;
            rootNodeIndices = List.copyOf(rootNodeIndices);
        }
    }

    /**
     * A flattened node hierarchy entry. Matrices are 4x4, column-major,
     * and map local node coordinates into the parent coordinate space.
     */
    public static final class Node {
        private final String name;
        private final int parentIndex;
        private final List<Integer> childIndices;
        private final List<Integer> meshIndices;
        private final int skinIndex;
        private final double[] localTransform;

        public Node(String name, int parentIndex, List<Integer> childIndices,
                    List<Integer> meshIndices, int skinIndex, double[] localTransform) {
            this.name = name == null || name.isBlank() ? "unnamed_node" : name;
            this.parentIndex = parentIndex;
            this.childIndices = List.copyOf(childIndices);
            this.meshIndices = List.copyOf(meshIndices);
            this.skinIndex = skinIndex;
            if (parentIndex < -1 || skinIndex < -1) {
                throw new IllegalArgumentException("Node indices cannot be less than -1");
            }
            if (localTransform == null || localTransform.length != 16) {
                throw new IllegalArgumentException("Node local transform must contain 16 values");
            }
            this.localTransform = localTransform.clone();
        }

        public String name() { return name; }
        /** -1 means this node has no parent. */
        public int parentIndex() { return parentIndex; }
        public List<Integer> childIndices() { return childIndices; }
        public List<Integer> meshIndices() { return meshIndices; }
        /** -1 means this node is not skinned. */
        public int skinIndex() { return skinIndex; }
        public double[] localTransform() { return localTransform.clone(); }
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
