package ru.progrevgoev.yasm.render.gltf;

import com.google.gson.*;
import de.javagl.jgltf.model.*;
import de.javagl.jgltf.model.io.GltfModelReader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Loads JSON glTF 2.0 resources and stages their relative external dependencies for jGLTF. */
public final class GltfModelLoader {
    private static final int MAX_IMAGE_BYTES = 64 * 1024 * 1024;
    private static final int MAX_BASE64_LENGTH = ((MAX_IMAGE_BYTES + 2) / 3) * 4;
    private static final int MAX_TEXTURE_DIMENSION = 16384;

    private final ResourceManager resources;

    public GltfModelLoader(ResourceManager resources) {
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    public GltfModel load(ResourceLocation modelLocation) throws IOException {
        Objects.requireNonNull(modelLocation, "modelLocation");
        Path temp = Files.createTempDirectory("yasm-gltf-");
        try {
            Path stagedModel = temp.resolve("model.gltf");
            try (InputStream in = open(modelLocation)) {
                Files.copy(in, stagedModel, StandardCopyOption.REPLACE_EXISTING);
            }

            JsonObject json;
            try (Reader reader = Files.newBufferedReader(stagedModel, StandardCharsets.UTF_8)) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            }

            stageReferences(json, modelLocation, temp);

            de.javagl.jgltf.model.GltfModel parsed =
                    new GltfModelReader().read(stagedModel);

            return convert(modelLocation, parsed, readDefaultSceneIndex(json), temp);
        } catch (JsonParseException | IllegalStateException | ClassCastException e) {
            throw new IOException("Invalid glTF JSON resource " + modelLocation, e);
        } catch (RuntimeException e) {
            throw new IOException("Failed to load glTF model " + modelLocation, e);
        } finally {
            deleteTree(temp);
        }
    }

    private static int readDefaultSceneIndex(JsonObject json) throws IOException {
        if (!json.has("scene") || json.get("scene").isJsonNull()) {
            return -1;
        }

        try {
            int index = json.get("scene").getAsInt();
            if (index < 0) {
                throw new IOException("glTF default scene index cannot be negative");
            }
            return index;
        } catch (NumberFormatException | UnsupportedOperationException e) {
            throw new IOException("Invalid glTF default scene index", e);
        }
    }

    private InputStream open(ResourceLocation location) throws IOException {
        Resource resource = resources.getResource(location)
                .orElseThrow(() -> new IOException("Missing glTF resource: " + location));
        return resource.open();
    }

