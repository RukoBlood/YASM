package ru.progrevgoev.yasm.render.animation;

import ru.progrevgoev.yasm.render.gltf.GltfAnimationSampler;
import ru.progrevgoev.yasm.render.gltf.GltfModel;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Mutable runtime pose for a single instance of a {@link GltfModel}.
 *
 * <p>The pose owns its own node transforms, local matrices, global matrices and
 * skinning matrices. The source model is only read, so several poses may be
 * created from the same model and animated independently.</p>
 *
 * <p>All matrices are 4x4 and stored column-major, matching the glTF convention.
 * A global matrix maps node-local coordinates into model space. A skinning
 * matrix maps a vertex from bind pose into the current pose:</p>
 *
 * <pre>skinningMatrix = globalJointTransform * inverseBindMatrix</pre>
 *
 * <p>This class is not thread-safe. It is intended to be owned by a runtime
 * model instance and updated on the render thread.</p>
 */
public final class SkeletonPose {
    private static final float QUATERNION_EPSILON = 1.0e-8f;

    private final GltfModel model;
    private final int[] updateOrder;
    private final boolean[] matrixNodes;
    private final float[][] fixedLocalMatrices;

    /** Three floats per node. */
    private final float[] translations;
    /** Four floats per node, xyzw. */
    private final float[] rotations;
    /** Three floats per node. */
    private final float[] scales;
    /** Sixteen floats per node. */
    private final float[] localMatrices;
    /** Sixteen floats per node. */
    private final float[] globalMatrices;

    /** Joint node indices per skin, in source skin order. */
    private final int[][] skinJointNodes;
    /** Sixteen floats per joint, in source skin order. */
    private final float[][] inverseBindMatrices;
    /** Sixteen floats per joint, in source skin order. */
    private final float[][] skinningMatrices;

    public SkeletonPose(GltfModel model) {
        this.model = Objects.requireNonNull(model, "model");

        int nodeCount = model.nodeCount();
        this.translations = new float[nodeCount * 3];
        this.rotations = new float[nodeCount * 4];
        this.scales = new float[nodeCount * 3];
        this.localMatrices = new float[nodeCount * 16];
        this.globalMatrices = new float[nodeCount * 16];
        this.matrixNodes = new boolean[nodeCount];
        this.fixedLocalMatrices = new float[nodeCount][];
        this.updateOrder = buildUpdateOrder(model);

        List<GltfModel.Node> nodes = model.nodes();
        for (int i = 0; i < nodeCount; i++) {
            GltfModel.Node node = nodes.get(i);
            if (node.matrixTransform()) {
                matrixNodes[i] = true;
                fixedLocalMatrices[i] = toFloats(node.localTransform());
            }
        }

        int skinCount = model.skinCount();
        this.skinJointNodes = new int[skinCount][];
        this.inverseBindMatrices = new float[skinCount][];
        this.skinningMatrices = new float[skinCount][];

        for (int skinIndex = 0; skinIndex < skinCount; skinIndex++) {
            GltfModel.Skin skin = model.skins().get(skinIndex);
            List<Integer> joints = skin.jointNodeIndices();
            int[] jointNodes = new int[joints.size()];

            for (int jointIndex = 0; jointIndex < joints.size(); jointIndex++) {
                int nodeIndex = joints.get(jointIndex);
                if (nodeIndex < 0 || nodeIndex >= nodeCount) {
                    throw new IllegalArgumentException(
                            "Skin " + skinIndex + " joint " + jointIndex
                                    + " references node " + nodeIndex
                                    + " outside node range 0.." + (nodeCount - 1));
                }
                jointNodes[jointIndex] = nodeIndex;
            }

            this.skinJointNodes[skinIndex] = jointNodes;
            this.inverseBindMatrices[skinIndex] = skin.inverseBindMatrices();
            this.skinningMatrices[skinIndex] = new float[joints.size() * 16];
        }

        reset();
    }

    public GltfModel model() {
        return model;
    }

    public int nodeCount() {
        return model.nodeCount();
    }

