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
