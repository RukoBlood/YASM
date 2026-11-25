# YASM — GLTF Module Implementation Plan

**Status:** In Progress  
**Target:** Minecraft 1.21.1, NeoForge, Java 21  
**Package:** `ru.progrevgoev.yasm.render.gltf`  
**Purpose:** Technical roadmap for implementing the glTF 2.0 model loading, animation, material, visibility, and rendering systems.

---

## 1. Project Goals

Implement a custom glTF 2.0 pipeline for YASM, designed primarily for character models created in Blender.

The system must support:

- Loading character models from glTF 2.0.
- External binary buffers (`.bin`).
- External PNG textures.
- Multiple materials per model.
- Skeletal animations.
- Hiding and showing models, nodes, and materials.
- Custom PS1-style rendering.

The renderer is intentionally not physically based. The goal is a retro PS1-inspired visual style.

### 1.1. Technical Constraints

- Minecraft 1.21.1.
- NeoForge.
- Java 21.
- glTF 2.0.
- jGLTF is used for glTF parsing only.
- Rendering is implemented independently by YASM.
- The loader must remain independent of Minecraft rendering APIs.
- `GltfModel` must remain renderer-independent.
- Prefer permissively licensed dependencies (MIT, BSD, Apache 2.0, ISC).
- Avoid unnecessary rewrites of already working code.

---

## 2. Current Implementation Status

The following functionality has already been implemented.

### 2.1. Model Loading

- [x] Parse JSON `.gltf` files.
- [x] Load external binary buffers.
- [x] Load `data:` URIs.
- [x] Decode vertex positions (`POSITION`).
- [x] Decode normals (`NORMAL`).
- [x] Decode texture coordinates (`TEXCOORD_0`).
- [x] Decode vertex indices.
- [x] Support common glTF accessor component types and normalized attributes.
- [x] Validate accessor formats and indices.

**Not implemented:** GLB support.

GLB is not currently required. External `.gltf`, `.bin`, and PNG files are sufficient for the intended Blender workflow.

### 2.2. Scene Graph

- [x] Read scenes.
- [x] Read nodes.
- [x] Read parent-child relationships.
- [x] Read node mesh references.
- [x] Read node skin references.
- [x] Read node local transforms.
- [x] Read default scene index.
- [x] Preserve TRS and matrix-based node transforms.

### 2.3. Skeletal Data

- [x] Read skins.
- [x] Read joint node indices.
- [x] Read skeleton root node.
- [x] Read inverse bind matrices.
- [x] Decode `JOINTS_0`.
- [x] Decode `WEIGHTS_0`.
- [x] Validate joint indices and vertex attribute counts.

### 2.4. Animation Data

- [x] Read animation clips.
- [x] Read animation channels.
- [x] Read animation samplers.
- [x] Read animation keyframe timestamps.
- [x] Support `translation`.
- [x] Support `rotation`.
- [x] Support `scale`.
- [x] Support `weights` channel parsing where applicable.
- [x] Support `STEP` interpolation.
- [x] Support `LINEAR` interpolation.
- [x] Support `CUBICSPLINE` interpolation.
- [x] Implement linear quaternion interpolation using SLERP.
- [x] Implement cubic Hermite interpolation.

**Important:** Animation data can currently be loaded and sampled, but the complete skeletal animation pipeline is not implemented.

### 2.5. Existing Supporting Classes

The repository currently contains:

- `GltfModel`
- `GltfModelLoader`
- `GltfAccessorDecoder`
- `GltfAnimationSampler`
- `GltfMaterial`
- `YasmModelRenderer`
- `AnimationController`
- `MaterialOverrides`

The renderer and animation controller are currently incomplete.

---

## 3. Required Implementation

## Phase 1 — Material System

**Priority:** High  
**Status:** Implemented (parsing complete; renderer integration pending)

Create a material representation suitable for the PS1-style renderer.

### Requirements

- [x] Create a `GltfMaterial` data structure.
- [x] Read material definitions from glTF.
- [x] Read material names.
- [x] Read base color texture references.
- [x] Read base color factors.
- [x] Read alpha mode.
- [x] Read alpha cutoff.
- [x] Read double-sided setting.
- [x] Associate materials with mesh primitives.
- [x] Preserve material indices or stable identifiers.
- [x] Handle primitives without an explicitly assigned material.

### Supported Material Properties

| Property | Required | Description |
|---|---|---|
| Base Color Texture | Yes | Main PNG texture |
| Base Color Factor | Yes | RGBA multiplier |
| Alpha Mode | Yes | OPAQUE, MASK, BLEND |
| Alpha Cutoff | Yes | Used by MASK |
| Double Sided | Yes | Disable back-face culling when needed |

