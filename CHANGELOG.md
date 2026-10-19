# 0.3.0 — Fine-detail scheduling and bounded recovery

Target Minecraft 1.21.1, NeoForge 21.1.250 and Java 21; retain the native Sodium 0.8.12 settings baseline and optional installation of the same JAR on the server.

## Added and fixed

- Add default-on, independently switchable near-detail prediction scheduling. Only selected fine leaves bypass hidden parent/medium dependencies; existing worker, frame, CPU, memory and retry admission remains in force. Disabling scheduling preserves the separate quality filter.
- Under memory pressure, retire obsolete parentless LOD 0/1 prediction caches outside the existing horizon-plus-retirement-margin, including unfinished 16/32 grids. Preserve desired/in-flight terrain, disk caches and real Voxy LOD; use the existing resource-release and ownership lifecycle.
- Add default-on client ingest backpressure using the actual Voxy pending-section count plus compressed-column count and bytes. Retain received columns while paused and resume below a lower watermark; the option is exposed in Sodium.
- Keep matching, fully received queued columns current during long local-processing pauses, avoiding timeout/retry replacement of their request IDs. Unanswered and incomplete transfers still time out; session reset clears the protection.
- Make session validation, dequeue and column/byte accounting atomic under a short queue lock, and prevent an old drain's completion from clearing the new session's processing marker. Verify same-dimension reconnect and cross-dimension transfer with a deterministic real-processor regression against the rebuilt artifact.
- Let an asynchronous server NBT read continue under its existing task reservation instead of acquiring the same budget again. Release task and read-gate leases on rejection, shutdown and failure paths.

## Defaults, migration and scope

New configurations still use original NeoVoxy rendering with independent HXZ performance scheduling. The 0.0.3 quality filter remains enabled by default: complete 64×64 predicted grids at no more than two-block spacing; no coarse prediction placeholders. Existing explicit choices and real LOD data are retained.

Retain RTF preset support, optional server sharing, download schedules and per-server/per-player limits, Velocity/Xaero isolation, legacy LOD imports, server guards and hot configuration through Sodium or commands. Replace older combined or standalone Voxy/NeoVoxy and VSS JARs; update the server to obtain server-side fixes.

This entry describes implementation changes, not a final performance or universal compatibility result. Final RTF measurements are in [BENCHMARK.md](docs/optimization-0.3.0/BENCHMARK.md) and validation scope in [VALIDATION.md](VALIDATION.md); no fixed FPS, accuracy percentage or all-seams-resolved claim is made here.

# 0.0.3 — Prefer detailed LOD

- Enable LOD quality-first by default. Both normal and Iris prediction snapshots reject incomplete grids and sample spacing above two blocks; ownership uses the same filtered map, including disk-restored meshes.
- Keep real NeoVoxy LOD and the planner's reusable samples. Fine prediction may arrive later; distant regions without eligible predictions wait for real LOD rather than displaying coarse placeholders.
- Default the smooth coarse-preview compatibility option off. Quality-first takes precedence over an old saved `smoothPredictionPreview=true`; explicit opt-out remains available in Sodium and rendering/performance presets preserve the choice.
- Avoid an extra prediction/water depth pass when no eligible prediction mesh exists.
- Verify actual RTF default/custom worlds, native snapshot selection and live Sodium Apply. No superflat world fixture is used.

# 0.0.2 — RTF quality and download fixes

## Fixed

- Mixed-size traffic no longer starves another player's large LOD column: bounded FIFO claims reserve part of newly earned bandwidth while other players and acknowledgements continue using the remainder.
- NeoVoxy's boundary mask now follows the final add/remove event, clears pending changes on reset and clears its empty depth target. Regression cases failed in both the supplied NeoVoxy and 0.0.1.
- VSS now recognizes NeoVoxy 0.6.1's normal final-depth shader and its actual opaque-to-translucent callback. Correct depth projection and prediction/water ordering can be disabled from Sodium.
- Coarse exterior prediction connects height samples into slopes instead of giant flat columns. A single captured cave no longer forces an entire exterior preview back to columns. Fine blocks, complex features and volume topology retain the VSS mesher.
- Prediction meshes use a new cache namespace when smooth previews are enabled; real saved LOD is unchanged.

