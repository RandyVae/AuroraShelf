# Existing Architectural Decisions

## Repository facts

Date: 2026-09-24

Status: Inferred from repository

Decision: Preserve the existing architecture, toolchain, and project-specific release rules while establishing `.ai` as the shared handoff location.

Reason: Existing source, configuration, and documentation are the authoritative evidence available in this pass.

Affected Components: Repository-wide development workflow.

Constraints: This is an inferred workflow fact, not a newly approved product architecture; do not treat it as authorization to refactor.

Consequences: Future agents must record confirmed architecture changes here and keep implementation state in `PROJECT_STATE.md` and `HANDOFF.md`.

## Huangguo cover CDN rotation

Date: 2026-09-24

Status: Confirmed from the current public page and CDN response.

Decision: Keep AES-CBC cover decryption in `CoverImageLoader`, recognize the current `pic.fisawck.cn` host, and allow unknown `pic.*` hosts only when the requesting page Referer is `huangguoai.com`.

Reason: Huangguo rotated its encrypted image CDN. A global `pic.*` rule would risk decrypting unrelated sources, while a Huangguo-Referer gate preserves the existing source boundary and tolerates future rotations.

Affected Components: `app/src/main/java/com/aurorashelf/app/ui/CoverImageLoader.kt` and its unit tests.

## Huangguo dynamic crypto configuration

Date: 2026-09-24

Status: Implemented and unit-tested against the current public worker.

Decision: Discover `mode`, `media_key`, and `media_iv` from Huangguo's public crypto worker at runtime, validate the values, and cache them for 15 minutes. Retain the last verified static AES-CBC configuration as a temporary fallback.

Reason: CDN host names and the public encryption values can rotate independently. Reading the site's published configuration avoids a release for each ordinary rotation while keeping parsing limited to public text rather than executing site JavaScript.

Affected Components: `app/src/main/java/com/aurorashelf/app/ui/CoverImageLoader.kt` and `app/src/test/java/com/aurorashelf/app/ui/CoverImageLoaderTest.kt`.

Boundary: A release may still be needed if Huangguo changes the worker URL, changes the encryption algorithm or padding, stops publishing the values, or changes the page/request protocol.

## Cold-start tab-scoped loading

Date: 2026-10-24

Status: Implemented, compiled, and covered by pure-JVM tests; device verification pending.

Decision: `ComicViewModel` and `LiveViewModel` no longer request data from `init`. `AuroraShelfApp` calls the idempotent `ensureLoaded()` from the COMICS / LIVE branch with `LaunchedEffect(Unit)`. `refresh()` remains public for manual refresh and retry.

Reason: Constructing all four ViewModels at composition time used to fire the default comic source and the Stripchat directory on cold start, before the user ever opened those tabs. The forum module already used an `ensureLoaded` pattern; this makes the convention consistent.

Affected Components: `AuroraShelfApp.kt`, `ComicViewModel.kt`, `LiveViewModel.kt`.

Boundary: If the first load fails, entering the tab again does not automatically retry because the one-shot flag is already set; the screen's retry action still works. Cold-start privacy/performance gain is not device-measured yet.

## Network search results are authoritative

Date: 2026-10-24

Status: Implemented, compiled, and unit-tested for the local-search helper.

Decision: `SearchScreen` must not re-filter site-wide network search results by title/author substring. Local search keeps the local matcher; both paths deduplicate by `VideoItem.id`.

Reason: Site search legitimately matches tags, performers, or aliases that are absent from the title, so the client-side second filter could discard valid results and show a misleading "tap search" empty state. Duplicate IDs could also crash `LazyColumn` keys.

Affected Components: `AuroraViewModel.kt` (`isRemoteSearch`, `localSearchMatches`), `AuroraShelfApp.kt` (`SearchScreen(filterLocally=…)`).

## Per-record tolerant decoding for local data

Date: 2026-10-24

Status: Implemented and unit-tested.

Decision: Persisted JSON is decoded record by record through pure codecs that return `null` instead of throwing (`OfflineMetadataCodec`, `decodeSavedVideos`). One corrupt record may only drop itself.

