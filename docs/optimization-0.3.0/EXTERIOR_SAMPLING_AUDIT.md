# Exact exterior sampling audit

The initial, pre-v2 180-second RTF diagnostic produced seven accepted detailed meshes in
0.3.0, with the first drawn mesh at 31.883 seconds. The 0.0.3 baseline produced no
accepted detailed mesh in the same window. This validates the narrow scheduling
change's ability to publish useful detail; it does not establish that sampling is
fast enough. Its first-draw observation used cumulative counters. The final
benchmark separately requires v2 window counter increments and matching settings;
these initial diagnostic numbers must not substitute for that verification.

The candidate's accumulated worker stage counters were 207,371 ms for sampling,
16,391 ms for surface work, 1,937 ms for meshing and 1,870 ms for packing. Sampling
subcounters included 91,194 ms labelled `native`, 6,980 ms for color and 109,092 ms
for exterior. These are accumulated worker durations, not wall-clock durations.
For an RTF Java backend, the `native` counter includes the Java grid loop and exact
`sampleForLod`; its name alone does not identify JNI or Rust as the bottleneck.

## What must remain exact

`PredictionJavaExterior.sample` examines the entire requested footprint and
returns the union of solid occupancy at each height. RTF's opaque density nodes
require the original column iteration order, including the cells above the
requested roof. A positive occupancy bit for a four-block footprint does not
prove occupancy for any one of its finer child footprints. Reusing that bit as a
fine sample could fill cavities or erase an overhang.

The existing rejected-roof cache is disabled for ordered opaque graphs. Exact
repeat requests consequently repeat the density traversal. This is an opportunity
only if normal RTF work actually submits the same request sufficiently often.

## Headless algorithm feedback

Run `python tools/exterior_repeat_test.py`. This creates a deterministic opaque
density function and calls the actual exterior iterator; it does not launch a
Minecraft world or claim to reproduce RTF terrain. For eight identical requests,
it compares every result element and counts density evaluations:

| Roof | Footprint step | One request | Eight requests |
| --- | ---: | ---: | ---: |
| Valid | 4 | 38,912 | 311,296 |
| Valid | 2 | 9,728 | 77,824 |
| Valid | 1 | 2,432 | 19,456 |
| Rejected | 4, 2 or 1 | 768 | 6,144 |

This feedback proves repeated work is present when requests repeat. It does not
measure a production hit rate or justify changing a production sampler.

## Runtime observation

`tests/client/ExteriorCostTraceMixin.java` is registered only in the separate
`--exterior-audit` fixture. It observes the actual `PredictionJavaExterior`
instance and the exact tuple `(x, z, step, bottom, top)`. It records completed
requests, exact repeats, null outputs and elapsed time, while every original
request still runs. Result length/hash comparisons can flag changed output; a
matching hash is diagnostic evidence, not a proof of output equality.

The observation retains at most 32,768 tuple keys for the session. Sampler entries
use weak ownership; the key allowance remains bounded even after a sampler is
collected. `untrackedNewKeys` makes an exhausted allowance visible. Exceptions
have no RETURN callback and are not included in completed request durations;
`observedAborts` detects those when the worker next enters the sampler. Counters
cover the sampler session, including preparation before the progress window.

The fixture prints one `HXZ_EXTERIOR_AUDIT` record at completion. It is a diagnostic
run and must not replace the normal cold-cache timing comparison. A low repeat
rate means no production cache should be added. Any later cache proposal must
retain world/sampler identity, bounded memory, cancellation behavior and exact
output equality, and should be tested against real RTF before adoption.

The audit fixture waits for the current dimension's actual exact sampler before
starting its measurement window. `hxz.smoke.exteriorAuditProfileWaitMs` controls
that bounded wait (default 300,000 ms); `hxz.smoke.exteriorAuditWindowMs` controls
the diagnostic observation window (default 180,000 ms). Both are test-only and
the latter permits up to 900,000 ms for investigation. The launcher must allow
enough process time for both waits and world initialization. Neither setting
changes the regular progress fixture, whose timer still includes sampler
initialization. `HXZ_EXTERIOR_AUDIT_START` identifies the different timing scope.
The summary is printed before a failed draw assertion and on other caught probe
failures; externally terminating the JVM can still prevent final output.

