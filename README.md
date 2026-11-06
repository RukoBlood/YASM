# YASM

**Yet Another ... Mod** — a Minecraft 1.21.1 / NeoForge project skeleton.

## Development environment

- JDK 21
- Gradle 8.14.3 (or a compatible Gradle 8.x release)
- Minecraft 1.21.1
- NeoForge 21.1.219

The project uses the NeoForge ModDevGradle plugin and jGLTF 3.0.1.

## Importing into an IDE

Open this folder as a Gradle project. The first Gradle sync downloads Minecraft, NeoForge and dependencies, and may take a while.

If you do not have a Gradle wrapper installed, run the system Gradle command once:

```shell
gradle wrapper --gradle-version 8.14.3
```

Then use `./gradlew` (Linux/macOS) or `gradlew.bat` (Windows).

## Common tasks

```shell
gradle runClient
gradle runServer
gradle build
gradle printRuntimeDependencies
```

`printRuntimeDependencies` prints resolved runtime artifacts for manual license review. It does **not** automatically determine their licenses.

## Project structure

```text
ru.progrevgoev.yasm
├── Main.java
├── registry/       Deferred registries
├── entity/         Base entity and character implementations
├── ai/             Ordinary companion AI goals
├── scene/          Scene state machine and character-specific scenes
├── render/         jGLTF adapter and custom renderer stubs
├── interaction/    Server-authoritative interaction entry points
├── gui/            Client GUI stubs
├── network/        NeoForge payload bootstrap stubs
├── interfaces/     Shared contracts
├── client/         Client-only event handlers and renderer stubs
├── server/         Server-side hooks
└── util/           Shared helpers
```

## Model resource convention

Use `.gltf` + `.bin` with external texture files. Keep texture URIs relative to the model file. A typical resource layout is:

```text
assets/yasm/models/girls/example_girl/
├── model.gltf
├── model.bin
├── config.json
└── textures/
    ├── body.png
    └── clothes.png
```

The included loader class is only an adapter placeholder; actual glTF loading, accessor decoding, skinning and GPU upload still need implementation.

## Licensing policy

YASM source code is licensed under Apache-2.0. Prefer MIT, BSD-2-Clause, BSD-3-Clause, ISC or Apache-2.0 dependencies. Review the complete transitive dependency graph before shipping. See `THIRD_PARTY_LICENSES.md`.

Minecraft/NeoForge development also involves upstream assets, mappings and platform licensing terms; the mod's Apache license does not relicense those materials.
