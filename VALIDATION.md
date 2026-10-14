# Final validation · 2026-10-14

Artifact: **Fantasy Town Voxy 0.0.3**, Minecraft 1.21.1 / native NeoForge, build `20261014-quality-first`.

SHA256: `5fa9b6c86a22921a5c936a38d0611fb883c723b061e1684231a3405cb0ab7230`.

The release packaging gate requires this exact hash in `FINAL-PASS.json`, `FULLPACK-PASS.json`, `VELOCITY-PASS.json`, `PREDICTION-PASS.json` and `QUALITY-PASS.json`. The distributed `validation-summary.json` combines those records. Fixture classes, source worlds, account data and raw production logs are excluded from the release.

## Quality-first rendering

The old 0.0.2 artifact fails the actual native snapshot selection contract: it accepts coarse and incomplete cached tiles. The new artifact filters both geometry and ownership before indexing, retains complete 64x64 grids with spacing of 1 or 2 blocks, leaves reusable planner samples intact and supports explicit opt-out. Rendering/performance presets preserve the preference. Sodium's actual Apply action is exercised off/on with renderer reconstruction in an RTF server session.

An actual custom RTF mountain startup produced seven coarse tiles; none were drawn with quality-first enabled, even with the old smooth-preview preference retained. Detailed predictions had not reached the GPU during this short run, so this fixture verifies suppression and continued genuine LOD rendering, not the visual quality of finished prediction. Detailed acceptance is separately checked in the native snapshot contract. This does not force full-resolution prediction across an arbitrary horizon.

## Retained regressions and RTF-only world fixtures

All nine client/server scenarios, the installed-modpack integrated world, proxy backends and new accuracy/visual fixtures use real NeoTerraForged default/custom worlds. The world preparation gate rejects a flat generator or a missing/disabled RTF data pack. GPU unit fixtures are geometry tests, not Minecraft worlds.

- Continuous small packets previously starved a second player's 128 KiB column at a 64 KiB/s global limit. The fixed one-minute test sends 10 large columns while small traffic continues; three large claimants all progress and abandoned claims expire. Total earned bandwidth is still enforced.
- Five boundary lifecycle cases verify last-event state and reset behavior. Both supplied NeoVoxy and 0.0.1 fail the remove/add and reset regressions. Native RTF tests exercise renderer reconstruction without the prior `Chunk not in map` warnings.
- The actual NeoVoxy final-depth shader compiles on the GPU and passes four projection/occlusion controls. The water callback was exercised with prediction in 0.0.2; the quality-first startup fixture now skips this extra pass while only rejected coarse tiles exist.
- The optional compatibility path (quality-first explicitly disabled in the test) uses the native packed prediction mesher to verify four corner heights, shared edges, no giant vertical walls, a captured cave, unchanged fine blocks, opt-out and liquid elevation.
- Fixed forest/mountain screenshots compare NeoVoxy, 0.0.1 and 0.0.3. Sky-colour and bright-neutral pixel diagnostics show no added gaps in those views. This is a screenshot diagnostic, not a depth or universal image-accuracy measurement. The reported white ring remains unconfirmed in these stationary fixtures; see [rendering scope and captures](RENDER_QUALITY.zh-CN.md).

## LOD persistence

The original failing regression used the real `WorldIdentifier` and a real RocksDB record: closing the store succeeded, but rejoining without a VSS identity chose a new random namespace and could not reopen the saved terrain. Stable endpoint-scoped namespaces fix the disk lookup while a separate changing epoch continues rejecting obsolete async work.

The native test starts two separate client JVMs against the same private endpoint. The first captures terrain and exits normally. The second disables vanilla ingestion, server LOD reception and prediction, then verifies the same database ID, non-air block mapping and level-4 parent LOD. Both ordinary servers without the mod and VSS-shared remote terrain are tested. Xaero's map/minimap root is also checked across the JVM restart. This tests saved data reuse rather than redownloading it.

## Xaero and actual proxy transfer

The fixture uses Velocity 3.4.0-SNAPSHOT build 563, NeoVelocity 1.2.6, Xaero World Map 1.40.16 and Xaero Minimap 25.3.13. Both backend worlds are copied from the same actual RTF saved fixture, so their biome seed is identical. The proxy address remains unchanged while the client switches **alpha → beta → alpha**.

Assertions verify that both real Xaero sessions select the expected root before map I/O, B uses a different map and LOD database, B does not read A's saved map configuration, and returning to A reads its original configuration. Minimap/waypoint and world-map roots agree. The prior VSS-to-Xaero late-producer race guard is separately exercised in the installed modpack.