### Out of Scope

Do not implement PBR material features:

- Metallic factor.
- Roughness factor.
- Metallic-roughness textures.
- Normal maps.
- Ambient occlusion maps.
- Emissive maps.
- Image-based lighting.
- Environment reflections.

### Acceptance Criteria

- A model with multiple materials loads correctly.
- Every primitive references the correct material.
- Material properties are available to the renderer.
- Missing optional material properties use sensible glTF defaults.

---

## Phase 2 — Texture Loading

**Priority:** High  
**Status:** Not implemented

Implement loading and management of textures referenced by glTF materials.

### Requirements

- [ ] Read glTF image references.
- [ ] Support external PNG files.
- [ ] Support image data URIs if practical.
- [ ] Resolve texture references through glTF texture and image objects.
- [ ] Load PNG image data.
- [ ] Associate loaded textures with materials.
- [ ] Handle texture resource lifetime.
- [ ] Avoid duplicate GPU uploads for shared textures.
- [ ] Handle missing or invalid texture references gracefully.

### Rendering Requirements

- [ ] Support nearest-neighbor filtering.
- [ ] Allow texture filtering settings to be configured.
- [ ] Ensure textures can be used by the custom renderer.

**Note:** Texture loading and GPU texture creation should remain separate responsibilities. The loader should not depend on Minecraft's rendering APIs.

### Acceptance Criteria

- External PNG textures load successfully.
- Multiple materials can reference the same texture.
- Texture resources are released appropriately.
- Textures can be passed to the renderer without coupling the loader to GPU APIs.

---

## Phase 3 — Skeletal Pose Evaluation

**Priority:** High  
**Status:** Not implemented

Implement runtime evaluation of skeletal animation.

The existing loader and sampler provide the necessary animation data. The missing part is converting sampled animation values into a complete animated skeleton pose.

### Requirements

- [ ] Create a runtime pose representation.
- [ ] Initialize the pose from the model's default node transforms.
- [ ] Sample animation channels at the current animation time.
- [ ] Apply sampled translation values.
- [ ] Apply sampled rotation values.
- [ ] Apply sampled scale values.
- [ ] Recalculate affected local transforms.
- [ ] Calculate global node transforms.
- [ ] Calculate global joint transforms.
- [ ] Support multiple independent skeleton instances.
- [ ] Avoid modifying the original immutable `GltfModel` data.

### Matrix Calculations

For each joint, calculate its skinning matrix using the joint's global transform and its inverse bind matrix.

Conceptually:

`SkinningMatrix = GlobalJointTransform * InverseBindMatrix`

The exact matrix conventions and multiplication order must be consistent throughout the implementation.

### Acceptance Criteria

- An animation can modify the runtime pose.
- Parent-child transformations propagate correctly.
- Joint global transforms are calculated correctly.
- The original model data remains unchanged.
- Multiple instances of the same model can have different poses.

---

## Phase 4 — GPU Skinning

**Priority:** High  
**Status:** Not implemented

Implement skeletal deformation of mesh geometry.

### Requirements

- [ ] Provide skinning matrices to the renderer.
- [ ] Use `JOINTS_0` to identify influencing joints.
- [ ] Use `WEIGHTS_0` to determine joint influence.
- [ ] Apply skinning to vertex positions.
- [ ] Apply appropriate transformations to vertex normals.
- [ ] Support the supported vertex joint influence count.
- [ ] Handle unskinned primitives.
- [ ] Handle vertices with zero total skinning weight safely.

### Implementation Notes

GPU skinning is the intended final solution.

CPU skinning may be implemented temporarily for testing or debugging, but should not become a permanent requirement unless there is a clear reason.

### Acceptance Criteria

- Skeletal animation visibly deforms the model.
- Vertices follow the correct joints.
- Meshes with multiple primitives can be skinned.
- Unskinned geometry still renders normally.

---

## Phase 5 — Visibility System

**Priority:** Medium  
**Status:** Partially implemented

The repository already contains `MaterialOverrides`, which provides a basic visibility override map.

It must be integrated into the model instance and renderer.

### Required Visibility Levels

#### Model Visibility

- [ ] Hide the entire model.
- [ ] Show the entire model.
- [ ] Preserve other visibility overrides when toggling model visibility.

#### Node Visibility

- [ ] Hide individual nodes.
- [ ] Show individual nodes.
- [ ] Handle child nodes appropriately.
- [ ] Allow individual body parts or clothing parts to be hidden independently.

