# Input provenance

This integration uses the exact binaries supplied by the user:

- NeoVoxy `neovoxy-0.6.1-offline.jar`: SHA256 `1ba68d6b19c8020d16f93f2ddfe184f23edb7fc138d692d732ee13ecd1e0071c`.
- VSS `vss-0.3.5-neoforge-1.21.1.jar`: SHA256 `6abc84e84cc40030d49f974f3e40055020b5121bcc1a72193cef7a69677a7559`.

The NeoVoxy metadata declares All Rights Reserved and credits Cortex. Its original classes, resources and library notices remain bundled, except for the explicitly compiled overlays and metadata adaptations listed in the release `build-manifest.json`. The original NeoVoxy metadata is retained at `licenses/NeoVoxy-original-metadata.toml` inside the output.

VSS sources used for adaptation are the previously retrieved `wish131400-voxy-server-side-neoforge-1c9fd92` source tree. Its MIT license, including copyright notices, is bundled as `licenses/VSS-MIT.txt`. Integration does not change the original components' licenses.

The Fantasy Town Voxy 0.0.1 overlays also use decompiled classes from the exact supplied binaries. `build-manifest.json` lists compiled overlays and explicit bytecode adaptations for resource caps, tall-column serialization and client integration. Legacy import reads the supplied Voxy/NeoVoxy SaveLoadSystem3 v0 record layout and uses current mapper/update APIs to rebuild data. The original RTF adapter and worldgen snapshot codec bytecode remain unchanged.

Sodium, Iris, NeoForge, Minecraft and RTF are external runtime components. They are not merged into this mod. Test fixture classes are not included in the release JAR.

The repository is not relicensed as MIT as a whole. The original component notices and license restrictions continue to apply. Only the VSS license in `licenses/VSS-MIT.txt` states MIT terms for its covered material.