Reason: Previously a single corrupt payload emptied the whole offline-cache list or the whole saved-video library, because one `runCatching` wrapped the entire traversal.

Affected Components: `OfflineCacheManager.kt`, `AppPreferences.kt` and their tests.

Boundary: JSON key names/format are unchanged, so existing downloaded payloads and saved preferences remain readable.

## Offline-cache cancel semantics

Date: 2026-10-24

Status: Implemented; end-to-end cancel path needs device/integration verification.

Decision: Track resolving jobs in `resolveJobs`. `remove(id)` / `clearAll()` cancel them; before `addDownload`, re-check coroutine activity and pending-entry identity; `finally` removes only the task's own map entry.

Reason: A resolver that finished after the user deleted or cleared the entry would still `addDownload`, resurrecting a deleted item and consuming traffic/storage.

Affected Components: `OfflineCacheManager.kt`.

Boundary: The implementation assumes `enqueue`/`remove`/`clearAll` are called on the main thread (all current call sites are Compose main thread). Media3 downloads cannot be instantiated in plain JVM tests, so only the pure acceptance predicate is unit-tested.

## Shared transient-transport retry predicate

Date: 2026-10-24

Status: Implemented and unit-tested.

Decision: `Throwable.isTransientTransportFailure()` lives in `data/TransientTransportFailure.kt` and detects SSLException/EOFException anywhere in the cause chain. Idempotent GET/HEAD requests retry exactly once; everything else propagates.

Reason: `PipePipeDownloader` and `VideoRepository` had identical private copies that could drift.

Affected Components: `PipePipeDownloader.kt`, `VideoRepository.kt`.

## Cover payload format recognition

Date: 2026-10-24

Status: Implemented and unit-tested.

Decision: Before attempting Huangguo AES-CBC decryption, treat AVIF/HEIC/HEIF (ISO-BMFF `ftyp` at offset 4, major brand in a fixed allow-list) as an already-decoded image, exactly like JPEG/PNG/GIF/WEBP.

Reason: Requests advertise `image/avif`; if the CDN serves AVIF, the previous detector treated it as ciphertext, failed decryption, and silently returned no cover.

Affected Components: `CoverImageLoader.kt`, `CoverImageLoaderTest.kt`.

Boundary: Magic-byte heuristic, not a full decode validation; `mif1`/`msf1` are generic HEIF brands.

## Forum image cookies stay same-origin

Date: 2026-10-24

Status: Implemented and unit-tested; device verification pending.

Decision: Forward the forum page cookie to an image request only when both URLs are HTTPS and share exactly the same host (case-insensitive, port deliberately ignored). Referer is unchanged from previous behaviour.

Reason: Forum posts embed third-party image CDNs; the previous implementation sent the forum cookie to any image host, leaking anonymous session state (and would leak a real session if login were ever added).

Affected Components: `ForumImageSaver.kt` (`shouldForwardCookies`), `ForumScreen.kt`.

Boundary: Cookies are host-scoped in HTTP, so a same-host different-port service would still receive them.

## Live pagination termination and polling lifecycle

Date: 2026-10-24

Status: Implemented; pure pagination logic unit-tested; device verification pending.

Decision: `canLoadMore` requires the page to add at least one new item and the merged size to stay below `totalCount`. The grid triggers `loadMore()` via `snapshotFlow`. Player discovery stops on ready / mismatch / attempt limit, and both discovery and comment polling run inside `repeatOnLifecycle(STARTED)`. A page-reload token restarts discovery after retry.

Reason: If the endpoint ignored the offset, the old size-based condition kept `canLoadMore=true` forever and requested pages indefinitely. Polling also continued while the app was backgrounded.

Affected Components: `LiveViewModel.kt` (`nextLivePage`), `LiveScreen.kt`.

## Comics list paging trigger

Date: 2026-10-24

Status: Implemented, compiled.

Decision: Use the same `snapshotFlow` near-bottom trigger as the forum and home feeds instead of a latched `derivedStateOf` boolean.

Reason: A boolean that stays true after a page append can miss the following page (no false→true transition).

Affected Components: `ComicScreen.kt`.

