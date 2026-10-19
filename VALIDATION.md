# Fantasy Town Voxy 0.3.0 validation and release gates

Updated: **2026-10-19**. Target: **Minecraft 1.21.1 / NeoForge 21.1.250 / Java 21**. Build: `20261019-detail-throughput`.

Artifact: `fantasy-town-voxy-0.3.0-neoforge-mc1.21.1.jar`.

SHA-256: `d1962b29b19b0452a7af4fef2f0350c2820990842e9700650bfc65ce220523fa`.

## Evidence status and exact-artifact packaging

All seven release records below have passed for this exact artifact, including the nine native client/server scenarios, the isolated 226-mod-file RTF modpack, actual Velocity transfer, four independent terrain-accuracy fixtures and their expected-failure control, visual-quality checks, measured cold-cache progress and native prediction contracts. The client session-race regression was added after review and passed against the rebuilt JAR; previous-candidate records were not reused as final-artifact passes. These are bounded tests with the limits described below, not a guarantee for every mod combination or long-running server.

This document defines acceptance and its limits. The final completion evidence is the set of machine-readable records, not the presence of a release-version heading or an older PASS file. `tools/package_release.py` requires **all seven** records below to have `allChecksPassed: true` and exactly the artifact hash above; the build manifest and the actual JAR must agree as well.

| Required record in `build/validation` | Scope |
|---|---|
| `FINAL-PASS.json` | Mechanism tests and all nine native client/server scenarios. |
| `FULLPACK-PASS.json` | The isolated installed-modpack RTF integrated world. |
| `VELOCITY-PASS.json` | Actual same-address, same-seed backend transfer and Xaero/LOD isolation. |
| `PREDICTION-PASS.json` | Independent FULL-chunk terrain comparisons and the expected-failure legacy control. |
| `QUALITY-PASS.json` | Required RTF visual, boundary, mesher and depth evidence. |
| `PREDICTION-PROGRESS-PASS.json` | A complete measured cold-cache window with new eligible prediction uploads **and actual draws**. |
| `PREDICTION-CONTRACT-PASS.json` | Actual transformed NeoForge manager/quality/ownership/pressure contracts, including 64/32/16 grids. |

The packaged `validation-summary.json` combines these seven records. Records from a previous build or an earlier candidate hash cannot satisfy the gate. Fixture classes, source worlds, account data and raw production logs are excluded from the release. ZIP integrity, one `voxy` mod entrypoint, required mixin classes/resources and exclusion of test classes are checked separately.

## Server pipeline regressions verified in 0.3.0

- **A full 64-slot budget must not reject its own continuation.** The actual runtime holds all 64 disk reservations, then completes an already-admitted NBT read. Its continuation uses the existing lease, delivers the exact column once, never exceeds 64 reservations and eventually releases every lease. Concurrent duplicate continuation submissions cannot consume the same lease twice.
- **Queued shutdown and hot limit reduction must not recurse through the whole gate.** Shutdown with 2,048 queued NBT reads and a hot disk-limit change from 64 to 16 exercise the formerly recursive rejection path. Rejections are drained iteratively; all affected listeners receive a terminal result, shared-read entries and gate slots are released, and no stack overflow occurs in the fixtures.
- **Cancellation and restart preserve accounting.** A cancelled queued decode does not run or release a still-owned reservation prematurely. Shutdown releases queued tasks and asynchronous leases; late callbacks from the old runtime cannot steal new-runtime permits or publish an obsolete result. Running I/O remains accounted for across a counter reset until its actual completion.
- **A broken optional listener must not lose other listeners' data.** Coalesced reads still perform one physical operation, and healthy subscribers receive the unchanged result when another callback raises an ordinary exception, `LinkageError` or `AssertionError`.
- **Fatal errors are cleaned up, not hidden.** Synthetic fatal failures in executor creation, the actual `ThreadPoolExecutor` thread-factory path, NBT starters and decoders release pending tasks, gate leases and shared state exactly once. `VirtualMachineError` and starter `ThreadDeath` remain observable after cleanup; they are not reported as successful terrain or swallowed as routine compatibility faults. Late results after failure are rejected.
- **Overload admission remains bounded.** A stalled executor receiving 5,000 ordinary or continuation submissions remains subject to the existing limits; factory failures leave no pending reservation. The fix does not introduce an unrestricted fallback pool.

