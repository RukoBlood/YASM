# Third-party licenses

This file is a starting inventory, not a substitute for reviewing the exact artifacts shipped in a release.

| Component                       |               Version | Intended use                  | License / status               | Notes                                                                                                  |
|---------------------------------|----------------------:|-------------------------------|--------------------------------|--------------------------------------------------------------------------------------------------------|
| NeoForge                        |              21.1.219 | Minecraft mod loader/API      | LGPL-2.1-only (upstream)       | API usage does not automatically relicense YASM; review distribution obligations and upstream notices. |
| jGLTF (`de.javagl:jgltf-model`) |                 3.0.1 | glTF 2.0 parsing/model access | MIT (project repository)       | Transitive dependencies must also be audited.                                                          |
| Jackson                         | Resolved transitively | JSON parsing used by jGLTF    | Apache-2.0 (upstream projects) | Record exact resolved versions before release.                                                         |

## Release checklist

- [ ] Run `gradle printRuntimeDependencies`.
- [ ] Inspect the license of every direct and transitive runtime dependency.
- [ ] Check whether any dependency bundles additional libraries.
- [ ] Include required LICENSE / NOTICE texts in the distributed artifact or accompanying notices.
- [ ] Review Minecraft mappings, assets and NeoForge terms separately.
- [ ] Re-run the audit whenever dependencies or versions change.

The project's preference is permissive licensing (Apache-2.0, MIT, BSD and ISC). GPL/AGPL dependencies are not accepted by project policy without an explicit maintainer decision.