    public int skinCount() {
        return model.skinCount();
    }

    /** Restores every node transform to the model's default values and updates the pose. */
    public void reset() {
        List<GltfModel.Node> nodes = model.nodes();

        for (int i = 0; i < nodes.size(); i++) {
            GltfModel.Node node = nodes.get(i);

            System.arraycopy(node.translation(), 0, translations, i * 3, 3);
            System.arraycopy(node.rotation(), 0, rotations, i * 4, 4);
            System.arraycopy(node.scale(), 0, scales, i * 3, 3);
        }

        update();
    }

    /**
     * Samples every channel of the animation at {@code time} and rebuilds the pose.
     * Morph weight channels are ignored because morph targets are out of scope.
     */
    public void applyAnimation(GltfModel.Animation animation, float time) {
        Objects.requireNonNull(animation, "animation");
        requireFiniteTime(time);

        for (GltfModel.AnimationChannel channel : animation.channels()) {
            applyChannelInternal(channel, time);
        }

        update();
    }

    /** Samples one channel at {@code time} and rebuilds the pose. */
    public void applyChannel(GltfModel.AnimationChannel channel, float time) {
        Objects.requireNonNull(channel, "channel");
        requireFiniteTime(time);
        applyChannelInternal(channel, time);
        update();
    }

    /**
     * Recomputes local matrices, global node matrices and skinning matrices.
     * Call this after modifying the pose through future setter APIs.
     */
    public void update() {
        List<GltfModel.Node> nodes = model.nodes();

        for (int nodeIndex : updateOrder) {
            if (matrixNodes[nodeIndex]) {
                System.arraycopy(fixedLocalMatrices[nodeIndex], 0,
                        localMatrices, nodeIndex * 16, 16);
            } else {
                composeLocalMatrix(nodeIndex);
            }

            int parentIndex = nodes.get(nodeIndex).parentIndex();
            if (parentIndex < 0) {
                System.arraycopy(localMatrices, nodeIndex * 16,
                        globalMatrices, nodeIndex * 16, 16);
            } else {
                multiply(
                        globalMatrices, parentIndex * 16,
                        localMatrices, nodeIndex * 16,
                        globalMatrices, nodeIndex * 16
                );
            }
        }

        updateSkinningMatrices();
    }

    public float[] translation(int nodeIndex) {
        requireNodeIndex(nodeIndex);
        return Arrays.copyOfRange(translations, nodeIndex * 3, nodeIndex * 3 + 3);
    }

    public float[] rotation(int nodeIndex) {
        requireNodeIndex(nodeIndex);
        return Arrays.copyOfRange(rotations, nodeIndex * 4, nodeIndex * 4 + 4);
    }

    public float[] scale(int nodeIndex) {
        requireNodeIndex(nodeIndex);
        return Arrays.copyOfRange(scales, nodeIndex * 3, nodeIndex * 3 + 3);
    }

    public float[] localMatrix(int nodeIndex) {
        requireNodeIndex(nodeIndex);
        return Arrays.copyOfRange(localMatrices, nodeIndex * 16, nodeIndex * 16 + 16);
    }

    public float[] globalMatrix(int nodeIndex) {
        requireNodeIndex(nodeIndex);
        return Arrays.copyOfRange(globalMatrices, nodeIndex * 16, nodeIndex * 16 + 16);
    }

    /** Returns the global transform of a skin joint in model space. */
    public float[] jointGlobalMatrix(int skinIndex, int jointIndex) {
        requireSkinIndex(skinIndex);
        requireJointIndex(skinIndex, jointIndex);
        return globalMatrix(skinJointNodes[skinIndex][jointIndex]);
    }

    /** Returns all skinning matrices of a skin, sixteen column-major floats per joint. */
    public float[] skinningMatrices(int skinIndex) {
        requireSkinIndex(skinIndex);
        return skinningMatrices[skinIndex].clone();
    }

