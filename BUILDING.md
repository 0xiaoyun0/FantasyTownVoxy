# Building and testing

This repository contains the maintained Java/resource overlays, bytecode adaptations and regression fixtures. It is a binary-overlay integration of the exact supplied NeoVoxy and VSS inputs, not a complete upstream source checkout. Third-party binaries, Minecraft libraries, worlds, credentials and private logs are not committed.

## Inputs

Use Python 3.9+ and JDK 21. The native client/server fixture scripts have been tested on Windows. Copy `build-local.example.json` to ignored `build-local.json` and replace its example paths. Environment variables with the same keys override that file.

| Key | Required input |
|---|---|
| `JAVA_HOME` | JDK 21 installation |
| `HXZ_NEOVOXY` | Exact `neovoxy-0.6.1-offline.jar`; default `reference/neovoxy-original.jar` |
| `HXZ_VSS` | Exact `vss-0.3.5-neoforge-1.21.1.jar`; default `reference/vss-original.jar` |
| `HXZ_MINECRAFT_JAR` | Installed NeoForge 21.1.250 merged client JAR with Mojmap names |
| `HXZ_NEOFORGE_API` | Optional override for the adjacent 21.1.250 universal JAR; required if kept outside the installed Maven layout |
| `HXZ_MINECRAFT_BASE` | Optional override for the installer's Mojmap `client-1.21.1-20240808.144430-srg.jar`, supplying classes not replaced by NeoForge patches |
| `HXZ_FML_JAR` | Optional override for the matching FML 4.0.44 loader JAR in the installed Maven layout |
| `HXZ_SODIUM_JAR` | Sodium NeoForge 0.8.12 for Minecraft 1.21.1 |
| `HXZ_CLASSPATH_FILE` | UTF-8 file containing the dependency classpath, separated by the OS path separator |
| `HXZ_CLIENT` | Installed launcher's version directory, for native client tests |
| `HXZ_NEOFORGE_SERVER` | Private NeoForge **21.1.250** server runtime, for libraries and an already accepted EULA |

The two upstream SHA256 values are enforced by `tools/build.py` and documented in `THIRD_PARTY_NOTICES.md`. The dependency classpath must provide NeoForge/FML, Minecraft dependencies, ASM, Mixin/MixinExtras and the upstream external libraries. It can be exported from an existing development setup; it is intentionally not a committed machine-specific path list. This release does not include an automatic Gradle dependency bootstrap.

```powershell
python tools/build.py
python tools/core_tests.py
python tools/pipeline_tests.py --artifact
python tools/ingest_backpressure_test.py
python tools/ingest_timeout_test.py --artifact
python tools/ingest_session_test.py
python tools/persistence_test.py
python tools/validate_release.py
python tools/fullpack_validation.py
```

The build compiles the Java overlays, merges the two inputs, applies `tools/Adapt.java`, retains third-party notices, and writes a deterministic ZIP plus `build-manifest.json` and `SHA256SUMS.txt` to `dist`. Minecraft, NeoForge, Sodium, Iris and RTF are not bundled. The original RTF adapter and worldgen snapshot codec are checked byte-for-byte in release validation.

## Native fixtures

Fixtures run in `build` on loopback addresses using synthetic players. They copy installed libraries/mods as required, never connect to a public server, and do not launch the source server or write its world. The full-pack fixture copies the installed modpack's configuration into an isolated game directory.

```powershell
python tools/server_smoke.py --client --plain --persistence --xaero
python tools/server_smoke.py --client --persistence --xaero
python tools/server_smoke.py --client --downloads --xaero
python tools/proxy_transfer_test.py
```

The proxy fixture additionally needs `build/proxy-download/velocity.jar` (tested with Velocity 3.4.0-SNAPSHOT build 563), NeoVelocity 1.2.6 in the source server's mods folder, both Xaero mods in the source client, and an already generated `build/client-accuracy-rtf-default/saves/hxz-accuracy-fixture`. It duplicates that actual RTF world to guarantee the same biome seed on both backends, gives each a different node identity, and switches A → B → A through one actual proxy. All generated forwarding secrets and logs stay under ignored `build`.

`tools/package_release.py` requires matching artifact hashes in seven validation records: native matrix, full-pack, Velocity, prediction accuracy, rendering quality, cold-cache progress and native prediction contracts. Native Minecraft tests require a working local graphics session; they are not headless mocks. The GPU regression harness also requires Python `glfw`, `PyOpenGL` and `numpy` and an OpenGL 4.6 implementation.

Prediction accuracy fixtures additionally use Python `nbtlib` and NeoTerraForged 0.0.6. Export the actual default and sea-level +9 presets through the mod's own data generator with `python tools/prediction_accuracy.py export`. Run `rtf-default` and `rtf-custom`, then each with `--seed=42`. `rtf-custom --legacy` is an expected accuracy failure exercising the original fast preview. Run `python tools/prediction_report.py` after those four measurements and the control. All records must match the release JAR.

All native world tests in the 0.3.0 matrix use RTF and NeoForge **21.1.250** on both sides. The launchers require that exact runtime instead of selecting the first installed NeoForge version. `rtf_fixture.py` verifies the noise generator, seed/settings and the actual RTF datapack fingerprint against the requested preset before launch; reused mismatched worlds fail instead of being silently relabelled. Standalone GPU tests use geometry fixtures, without a Minecraft world. Production installations remain unchanged.

```powershell
python tools/native_prediction_contract.py
python tools/client_smoke.py --quality-visual --rtf=default --quality-label=release030
python tools/client_smoke.py --quality-visual --quality-prediction --rtf=custom --quality-label=quality030 --quality-scene=1 --smooth-contract
python tools/client_smoke.py --prediction-progress --rtf=custom --quality-label=release030-final2
python tools/prediction_progress_report.py build/progress-rtf-custom-release030-final2/console.log --baseline build/progress-rtf-custom-baseline003-strict1/console.log --output build/validation/PREDICTION-PROGRESS-PASS.json --benchmark
python tools/quality_report.py
```

Quality reporting also requires the preserved supplied-NeoVoxy and 0.0.1 comparison captures. It verifies exact JAR hashes, native meshing/bridge execution and screenshot sky-colour diagnostics; visual limits are documented separately from sampler accuracy.

The progress fixture runs for 180 seconds at a fixed actual custom-RTF mountain. It rejects a reused work directory; choose a fresh label for each cold run. Use `HXZ_TEST_JAR` with the immutable 0.0.3 JAR and add `--progress-baseline` for the comparison. The candidate must publish complete 64×64 terrain grids at 1–2-block spacing **and actually draw them**, not merely hide coarse output. Sampling heap use and frame interval percentiles are recorded without forcing GC or raising thread counts. The package requires this exact-artifact progress record in addition to the other validation records.