These tests exercise the real runtime and executor classes with deterministic blocked I/O and injected failures. They establish bounded ownership and recovery for these paths, not immunity to every third-party deadlock, native crash or exhausted JVM.

## Client ingest backpressure and complete-column retention

The regression uses the actual `ClientColumnProcessor` and actual Voxy pending-work signal. With 8,192 ingest tasks pending, the old processor still dequeues another complete compressed column; the new policy retains both priority and normal queues and their byte/count accounting. Lowering the ingest backlog resumes the same queued data in the original order. New request scans stop before the decode drain's higher watermark, and compressed-queue pressure does not block the drain that must empty that queue.

The long-pause test advances its controlled clock through **180 seconds** with a complete column still queued, exceeding the normal request and assembler-idle periods. Without the fix, timeout followed by a same-position retry makes the retained column `STALE`, with no ingest callback. With the fix, the original request remains current and produces `APPLIED` with exactly one callback; unanswered requests still time out. Recovery also checks the actual ingest pause/resume signal.

Additional checks cover a newly completed column arriving inside another request's one-second refresh interval, opt-out, queue/session clearing and 1,024 queued columns across 1,200 sweeps. The observed deadline heap remains bounded at 3,072 entries in that fixture. Only complete queued columns with a matching request ID and packed position receive renewal; incomplete transfers and missing server responses are not kept alive by this protection.

Session reset and shutdown clear the queue-specific renewal state. The change does not acknowledge a column before the existing version/session checks and consumer callback accept it. It does not change server download windows or byte caps. Pending section counts are a workload signal, **not** a precise heap-byte ceiling: variable section snapshots, one already-running complete column and ordinary Minecraft ingestion can temporarily exceed the stopping watermark.

### Session-switch queue race: rebuilt-artifact regression

A deterministic failing interleaving pauses an old drain before dequeue, resets the session and queues a new-session column, then resumes the old drain. The old code consumes the new column before rejecting its stale epoch: the queue ends at **zero columns and zero bytes, with no dispatch**. This is distinct from timeout expiry and can lose a fully received column even with the timeout protection enabled.

The fix uses a short queue lock to make the epoch check, dequeue and byte/count changes atomic with session reset and queue mutation. Downstream pacing, decoding and dispatch remain outside that critical section. Completion is epoch-aware: an old task's `finally` cannot clear the new session's `processing` marker.

`tools/ingest_session_test.py` passes against this rebuilt artifact for both same-dimension reconnect and cross-dimension transfer. It invokes the real scheduling lambda, including its `finally`, and retains one complete column with all 44 retained bytes while the old task exits. The new session's processing marker remains owned; its current worker then dispatches exactly once and releases that marker. Only world inputs and network exits are test doubles; the actual processor, assembler, payload and decode path are used. This deterministic test is separate from the live proxy fixture.

## Native quality, parentless detail and memory recovery

`PREDICTION-CONTRACT-PASS.json` identifies the exact final hash and NeoForge 21.1.250. The native contract runs against transformed classes rather than a separately rewritten policy model.

- Both geometry and ownership use the quality-filtered snapshot. Eligible predictions have a complete 64×64 grid and one- or two-block spacing; disk-restored coarse/incomplete meshes cannot bypass it. Explicit opt-out is preserved.
- Only selected fine leaves bypass hidden parent/medium coverage dependencies. Near completion receives priority while the original worker, frame, CPU, memory and retry admission remains in force. The independent scheduling switch restores the original ordering without disabling the quality filter.
- A parentless complete fine tile is reachable through the actual GPU-residency snapshot and renderer ownership selection for all 4,096 cells. This is an ownership contract, not a timed OpenGL draw benchmark and not a change to Voxy's real-data octree discovery rules.
- The actual adaptive memory budget and manager reproduce a sustained-pressure cycle in the old retirement rule: obsolete parentless detail waits for a parent whose build cannot acquire workspace. The fixed path releases only additional LOD 0/1 caches that are no longer desired or pending and are beyond the existing horizon plus 1,024-block retirement margin. Tests cover completed 64 grids and hidden 32/16 intermediate grids left behind by movement.
- Retirement releases the resident reservation and surface/stored bookkeeping, invalidates coverage epochs and removes the obsolete GPU-residency reference. The nearby selected tile remains; after simulated collection, a new workspace is admissible and the planner no longer exits before scheduling. No prediction disk entry or real Voxy LOD is deleted.