    /** Returns one skinning matrix, sixteen column-major floats. */
    public float[] skinningMatrix(int skinIndex, int jointIndex) {
        requireSkinIndex(skinIndex);
        requireJointIndex(skinIndex, jointIndex);
        int offset = jointIndex * 16;
        return Arrays.copyOfRange(skinningMatrices[skinIndex], offset, offset + 16);
    }

    private void applyChannelInternal(GltfModel.AnimationChannel channel, float time) {
        int nodeIndex = channel.nodeIndex();
        if (nodeIndex < 0 || nodeIndex >= model.nodeCount()) {
            throw new IllegalArgumentException(
                    "Animation targets node " + nodeIndex
                            + " outside range 0.." + (model.nodeCount() - 1));
        }

        String path = channel.path();

        if ("weights".equals(path)) {
            // Morph targets are out of scope for YASM.
            return;
        }

        if (matrixNodes[nodeIndex]) {
            throw new IllegalArgumentException(
                    "Cannot animate node " + nodeIndex
                            + " because it uses a matrix transform");
        }

        float[] value = GltfAnimationSampler.sample(channel, time);

        switch (path) {
            case "translation" -> {
                requireComponents(value, 3, path);
                System.arraycopy(value, 0, translations, nodeIndex * 3, 3);
            }
            case "rotation" -> {
                requireComponents(value, 4, path);
                System.arraycopy(value, 0, rotations, nodeIndex * 4, 4);
            }
            case "scale" -> {
                requireComponents(value, 3, path);
                System.arraycopy(value, 0, scales, nodeIndex * 3, 3);
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported animation path: " + path);
        }
    }

    private void updateSkinningMatrices() {
        for (int skinIndex = 0; skinIndex < skinningMatrices.length; skinIndex++) {
            int[] joints = skinJointNodes[skinIndex];
            float[] inverseBind = inverseBindMatrices[skinIndex];
            float[] output = skinningMatrices[skinIndex];

            for (int jointIndex = 0; jointIndex < joints.length; jointIndex++) {
                multiply(
                        globalMatrices, joints[jointIndex] * 16,
                        inverseBind, jointIndex * 16,
                        output, jointIndex * 16
                );
            }
        }
    }

    private void composeLocalMatrix(int nodeIndex) {
        int matrixOffset = nodeIndex * 16;
        int translationOffset = nodeIndex * 3;
        int rotationOffset = nodeIndex * 4;
        int scaleOffset = nodeIndex * 3;

        float x = rotations[rotationOffset];
        float y = rotations[rotationOffset + 1];
        float z = rotations[rotationOffset + 2];
        float w = rotations[rotationOffset + 3];

        float lengthSquared = x * x + y * y + z * z + w * w;
        if (!Float.isFinite(lengthSquared) || lengthSquared < QUATERNION_EPSILON) {
            throw new IllegalArgumentException(
                    "Node " + nodeIndex + " has an invalid rotation quaternion");
        }
        float inverseLength = (float) (1.0 / Math.sqrt(lengthSquared));
        x *= inverseLength;
        y *= inverseLength;
        z *= inverseLength;
        w *= inverseLength;

        float xx = x * x;
        float yy = y * y;
        float zz = z * z;
        float xy = x * y;
        float xz = x * z;
        float yz = y * z;
        float xw = x * w;
        float yw = y * w;
        float zw = z * w;

        float sx = scales[scaleOffset];
        float sy = scales[scaleOffset + 1];
        float sz = scales[scaleOffset + 2];

        // Column 0.
        localMatrices[matrixOffset] = (1.0f - 2.0f * (yy + zz)) * sx;
        localMatrices[matrixOffset + 1] = 2.0f * (xy + zw) * sx;
        localMatrices[matrixOffset + 2] = 2.0f * (xz - yw) * sx;
        localMatrices[matrixOffset + 3] = 0.0f;

        // Column 1.
        localMatrices[matrixOffset + 4] = 2.0f * (xy - zw) * sy;
        localMatrices[matrixOffset + 5] = (1.0f - 2.0f * (xx + zz)) * sy;
        localMatrices[matrixOffset + 6] = 2.0f * (yz + xw) * sy;
        localMatrices[matrixOffset + 7] = 0.0f;

        // Column 2.
        localMatrices[matrixOffset + 8] = 2.0f * (xz + yw) * sz;
        localMatrices[matrixOffset + 9] = 2.0f * (yz - xw) * sz;
        localMatrices[matrixOffset + 10] = (1.0f - 2.0f * (xx + yy)) * sz;
        localMatrices[matrixOffset + 11] = 0.0f;

        // Column 3.
        localMatrices[matrixOffset + 12] = translations[translationOffset];
        localMatrices[matrixOffset + 13] = translations[translationOffset + 1];
        localMatrices[matrixOffset + 14] = translations[translationOffset + 2];
        localMatrices[matrixOffset + 15] = 1.0f;
    }

    /**
     * Multiplies two column-major 4x4 matrices: {@code output = left * right}.
     * The output range must not overlap either input range.
     */
    private static void multiply(float[] left, int leftOffset,
                                 float[] right, int rightOffset,
                                 float[] output, int outputOffset) {
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0.0f;
                for (int k = 0; k < 4; k++) {
                    sum += left[leftOffset + k * 4 + row]
                            * right[rightOffset + column * 4 + k];
                }
                output[outputOffset + column * 4 + row] = sum;
            }
        }
    }

    private static int[] buildUpdateOrder(GltfModel model) {
        int nodeCount = model.nodeCount();
        int[] order = new int[nodeCount];
        boolean[] placed = new boolean[nodeCount];
        int placedCount = 0;

        for (int i = 0; i < nodeCount; i++) {
            int parentIndex = model.nodes().get(i).parentIndex();
            if (parentIndex < -1 || parentIndex >= nodeCount) {
                throw new IllegalArgumentException(
                        "Node " + i + " has invalid parent index " + parentIndex);
            }
            if (parentIndex < 0) {
                order[placedCount++] = i;
                placed[i] = true;
            }
        }

        boolean progress = true;
        while (placedCount < nodeCount && progress) {
            progress = false;

            for (int i = 0; i < nodeCount; i++) {
                if (placed[i]) {
                    continue;
                }

                int parentIndex = model.nodes().get(i).parentIndex();
                if (placed[parentIndex]) {
                    order[placedCount++] = i;
                    placed[i] = true;
                    progress = true;
                }
            }
        }

        if (placedCount != nodeCount) {
            throw new IllegalArgumentException("Node hierarchy contains a cycle");
        }

        return order;
    }

    private static float[] toFloats(double[] values) {
        float[] result = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (float) values[i];
        }
        return result;
    }

    private static void requireFiniteTime(float time) {
        if (!Float.isFinite(time)) {
            throw new IllegalArgumentException("Animation time must be finite");
        }
    }

    private static void requireComponents(float[] value, int expected, String path) {
        if (value.length != expected) {
            throw new IllegalArgumentException(
                    "Sampled " + path + " value has " + value.length
                            + " components, expected " + expected);
        }
    }

    private void requireNodeIndex(int nodeIndex) {
        if (nodeIndex < 0 || nodeIndex >= model.nodeCount()) {
            throw new IndexOutOfBoundsException(
                    "Node index " + nodeIndex + " outside range 0.." + (model.nodeCount() - 1));
        }
    }

    private void requireSkinIndex(int skinIndex) {
        if (skinIndex < 0 || skinIndex >= skinningMatrices.length) {
            throw new IndexOutOfBoundsException(
                    "Skin index " + skinIndex + " outside range 0.."
                            + (skinningMatrices.length - 1));
        }
    }

    private void requireJointIndex(int skinIndex, int jointIndex) {
        int jointCount = skinJointNodes[skinIndex].length;
        if (jointIndex < 0 || jointIndex >= jointCount) {
            throw new IndexOutOfBoundsException(
                    "Joint index " + jointIndex + " outside range 0.." + (jointCount - 1)
                            + " for skin " + skinIndex);
        }
    }
}