No exterior cache, sparse sampling, altered RTF filtering, or extra worker has
been added by this audit.

## RTF audit 2: repeated work is real, memoization is not yet safe

The completed diagnostic run is
`build/progress-rtf-custom-exterior-audit2/console.log`. It installed an actual
`FreeTerraForgedTerrainSampler`, observed eight quality-eligible meshes and
recorded these sampler-session counters:

| Counter | Observed value |
| --- | ---: |
| Completed requests | 10,358 |
| Exact repeated requests | 3,069 |
| Null results | 581 |
| Repeated requests returning null | 143 |
| Accumulated completed sampling time | 110,670 ms |
| Accumulated time spent on exact repeated requests | 25,421 ms |
| Changed result digests | 1 |
| Observed aborted calls | 1 |
| Retained exact request keys | 7,289 |
| Untracked keys beyond the 32,768-key allowance | 0 |
| Observed sampler instances at completion | 1 |

Thus 29.6% of completed calls repeated a recorded request; those calls accounted
for about 23.0% of accumulated exterior sampling time. **25,421 ms is accumulated
sampling work, not a measured wall-clock saving.** No cache was tested, and these
figures do not establish an achievable speedup after cache overhead, concurrency,
other work and correctness requirements are considered. This diagnostic run is
separate from the strict 180-second benchmark.

The changed digest means one completed repeat differed from its first recorded
result in nullness, length or array hash. The current trace does not save the key,
complete arrays, thread or cache phase for that change, so it cannot distinguish
these cases or identify the source of the mismatch. Equal hashes also do not
prove identical arrays. The single observed abort cannot be causally connected
to the single changed digest from these aggregate counters.

The trace covers all five numeric method arguments and the actual exterior
sampler instance. The omitted `BooleanSupplier valid` controls cancellation:
the implementation throws on invalid/interrupted work instead of returning a
different successful occupancy array. Each sampled array is newly allocated and
its digest is computed inside the RETURN injection before the caller receives
it. Only the immutable digest is retained; later caller mutation would not
alter a recorded digest. The inspected consumer reads the array while making a
volume. These facts do not support an explanation based on post-return mutation
or an omitted numeric argument.

There is nevertheless a concrete state dependence in the installed
`neoterraforged-1.21.1-0.0.6 (1).jar`. Narrow `javap -p -c` inspection of
`CellSampler`, `CellSampler.Cache2d`, `CellSampler.CacheChunk` and `WorldLookup`
shows that the fallback cell cache rounds to quart coordinates and remembers
the last position; `WorldLookup.applyCell` can use
`TileCache.provideIfPresent`, then fall back to direct heightmap computation when
the tile is absent. The presence of a generated/filtered tile can therefore
change the path supplying the same coordinate. `Cache2d` does not include the
lookup identity or tile generation in its last-position key. Inspection output
is retained locally in `build/rtf-exterior-state-javap.txt`.

The VSS integration also uses two distinct paths: `ClientTerrainSampler` maps
the prediction router for its surface density fields, while
`PredictionJavaExterior` receives the original `RandomState` and invokes the
generator's original column iterator. The mapped exact-cell adapter's behavior
is consequently not proof that exterior queries are pure. RTF coarse scopes
restore their thread-local flag in `finally`, and the exterior call occurs after
those surface/color calls return; this audit did not find evidence of a leaked
coarse flag. **The inspected state-dependent paths are plausible explanations,
not a demonstrated root cause for the one observed mismatch.**

For 0.3.0, production exterior sampling remains unchanged and no exterior memo
is added. Caching the first result could retain a transient roof rejection or
incorrect cavity occupancy. Before a future optimization, a separate diagnostic
must preserve bounded complete reference outputs and mismatch coordinates,
identify relevant sampler/world/thread and tile-cache phases, compare cold and
warm filtered states against an independent full-column reference, and verify
cancellation, invalidation and cross-world behavior. A proposed cache must then
produce identical complete arrays under those conditions while retaining a
strict memory bound. Repeated-key counts alone are insufficient.