The headless pressure test redirects exactly the same two pressure-retirement call sites for isolation. It is not substituted for the separate native Mixin contract. Neither form enlarges the prediction horizon or authorizes coarse prediction placeholders.

## Measured result

The strict 180-second custom-RTF window observed zero eligible fine meshes in 0.0.3 and a maximum of eight in this artifact, with the first measured upload/draw at 23.513 seconds. The heap and frame-interval measurements, including the higher p95 frame interval, are reported in [BENCHMARK.md](docs/optimization-0.3.0/BENCHMARK.md); host hardware and the full-modpack startup-timeout retry are documented in [TEST_ENVIRONMENT.md](docs/optimization-0.3.0/TEST_ENVIRONMENT.md). This is one run per version and does not support a general FPS or speed-multiplier claim.

## Fine prediction must actually reach the renderer

The earlier 0.0.3 observation that only coarse meshes were suppressed and no finished prediction reached the GPU is **not an acceptable completion result for 0.3.0**. Suppression alone can leave a permanently empty high-quality draw list while looking successful in a ready-tile counter.

The official progress record therefore requires all of the following:

1. A full **180-second**, cold-cache, actual custom-RTF window on NeoForge 21.1.250 with quality-first enabled and the exact final candidate hash.
2. At least one eligible published fine grid, a measured first draw within that window, and positive **window-local deltas** for both GPU uploads and rendered counts. Cumulative counters from before the window are insufficient.
3. A clean fixture completion, complete frame samples and no logged prediction-build, shader-compile or OpenGL failures in the measured run.
4. No exterior-audit instrumentation in the certified run. A strict baseline comparison requires the same heap, worker limits, prediction distances, RTF preset, camera position, vanilla radius and declared window.

An old snapshot-only zero-output baseline may document that earlier observation, but cannot support a strict resource or first-draw comparison. Actual first-eligible/first-draw times, sampled heap and frame-interval data belong in [the benchmark report](docs/optimization-0.3.0/BENCHMARK.md), generated only after progress certification. This document does not invent or carry forward timing from a different candidate hash.

First-draw evidence is not a 90% viewport-coverage measure, a visual beauty score or a terrain-accuracy result. Heap sampling excludes native memory and VRAM and may miss instantaneous peaks. Frame intervals include Minecraft, GC, system load and frame limiting; one run cannot establish a universal FPS increase or a generation-speed multiplier.

## RTF fixtures and native matrix acceptance

World-based tests use actual NeoTerraForged default/custom terrain. The preparation gate rejects a flat generator and a missing or disabled RTF data pack. Pure queue, synthetic geometry and GPU shader fixtures are not substitute Minecraft worlds.

`tools/validate_release.py` runs the following nine client/server scenarios serially; each has its own hash and result record before `FINAL-PASS.json` may be written:

1. Feature, Sodium UI and legacy-import checks.
2. Request flooding with aggressive pre-existing configuration.
3. Administrator sharing toggle and recovery.
4. A server without this mod, using original rendering on the client.
5. A client without this mod, connecting to the modded server.
6. Sharing disabled, without Iris.
7. Plain-server two-JVM persistence with Xaero.
8. Shared-LOD two-JVM persistence with Xaero.
9. Live download scheduling, byte caps and administrator Apply with Xaero.

The installed-modpack integrated RTF world, actual proxy transfer, terrain comparisons, visual-quality scenarios and measured-progress window have separate records. Their completion is not implied by the mechanism PASS results above.

## Persistence, proxy identity and administrator acceptance

The pure persistence regression uses the real `WorldIdentifier` and RocksDB records: stable endpoint-scoped namespaces must reopen saved terrain while a changing async epoch continues rejecting stale work. The native persistence cases additionally require **two separate client JVMs** at the same private endpoint. The second disables vanilla ingestion, server LOD reception and prediction, then checks the same database identity, non-air mapping and level-4 parent LOD; it cannot pass merely by downloading the terrain again. Plain and shared terrain, and Xaero map/minimap roots, are checked separately.

The proxy fixture uses actual Velocity/NeoVelocity and Xaero map/minimap sessions. Its backend worlds are copies of the same RTF fixture, with identical biome seeds and an unchanged proxy address during **alpha → beta → alpha**. Acceptance requires different B map/LOD roots, no reading of A's saved map configuration while on B, and restoration of A's original configuration after returning. Minimap/waypoint and world-map roots must agree. Old-session producer rejection is also covered in the modpack fixture.

