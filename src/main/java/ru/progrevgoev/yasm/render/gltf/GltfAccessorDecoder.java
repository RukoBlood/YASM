package ru.progrevgoev.yasm.render.gltf;

import de.javagl.jgltf.model.*;

import java.io.IOException;

/** Converts jGLTF accessor data to the flat arrays used by YASM. */
final class GltfAccessorDecoder {
    private static final int BYTE = 5120;
    private static final int UNSIGNED_BYTE = 5121;
    private static final int SHORT = 5122;
    private static final int UNSIGNED_SHORT = 5123;
    private static final int UNSIGNED_INT = 5125;
    private static final int FLOAT = 5126;

    private GltfAccessorDecoder() {}

    static float[] readPositions(AccessorModel accessor) throws IOException {
        requireShape(accessor, "POSITION", 3);
        if (accessor.getComponentType() != FLOAT || accessor.isNormalized())
            throw new IOException("POSITION must use non-normalized FLOAT components");
        return readFloats(accessor);
    }

    static float[] readNormals(AccessorModel accessor) throws IOException {
        requireShape(accessor, "NORMAL", 3);
        int type = accessor.getComponentType();
        if (type == FLOAT && !accessor.isNormalized()) return readFloats(accessor);
        if ((type == BYTE || type == SHORT) && accessor.isNormalized()) return readNormalizedIntegers(accessor);
        throw new IOException("NORMAL must use FLOAT or normalized BYTE/SHORT components");
    }

    static float[] readTexCoords(AccessorModel accessor) throws IOException {
        requireShape(accessor, "TEXCOORD_0", 2);
        int type = accessor.getComponentType();
        if (type == FLOAT && !accessor.isNormalized()) return readFloats(accessor);
        if ((type == BYTE || type == UNSIGNED_BYTE || type == SHORT || type == UNSIGNED_SHORT)
                && accessor.isNormalized()) return readNormalizedIntegers(accessor);
        throw new IOException("TEXCOORD_0 must use FLOAT or normalized BYTE/UBYTE/SHORT/USHORT components");
    }

    static int[] readJoints(AccessorModel accessor) throws IOException {
        requireShape(accessor, "JOINTS_0", 4);
        if (accessor.isNormalized()) throw new IOException("JOINTS_0 cannot be normalized");
        AccessorData data = accessor.getAccessorData();
        int[] result = new int[accessor.getCount() * 4];
        switch (accessor.getComponentType()) {
            case UNSIGNED_BYTE -> {
                if (!(data instanceof AccessorByteData bytes) || !bytes.isUnsigned())
                    throw new IOException("Invalid JOINTS_0 UNSIGNED_BYTE accessor data");
                for (int i = 0; i < result.length; i++) result[i] = bytes.getInt(i);
            }
            case UNSIGNED_SHORT -> {
                if (!(data instanceof AccessorShortData shorts) || !shorts.isUnsigned())
                    throw new IOException("Invalid JOINTS_0 UNSIGNED_SHORT accessor data");
                for (int i = 0; i < result.length; i++) result[i] = shorts.getInt(i);
            }
            default -> throw new IOException("JOINTS_0 must use UNSIGNED_BYTE or UNSIGNED_SHORT");
        }
        return result;
    }

    static float[] readWeights(AccessorModel accessor) throws IOException {
        requireShape(accessor, "WEIGHTS_0", 4);
        int type = accessor.getComponentType();
        if (type == FLOAT && !accessor.isNormalized()) return readFloats(accessor);
        if ((type == UNSIGNED_BYTE || type == UNSIGNED_SHORT) && accessor.isNormalized())
            return readNormalizedIntegers(accessor);
        throw new IOException("WEIGHTS_0 must use FLOAT or normalized UNSIGNED_BYTE/UNSIGNED_SHORT");
    }

    static int[] readIndices(AccessorModel accessor) throws IOException {
        requireShape(accessor, "indices", 1);
        if (accessor.isNormalized()) throw new IOException("Index accessor cannot be normalized");
        AccessorData data = accessor.getAccessorData();
        int[] result = new int[accessor.getCount()];
        switch (accessor.getComponentType()) {
            case UNSIGNED_BYTE -> {
                if (!(data instanceof AccessorByteData bytes) || !bytes.isUnsigned()) throw new IOException("Invalid UNSIGNED_BYTE index accessor data");
                for (int i = 0; i < result.length; i++) result[i] = bytes.getInt(i);
            }
            case UNSIGNED_SHORT -> {
                if (!(data instanceof AccessorShortData shorts) || !shorts.isUnsigned()) throw new IOException("Invalid UNSIGNED_SHORT index accessor data");
                for (int i = 0; i < result.length; i++) result[i] = shorts.getInt(i);
            }
            case UNSIGNED_INT -> {
                if (!(data instanceof AccessorIntData ints) || !ints.isUnsigned()) throw new IOException("Invalid UNSIGNED_INT index accessor data");
                for (int i = 0; i < result.length; i++) {
                    long value = ints.getLong(i);
                    if (value > Integer.MAX_VALUE) throw new IOException("Index exceeds Java signed int range: " + value);
                    result[i] = (int) value;
                }
            }
            default -> throw new IOException("Indices must use UNSIGNED_BYTE, UNSIGNED_SHORT or UNSIGNED_INT");
        }
        return result;
    }

    private static float[] readFloats(AccessorModel accessor) throws IOException {
        AccessorData data = accessor.getAccessorData();
        if (!(data instanceof AccessorFloatData floats)) throw new IOException("Expected FLOAT accessor data");
        float[] values = new float[floats.getTotalNumComponents()];
        for (int i = 0; i < values.length; i++) {
            float value = floats.get(i);
            if (!Float.isFinite(value)) throw new IOException("Accessor contains a non-finite float");
            values[i] = value;
        }
        return values;
    }

    private static float[] readNormalizedIntegers(AccessorModel accessor) throws IOException {
        AccessorData data = accessor.getAccessorData();
        int count = accessor.getCount() * data.getNumComponentsPerElement();
        float[] result = new float[count];
        int type = accessor.getComponentType();
        for (int i = 0; i < count; i++) {
            switch (type) {
                case BYTE, UNSIGNED_BYTE -> {
                    if (!(data instanceof AccessorByteData bytes)) throw new IOException("Invalid BYTE accessor data");
                    int value = bytes.getInt(i);
                    result[i] = bytes.isUnsigned() ? value / 255.0f : Math.max(value / 127.0f, -1.0f);
                }
                case SHORT, UNSIGNED_SHORT -> {
                    if (!(data instanceof AccessorShortData shorts)) throw new IOException("Invalid SHORT accessor data");
                    int value = shorts.getInt(i);
                    result[i] = shorts.isUnsigned() ? value / 65535.0f : Math.max(value / 32767.0f, -1.0f);
                }
                default -> throw new IOException("Unsupported normalized accessor component type: " + type);
            }
        }
        return result;
    }

    private static void requireShape(AccessorModel accessor, String semantic, int components) throws IOException {
        if (accessor == null) throw new IOException("Missing accessor for " + semantic);
        if (accessor.getCount() < 1 || accessor.getElementType().getNumComponents() != components)
            throw new IOException(semantic + " must have " + components + " components per element");
    }
}
