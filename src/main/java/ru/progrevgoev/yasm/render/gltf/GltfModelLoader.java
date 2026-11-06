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
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Loads glTF 2.0 from Minecraft resources and converts mesh data to YASM types. */

public final class GltfModelLoader {
    private static final int UNSIGNED_BYTE = 5121, UNSIGNED_SHORT = 5123, UNSIGNED_INT = 5125;
    private final ResourceManager resources;

    public GltfModelLoader(ResourceManager resources) { this.resources = Objects.requireNonNull(resources); }

    public GltfModel load(ResourceLocation modelLocation) throws IOException {
        Resource modelResource = resources.getResource(modelLocation).orElseThrow(() -> new IOException("glTF resource not found: " + modelLocation));
        Path temp = Files.createTempDirectory("yasm-gltf-");

        try {
            JsonObject json;
            try (InputStream in = modelResource.open();
                 InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            }
            stageReferences(json, modelLocation, temp);
            Path stagedModel = temp.resolve("model.gltf");
            try (InputStream in = open(modelLocation)) {
                Files.copy(in, stagedModel, StandardCopyOption.REPLACE_EXISTING);
            }
            de.javagl.jgltf.model.GltfModel parsed = new GltfModelReader().read(stagedModel);
            return convert(modelLocation, parsed);
        } catch (RuntimeException e) {
            throw new IOException("Failed to parse glTF model " + modelLocation, e);
        } finally {
            deleteTree(temp);
        }
    }

    private InputStream open(ResourceLocation location) throws IOException{
        Resource resource = resources.getResource(location).orElseThrow(() -> new IOException("Missing glTF dependency: " + location));
        return resource.open();
    }

    /* jGLTF resolves external references through filesystem URIs. Stage only
       the model's declared relative buffer/image references for that reader. */
    private void stageReferences(JsonObject json, ResourceLocation model, Path temp) throws IOException {
        Set<String> copied = new HashSet<>();
        for (String section : List.of("buffers", "images")) {
            JsonArray entries = json.has(section) && json.get(section).isJsonArray() ? json.getAsJsonArray(section) : new JsonArray();

            for (JsonElement element : entries) {
                if (element.isJsonObject()) {
                    JsonObject object = element.getAsJsonObject();
                    if (object.has("uri") && !object.get("uri").isJsonNull()) {
                        String raw = object.get("uri").getAsString();
                        if (!raw.startsWith("data:")) {
                            URI uri;

                            try {
                                uri = URI.create(raw);
                            } catch (IllegalArgumentException e) {
                                throw new IOException("Invalid glTF URI: " + raw, e);
                            }

                            if (uri.isAbsolute() || uri.getRawQuery() != null || uri.getRawFragment() != null)
                                throw new IOException("Only relative glTF URIs are supported: " + raw);

                            String decoded = uri.getPath();
                            if (decoded == null || decoded.isBlank() || decoded.startsWith("/") || decoded.contains("\\"))
                                throw new IOException("Invalid relative glTF URI: " + raw);

                            Path relative = Path.of(decoded).normalize();
                            if (relative.isAbsolute() || relative.startsWith(".."))
                                throw new IOException("glTF URI escapes model directory: " + raw);

                            String key = relative.toString().replace('\\', '/');
                            if (copied.add(key)) {
                                String modelPath = model.getPath();
                                int slash = modelPath.lastIndexOf('/');
                                String parent = slash < 0 ? "" : modelPath.substring(0, slash + 1);
                                ResourceLocation dependency = ResourceLocation.fromNamespaceAndPath(model.getNamespace(), parent + key);

                                Path target = temp.resolve(relative).normalize();
                                if (!target.startsWith(temp)) throw new IOException("Invalid staged glTF path: " + raw);

                                Files.createDirectories(target.getParent());
                                try (InputStream in = open(dependency)) {
                                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private GltfModel convert(ResourceLocation source, de.javagl.jgltf.model.GltfModel parsed) throws IOException {
        List<MaterialModel> materials = parsed.getMaterialModels();
        List<String> slots = new ArrayList<>();
        for (int i = 0; i < materials.size(); i++) {
            String name = materials.get(i).getName();
            slots.add(name == null || name.isBlank() ? "material_" + i : name);
        }

        List<GltfModel.Mesh> meshes = new ArrayList<>();
        for (MeshModel mesh : parsed.getMeshModels()) {
            List<GltfModel.Primitive> primitives = new ArrayList<>();
            for (Object item : mesh.getMeshPrimitiveModels()) {
                MeshPrimitiveModel primitive = (MeshPrimitiveModel) item;
                Map<?, ?> attributes = primitive.getAttributes();
                AccessorModel position = getAccessor(attributes, "POSITION");
                if (position == null)
                    throw new IOException("Mesh primitive has no POSITION attribute");

                float[] positions = readFloat(position, "POSITION");
                float[] normals = readOptionalFloat(attributes, "NORMAL");
                float[] uv = readOptionalFloat(attributes, "TEXCOORD_0");
                int[] indices = primitive.getIndices() == null ? new int[0] : readIndices(primitive.getIndices());
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
        return new GltfModel(source, meshes, slots, parsed.getNodeModels().size(), parsed.getSkinModels().size(), parsed.getAnimationModels().size());
    }

    private static AccessorModel getAccessor(Map<?, ?> attributes, String semantic) {
        Object value = attributes.get(semantic);
        return value instanceof AccessorModel accessor ? accessor : null;
    }

    private static float[] readOptionalFloat(Map<?, ?> attributes, String semantic) throws IOException {
        AccessorModel accessor = getAccessor(attributes, semantic);
        return accessor == null ? new float[0] : readFloat(accessor, semantic);
    }

    private static float[] readFloat(AccessorModel accessor, String semantic) throws IOException {
        AccessorData data = accessor.getAccessorData();
        if (!(data instanceof AccessorFloatData floats))
            throw new IOException(semantic + " must use FLOAT components in this stage; componentType=" + accessor.getComponentType());

        float[] values = new float[floats.getTotalNumComponents()];
        for (int i = 0; i < values.length; i++)
            values[i] = floats.get(i);

        return values;
    }

    private static int[] readIndices(AccessorModel accessor) throws IOException {
        AccessorData data = accessor.getAccessorData();
        if (data == null)
            throw new IOException("Index accessor has no decoded data");

        ByteBuffer buffer = data.createByteBuffer().duplicate().order(ByteOrder.nativeOrder());
        int[] result = new int[accessor.getCount()];

        switch (accessor.getComponentType()) {
            case UNSIGNED_BYTE -> { for (int i = 0; i < result.length; i++) result[i] = Byte.toUnsignedInt(buffer.get()); }
            case UNSIGNED_SHORT -> { for (int i = 0; i < result.length; i++) result[i] = Short.toUnsignedInt(buffer.getShort()); }
            case UNSIGNED_INT -> {
                for (int i = 0; i < result.length; i++) {
                    int value = buffer.getInt();
                    if (value < 0) throw new IOException("Index exceeds Java signed int range");
                    result[i] = value;
                }
            }
            default -> throw new IOException("Unsupported index component type: " + accessor.getComponentType());
        }
        return result;
    }

    private static void deleteTree(Path root) {
        if (root != null && Files.exists(root)) {
            try (var paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
            } catch (IOException ignored) {
            }
        }
    }
}
