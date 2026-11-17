
package ru.progrevgoev.yasm.render.gltf;

import java.util.Objects;

/**
 * Samples glTF animation channels at arbitrary points in time.
 *
 * Returned values are independent arrays and never modify the source model.
 */
public final class GltfAnimationSampler {
    private static final float QUATERNION_EPSILON = 1.0e-8f;
    private static final float SLERP_LINEAR_THRESHOLD = 0.9995f;

    private GltfAnimationSampler() {
    }

    /**
     * Samples a channel at the specified animation time.
     *
     * @param channel animation channel
     * @param time    animation time in seconds
     * @return sampled components (xyz, xyzw, or morph weights)
     */
    public static float[] sample(GltfModel.AnimationChannel channel, float time) {
        Objects.requireNonNull(channel, "channel");

        if (!Float.isFinite(time)) {
            throw new IllegalArgumentException("Animation sample time must be finite");
        }

        float[] times = channel.times();
        float[] values = channel.values();

        int components = channel.componentsPerValue();
        String path = channel.path();
        String interpolation = channel.interpolation();

        if ("rotation".equals(path) && components != 4) {
            throw new IllegalArgumentException("Rotation channels must have four components");
        }

        if (("translation".equals(path) || "scale".equals(path)) && components != 3) {
            throw new IllegalArgumentException(path + " channels must have three components");
        }

        int lastKey = times.length - 1;

        if (time <= times[0]) {
            return sampleKey(values, 0, components, interpolation, path);
        }

        if (time >= times[lastKey]) {
            return sampleKey(values, lastKey, components, interpolation, path);
        }

        // Find the first keyframe whose time is greater than the sample time.
        int low = 0;
        int high = lastKey;

        while (low < high) {
            int middle = (low + high) >>> 1;

            if (times[middle] <= time) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }

        int rightKey = low;
        int leftKey = rightKey - 1;

        if ("STEP".equals(interpolation)) {
            return sampleKey(values, leftKey, components, interpolation, path);
        }

        float startTime = times[leftKey];
        float endTime = times[rightKey];
        float duration = endTime - startTime;

        float factor = (time - startTime) / duration;

        if ("LINEAR".equals(interpolation)) {
            if ("rotation".equals(path)) {
                return slerp(
                        keyValue(values, leftKey, components, interpolation),
                        keyValue(values, rightKey, components, interpolation),
                        factor
                );
            }

            return lerp(
                    keyValue(values, leftKey, components, interpolation),
                    keyValue(values, rightKey, components, interpolation),
                    factor
            );
        }

        if ("CUBICSPLINE".equals(interpolation)) {
            float[] p0 = cubicPart(values, leftKey, components, 1);
            float[] m0 = cubicPart(values, leftKey, components, 2);
            float[] p1 = cubicPart(values, rightKey, components, 1);
            float[] m1 = cubicPart(values, rightKey, components, 0);

            float[] result = hermite(p0, m0, p1, m1, factor, duration);

            if ("rotation".equals(path)) {
                normalizeQuaternion(result);
            }

            return result;
        }

        throw new IllegalArgumentException("Unsupported interpolation: " + interpolation);
    }

    private static float[] sampleKey(
            float[] values,
            int key,
            int components,
            String interpolation,
            String path
    ) {
        float[] result;

        if ("CUBICSPLINE".equals(interpolation)) {
            result = cubicPart(values, key, components, 1);
        } else {
            result = keyValue(values, key, components, interpolation);
        }

        if ("rotation".equals(path)) {
            normalizeQuaternion(result);
        }

        return result;
    }

    private static float[] keyValue(
            float[] values,
            int key,
            int components,
            String interpolation
    ) {
        int multiplier = "CUBICSPLINE".equals(interpolation) ? 3 : 1;
        int offset = key * components * multiplier;

        if (multiplier == 3) {
            offset += components;
        }

        float[] result = new float[components];
        System.arraycopy(values, offset, result, 0, components);
        return result;
    }

    /**
     * Returns one of the CUBICSPLINE triplet components:
     * 0 = in-tangent, 1 = value, 2 = out-tangent.
     */
    private static float[] cubicPart(
            float[] values,
            int key,
            int components,
            int part
    ) {
        int offset = key * components * 3 + part * components;

        float[] result = new float[components];
        System.arraycopy(values, offset, result, 0, components);
        return result;
    }

    private static float[] lerp(float[] a, float[] b, float factor) {
        float[] result = new float[a.length];

        for (int i = 0; i < result.length; i++) {
            result[i] = a[i] + (b[i] - a[i]) * factor;
        }

        return result;
    }

    /**
     * Cubic Hermite interpolation.
     * Tangents are scaled by the time interval, as required by glTF.
     */
    private static float[] hermite(float[] p0, float[] m0, float[] p1, float[] m1, float t, float duration) {
        float t2 = t * t;
        float t3 = t2 * t;

        float h00 = 2.0f * t3 - 3.0f * t2 + 1.0f;
        float h10 = t3 - 2.0f * t2 + t;
        float h01 = -2.0f * t3 + 3.0f * t2;
        float h11 = t3 - t2;

        float[] result = new float[p0.length];

        for (int i = 0; i < result.length; i++) {
            result[i] = h00 * p0[i] + h10 * duration * m0[i] + h01 * p1[i] + h11 * duration * m1[i];
        }

        return result;
    }

    /**
     * Quaternion spherical linear interpolation.
     * Uses the shortest path and normalizes the result.
     */
    private static float[] slerp(float[] a, float[] b, float factor) {
        float[] q0 = a.clone();
        float[] q1 = b.clone();

        normalizeQuaternion(q0);
        normalizeQuaternion(q1);

        float dot = q0[0] * q1[0] + q0[1] * q1[1] + q0[2] * q1[2] + q0[3] * q1[3];

        // q and -q represent the same rotation.
        // Negating q1 ensures interpolation follows the shortest path.
        if (dot < 0.0f) {
            dot = -dot;
            for (int i = 0; i < 4; i++) {
                q1[i] = -q1[i];
            }
        }

        dot = Math.min(1.0f, Math.max(-1.0f, dot));

        float[] result = new float[4];

        if (dot > SLERP_LINEAR_THRESHOLD) {
            for (int i = 0; i < 4; i++) {
                result[i] = q0[i] + factor * (q1[i] - q0[i]);
            }
        } else {
            double angle = Math.acos(dot);
            double sinAngle = Math.sin(angle);

            float weight0 = (float) (
                    Math.sin((1.0f - factor) * angle) / sinAngle
            );

            float weight1 = (float) (
                    Math.sin(factor * angle) / sinAngle
            );

            for (int i = 0; i < 4; i++) {
                result[i] = weight0 * q0[i] + weight1 * q1[i];
            }
        }

        normalizeQuaternion(result);
        return result;
    }

    private static void normalizeQuaternion(float[] quaternion) {
        if (quaternion.length != 4) {
            throw new IllegalArgumentException("A quaternion must have four components");
        }

        float lengthSquared = 0.0f;

        for (float component : quaternion) {
            lengthSquared += component * component;
        }

        if (!Float.isFinite(lengthSquared)
                || lengthSquared < QUATERNION_EPSILON) {
            throw new IllegalArgumentException("Cannot normalize an invalid quaternion");
        }

        float inverseLength = (float) (1.0 / Math.sqrt(lengthSquared));

        for (int i = 0; i < 4; i++) {
            quaternion[i] *= inverseLength;
        }
    }
}