    private void stageReferences(JsonObject json, ResourceLocation model, Path temp) throws IOException {
        Set<String> copied = new HashSet<>();

        for (String section : List.of("buffers", "images")) {
            JsonArray entries = json.has(section) && json.get(section).isJsonArray()
                    ? json.getAsJsonArray(section)
                    : new JsonArray();

            for (JsonElement element : entries) {
                if (!element.isJsonObject()) {
                    continue;
                }

                JsonObject object = element.getAsJsonObject();
                if (!object.has("uri") || object.get("uri").isJsonNull()) {
                    continue;
                }

                String raw = object.get("uri").getAsString();
                if (raw.startsWith("data:")) {
                    continue;
                }

                Path relative = parseRelativeUri(raw, "glTF");

                String key = relative.toString().replace('\\', '/');
                if (!copied.add(key)) {
                    continue;
                }

                String modelPath = model.getPath();
                int slash = modelPath.lastIndexOf('/');
                String parent = slash < 0 ? "" : modelPath.substring(0, slash + 1);

                ResourceLocation dependency;
                try {
                    dependency = ResourceLocation.fromNamespaceAndPath(
                            model.getNamespace(), parent + key);
                } catch (RuntimeException e) {
                    throw new IOException("Invalid glTF dependency path: " + raw, e);
                }

                Path target = temp.resolve(relative).normalize();
                if (!target.startsWith(temp)) {
                    throw new IOException("Invalid staged glTF path: " + raw);
                }

                Files.createDirectories(target.getParent());

                try (InputStream in = open(dependency)) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private GltfModel convert(ResourceLocation source,
                              de.javagl.jgltf.model.GltfModel parsed,
                              int defaultSceneIndex,
                              Path stagedRoot) throws IOException {

        List<MaterialModel> sourceMaterials = parsed.getMaterialModels();
        List<TextureModel> sourceTextures = parsed.getTextureModels();
        IdentityHashMap<MaterialModel, Integer> materialIndices = new IdentityHashMap<>();
        IdentityHashMap<TextureModel, Integer> textureIndices = new IdentityHashMap<>();
        List<GltfTexture> textures = new ArrayList<>(sourceTextures.size());
        List<GltfMaterial> gltfMaterials = new ArrayList<>(sourceMaterials.size());
        List<String> slots = new ArrayList<>(sourceMaterials.size());

        for (int i = 0; i < sourceTextures.size(); i++) {
            TextureModel sourceTexture = sourceTextures.get(i);
            textureIndices.put(sourceTexture, i);
            textures.add(loadTexture(sourceTexture, i, stagedRoot));
        }

        for (int i = 0; i < sourceMaterials.size(); i++) {
            MaterialModel sourceMaterial = sourceMaterials.get(i);
            materialIndices.put(sourceMaterial, i);

            String name = sourceMaterial.getName();
            String materialName = name == null || name.isBlank() ? "material_" + i : name;
            slots.add(materialName);

            if (!(sourceMaterial instanceof PbrMaterialModel pbrMaterial)) {
                throw new IOException("Unsupported glTF material model at index " + i
                        + ": expected a glTF 2.0 PBR material");
            }

            PbrMetallicRoughnessModel pbr = pbrMaterial.getPbrMetallicRoughnessModel();
            double[] sourceColorFactor = pbr == null ? null : pbr.getBaseColorFactor();
            float[] baseColorFactor = readBaseColorFactor(sourceColorFactor, materialName);

            TextureModel baseColorTexture = pbrMaterial.getBaseColorTextureModel();
            int baseColorTextureIndex = baseColorTexture == null
                    ? -1
                    : requireIndex(textureIndices, baseColorTexture, "base color texture");

            Integer sourceTexCoord = pbrMaterial.getBaseColorTexcoord();
            int baseColorTexCoord = sourceTexCoord == null ? 0 : sourceTexCoord;
            if (baseColorTexCoord != 0) {
                throw new IOException("Material " + materialName + " uses TEXCOORD_" + baseColorTexCoord
                        + " for its base color texture, but only TEXCOORD_0 is currently supported");
            }

            PbrMaterialModel.AlphaMode sourceAlphaMode = pbrMaterial.getAlphaMode();
            GltfMaterial.AlphaMode alphaMode = sourceAlphaMode == null
                    ? GltfMaterial.AlphaMode.OPAQUE
                    : GltfMaterial.AlphaMode.valueOf(sourceAlphaMode.name());

            Double sourceAlphaCutoff = pbrMaterial.getAlphaCutoff();
            float alphaCutoff = sourceAlphaCutoff == null ? 0.5f : checkedFloat(
                    sourceAlphaCutoff, "alpha cutoff for material " + materialName);

            Boolean sourceDoubleSided = pbrMaterial.isDoubleSided();
            boolean doubleSided = sourceDoubleSided != null && sourceDoubleSided;

            try {
                gltfMaterials.add(new GltfMaterial(
                        materialName,
                        baseColorFactor,
                        baseColorTextureIndex,
                        baseColorTexCoord,
                        alphaMode,
                        alphaCutoff,
                        doubleSided
                ));
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid glTF material " + materialName + ": " + e.getMessage(), e);
            }
        }

        List<MeshModel> sourceMeshes = parsed.getMeshModels();
        IdentityHashMap<MeshModel, Integer> meshIndices = new IdentityHashMap<>();
        List<GltfModel.Mesh> meshes = new ArrayList<>();

        for (int meshIndex = 0; meshIndex < sourceMeshes.size(); meshIndex++) {
            MeshModel mesh = sourceMeshes.get(meshIndex);
            meshIndices.put(mesh, meshIndex);

            List<GltfModel.Primitive> primitives = new ArrayList<>();

            for (MeshPrimitiveModel primitive : mesh.getMeshPrimitiveModels()) {
                Map<String, AccessorModel> attributes = primitive.getAttributes();

                AccessorModel position = attributes.get("POSITION");
                if (position == null) {
                    throw new IOException("Mesh primitive has no POSITION attribute");
                }

                float[] positions = GltfAccessorDecoder.readPositions(position);

                AccessorModel normalAccessor = attributes.get("NORMAL");
                AccessorModel uvAccessor = attributes.get("TEXCOORD_0");
                AccessorModel jointsAccessor = attributes.get("JOINTS_0");
                AccessorModel weightsAccessor = attributes.get("WEIGHTS_0");

                if ((jointsAccessor == null) != (weightsAccessor == null)) {
                    throw new IOException("JOINTS_0 and WEIGHTS_0 must be provided together");
                }

                float[] normals = normalAccessor == null
                        ? new float[0]
                        : GltfAccessorDecoder.readNormals(normalAccessor);

                float[] uv = uvAccessor == null
                        ? new float[0]
                        : GltfAccessorDecoder.readTexCoords(uvAccessor);

                int[] joints = jointsAccessor == null
                        ? new int[0]
                        : GltfAccessorDecoder.readJoints(jointsAccessor);

                float[] weights = weightsAccessor == null
                        ? new float[0]
                        : GltfAccessorDecoder.readWeights(weightsAccessor);

                int[] indices = primitive.getIndices() == null
                        ? new int[0]
                        : GltfAccessorDecoder.readIndices(primitive.getIndices());

                MaterialModel material = primitive.getMaterialModel();
                String slot = "default";
                int materialIndex = -1;

                if (material != null) {
                    materialIndex = requireIndex(materialIndices, material, "primitive material");
                    slot = slots.get(materialIndex);
                }

                try {
                    primitives.add(new GltfModel.Primitive(
                            primitive.getMode(),
                            slot,
                            materialIndex,
                            positions,
                            normals,
                            uv,
                            joints,
                            weights,
                            indices
                    ));
                } catch (IllegalArgumentException e) {
                    throw new IOException(
                            "Invalid primitive in mesh " + mesh.getName() + ": " + e.getMessage(),
                            e
                    );
                }
            }

            meshes.add(new GltfModel.Mesh(mesh.getName(), primitives));
        }

        // Build stable object-to-index mappings.
        List<NodeModel> sourceNodes = parsed.getNodeModels();
        IdentityHashMap<NodeModel, Integer> nodeIndices = new IdentityHashMap<>();

        for (int i = 0; i < sourceNodes.size(); i++) {
            nodeIndices.put(sourceNodes.get(i), i);
        }

        List<SkinModel> sourceSkins = parsed.getSkinModels();
        IdentityHashMap<SkinModel, Integer> skinIndices = new IdentityHashMap<>();

        for (int i = 0; i < sourceSkins.size(); i++) {
            skinIndices.put(sourceSkins.get(i), i);
        }

        // Convert nodes.
        List<GltfModel.Node> nodes = new ArrayList<>(sourceNodes.size());

        for (NodeModel node : sourceNodes) {
            int parentIndex = node.getParent() == null
                    ? -1
                    : requireIndex(nodeIndices, node.getParent(), "parent node");

            List<Integer> childIndices = new ArrayList<>(node.getChildren().size());
            for (NodeModel child : node.getChildren()) {
                childIndices.add(requireIndex(nodeIndices, child, "child node"));
            }

            List<Integer> attachedMeshes = new ArrayList<>(node.getMeshModels().size());
            for (MeshModel mesh : node.getMeshModels()) {
                attachedMeshes.add(requireIndex(meshIndices, mesh, "mesh"));
            }

            int skinIndex = node.getSkinModel() == null
                    ? -1
                    : requireIndex(skinIndices, node.getSkinModel(), "skin");

            double[] transform = node.computeLocalTransform(null).clone();

            float[] translation = readNodeComponents(
                    node.getTranslation(),
                    new float[]{0.0f, 0.0f, 0.0f},
                    3,
                    "translation"
            );

            float[] rotation = readNodeComponents(
                    node.getRotation(),
                    new float[]{0.0f, 0.0f, 0.0f, 1.0f},
                    4,
                    "rotation"
            );

            float[] scale = readNodeComponents(
                    node.getScale(),
                    new float[]{1.0f, 1.0f, 1.0f},
                    3,
                    "scale"
            );

            boolean matrixTransform = node.getMatrix() != null;

            nodes.add(new GltfModel.Node(
                    node.getName(),
                    parentIndex,
                    childIndices,
                    attachedMeshes,
                    skinIndex,
                    transform,
                    translation,
                    rotation,
                    scale,
                    matrixTransform
            ));
        }

        // Convert skins.
        List<GltfModel.Skin> skins = new ArrayList<>(sourceSkins.size());

        for (SkinModel skin : sourceSkins) {
            List<NodeModel> sourceJoints = skin.getJoints();

            if (sourceJoints == null || sourceJoints.isEmpty()) {
                throw new IOException("glTF skin has no joints");
            }

            List<Integer> jointNodeIndices = new ArrayList<>(sourceJoints.size());

            for (NodeModel joint : sourceJoints) {
                jointNodeIndices.add(requireIndex(nodeIndices, joint, "skin joint node"));
            }

            int skeletonRoot = skin.getSkeleton() == null
                    ? -1
                    : requireIndex(nodeIndices, skin.getSkeleton(), "skin skeleton root");

            float[] inverseBindMatrices = new float[sourceJoints.size() * 16];
            AccessorModel inverseAccessor = skin.getInverseBindMatrices();

            if (inverseAccessor == null) {
                for (int i = 0; i < sourceJoints.size(); i++) {
                    int offset = i * 16;
                    inverseBindMatrices[offset] = 1.0f;
                    inverseBindMatrices[offset + 5] = 1.0f;
                    inverseBindMatrices[offset + 10] = 1.0f;
                    inverseBindMatrices[offset + 15] = 1.0f;
                }
            } else {
                if (inverseAccessor.getComponentType() != 5126
                        || inverseAccessor.isNormalized()
                        || inverseAccessor.getElementType().getNumComponents() != 16
                        || inverseAccessor.getCount() < sourceJoints.size()) {
                    throw new IOException(
                            "inverseBindMatrices must be a FLOAT MAT4 accessor with at least one matrix per joint");
                }

                for (int i = 0; i < sourceJoints.size(); i++) {
                    double[] matrix = skin.getInverseBindMatrix(i, null);

                    if (matrix == null || matrix.length != 16) {
                        throw new IOException("Invalid inverse bind matrix for joint " + i);
                    }

                    for (int j = 0; j < 16; j++) {
                        double value = matrix[j];

                        if (!Double.isFinite(value) || Math.abs(value) > Float.MAX_VALUE) {
                            throw new IOException("Invalid inverse bind matrix value for joint " + i);
                        }

                        inverseBindMatrices[i * 16 + j] = (float) value;
                    }
                }
            }

            try {
                skins.add(new GltfModel.Skin(
                        skin.getName(),
                        skeletonRoot,
                        jointNodeIndices,
                        inverseBindMatrices
                ));
            } catch (IllegalArgumentException e) {
                throw new IOException(
                        "Invalid glTF skin " + skin.getName() + ": " + e.getMessage(),
                        e
                );
            }
        }

        // JOINTS_0 values are local indices into the skin.joints array, not node indices.
        for (GltfModel.Node node : nodes) {
            if (node.skinIndex() < 0) {
                continue;
            }

            int jointCount = skins.get(node.skinIndex()).jointCount();

            for (int meshIndex : node.meshIndices()) {
                for (GltfModel.Primitive primitive : meshes.get(meshIndex).primitives()) {
                    if (!primitive.isSkinned()) {
                        continue;
                    }

                    for (int joint : primitive.joints0()) {
                        if (joint >= jointCount) {
                            throw new IOException(
                                    "JOINTS_0 index " + joint + " exceeds skin joint count " + jointCount);
                        }
                    }
                }
            }
        }

        // Convert scenes.
        List<GltfModel.Scene> scenes = new ArrayList<>();

        for (SceneModel scene : parsed.getSceneModels()) {
            List<Integer> roots = new ArrayList<>(scene.getNodeModels().size());

            for (NodeModel root : scene.getNodeModels()) {
                roots.add(requireIndex(nodeIndices, root, "scene root node"));
            }

            scenes.add(new GltfModel.Scene(scene.getName(), roots));
        }

        if (defaultSceneIndex >= scenes.size()) {
            throw new IOException(
                    "glTF default scene index " + defaultSceneIndex
                            + " is outside scene count " + scenes.size());
        }

        // Convert animations.
        List<GltfModel.Animation> animations = new ArrayList<>();

        for (AnimationModel animation : parsed.getAnimationModels()) {
            List<GltfModel.AnimationChannel> channels = new ArrayList<>();

            for (AnimationModel.Channel channel : animation.getChannels()) {
                NodeModel targetNode = channel.getNodeModel();

                if (targetNode == null) {
                    throw new IOException("Animation channel has no target node");
                }

                int targetNodeIndex = requireIndex(nodeIndices, targetNode, "animation target node");
                String path = channel.getPath();

                if (!("translation".equals(path)
                        || "rotation".equals(path)
                        || "scale".equals(path)
                        || "weights".equals(path))) {
                    throw new IOException("Unsupported glTF animation target path: " + path);
                }

                AnimationModel.Sampler sampler = channel.getSampler();

                if (sampler == null
                        || sampler.getInput() == null
                        || sampler.getOutput() == null
                        || sampler.getInterpolation() == null) {
                    throw new IOException("Animation channel has an incomplete sampler");
                }

                AccessorModel input = sampler.getInput();
                AccessorModel output = sampler.getOutput();

                if (input.getComponentType() != 5126
                        || input.isNormalized()
                        || input.getElementType().getNumComponents() != 1) {
                    throw new IOException(
                            "Animation sampler input must be a non-normalized FLOAT SCALAR accessor");
                }

                if (output.getComponentType() != 5126 || output.isNormalized()) {
                    throw new IOException(
                            "Animation sampler output must use non-normalized FLOAT components");
                }

                // TRS animation targets must not refer to nodes with a matrix transform.
                if (!"weights".equals(path) && targetNode.getMatrix() != null) {
                    throw new IOException(
                            "Animation targets TRS property of node " + targetNodeIndex
                                    + ", but the node uses a matrix transform");
                }

                float[] times = readAnimationFloats(input, "animation input");

                int interpolationMultiplier =
                        sampler.getInterpolation() == AnimationModel.Interpolation.CUBICSPLINE ? 3 : 1;

                int componentsPerValue;

                if ("translation".equals(path) || "scale".equals(path)) {
                    componentsPerValue = 3;
                } else if ("rotation".equals(path)) {
                    componentsPerValue = 4;
                } else {
                    if (output.getElementType().getNumComponents() != 1) {
                        throw new IOException("Animation weights output must use SCALAR elements");
                    }

                    int denominator = times.length * interpolationMultiplier;

                    if (denominator == 0 || output.getCount() % denominator != 0) {
                        throw new IOException(
                                "Animation weights output count does not match input keyframes");
                    }

                    componentsPerValue = output.getCount() / denominator;

                    if (componentsPerValue < 1) {
                        throw new IOException("Animation weights channel has no morph target values");
                    }
                }

                if ("weights".equals(path)) {
                    if (output.getCount()
                            != times.length * componentsPerValue * interpolationMultiplier) {
                        throw new IOException("Animation weights output count is inconsistent");
                    }
                } else if (output.getElementType().getNumComponents() != componentsPerValue
                        || output.getCount() != times.length * interpolationMultiplier) {
                    throw new IOException("Animation output shape does not match target path " + path);
                }

                float[] values = readAnimationFloats(output, "animation output");
                String interpolation = sampler.getInterpolation().name();

                try {
                    channels.add(new GltfModel.AnimationChannel(
                            targetNodeIndex,
                            path,
                            interpolation,
                            times,
                            values,
                            componentsPerValue
                    ));
                } catch (IllegalArgumentException e) {
                    throw new IOException("Invalid animation channel: " + e.getMessage(), e);
                }
            }

            animations.add(new GltfModel.Animation(animation.getName(), channels));
        }

        return new GltfModel(
                source,
                meshes,
                gltfMaterials,
                textures,
                slots,
                nodes,
                scenes,
                skins,
                animations,
                defaultSceneIndex
        );
    }

    private static float[] readBaseColorFactor(double[] source, String materialName) throws IOException {
        if (source == null) {
            return new float[]{1.0f, 1.0f, 1.0f, 1.0f};
        }
        if (source.length != 4) {
            throw new IOException("Base color factor for material " + materialName
                    + " must contain four components");
        }

        float[] result = new float[4];
        for (int i = 0; i < result.length; i++) {
            double value = source[i];
            if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
                throw new IOException("Base color factor for material " + materialName
                        + " contains an invalid component at index " + i);
            }
            result[i] = (float) value;
        }
        return result;
    }

    private static float checkedFloat(double value, String description) throws IOException {
        if (!Double.isFinite(value) || Math.abs(value) > Float.MAX_VALUE) {
            throw new IOException("Invalid " + description + ": " + value);
        }
        return (float) value;
    }

    private static GltfTexture loadTexture(TextureModel texture, int index, Path stagedRoot)
            throws IOException {
        ImageModel image = texture.getImageModel();
        if (image == null) {
            throw new IOException("Texture " + index + " does not reference an image");
        }

        String name = texture.getName();
        if (name == null || name.isBlank()) {
            name = image.getName();
        }
        if (name == null || name.isBlank()) {
            name = "texture_" + index;
        }

        String description = "texture " + index + " (" + name + ")";
        String mimeType = image.getMimeType();
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = "image/png";
        }
        if (!"image/png".equalsIgnoreCase(mimeType)) {
            throw new IOException(description + " uses unsupported mime type " + mimeType
                    + "; only image/png is supported");
        }

        byte[] data = readImageData(image, stagedRoot, description);
        int[] dimensions = readPngDimensions(data, description);

        try {
            return new GltfTexture(
                    name,
                    mimeType,
                    data,
                    dimensions[0],
                    dimensions[1],
                    readMagFilter(texture.getMagFilter(), description),
                    readMinFilter(texture.getMinFilter(), description),
                    readWrapMode(texture.getWrapS(), description),
                    readWrapMode(texture.getWrapT(), description)
            );
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid glTF " + description + ": " + e.getMessage(), e);
        }
    }

    private static byte[] readImageData(ImageModel image, Path stagedRoot, String description)
            throws IOException {
        // DefaultImageModel#getImageData() throws an NPE when neither inline data nor a
        // buffer view is present, so only call it for buffer-view-backed images.
        ByteBuffer inline = image.getBufferViewModel() == null ? null : image.getImageData();
        if (inline != null) {
            byte[] data = copyBuffer(inline, description);
            if (data.length == 0) {
                throw new IOException(description + " has empty image data");
            }
            return data;
        }

        String uri = image.getUri();
        if (uri == null || uri.isBlank()) {
            throw new IOException(description + " has no image URI or image data");
        }
        if (uri.startsWith("data:")) {
            return decodeDataUri(uri, description);
        }

        Path relative = parseRelativeUri(uri, description);
        Path file = stagedRoot.resolve(relative).normalize();
        if (!file.startsWith(stagedRoot)) {
            throw new IOException(description + " resolves outside the staged model directory");
        }
        if (!Files.isRegularFile(file)) {
            throw new IOException(description + " image file is missing: " + uri);
        }

        long size = Files.size(file);
        if (size <= 0) {
            throw new IOException(description + " image file is empty: " + uri);
        }
        if (size > MAX_IMAGE_BYTES) {
            throw new IOException(description + " image file exceeds " + MAX_IMAGE_BYTES + " bytes: " + uri);
        }
        return Files.readAllBytes(file);
    }

    private static byte[] copyBuffer(ByteBuffer buffer, String description) throws IOException {
        ByteBuffer copy = buffer.duplicate();
        copy.position(0);
        int length = copy.limit();
        if (length <= 0) {
            return new byte[0];
        }
        if (length > MAX_IMAGE_BYTES) {
            throw new IOException(description + " image data exceeds " + MAX_IMAGE_BYTES + " bytes");
        }
        byte[] data = new byte[length];
        copy.get(data);
        return data;
    }

    private static byte[] decodeDataUri(String uri, String description) throws IOException {
        int comma = uri.indexOf(',');
        if (comma < 0) {
            throw new IOException(description + " has an invalid data URI");
        }

        String metadata = uri.substring("data:".length(), comma);
        String payload = uri.substring(comma + 1);

        int semicolon = metadata.indexOf(';');
        String mediaType = semicolon < 0 ? metadata : metadata.substring(0, semicolon);
        if (!mediaType.isEmpty() && !"image/png".equalsIgnoreCase(mediaType)) {
            throw new IOException(description + " uses unsupported data URI media type "
                    + mediaType + "; only image/png is supported");
        }
        if (semicolon < 0 || !"base64".equalsIgnoreCase(metadata.substring(semicolon + 1))) {
            throw new IOException(description + " data URI must use base64 encoding");
        }
        if (payload.length() > MAX_BASE64_LENGTH) {
            throw new IOException(description + " data URI is too large");
        }

        byte[] data;
        try {
            data = Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException e) {
            throw new IOException(description + " has invalid base64 image data", e);
        }
        if (data.length == 0) {
            throw new IOException(description + " data URI is empty");
        }
        if (data.length > MAX_IMAGE_BYTES) {
            throw new IOException(description + " image data exceeds " + MAX_IMAGE_BYTES + " bytes");
        }
        return data;
    }

    private static int[] readPngDimensions(byte[] data, String description) throws IOException {
        // 8-byte signature + 4-byte chunk length + 4-byte chunk type + 13-byte IHDR + 4-byte CRC.
        if (data.length < 33) {
            throw new IOException(description + " is not a valid PNG: file is too short");
        }

        int[] signature = {137, 80, 78, 71, 13, 10, 26, 10};
        for (int i = 0; i < signature.length; i++) {
            if ((data[i] & 0xFF) != signature[i]) {
                throw new IOException(description + " is not a PNG image");
            }
        }

        if (readIntBigEndian(data, 8) != 13
                || data[12] != 'I' || data[13] != 'H'
                || data[14] != 'D' || data[15] != 'R') {
            throw new IOException(description + " has an invalid PNG IHDR chunk");
        }

        int width = readIntBigEndian(data, 16);
        int height = readIntBigEndian(data, 20);
        if (width <= 0 || height <= 0) {
            throw new IOException(description + " has invalid PNG dimensions " + width + "x" + height);
        }
        if (width > MAX_TEXTURE_DIMENSION || height > MAX_TEXTURE_DIMENSION) {
            throw new IOException(description + " PNG dimensions exceed " + MAX_TEXTURE_DIMENSION
                    + ": " + width + "x" + height);
        }
        return new int[]{width, height};
    }

    private static int readIntBigEndian(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    private static GltfTexture.MagFilter readMagFilter(Integer value, String description)
            throws IOException {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case 9728 -> GltfTexture.MagFilter.NEAREST;
            case 9729 -> GltfTexture.MagFilter.LINEAR;
            default -> throw new IOException(description + " has unsupported magFilter " + value);
        };
    }

    private static GltfTexture.MinFilter readMinFilter(Integer value, String description)
            throws IOException {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case 9728 -> GltfTexture.MinFilter.NEAREST;
            case 9729 -> GltfTexture.MinFilter.LINEAR;
            case 9984 -> GltfTexture.MinFilter.NEAREST_MIPMAP_NEAREST;
            case 9985 -> GltfTexture.MinFilter.LINEAR_MIPMAP_NEAREST;
            case 9986 -> GltfTexture.MinFilter.NEAREST_MIPMAP_LINEAR;
            case 9987 -> GltfTexture.MinFilter.LINEAR_MIPMAP_LINEAR;
            default -> throw new IOException(description + " has unsupported minFilter " + value);
        };
    }