## Recorded earlier decisions missing from this file (backfilled from repository evidence)

Date: 2026-10-24

Status: Reconstructed from code and documentation; no behaviour change in this pass.

- **Stripchat live module boundary**: native Compose list plus a WebView player; the WebView may only navigate to `stripchat.com` and its HTTPS subdomains; comments are read-only; login, payment, private messages, tips, and posting are intentionally excluded. Site scripts and cookies still occur (documented in README). Components: `StripchatLiveRepository.kt`, `LiveViewModel.kt`, `LiveScreen.kt`, `LiveItem.kt`.
- **Shared HTML pagination contract**: the 14 PipePipe kiosks follow explicit next links through `HtmlPagination` with same-origin HTTPS validation, no self/external redirect, encoded-URL support, and "stop when no marker" semantics. `xHamsterService` still builds `/newest/2`-style URLs from a regex when no explicit next link exists, which conflicts with the documented "never guess URLs" claim — open item.
- **Apple HIG UI baseline**: `docs/ui/design-system.md` and `docs/ui/visual-qa.md` are mandatory for UI work (referenced from `AGENTS.md`), with Android-native Material 3 / Predictive Back / 48dp / TalkBack kept. Open conflict: `docs/ui/design-system.md` forbids faking blur/Liquid Glass unless the target SDK provides a native API, while the shipped UI uses the Backdrop optical engine to draw the glass dock. This conflict is unresolved; until resolved, real-device visual QA per `docs/ui/visual-qa.md` is the gate, and the project's existing Backdrop implementation remains the shipped behaviour.

## UI structure split (AuroraShelfApp.kt)

Date: 2026-10-24

Status: Implemented and verified (byte-level round-trip proof; build/tests/device-suite pass).

Decision: Split the 2,587-line `AuroraShelfApp.kt` into a 596-line app shell plus `HomeScreen.kt`, `SearchScreen.kt`, `PlayerScreen.kt`, `PersonalScreen.kt` and shared `AppComponents.kt`. Only visibility (`private` → `internal`) changed for cross-file symbols; no logic/UI/testTag changes.

Reason: The single file made review, navigation and parallel work impractical.

Boundary: Cross-file references are same-package; no module boundary introduced. Future page work should edit the page file, not the shell.

## HIG colour and typography decisions

Date: 2026-10-24

Status: Implemented; HIG audit report `docs/ui/hig-audit-2026-10-24.md`.

Decision:
- Dark fallback `onPrimary` is a dark maroon (`#3F0015`) on the coral primary → 5.45:1; plain white was 3.15:1 and failed WCAG AA.
- Dark fallback `outline` raised to `#5C6879` (3.17:1 on dark surface) so control borders reach the non-text 3:1 threshold.
- Fixed-black chrome (live player/comments/reader) uses fixed high-contrast foregrounds (`AuroraCoral #FF4F7B` → 6.66:1 on black), never theme-dependent `primary` on black.
- Hardcoded glass/ink/reader colours moved into `Theme.kt` tokens (`AuroraGlassContainer*`, `AuroraInk*`, `AuroraReaderBackground`, `AuroraImmersiveBackground`).
- The floating dock height follows `fontScale` (78/64/56 dp + up to 44 dp) so icon + label still fit at 200% system font.

Reason: WCAG AA text contrast (4.5:1), non-text 3:1, and HIG Dynamic Type/large-text reflow.

Boundary: Increase Contrast variant cannot be implemented reliably (no public Android API); dynamic-colour contrast depends on the user wallpaper. Both are declared in the HIG report as unmet/blocked.

## Startup and cache initialisation

Date: 2026-10-24

Status: Implemented; startup measured once on emulator (Release 1238 ms cold total), not a benchmark.

Decision: Comic and live feeds load only on first entry (`ensureLoaded()`); Media3 download manager warm-up runs on a daemon background thread because Media3 resolves its application handler with `createHandlerForCurrentOrMainLooper`, keeping listener callbacks on the main looper.

Reason: Cold start previously opened the download database/cache on the main thread and fired third-party requests for tabs the user had not opened.

