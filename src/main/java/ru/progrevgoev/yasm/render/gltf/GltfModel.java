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
    private final List<Skin> skins;
    private final List<Animation> animations;
    private final int defaultSceneIndex;

    public GltfModel(ResourceLocation source, List<Mesh> meshes, List<String> materialSlots,
                     List<Node> nodes, List<Scene> scenes, List<Skin> skins,
                     List<Animation> animations, int defaultSceneIndex) {
        this.source = Objects.requireNonNull(source, "source");
        this.meshes = List.copyOf(meshes);
        this.materialSlots = List.copyOf(materialSlots);
        this.nodes = List.copyOf(nodes);
        this.scenes = List.copyOf(scenes);
        this.skins = List.copyOf(skins);
        this.animations = List.copyOf(animations);
        if (defaultSceneIndex < -1 || defaultSceneIndex >= this.scenes.size()) {
            throw new IllegalArgumentException("Default scene index is out of range: " + defaultSceneIndex);
        }
        this.defaultSceneIndex = defaultSceneIndex;
    }

    public ResourceLocation source() { return source; }
    public List<Mesh> meshes() { return meshes; }
    public List<String> materialSlots() { return materialSlots; }
    public List<Node> nodes() { return nodes; }
    public List<Scene> scenes() { return scenes; }
    public List<Skin> skins() { return skins; }
    public List<Animation> animations() { return animations; }
    /** Returns -1 when the glTF asset does not declare a default scene. */
    public int defaultSceneIndex() { return defaultSceneIndex; }
    public int nodeCount() { return nodes.size(); }
    public int skinCount() { return skins.size(); }
    public int animationCount() { return animations.size(); }
    public int primitiveCount() { return meshes.stream().mapToInt(m -> m.primitives().size()).sum(); }

    public record Mesh(String name, List<Primitive> primitives) {
        public Mesh {
            name = name == null || name.isBlank() ? "unnamed_mesh" : name;
            primitives = List.copyOf(primitives);
        }
    }

    public record Scene(String name, List<Integer> rootNodeIndices) {
        public Scene {
            name = name == null || name.isBlank() ? "unnamed_scene" : name;
            rootNodeIndices = List.copyOf(rootNodeIndices);
        }
    }

    /** Joint order is identical to the source skin's joints array. */
    public static final class Skin {
        private final String name;
        private final int skeletonRootNodeIndex;
        private final List<Integer> jointNodeIndices;
        /** 16 column-major floats per joint. */
        private final float[] inverseBindMatrices;

        public Skin(String name, int skeletonRootNodeIndex, List<Integer> jointNodeIndices,
                    float[] inverseBindMatrices) {
            this.name = name == null || name.isBlank() ? "unnamed_skin" : name;
            this.skeletonRootNodeIndex = skeletonRootNodeIndex;
            this.jointNodeIndices = List.copyOf(jointNodeIndices);
            if (skeletonRootNodeIndex < -1) throw new IllegalArgumentException("Invalid skeleton root index");
            if (this.jointNodeIndices.isEmpty()) throw new IllegalArgumentException("A skin must contain joints");
            for (int index : this.jointNodeIndices) if (index < 0) throw new IllegalArgumentException("Invalid joint node index");
            Objects.requireNonNull(inverseBindMatrices, "inverseBindMatrices");
            if (inverseBindMatrices.length != this.jointNodeIndices.size() * 16)
                throw new IllegalArgumentException("Expected one MAT4 inverse bind matrix per joint");
            for (float value : inverseBindMatrices) if (!Float.isFinite(value))
                throw new IllegalArgumentException("Inverse bind matrix contains a non-finite value");
            this.inverseBindMatrices = inverseBindMatrices.clone();
        }

        public String name() { return name; }
        /** -1 means the skin does not specify a skeleton root. */
        public int skeletonRootNodeIndex() { return skeletonRootNodeIndex; }
        public List<Integer> jointNodeIndices() { return jointNodeIndices; }
        public float[] inverseBindMatrices() { return inverseBindMatrices.clone(); }
        public int jointCount() { return jointNodeIndices.size(); }
    }

    /** One source glTF animation. Channels retain their original keyframe data. */
    public record Animation(String name, List<AnimationChannel> channels) {
        public Animation {
            name = name == null || name.isBlank() ? "unnamed_animation" : name;
            channels = List.copyOf(channels);
        }
    }

    /**
     * Animation data for one node property. Values are flattened floats.
     * For CUBICSPLINE, each keyframe stores in-tangent, value, out-tangent
     * consecutively, as required by glTF.
     */
    public static final class AnimationChannel {
        private final int nodeIndex;
        private final String path;
        private final String interpolation;
        private final float[] times;
        private final float[] values;
        private final int componentsPerValue;

        public AnimationChannel(int nodeIndex, String path, String interpolation,
                                float[] times, float[] values, int componentsPerValue) {
            if (nodeIndex < 0) throw new IllegalArgumentException("Animation target node index cannot be negative");
            if (!("translation".equals(path) || "rotation".equals(path)
                    || "scale".equals(path) || "weights".equals(path)))
                throw new IllegalArgumentException("Unsupported animation path: " + path);
            if (!("STEP".equals(interpolation) || "LINEAR".equals(interpolation)
                    || "CUBICSPLINE".equals(interpolation)))
                throw new IllegalArgumentException("Unsupported interpolation: " + interpolation);
            if (times == null || times.length == 0) throw new IllegalArgumentException("Animation has no keyframes");
            if (values == null || componentsPerValue < 1) throw new IllegalArgumentException("Invalid animation output data");
            int multiplier = "CUBICSPLINE".equals(interpolation) ? 3 : 1;
            if (values.length != times.length * componentsPerValue * multiplier)
                throw new IllegalArgumentException("Animation output size does not match its keyframes");
            this.times = times.clone();
            for (int i = 0; i < this.times.length; i++) {
                if (!Float.isFinite(this.times[i]) || this.times[i] < 0.0f
                        || (i > 0 && this.times[i] <= this.times[i - 1]))
                    throw new IllegalArgumentException("Animation keyframe times must be finite, non-negative and increasing");
            }
            this.values = values.clone();
            for (float value : this.values) if (!Float.isFinite(value))
                throw new IllegalArgumentException("Animation output contains a non-finite value");
            this.nodeIndex = nodeIndex;
            this.path = path;
            this.interpolation = interpolation;
            this.componentsPerValue = componentsPerValue;
        }

        public int nodeIndex() { return nodeIndex; }
        public String path() { return path; }
        public String interpolation() { return interpolation; }
        public float[] times() { return times.clone(); }
        public float[] values() { return values.clone(); }
        public int componentsPerValue() { return componentsPerValue; }
        public int keyframeCount() { return times.length; }
    }

    /** Matrices are 4x4, column-major, and map local node coordinates into parent space. */
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
            if (parentIndex < -1 || skinIndex < -1) throw new IllegalArgumentException("Node indices cannot be less than -1");
            if (localTransform == null || localTransform.length != 16)
                throw new IllegalArgumentException("Node local transform must contain 16 values");
            this.localTransform = localTransform.clone();
        }

        public String name() { return name; }
        public int parentIndex() { return parentIndex; }
        public List<Integer> childIndices() { return childIndices; }
        public List<Integer> meshIndices() { return meshIndices; }
        public int skinIndex() { return skinIndex; }
        public double[] localTransform() { return localTransform.clone(); }
    }

    /** Vertex attributes are flattened; JOINTS_0 has four indices and WEIGHTS_0 four weights per vertex. */
    public static final class Primitive {
        private final int mode;
        private final String materialSlot;
        private final float[] positions, normals, texCoords0, weights0;
        private final int[] joints0, indices;

        public Primitive(int mode, String materialSlot, float[] positions, float[] normals,
                         float[] texCoords0, int[] joints0, float[] weights0, int[] indices) {
            if (mode < 0 || mode > 6) throw new IllegalArgumentException("Unsupported primitive mode: " + mode);
            this.mode = mode;
            this.materialSlot = materialSlot == null || materialSlot.isBlank() ? "default" : materialSlot;
            this.positions = Objects.requireNonNull(positions, "positions").clone();
            this.normals = normals == null ? new float[0] : normals.clone();
            this.texCoords0 = texCoords0 == null ? new float[0] : texCoords0.clone();
            this.joints0 = joints0 == null ? new int[0] : joints0.clone();
            this.weights0 = weights0 == null ? new float[0] : weights0.clone();
            this.indices = indices == null ? new int[0] : indices.clone();

            if (this.positions.length == 0 || this.positions.length % 3 != 0)
                throw new IllegalArgumentException("POSITION must contain xyz triples");
            int vertices = this.positions.length / 3;
            if (this.normals.length != 0 && this.normals.length != this.positions.length)
                throw new IllegalArgumentException("NORMAL count differs from POSITION count");
            if (this.texCoords0.length != 0 && this.texCoords0.length != vertices * 2)
                throw new IllegalArgumentException("TEXCOORD_0 count differs from POSITION count");
            if ((this.joints0.length == 0) != (this.weights0.length == 0))
                throw new IllegalArgumentException("JOINTS_0 and WEIGHTS_0 must be provided together");
            if (this.joints0.length != 0 && this.joints0.length != vertices * 4)
                throw new IllegalArgumentException("JOINTS_0 must contain four indices per vertex");
            if (this.weights0.length != 0 && this.weights0.length != vertices * 4)
                throw new IllegalArgumentException("WEIGHTS_0 must contain four weights per vertex");
            for (int joint : this.joints0) if (joint < 0) throw new IllegalArgumentException("Joint index cannot be negative");
            for (float weight : this.weights0) if (!Float.isFinite(weight) || weight < 0.0f)
                throw new IllegalArgumentException("Weights must be finite and non-negative");
            for (int index : this.indices) if (index < 0 || index >= vertices)
                throw new IllegalArgumentException("Index " + index + " is outside vertex range 0.." + (vertices - 1));
        }

        public int mode() { return mode; }
        public String materialSlot() { return materialSlot; }
        public float[] positions() { return positions.clone(); }
        public float[] normals() { return normals.clone(); }
        public float[] texCoords0() { return texCoords0.clone(); }
        public int[] joints0() { return joints0.clone(); }
        public float[] weights0() { return weights0.clone(); }
        public int[] indices() { return indices.clone(); }
        public int vertexCount() { return positions.length / 3; }
        public boolean isSkinned() { return joints0.length != 0; }
    }
}