    private static GltfTexture.WrapMode readWrapMode(Integer value, String description)
            throws IOException {
        if (value == null) {
            return GltfTexture.WrapMode.REPEAT;
        }
        return switch (value) {
            case 10497 -> GltfTexture.WrapMode.REPEAT;
            case 33071 -> GltfTexture.WrapMode.CLAMP_TO_EDGE;
            case 33648 -> GltfTexture.WrapMode.MIRRORED_REPEAT;
            default -> throw new IOException(description + " has unsupported wrap mode " + value);
        };
    }

    private static Path parseRelativeUri(String raw, String description) throws IOException {
        URI uri;
        try {
            uri = URI.create(raw);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid " + description + " URI: " + raw, e);
        }

        if (uri.isAbsolute() || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IOException("Only relative " + description + " URIs are supported: " + raw);
        }

        String decoded = uri.getPath();
        if (decoded == null || decoded.isBlank() || decoded.startsWith("/")
                || decoded.contains("\\") || decoded.indexOf('\0') >= 0) {
            throw new IOException("Invalid relative " + description + " URI: " + raw);
        }

        Path relative;
        try {
            relative = Path.of(decoded).normalize();
        } catch (InvalidPathException e) {
            throw new IOException("Invalid relative " + description + " URI: " + raw, e);
        }

        if (relative.isAbsolute() || relative.startsWith("..")) {
            throw new IOException(description + " URI escapes model directory: " + raw);
        }
        return relative;
    }

