package ru.progrevgoev.yasm.render.gltf;

import com.google.gson.*;
import de.javagl.jgltf.model.*;
import de.javagl.jgltf.model.io.GltfModelReader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Loads JSON glTF 2.0 resources and stages their relative external dependencies for jGLTF. */
public final class GltfModelLoader {
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
            de.javagl.jgltf.model.GltfModel parsed = new GltfModelReader().read(stagedModel);
            return convert(modelLocation, parsed, readDefaultSceneIndex(json));
        } catch (JsonParseException | IllegalStateException | ClassCastException e) {
            throw new IOException("Invalid glTF JSON resource " + modelLocation, e);
        } catch (RuntimeException e) {
            throw new IOException("Failed to load glTF model " + modelLocation, e);
        } finally {
            deleteTree(temp);
        }
    }

    private static int readDefaultSceneIndex(JsonObject json) throws IOException {
        if (!json.has("scene") || json.get("scene").isJsonNull()) return -1;
        try {
            int index = json.get("scene").getAsInt();
            if (index < 0) throw new IOException("glTF default scene index cannot be negative");
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
                    ? json.getAsJsonArray(section) : new JsonArray();
            for (JsonElement element : entries) {
                if (!element.isJsonObject()) continue;
                JsonObject object = element.getAsJsonObject();
                if (!object.has("uri") || object.get("uri").isJsonNull()) continue;
                String raw = object.get("uri").getAsString();
                if (raw.startsWith("data:")) continue;
                URI uri;
                try { uri = URI.create(raw); }
                catch (IllegalArgumentException e) { throw new IOException("Invalid glTF URI: " + raw, e); }
                if (uri.isAbsolute() || uri.getRawQuery() != null || uri.getRawFragment() != null)
                    throw new IOException("Only relative glTF URIs are supported: " + raw);
                String decoded = uri.getPath();
                if (decoded == null || decoded.isBlank() || decoded.startsWith("/") || decoded.contains("\\")
                        || decoded.indexOf('\0') >= 0) throw new IOException("Invalid relative glTF URI: " + raw);
                Path relative;
                try { relative = Path.of(decoded).normalize(); }
                catch (InvalidPathException e) { throw new IOException("Invalid relative glTF URI: " + raw, e); }
                if (relative.isAbsolute() || relative.startsWith(".."))
                    throw new IOException("glTF URI escapes model directory: " + raw);
                String key = relative.toString().replace('\\', '/');
                if (!copied.add(key)) continue;
                String modelPath = model.getPath();
                int slash = modelPath.lastIndexOf('/');
                String parent = slash < 0 ? "" : modelPath.substring(0, slash + 1);
                ResourceLocation dependency;
                try { dependency = ResourceLocation.fromNamespaceAndPath(model.getNamespace(), parent + key); }
                catch (RuntimeException e) { throw new IOException("Invalid glTF dependency path: " + raw, e); }
                Path target = temp.resolve(relative).normalize();
                if (!target.startsWith(temp)) throw new IOException("Invalid staged glTF path: " + raw);
                Files.createDirectories(target.getParent());
                try (InputStream in = open(dependency)) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private GltfModel convert(ResourceLocation source, de.javagl.jgltf.model.GltfModel parsed,
                              int defaultSceneIndex) throws IOException {
        List<MaterialModel> materials = parsed.getMaterialModels();
        List<String> slots = new ArrayList<>(materials.size());
        for (int i = 0; i < materials.size(); i++) {
            String name = materials.get(i).getName();
            slots.add(name == null || name.isBlank() ? "material_" + i : name);
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
                if (position == null) throw new IOException("Mesh primitive has no POSITION attribute");
                float[] positions = GltfAccessorDecoder.readPositions(position);
                AccessorModel normalAccessor = attributes.get("NORMAL");
                AccessorModel uvAccessor = attributes.get("TEXCOORD_0");
                AccessorModel jointsAccessor = attributes.get("JOINTS_0");
                AccessorModel weightsAccessor = attributes.get("WEIGHTS_0");
                if ((jointsAccessor == null) != (weightsAccessor == null))
                    throw new IOException("JOINTS_0 and WEIGHTS_0 must be provided together");
                float[] normals = normalAccessor == null ? new float[0] : GltfAccessorDecoder.readNormals(normalAccessor);
                float[] uv = uvAccessor == null ? new float[0] : GltfAccessorDecoder.readTexCoords(uvAccessor);
                int[] joints = jointsAccessor == null ? new int[0] : GltfAccessorDecoder.readJoints(jointsAccessor);
                float[] weights = weightsAccessor == null ? new float[0] : GltfAccessorDecoder.readWeights(weightsAccessor);
                int[] indices = primitive.getIndices() == null ? new int[0]
                        : GltfAccessorDecoder.readIndices(primitive.getIndices());
                MaterialModel material = primitive.getMaterialModel();
                String slot = "default";
                if (material != null) {
                    int index = materials.indexOf(material);
                    String name = material.getName();
                    slot = name == null || name.isBlank() ? "material_" + Math.max(index, 0) : name;
                }
                try {
                    primitives.add(new GltfModel.Primitive(primitive.getMode(), slot, positions, normals, uv,
                            joints, weights, indices));
                } catch (IllegalArgumentException e) {
                    throw new IOException("Invalid primitive in mesh " + mesh.getName() + ": " + e.getMessage(), e);
                }
            }
            meshes.add(new GltfModel.Mesh(mesh.getName(), primitives));
        }

        List<NodeModel> sourceNodes = parsed.getNodeModels();
        IdentityHashMap<NodeModel, Integer> nodeIndices = new IdentityHashMap<>();
        for (int i = 0; i < sourceNodes.size(); i++) nodeIndices.put(sourceNodes.get(i), i);
        List<SkinModel> sourceSkins = parsed.getSkinModels();
        IdentityHashMap<SkinModel, Integer> skinIndices = new IdentityHashMap<>();
        for (int i = 0; i < sourceSkins.size(); i++) skinIndices.put(sourceSkins.get(i), i);

        List<GltfModel.Node> nodes = new ArrayList<>(sourceNodes.size());
        for (NodeModel node : sourceNodes) {
            int parentIndex = node.getParent() == null ? -1 : requireIndex(nodeIndices, node.getParent(), "parent node");
            List<Integer> childIndices = new ArrayList<>(node.getChildren().size());
            for (NodeModel child : node.getChildren()) childIndices.add(requireIndex(nodeIndices, child, "child node"));
            List<Integer> attachedMeshes = new ArrayList<>(node.getMeshModels().size());
            for (MeshModel mesh : node.getMeshModels()) attachedMeshes.add(requireIndex(meshIndices, mesh, "mesh"));
            int skinIndex = node.getSkinModel() == null ? -1 : requireIndex(skinIndices, node.getSkinModel(), "skin");
            double[] transform = node.computeLocalTransform(null).clone();
            nodes.add(new GltfModel.Node(node.getName(), parentIndex, childIndices, attachedMeshes, skinIndex, transform));
        }

        List<GltfModel.Skin> skins = new ArrayList<>(sourceSkins.size());
        for (SkinModel skin : sourceSkins) {
            List<NodeModel> sourceJoints = skin.getJoints();
            if (sourceJoints == null || sourceJoints.isEmpty()) throw new IOException("glTF skin has no joints");
            List<Integer> jointNodeIndices = new ArrayList<>(sourceJoints.size());
            for (NodeModel joint : sourceJoints)
                jointNodeIndices.add(requireIndex(nodeIndices, joint, "skin joint node"));
            int skeletonRoot = skin.getSkeleton() == null ? -1
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
                if (inverseAccessor.getComponentType() != 5126 || inverseAccessor.isNormalized()
                        || inverseAccessor.getElementType().getNumComponents() != 16
                        || inverseAccessor.getCount() < sourceJoints.size())
                    throw new IOException("inverseBindMatrices must be a FLOAT MAT4 accessor with at least one matrix per joint");
                for (int i = 0; i < sourceJoints.size(); i++) {
                    double[] matrix = skin.getInverseBindMatrix(i, null);

                    if (matrix == null || matrix.length != 16)
                        throw new IOException("Invalid inverse bind matrix for joint " + i);

                    for (int j = 0; j < 16; j++) {
                        double value = matrix[j];

                        if (!Double.isFinite(value) || Math.abs(value) > Float.MAX_VALUE)
                            throw new IOException("Invalid inverse bind matrix value for joint " + i);

                        inverseBindMatrices[i * 16 + j] = (float) value;
                    }
                }
            }
            try {
                skins.add(new GltfModel.Skin(skin.getName(), skeletonRoot, jointNodeIndices, inverseBindMatrices));
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid glTF skin " + skin.getName() + ": " + e.getMessage(), e);
            }
        }

        // JOINTS_0 values are local indices into the skin.joints array, not node indices.
        for (GltfModel.Node node : nodes) {
            if (node.skinIndex() < 0) continue;
            int jointCount = skins.get(node.skinIndex()).jointCount();
            for (int meshIndex : node.meshIndices()) {
                for (GltfModel.Primitive primitive : meshes.get(meshIndex).primitives()) {
                    if (!primitive.isSkinned()) continue;
                    for (int joint : primitive.joints0()) if (joint >= jointCount)
                        throw new IOException("JOINTS_0 index " + joint + " exceeds skin joint count " + jointCount);
                }
            }
        }

        List<GltfModel.Scene> scenes = new ArrayList<>();
        for (SceneModel scene : parsed.getSceneModels()) {
            List<Integer> roots = new ArrayList<>(scene.getNodeModels().size());
            for (NodeModel root : scene.getNodeModels()) roots.add(requireIndex(nodeIndices, root, "scene root node"));
            scenes.add(new GltfModel.Scene(scene.getName(), roots));
        }
        if (defaultSceneIndex >= scenes.size())
            throw new IOException("glTF default scene index " + defaultSceneIndex + " is outside scene count " + scenes.size());
        return new GltfModel(source, meshes, slots, nodes, scenes, skins,
                defaultSceneIndex, parsed.getAnimationModels().size());
    }

    private static <T> int requireIndex(IdentityHashMap<T, Integer> indices, T value, String description)
            throws IOException {
        Integer index = indices.get(value);
        if (index == null) throw new IOException("Unable to resolve " + description + " reference");
        return index;
    }

    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); }
                catch (IOException ignored) { /* Best-effort cleanup. */ }
            });
        } catch (IOException ignored) { /* Best-effort cleanup. */ }
    }
}