Boundary: Download resume now depends on the background warm-up finishing; if the process dies before it completes, downloads resume on the next launch. No device measurement of ANR/startup trade-off beyond the single cold-start value.

## Release candidate policy (versionCode 136)

Date: 2026-10-24

Status: Built, verified, installed on emulator.

Decision: Keep versionName `2.15.0` and bump versionCode to 136 to disambiguate from the two earlier 135 builds. The verified artifact is `dist/AuroraShelf-2.15.0-136-hig-optimized.apk` with SHA-256 `51c8f5b1027aa909dca58aa11497007f72d085a68e0a131553e622e41d8d11bc`.

Reason: Two versionCode-135 builds existed (verified f84e… / unverified e819…); the previous evidence chain was ambiguous.

Boundary: The APK uses the debug signing certificate and is an internal candidate only; `dist/` is git-ignored so the SHA is recorded in `docs/verification.md` and `.ai/`, not in git.

## xHamster pagination exception

Date: 2026-10-24

Status: Documented; not changed in behaviour.

Decision: Record xHamster as the single deliberate exception to "never guess pagination URLs": it derives same-origin `/newest/2…` URLs when no explicit next link exists. The rest of the kiosks only follow explicit links.

Reason: Removing the guess without a working alternative would break a previously working source; no fresh full-source evidence was obtained in this pass.

Affected Components: `third_party/.../XHamsterService.java`, `CHANGELOG.md`, `docs/pre-release-uat-2026-09-10.md`.

## Loading pipeline (first paint, covers, playback resolution)

Date: 2026-10-24

Status: Implemented; JVM tests + emulator before/after measurement.

Decision:
- Home first page is persisted per source+category (`feed_cache`, 24 h TTL) and painted before the network refresh; refresh always overwrites it. Favorites/history stay synchronous because screens must be complete on their first frame (an async variant caused an empty favorites regression caught by `favoriteAppearsInAll` test).
- Covers are cached in a 24 MiB memory LRU + 96 MiB disk LRU (`cacheDir/cover_cache`, SHA-256 filenames, atomic tmp+rename, background prune), with single-flight de-duplication, a 60 s failure negative cache, and a shared OkHttp client for connection reuse. Disk hits/downloads are validated with `looksLikeImage` so error pages are never cached.
- Playback resolution is cached in memory: 2 min for signed media URLs, 10 min for episode lists; failures are not cached.
- First 8 above-the-fold covers are prefetched off the main thread after the feed loads; dedupe makes this free relative to composable requests.

Reason: repeated downloads dominated scroll/jank and cold-start perceived loading; measured time-to-hero dropped from 4.79/5.80 s (pre-change) to 4.50 s cold / 3.22–4.04 s warm on the same emulator (uiautomator sampling cost included).

Boundary: cache directory may be evicted by the OS; feed cache is only for perceived latency, never the source of truth.

## HIG round 2 closure

Date: 2026-10-24

Status: Implemented; device screenshots for dark dim, 200% columns, high contrast; 3 accessibility device tests pass.

Decision:
- Increase Contrast: full light/dark/high-contrast token set; detection uses the official `AccessibilityManager.isHighContrastTextEnabled()` on API ≥36 and falls back to `Settings.Secure/Global.high_text_contrast_enabled` on older/OEM devices; settings changes are observed live. Dynamic color is replaced by a tuned palette when any audited pair falls below 4.5:1 (text) / 3:1 (outline).
- A7: 11% black scrim over network artwork only in dark appearance (never intercepts touches); comic reader and forum WebView images are documented exceptions.
- C3/C7: adaptive grid width scales with fontScale ≥1.3 (capped 1.6) collapsing live/comic grids to one column; home grid switches to one column at ≥1.5; hero reserves end space so metadata never sits under the favorite button at 200% font.
- G3: 15 headings added across pages; cache action stateDescription; new `HigAccessibilityTest` covers 48 dp tabs, headings and labels.
- G5: ExoPlayer prefers zh-Hans/zh/en text tracks and selects undetermined-language tracks; only applies when the source provides subtitles.

Boundary: OEM-specific high-contrast switches that do not write the AOSP key are not detected; TalkBack traversal still needs a manual pass; API ≥36 official change listener is not registered (relaunch/settings observer only).