Independent backends still require distinct stable server/node identities. Identity-free worlds with the same address and seed cannot reliably be distinguished by client-side evidence alone. Old ambiguous map directories remain on disk and are not automatically merged.

Download-policy tests cover inclusive/exclusive time boundaries, fixed UTC offsets, cross-midnight/all-day windows, disabled scheduling, shared/per-player budgets, reconnect accounting and lower caps. Native acceptance starts with downloads blocked, enables them through real OP commands, blocks them again without deleting saved terrain, then checks an immediate read/Apply operation in native Sodium against authoritative server values. Client drafts, precise input, import confirmation, malformed-config retention, stale administrator revisions and non-OP rejection remain acceptance requirements. Configuration updates are triggered by Apply or commands, not an automatic file watcher.

## Retained regression scope and limitations

- Tall-column serialization, splitting, decoding and persistent cache paths retain legal 64/65/68/128/255-section cases and reject oversized or duplicate manifests. The prior `Too many replacement sections: 65` exception remains a regression target.
- Legacy raw/Zstd LOD, palette validation, truncation/fuzz cases and unchanged read-only source hashes remain covered by import tests. Native acceptance remaps a deliberately different block ID and rebuilds parent LODs. This does not make arbitrary LOD database formats importable.
- Boundary last-event/reset behavior, original and HXZ GPU traversal, standard/reverse-depth shaders, projection/occlusion, coarse/invalid mesh guards and bounded hidden preparation remain regression cases. The compatibility mesher path is tested with quality-first explicitly disabled; this does not turn it on by default.
- Existing bandwidth fairness tests retain large-column progress while small packets continue, without bypassing earned global/player credit. Native acceptance additionally requires bounded request flooding and recovery from injected ordinary failures through administrator controls.
- The installed-modpack case checks Sable plot rejection, Xaero late writes, RTF cache identity and native Sodium. It is short-duration coverage, not a certification of every aircraft, shader, structure, mod version or data-pack combination.

The reported white ring at the real-terrain/LOD boundary has not been reproduced consistently in the stationary RTF comparisons. Existing boundary/depth fixes and screenshot diagnostics do **not** justify claiming all seams are gone. See [rendering scope and captures](RENDER_QUALITY.zh-CN.md); sky-colour/bright-pixel counts alone are not a depth or universal image-accuracy metric.

The terrain method in [the accuracy report](PREDICTION_ACCURACY.zh-CN.md) compares predictions to independently generated FULL chunks in four default/custom RTF fixtures with two seeds. Its 90% thresholds apply separately to the defined height-tolerance land/water/high-ground samples and preview/refinement controls. They are **not** a 90% guarantee for arbitrary worlds, exact blocks, vegetation, structures, player builds or rendered pixels. The expected-failure legacy RTF control remains required, and the final prediction record must match this artifact rather than only quoting historical results.

## Reproduction and release completion

```powershell
python tools/build.py
python tools/validate_release.py
python tools/fullpack_validation.py
python tools/proxy_transfer_test.py
```

The native prediction contracts are included in `validate_release.py` and can also be run with `python tools/native_prediction_contract.py`. Complete the RTF prediction/legacy-control and quality scenarios described in [BUILDING.md](BUILDING.md) and [the accuracy report](PREDICTION_ACCURACY.zh-CN.md#复现), then produce their records with `tools/prediction_report.py` and `tools/quality_report.py`.

Run the normal cold-cache prediction-progress fixture and certify its console log using `tools/prediction_progress_report.py`, with `--output build/validation/PREDICTION-PROGRESS-PASS.json` and `--benchmark`. Supply the corresponding baseline through `--baseline` only at its supported evidence level. Exact timing and frame data are written to the benchmark report, not inferred from the contract tests.

Finally run `python tools/package_release.py`. Packaging fails when any of the seven records is absent, unsuccessful or from a different JAR. Rebuilding or changing the final artifact requires matching evidence again; the document alone never authorizes reuse of older PASS records.

Fixtures use isolated local endpoints and synthetic players. Cooperative per-tick budgets cannot preempt a running third-party codec or native call; 30 FPS is a scheduling target, not a whole-game guarantee. No long-running cloud-server certification is claimed.