    /**
     * Converts jGLTF's double-precision node components to the float arrays
     * used by our renderer-independent model.
     */
    private static float[] readNodeComponents(
            double[] source,
            float[] fallback,
            int expected,
            String description
    ) throws IOException {
        if (source == null) {
            return fallback.clone();
        }

        if (source.length != expected) {
            throw new IOException(
                    "Node " + description + " must contain " + expected + " values");
        }

        float[] result = new float[expected];

        for (int i = 0; i < expected; i++) {
            double value = source[i];

            if (!Double.isFinite(value) || Math.abs(value) > Float.MAX_VALUE) {
                throw new IOException(
                        "Node " + description + " contains an invalid value at index " + i);
            }

            result[i] = (float) value;
        }

        return result;
    }

    private static float[] readAnimationFloats(AccessorModel accessor, String description)
            throws IOException {
        AccessorData data = accessor.getAccessorData();

        if (!(data instanceof AccessorFloatData floats)) {
            throw new IOException(description + " accessor does not contain FLOAT data");
        }

        float[] result = new float[floats.getTotalNumComponents()];

        for (int i = 0; i < result.length; i++) {
            float value = floats.get(i);

            if (!Float.isFinite(value)) {
                throw new IOException(description + " contains a non-finite value");
            }

            result[i] = value;
        }

        return result;
    }

    private static <T> int requireIndex(
            IdentityHashMap<T, Integer> indices,
            T value,
            String description
    ) throws IOException {
        Integer index = indices.get(value);

        if (index == null) {
            throw new IOException("Unable to resolve " + description + " reference");
        }

        return index;
    }

    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }

        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort cleanup.
                }
            });
        } catch (IOException ignored) {
            // Best-effort cleanup.
        }
    }
}