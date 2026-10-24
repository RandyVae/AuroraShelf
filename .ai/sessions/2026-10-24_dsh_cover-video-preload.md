# DSH — Cover Decode Cache/Preload + Video Preload (139)

Date: 2026-10-24 17:45 HKT

## Delivered

- Cover decoding: ByteArray requests get stable URL memoryCacheKey; Coil explicit 25%/64MB memory + 128MB disk cache; per-section prefetch (comics 10, live 8, search 8, related 6) with 4-way concurrency and 10-min dedupe.
- Video preload: home first-page detail prefetch (2 URLs, 1.5 s delay); next-episode playback resolve (2 s delay); Settings switch default on, Wi-Fi only, persisted.
- Policy: single PrefetchPolicy (enabled && non-metered); toggle-off logs skipped and makes no requests.

## Measured (Release 139)

- Hero tap after prefetch: source resolved 48 ms (baseline 493–3180 ms).
- Live covers 8 in 1984 ms; search 8 in 2081 ms; comics 10 enqueued; related 6 (4 ms); home detail prefetch 834/498 ms.
- Coil disk cache image_cache 776 KB after session.

## Verified

- JVM 150/0/1; device 32/32; lint 0 errors / 19 warnings / 3 hints; release 139 SHA 2209ed23…, installed + cold start.
- Settings switch UI screenshot; toggle off persists and suppresses prefetch.

## Exclusions / risks

- HomeScreen untouched to avoid the parallel Netgrey brand edits; home byte prefetch stays in AuroraViewModel and uses the new decoded key.
- Preload costs Wi-Fi traffic; Coil EXACT precision may re-decode for sizes other than the prefetched one.
- Frame-level profiling not done; no FPS claims.