## Forum and video loading round 3

Date: 2026-10-24

Status: Implemented and measured on a single idle emulator; full device suite 32/32 PASS.

Decision:
- Forum loading is progressive: `ForumRepository.loadProgressively` emits a merged page as soon as each source finishes, so the fastest source paints first. Page 1 is snapshotted in preferences (`forum_cache`, 12 h, 40 posts, tolerant decoding) and painted before the refresh. Page N+1 is prefetched after page N succeeds (single-slot, 2 min TTL) so scrolling usually needs no network.
- Both forum sources now share one OkHttp connection pool instead of per-page `Jsoup.connect`; HTML is parsed with `Jsoup.parse(stream, null, url)` so charset is sniffed from the document. The startup HEAD warm-up was implemented, measured as an unquantified trade-off that sends requests to adult sites even when the user never opens the forum tab, and therefore removed.
- Video: detail-page fetches are cached at companion level for 60 s with single-flight, shared by `resolve()` and `resolveEpisodes()`; opening one video now performs one detail-page GET instead of two. ExoPlayer uses `DefaultLoadControl(15s, 30s, 800ms, 1500ms)` for faster start/rebuffer recovery; HLS start remains network-bound, so measured READY improvements are partly network variance and must not be attributed to the buffer change.
- Subtitle track selection now has a player overlay menu (48 dp, hidden when no text track); ExoPlayer calls `setOverrideForType`/`setTrackTypeDisabled`.

Boundary: forum snapshot hides refresh failures behind stale content; forum prefetch costs one extra page of mobile data; unknown OEM high-contrast keys remain undetected.

## Cover decoding cache and preload round 4

Date: 2026-10-24

Status: Implemented, measured on device; JVM 150/0/1, device suite 32/32, lint 0 errors.

Decision:
- `MediaImage`/`LiveCard` no longer hand a raw `ByteArray` to Coil. They build `ImageRequest.data(bytes).memoryCacheKey(url)` so scrolling back to the same layout size reuses the decoded bitmap instead of decoding again. The implicit Coil default disk cache was replaced with an explicit 128 MB disk cache in `cacheDir/image_cache` and a 25%-of-heap memory cache capped at 64 MB (Coil 3.3 defaults to a shared temp-dir cache and 15–20% memory).
- `CoverPrefetcher` warms the first screen of every image-bearing section: comics 10, live 8, search 8, player recommendations 6 (home already had byte prefetch). Byte fetches are limited to 4 concurrent, decode is enqueued into Coil, and a 10-minute per-URL dedupe keeps tab switches from repeating work. All warm-ups go through the same user-facing preload switch and non-metered-network check.
- Video preload: after the first home page loads, two detail pages are fetched into the 60 s page cache (1.5 s delay); once an episode list is ready, the next episode's playback URL is resolved into the 2 min playback cache (2 s delay). A new Settings switch ("视频预加载", default on, Wi-Fi only) controls both; disabling it logs `Prefetch: detail skipped` and performs no requests.

Measured: hero tap resolve 493–3180 ms → 48 ms (page cache hit); live cover warm-up 8 covers in 1.98 s; search 8 in 2.08 s; comics 10 enqueued (ms=0, async decode); Coil disk cache observed at 776 KB after a short session.

Boundary: preload costs Wi-Fi traffic; Coil `Precision.EXACT` means warm-up for other sizes may re-decode (same-size scroll reuse is the guaranteed gain); cached bitmaps are keyed by URL so a changed image may show stale until eviction.

## Launch brand intro (Netflix-style request, original execution)

Date: 2026-10-24

Status: Implemented and visually verified on an Android 14 emulator; release build only.