Independent backends still need distinct server/node identities. Identity-free worlds at the same address and seed cannot be reliably distinguished from client-side data alone. Old ambiguous map directories are retained, not automatically assigned or merged.

## Download policy and administrator panel

Pure deterministic tests cover 17:00-inclusive/23:00-exclusive boundaries, fixed time zones, cross-midnight and all-day windows, disabled scheduling, four-player global/per-player budgets, reconnect accounting and lowered rates. A regression interleaves small acknowledgements with a large indivisible packet, checking that the large packet eventually obtains its earned credit.

The native client initially joins while downloads are blocked and receives no LOD. Real OP commands set time zone/rates and permit sharing; remote columns then arrive. Blocking again stops further columns while a previously saved terrain section remains readable. An immediate read-then-Apply operation in the actual Sodium panel changes the personal cap, disables the schedule and resumes the online session. The authoritative server values are read back. Queued UI operations remain bound to their original connection.

All 14 pages use native Sodium controls. Other checks cover command/mod-list entry points, Apply batching, preserved client drafts, precise integer entry, import confirmation, malformed config retention, stale administrator revisions and denial of non-OP changes.

## Retained crash, rendering and compatibility regressions

- Real 64/65/68/128/255-section packet serialization, splitting, decoding and persistent cache round trips; oversized and duplicate manifests rejected. The supplied older build's `Too many replacement sections: 65` exception is the regression target.
- Legacy raw/Zstd LOD records, malformed palettes, truncation and 1000 fuzz cases; read-only source RocksDB hashes unchanged. A native import remaps a deliberately different diamond-block ID and rebuilds parent LODs.
- Stalled executor, continuation and factory-failure tests; 64000 concurrent queue offers with global/per-player bounds and fair draining. A native client submits 16384 requests; actual column dispatch remains bounded, slow ticks suspend admission, and an injected ordinary failure can be reset without stopping the server.
- Real OpenGL 4.6 traversal on an RTX 3050 Laptop GPU: original and HXZ paths, standard/reverse-depth shader compilation, coarse/invalid mesh guards, hidden preparation and concurrent request limits. Controller tests cover warmup, 30/60/120 FPS target recovery, pressure and elapsed-time budgets.
- RTF adapter and worldgen snapshot codec retain upstream bytecode. Native NeoTerraForged custom-preset round trip preserves a changed sea level and the full encoded preset; altered preset/registry/backend inputs select different prediction caches.
- Installed modpack fixture exercises Sable plot rejection, Xaero late writes, RTF caches and native Sodium in an actual RTF integrated world. This is short-duration compatibility coverage, not every aircraft, structure, shader or world-generation combination.
- ZIP integrity, one `voxy` mod entrypoint, required mixin class/resource presence, no test classes in the JAR, matching source resource bytes and optional client/server installation combinations are checked.

## Prediction stability and terrain accuracy

See [the measured report](PREDICTION_ACCURACY.zh-CN.md). Four native fixtures compare predictions to independently generated FULL chunks using two seeds and actual default/custom RTF generation. Preview and refinement, plus land/water/high-ground categories, each have a 90% minimum threshold; the report states height tolerances and remaining errors. The actual Java/RTF preview path is exercised. The RTF legacy-mode control intentionally fails the threshold. A second-seed frozen-ocean fixture previously scored 88.44% before the missing Java surface extensions were repaired. Packaging requires the exact-artifact prediction record.

## Reproduction

```powershell
python tools/build.py
python tools/validate_release.py
python tools/fullpack_validation.py
python tools/proxy_transfer_test.py
```

Next complete the four RTF prediction fixtures and the expected-failure RTF legacy control described in [the accuracy report](PREDICTION_ACCURACY.zh-CN.md#复现), then run `python tools/prediction_report.py`, the RTF quality scenarios in BUILDING.md, `python tools/quality_report.py` and `python tools/package_release.py`. Packaging fails if any of the five validation records is absent or belongs to a different JAR.

The native matrix has nine scenarios: feature/UI/import tests, request flood with aggressive existing configuration, administrator sharing toggle, plain server with original rendering, client without this mod, sharing disabled without Iris, plain two-JVM persistence with Xaero, shared two-JVM persistence with Xaero, and live download policy with Xaero. See `BUILDING.md` for local prerequisites.

All fixtures use private loopback endpoints and synthetic players. No production server, production save or player account is used. Cooperative per-tick budgets cannot preempt an already running third-party codec or native call; 30 FPS is a scheduling target, not a global guarantee. No long-running cloud-server certification is claimed.