## Validation and scope

Native world fixtures use actual NeoTerraForged default/custom terrain. New tests cover competing bandwidth claims, boundary lifecycle, actual OpenGL depth composition and the real packed prediction mesher. RTF visual fixtures compare the supplied NeoVoxy, 0.0.1 and this build. Detailed results and unresolved visual limits are in VALIDATION.md and RENDER_QUALITY.zh-CN.md.

The reported white ring has not been reproduced reliably in the stationary RTF comparison. Fixes address verified boundary/depth faults; this release does not claim all seams, arbitrary presets or vegetation match perfectly. Existing user options remain respected.

---

# 0.0.1 — public test release

Continues the earlier local 0.2 integration under the requested new version numbering.

## Fixed

- RTF prediction bootstrap reconstructs the active noise-settings registry using the same decoded settings object required by NeoTerraForged's identity check. Preset codec round trips alone did not catch this initialization failure.
- Stable previews use the refined surface/water sampler, including filtered RTF terrain and the native batch path. Prediction caches distinguish all accuracy switches and the new sampling revision.
- Java/RTF prediction now applies Minecraft's frozen-ocean and eroded-badlands surface extensions on isolated columns, retaining ice overhangs and water gaps. A second-seed frozen-ocean fixture reproduced the missing geometry before this fix.
- Reconnecting without a VSS identity no longer adds a random connection UUID to the Voxy disk namespace. Written LOD sections and block mappings reopen after a full client JVM restart; the separate async session epoch still rejects old tasks.
- Xaero map and minimap/waypoint roots are selected before their login-time disk I/O. Stable endpoint, backend identity and biome seed distinguish same-seed Velocity backends while allowing A → B → A to reopen A's saved configuration.
- Time commands accept unquoted `HH:mm` and `+08:00` values through the actual Minecraft command dispatcher.
- Large LOD packets retain earned bandwidth credit while small acknowledgements are interleaved; fast read-then-Apply operations honor the administrator request cooldown.

## Added

- Three default-on Sodium controls for stable prediction, strict grid sampling and Java surface extensions, independent of the renderer and performance preset; disabling restores the respective VSS paths.
- Native terrain comparisons against independently generated FULL chunks, with separate land, water and high-ground accuracy thresholds and legacy-mode regression checks.
- Server-authoritative daily download window with cross-midnight support and configurable fixed UTC offset; blocking admission and already queued sends; online resume handshakes.
- Atomic shared/per-player byte budgets, bounded burst credit, retained per-player accounting across rapid reconnects and bounded accounting storage.
- “管理员 · 下载限制” in native Sodium, with permission checks, server revision checks, persistent apply and hot update; matching `/voxyhxzserver downloads` commands.
- Optional Xaero storage isolation, enabled by default; takes effect on the next connection. Existing map directories remain untouched.
- Real Velocity/NeoVelocity same-seed transfer tests, real two-JVM LOD persistence tests, and live command/Sodium pause-resume tests.

## Retained

Native NeoForge single-JAR startup and optional server installation, NeoVoxy default visuals, independently switchable HXZ optimization and scheduling, RTF preset compatibility, Sable plot guards, old LOD import with palette remapping, 255-section transport, bounded ingress/background work and overload recovery.

## Migration

Replace the earlier Voxy/NeoVoxy/HXZ and standalone VSS JARs. New Xaero storage scopes do not automatically merge old ambiguous proxy maps. Existing random-session Voxy directories remain on disk and can be imported from a stopped, copied source database. Distinct backends need distinct stable identities; duplicated backend configuration must not declare independent worlds shared.