Decision:
- Added an original ~1.9 s launch brand moment: pure black stage, three light strokes drawing a stylized "N", a diagonal highlight sweep, then the wordmark expanding with letter spacing before a 120 ms fade. The app composes and starts loading underneath the overlay, so the intro does not delay first paint. Tap anywhere or Back dismisses it immediately.
- No audio. Netflix's ident is ~4 s, unskippable and scored; copying that would violate HIG F3 (animations must be cancellable) and G8 (avoid autoplaying audio), so only the *feel* (black stage, light-drawn mark, letter-spacing reveal) was reused with an original mark and no third-party assets.
- Reduce Motion: when `ValueAnimator.areAnimatorsEnabled()` is false, the complete mark + wordmark are shown statically for 0.7 s, then faded. No movement or drawing animation.
- Debug builds skip the intro (`BuildConfig.DEBUG`) so instrumented tests are not blocked by a full-screen overlay; the release build shows it once per process start (`rememberSaveable` prevents replay on rotation).
- System bar icons switch to light while the black stage is visible and restore to the theme afterwards.

Boundary: Android 12+ system splash still precedes the custom intro; TalkBack speech order not manually walked; intro is discretionary brand expression, not informational feedback.

## Cinematic launch intro round 2 (141)

Date: 2026-10-24

Status: Implemented; visually verified on emulator; device suite 32/32.

Decision:
- The product owner asked for a Netflix-near-identical intro. The Netflix ribbon ident and "ta-dum" audio are protected audiovisual trademarks, so they are not recreated. Instead the launch moment was rebuilt at a much higher production tier as an original piece: deep-space stage, a single luminous spiral drawn as a trimmed path with head sparks and trails, shockwave rings, particles, camera push, per-letter wordmark reveal with tracking, hairline finale, and an original synthesized 2.9 s sting (low impact + riser + metallic overtones + minor chord) shipped as a 60 KB AAC asset.
- The intro owns the first frames: `AuroraShelfApp` composition is deferred until the intro finishes; `AuroraViewModel` is created in `onCreate` so feed/cover warm-up runs during the intro; a black brand veil covers the app's first composition and fades out after the first drawn frame. Debug builds skip the intro for instrumented tests.
- Settings expose "开屏动画" and "开屏音效" switches (default on). Sound respects silent mode and zero media volume, and is skipped with Reduce Motion.
- HIG deviations are explicit: audio autoplay (G8) and timed auto-dismiss (G9) are accepted because the owner explicitly requested a cinematic title sequence; skippability (F3) and Reduce Motion (F5/G7) are preserved.

Boundary: Compose Canvas cannot reach Netflix's 3D-ribbon render quality; a真正同级的实现需要专业动效设计与预渲染视频资产。On the loaded emulator the measured wall clock is ~5.9 s vs the nominal 4.2 s due to dropped frames; real-device timing not verified.

## Launch film (142): pre-rendered cinematic asset

Date: 2026-10-24

Status: Implemented; played on device with full-bleed immersive mode; device suite 32/32.

Decision:
- After three rejected Compose-drawn intros ("cheap, no artistry"), the team changed production method: art-direct a still first, then render an offline film and ship it as an asset. The film is 6.6 s, 1080×2400, 30 fps, H.264+AAC (~3.4 MB), generated with Pillow/numpy per-frame compositing and ffmpeg, with an original synthesized score (drone + D minor add9 pad + reveal whoosh + metallic shimmer + soft sub + convolution reverb).
- Art direction: a blade of light carves a Didot-sculpted N out of darkness; brushed-platinum metal, thin-film iridescent edge, anamorphic streaks, Didot wordmark in warm silver with wide tracking, a single deep-crimson hairline, film grain/halation/teal-shadow-warm-highlight grade. No Netflix logos, ribbon design or audio are reproduced.
- Playback: ExoPlayer with `PlayerView` (no controls, ZOOM), system bars hidden during the film and restored afterwards; near-black start poster under the player, finished-lockup poster for Reduce Motion; skip on tap/Back; 9.5 s safety timeout; original audio muted when the user disabled it, the device is silent, or media volume is zero.
- The app is composed behind the film and its first frame is covered by the existing brand veil; `AuroraViewModel` is warmed in `onCreate` so feed data is ready when the film ends.

Boundary: this is a 2.5D offline render, not a real 3D pipeline; further quality needs professional 3D/motion-design assets. HIG deviations (autoplay audio G8, timed 6.6 s G9) are explicit user decisions; skippability and Reduce Motion remain.