#### Material Visibility

- [ ] Hide all primitives using a specified material.
- [ ] Show all primitives using a specified material.
- [ ] Ensure material visibility does not replace node visibility.

### Important Design Requirement

Node visibility and material visibility must be independent.

For example, hiding a material may hide several different body parts that share the same material. Hiding a node should only affect the selected node and its intended subtree.

The implementation must define and consistently apply visibility inheritance rules.

### Acceptance Criteria

- Entire models can be hidden and shown.
- Individual model parts can be hidden and shown.
- Materials can be hidden and shown.
- Multiple visibility overrides can coexist.
- Visibility changes do not modify the original model data.

---

## Phase 6 — PS1-Style Renderer

**Priority:** High  
**Status:** Not implemented

Implement the custom renderer responsible for GPU resource management and draw submission.

`YasmModelRenderer` should own GPU upload and rendering, not glTF parsing.

### Requirements

#### Geometry

- [ ] Upload vertex positions.
- [ ] Upload normals.
- [ ] Upload UV coordinates.
- [ ] Upload indices.
- [ ] Support multiple primitives.
- [ ] Support multiple materials.
- [ ] Manage GPU buffer lifetime.

#### Materials and Textures

- [ ] Bind the correct material for each primitive.
- [ ] Bind the correct texture.
- [ ] Apply base color factor.
- [ ] Handle opaque materials.
- [ ] Handle masked materials.
- [ ] Handle blended materials.
- [ ] Respect double-sided materials.

#### Animation

- [ ] Receive skinning matrices.
- [ ] Apply GPU skinning.
- [ ] Render animated geometry.

#### Visibility

- [ ] Respect model visibility.
- [ ] Respect node visibility.
- [ ] Respect material visibility.

#### PS1-Style Features

- [ ] Nearest-neighbor texture filtering.
- [ ] Optional affine texture mapping.
- [ ] Retro-style rendering configuration.
- [ ] Separate opaque and transparent rendering passes.

### Acceptance Criteria

- Static models render correctly.
- Models with multiple materials render correctly.
- Textures render correctly.
- Animated models render correctly.
- Visibility overrides work correctly.
- Renderer does not perform glTF parsing.

---

## Phase 7 — Animation Controller

**Priority:** Medium  
**Status:** Stub exists

Implement runtime animation playback.

The initial implementation should remain deliberately simple.

### Required Features

- [ ] Select an animation clip.
- [ ] Start playback.
- [ ] Stop playback.
- [ ] Pause playback.
- [ ] Resume playback.
- [ ] Set playback time.
- [ ] Set playback speed.
- [ ] Support looping.
- [ ] Support non-looping playback.
- [ ] Detect animation completion.
- [ ] Update the runtime skeleton pose.

### Out of Scope for Initial Implementation

- Complex animation state machines.
- Blend trees.
- Multi-layer animation blending.
- Advanced cross-fading.
- Procedural animation systems.

Cross-fading can be added later if gameplay requires it.

### Acceptance Criteria

- A character can play an animation.
- An animation can be stopped and restarted.
- Looping works correctly.
- Playback speed can be changed.
- Animation updates the runtime skeleton pose.

---

## 4. Explicitly Out of Scope

The following features are not planned.

| Feature | Decision |
|---|---|
| Shape Keys | Not required |
| Morph Targets | Not required |
| Facial blend shapes | Not required |
| PBR rendering | Not required |
| Metallic/Roughness | Not required |
| Normal Mapping | Not required |
| Ambient Occlusion | Not required |
| Emissive Mapping | Not required |
| Environment Mapping | Not required |
| Advanced Animation Blending | Not required initially |
| GLB Support | Optional future feature |

Do not implement these features unless the project requirements change.

---

## 5. Recommended Implementation Order

The recommended development sequence is:

1. Material data structures and glTF material parsing.
2. Texture loading and resource management.
3. Runtime skeletal pose evaluation.
4. Skinning matrix calculation.
5. GPU skinning.
6. Model, node, and material visibility.
7. PS1-style renderer integration.
8. Animation controller.
9. Integration testing and optimization.

The order may be adjusted when architectural dependencies require it, but each phase should remain independently understandable and testable.

---

## 6. Architectural Rules

All future implementations must follow these rules.

### Loader

`GltfModelLoader` is responsible for reading glTF files and converting them into YASM model data.

It must not:

- Create GPU buffers.
- Create Minecraft render resources.
- Submit draw calls.
- Manage animation playback.

### Model Data

