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
            return convert(modelLocation, parsed);
        } catch (JsonParseException | IllegalStateException | ClassCastException e) {
            throw new IOException("Invalid glTF JSON resource " + modelLocation, e);
        } catch (RuntimeException e) {
            throw new IOException("Failed to load glTF model " + modelLocation, e);
        } finally {
            deleteTree(temp);
        }
    }

    private InputStream open(ResourceLocation location) throws IOException {
        Resource resource = resources.getResource(location)
                .orElseThrow(() -> new IOException("Missing glTF resource: " + location));
        return resource.open();
    }

    /** jGLTF resolves external URIs as filesystem paths, so copy only declared relative assets. */
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
                try {
                    uri = URI.create(raw);
                } catch (IllegalArgumentException e) {
                    throw new IOException("Invalid glTF URI: " + raw, e);
                }
                if (uri.isAbsolute() || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                    throw new IOException("Only relative glTF URIs are supported: " + raw);
                }

                String decoded = uri.getPath();
                if (decoded == null || decoded.isBlank() || decoded.startsWith("/") || decoded.contains("\\")
                        || decoded.indexOf('\0') >= 0) {
                    throw new IOException("Invalid relative glTF URI: " + raw);
                }
                Path relative;
                try {
                    relative = Path.of(decoded).normalize();
                } catch (InvalidPathException e) {
                    throw new IOException("Invalid relative glTF URI: " + raw, e);
                }
                if (relative.isAbsolute() || relative.startsWith("..")) {
                    throw new IOException("glTF URI escapes model directory: " + raw);
                }

                String key = relative.toString().replace('\\', '/');
                if (!copied.add(key)) continue;

                String modelPath = model.getPath();
                int slash = modelPath.lastIndexOf('/');
                String parent = slash < 0 ? "" : modelPath.substring(0, slash + 1);
                ResourceLocation dependency;
                try {
                    dependency = ResourceLocation.fromNamespaceAndPath(model.getNamespace(), parent + key);
                } catch (RuntimeException e) {
                    throw new IOException("Invalid glTF dependency path: " + raw, e);
                }

                Path target = temp.resolve(relative).normalize();
                if (!target.startsWith(temp)) throw new IOException("Invalid staged glTF path: " + raw);
                Files.createDirectories(target.getParent());
                try (InputStream in = open(dependency)) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private GltfModel convert(ResourceLocation source, de.javagl.jgltf.model.GltfModel parsed) throws IOException {
        List<MaterialModel> materials = parsed.getMaterialModels();
        List<String> slots = new ArrayList<>(materials.size());
        for (int i = 0; i < materials.size(); i++) {
            String name = materials.get(i).getName();
            slots.add(name == null || name.isBlank() ? "material_" + i : name);
        }

        List<GltfModel.Mesh> meshes = new ArrayList<>();
        for (MeshModel mesh : parsed.getMeshModels()) {
            List<GltfModel.Primitive> primitives = new ArrayList<>();
            for (MeshPrimitiveModel primitive : mesh.getMeshPrimitiveModels()) {
                Map<String, AccessorModel> attributes = primitive.getAttributes();
                AccessorModel position = attributes.get("POSITION");
                if (position == null) throw new IOException("Mesh primitive has no POSITION attribute");

                float[] positions = GltfAccessorDecoder.readPositions(position);
                AccessorModel normalAccessor = attributes.get("NORMAL");
                AccessorModel uvAccessor = attributes.get("TEXCOORD_0");
                float[] normals = normalAccessor == null ? new float[0] : GltfAccessorDecoder.readNormals(normalAccessor);
                float[] uv = uvAccessor == null ? new float[0] : GltfAccessorDecoder.readTexCoords(uvAccessor);
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
                    primitives.add(new GltfModel.Primitive(primitive.getMode(), slot, positions, normals, uv, indices));
                } catch (IllegalArgumentException e) {
                    throw new IOException("Invalid primitive in mesh " + mesh.getName() + ": " + e.getMessage(), e);
                }
            }
            meshes.add(new GltfModel.Mesh(mesh.getName(), primitives));
        }

        return new GltfModel(source, meshes, slots, parsed.getNodeModels().size(),
                parsed.getSkinModels().size(), parsed.getAnimationModels().size());
    }

    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort cleanup; preserve the original loading exception.
                }
            });
        } catch (IOException ignored) {
            // Best-effort cleanup; preserve the original loading exception.
        }
    }
}