`GltfModel` and related data structures represent loaded model data.

They should remain independent of runtime rendering state.

Avoid storing mutable per-character animation state inside shared model data.

### Runtime Instance

A runtime model instance should own mutable state such as:

- Current animation.
- Current animation time.
- Runtime node pose.
- Skinning matrices.
- Visibility overrides.

Different characters using the same model must be able to animate independently.

### Animation System

The animation system evaluates animation data and updates runtime pose state.

It should not be responsible for GPU resource management.

### Renderer

`YasmModelRenderer` owns GPU resources and draw submission.

It consumes model data, runtime pose, material data, textures, and visibility state.

It should not parse glTF files.

### Code Quality

- Prefer small, focused changes.
- Do not rewrite working systems without a concrete reason.
- Preserve existing public APIs unless changes are necessary.
- Validate array sizes, indices, and matrix dimensions.
- Handle malformed model data gracefully.
- Avoid introducing unnecessary dependencies.
- Do not claim a build succeeds unless it has actually been tested.

---

## 7. Integration Testing Checklist

A complete implementation must be tested using Blender-exported models.

### Model Loading

- [ ] Simple static character.
- [ ] Character with multiple meshes.
- [ ] Character with multiple materials.
- [ ] Character with external PNG textures.
- [ ] Character with external `.bin` buffers.
- [ ] Character with multiple scenes or nodes.

### Materials

- [ ] Multiple materials render correctly.
- [ ] Shared textures work.
- [ ] Base color factors work.
- [ ] OPAQUE materials work.
- [ ] MASK materials work.
- [ ] BLEND materials work.
- [ ] Double-sided materials work.

### Animation

- [ ] Basic skeletal animation.
- [ ] Multiple joints.
- [ ] Parent-child joint hierarchy.
- [ ] Translation animation.
- [ ] Rotation animation.
- [ ] Scale animation.
- [ ] STEP interpolation.
- [ ] LINEAR interpolation.
- [ ] CUBICSPLINE interpolation.
- [ ] Looping playback.
- [ ] Multiple instances of the same animated model.

### Visibility

- [ ] Hide entire model.
- [ ] Hide individual node.
- [ ] Hide node subtree.
- [ ] Hide individual material.
- [ ] Restore visibility.
- [ ] Combine multiple visibility overrides.

### Rendering

- [ ] Static model rendering.
- [ ] Animated model rendering.
- [ ] Multiple materials.
- [ ] Multiple textures.
- [ ] Transparent materials.
- [ ] Nearest-neighbor filtering.
- [ ] Affine texture mapping.

---

## 8. Definition of Done

The YASM glTF module is considered functionally complete when:

1. Blender-exported glTF character models load successfully.
2. External PNG textures load successfully.
3. Models with multiple materials render correctly.
4. Skeletal animations can be played at runtime.
5. Skinning correctly deforms the character geometry.
6. Entire models can be hidden and shown.
7. Individual nodes and materials can be hidden and shown.
8. The renderer supports the intended PS1-style visual features.
9. Multiple instances of the same model can maintain independent animation and visibility state.
10. The implementation works in the target Minecraft and NeoForge environment.

---

## 9. Implementation Log

### 2026-11-26 — Phase 1: Material System

Implemented material parsing and wired it into the immutable model data.

- Added `GltfMaterial` with name, base color factor, base color texture index and texcoord set, alpha mode, alpha cutoff, and double-sided flag.
- Extended `GltfModel` with a materials list and `Primitive.materialIndex()`; the legacy constructors are preserved. Material indices are validated against the material list.
- Updated `GltfModelLoader` to read `PbrMaterialModel` data:
  - base color factor defaults to `1, 1, 1, 1`;
  - base color texture index defaults to `-1` when absent;
  - only `TEXCOORD_0` is accepted for the base color texture;
  - alpha mode defaults to `OPAQUE`, alpha cutoff defaults to `0.5`;
  - `doubleSided` defaults to `false`.
- Primitives without a material keep `materialIndex = -1` and the `"default"` slot.
- Fixed and applied `gltf-phase1-material-system.patch`: the loader hunks had no context lines and stale offsets, so `git apply` rejected them. The patch now applies cleanly.
- Verified with `gradlew compileJava` — build successful.

**Not done in this phase:** texture bytes are not loaded yet (Phase 2); the renderer does not consume material data yet (Phase 6); no Blender-exported model has been tested yet.

---

**Development principle:** Implement only what YASM needs. The goal is a reliable, focused glTF pipeline for character models and basic skeletal animations, not a complete general-purpose glTF engine.
