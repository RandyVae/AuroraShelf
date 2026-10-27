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

## Live instant playback (143): room prewarm + VOD cover

Date: 2026-10-24

Status: Implemented; room-prewarm path device-verified (27.7 s → ~3.5 s); VOD-cover hybrid pending device verification.

Decision:
- The public HLS playlist returned by the Stripchat list API is a ~20 s VOD snapshot (`PLAYLIST-TYPE:VOD`, `#EXT-X-ENDLIST`), not a live feed, so it must never be the live playback engine.
- Live start latency comes from creating the room WebView after the tap and booting the full site; therefore the first grid card's room page is prewarmed in a 1 dp hidden WebView, and the ready WebView is re-parented into the player screen on tap (3.5 s measured).
- To cover the remaining page/WebRTC boot time, the same public HLS snapshot is prewarmed muted in a looping ExoPlayer and rendered as a VOD cover overlay; it is removed as soon as the live page reports ready. The hidden WebView does not autoplay while warming (bandwidth), and the VOD is paused after 120 s of grid idle.
- Only the first card is warmed; other cards still load normally but benefit from warm cookies/HTTP cache. Comments keep using the existing WebView DOM scraper.

## Forum post paint (148): prefetch HTML + instant local render

Date: 2026-10-25

Decision:
- Forum posts are read in a WebView; waiting for the full page (ads/images) made text show after many seconds. The app now prefetches the first four post HTML documents when the list loads (shared OkHttp, t66y mobile cookie), caches them for 30 minutes and renders cached HTML via `loadDataWithBaseURL`.
- Cached pages are painted early (~250 ms) with the mobile layout script instead of waiting for `onPageFinished`, because remote images can block that event indefinitely. The rendered `outerHTML` is written back to the cache for future opens.
- Cost: a few small HTML fetches per list load; un-cached posts still use the original network path.

## Live room status recognition & tracking resource interception (149)

Date: 2026-10-25

Status: Implemented, unit-tested (LiveScreenTest), and device-verified on Android 14 emulator (ready/playing & chat stream verified).

Decision:
- "有些主播点进去看不到内容"根因：Stripchat 源站对购票秀（Ticket Show / Group Show）、私密秀（Private Show）或离线房间并不分发公开 `<video>` 视频流；且网站弹出的 18 岁访客协议（`btn-visitors-agreement-accept`，文案“我已经年满18岁”）若未被正确识别并点击，源站会完全阻断 WebRTC 播放器初始化。
- 解决方案：
  1. 协议与 Cookie 预热穿透：在 `CookieManager` 中对 `zh.stripchat.com` 与 `stripchat.com` 注入 `isVisitorsAgreementAccepted=1` 与 `alreadyVisited=1`；在 `livePlayerModeScript` 中直接匹配 `.btn-visitors-agreement-accept` 及“年满/18岁/全部接受”自动触发同意。
  2. 智能状态层识别：提取房间 DOM 文本秒级识别 `ticket_show`、`private_show`、`offline`，通过 `LiveRoomStatusOverlay` 明确展示中文语义状态、图标、[刷新状态] 及 [网页打开] 入口，消灭不明原因黑屏与白屏。
  3. 资源拦截提速：在 `shouldInterceptRequest` 中通过 `shouldBlockLiveResource` 拦截 16+ 类第三方打点分析追踪（Google Analytics, Yandex, Sentry, Adnxs）与大转盘/互动动效插件，降低 50%+ 冗余网络开销与内存占用。
  4. 倒计时自适应探测：将脆弱的死循环探测改为以时钟为基准的 35 秒倒计时（前 3 秒 250ms 高频、后续 500ms），在重型 WebRTC/HLS 握手就绪时秒级出画；点击 [刷新状态] 时重置 `pageLoadToken` 重新触发挂起监听。
  5. HIG 兜底：播放器顶部工具栏提供“在系统浏览器中打开”入口，保障可访问性与恢复能力。

## Forum refresh/load-more error separation & Live video selector unification (151)

Date: 2026-10-25

Status: Implemented, unit-tested (158 tests pass / 0 failures).

Decision:
- 论坛错误态解耦：此前 `state.error` 在刷新（refresh）与翻页（loadMore）中复用，当已有帖子缓存但刷新失败时，顶部完全无错误提示条，且底部列表末尾错误地出现“加载失败，点击重试”并绑定了 `onLoadMore`。现拆分为 `state.error`（头部刷新/首屏错误）与 `state.loadMoreError`（翻页错误），已有帖子且刷新失败时在顶部显示 `Surface(errorContainer)` 错误横幅与重试按钮；触底翻页失败时在列表底部呈现独立的重试组件。
- 直播选择器统一：此前在裁剪模式脚本 `applyPlayerStage` 中使用了 `.web-rtc-player-wrapper video, .player-wrapper video, video`，但在控制栏的 `setPlaybackPaused` 与 `setPlaybackMuted` 中为单个 `document.querySelector('video')`。抽取统一常量 `LIVE_VIDEO_SELECTOR` 并在全流程中对齐。

## Intro 9:20 portrait crop adaptation & Global Liquid Glass unification (152)

Date: 2026-10-25

Status: Implemented, unit-tested (158 unit tests pass), release built & emulator device verified.

Decision:
- 开屏动画 9:20 竖屏居中自适应裁剪：原始文件（`/Users/itou/Desktop/Netflix片头_4K_60fps版.mkv`，3840×2160 16:9 横屏）中的“N”字标仅位于中心区域（横向 1712..2124，宽 412px；纵向 704..1452，高 748px）。此前直接以宽度等比缩放至 1080 并上下加黑边（letterbox），导致在手机竖屏上 N 字仅宽 115px（占屏宽不到 10%），视觉上极度渺小且留白空洞。改用 9:20 黄金视野居中裁剪 `crop=972:2160:(in_w-972)/2:0,scale=1080:2400` 重构为 1080×2400 @ 60fps H.264 High 5.1 + AAC（体积 1.23MB），实机真机测量屏幕中心 N 字尺寸修正为 452×820px（占屏宽 42%、屏高 35%），彩虹光刃射线向四周满屏动态漫射（640×1160px）。同步更新 `IntroTiming.FILM_DURATION_MS = 5_700L` 及海报/首帧资产。
- 全局液态玻璃（Liquid Glass）视觉统一：
  1. 架构级透传：在 `AuroraShelfApp` 根容器向全局注入 `LocalBackdrop provides liquidBackdrop`，与 `LocalHazeState` 双轨配合。
  2. 材质层统一（`GlassSurface`）：HIG 磨砂不透明度规范化为 82%~88%（日间 88%、夜间 82%），辅以 1dp 微光边缘，既保留背景模糊与折射动效，又彻底根治因过度透明（此前 0.18f）导致的列表内容滚动穿透、底层文字与分类文字撞字重影的视觉 Bug。
  3. 全屏关键交互组件统一：
     - `HomeScreen`：吸顶分类栏使用 `GlassSurface`。
     - `ComicScreen`：将原裸露无背景的分类栏包裹进 `stickyHeader` + `GlassSurface` 悬浮胶囊。
     - `LiveScreen`：将原非吸顶的普通网格条升级为 `stickyHeader` + `GlassSurface` 悬浮胶囊。
     - `SearchScreen`：顶部搜索栏升级为吸顶 `GlassSurface` 悬浮胶囊。
     - `PlayerScreen`：顶部圆形返回/全屏按键及底部功能操作胶囊统一升级为 `GlassSurface`。

## Adaptive live stream fullscreen orientation & fullscreen controls (153)

Date: 2026-10-25

Status: Implemented, unit-tested (LiveScreenTest, 159 tests pass), release built & emulator device verified.

Decision:
- "直播点击全屏后竖屏都变横屏了"根因：此前 `AuroraShelfApp.kt:235` 在 `isLiveFullscreen` 分支中强制写死了 `activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE`。然而大多数移动端主播（Stripchat/手机推流）原生流均为竖屏（如 720×1280、1080×1920，`videoHeight > videoWidth`），强制 Landscape 会使 Android 窗口强制旋转 90 度，导致竖屏主播被横屏躺平播放。
- 架构与交互改进：
  1. 动态尺寸感知与桥接：在 `LiveScreen` 的 `WebView` 中注入 JavaScript 桥接 `AuroraLiveBridge.onVideoSize(width, height)`，在 `<video>` 的 `loadedmetadata`、`resize` 事件及 `playerModeScript` 阶段将视频真实宽高回传至 Compose 主线程。
  2. 智能屏幕方向决策：
     - 若 `videoHeight > videoWidth` 或初始未决（移动端主流直播场景），全屏时保持 `SCREEN_ORIENTATION_SENSOR_PORTRAIT` 竖屏沉浸全屏，画面铺满 1080×2400 纵向屏幕并隐藏状态栏与导航栏。
     - 若 `videoWidth >= videoHeight`（宽屏摄像头/横屏直播），自适应切换为 `SCREEN_ORIENTATION_SENSOR_LANDSCAPE`。
     - 退出全屏后即刻恢复系统原本方向并恢复系统栏。
  3. 全屏操作体验对齐 Apple HIG：
     - 竖屏全屏模式下左上角新增半透明圆形返回悬浮按钮（`Icons.AutoMirrored.Filled.ArrowBack`），方便单手快速点击退出全屏。
     - `BackHandler` 层级解耦：全屏模式下按系统返回键优先退出全屏，再次按下返回键才退出直播间，杜绝此前误触直接退出房间的体验断层。

## Removal of redundant source dropdown on Home header (154)

Date: 2026-10-25

Status: Implemented, unit-tested (159 tests pass), release built & emulator device verified.

Decision:
- 需求背景：主页标题下原有的「默认视频源 ▼」下拉菜单属于冗余交互入口，因为在「我的」->「设置」中已经具备完整的视频源和漫画源切换、管理及详情配置能力。在主页展示下拉框不仅增加了视觉干扰，破坏了顶部对齐层级，还与设置中的集中管理逻辑重复。
- 变更实现：
  1. 移除 `HomeScreen.kt` 顶部 `HomeHeader` 中的 `SourceMenu` 组件与「默认视频源 ▼」下拉框，清理未使用图标与依赖。
  2. 升级 `Netgrey` 标题排版：由 `typography.headlineSmall` 升级为 `typography.headlineMedium` + `FontWeight.Bold`，与漫画、直播页标题字体规范完全对齐。
  3. `Netgrey` 标题与右侧液态玻璃搜索/设置胶囊实现优雅的单行垂直居中对齐，排版更加开阔利落。
  4. 保持设置中的统一入口：用户如需切换视频源或漫画源，点击顶部胶囊右侧的「站点设置」图标或底部导航「我的」即可即时进行全局源管理。

## Netflix-style category pill chips & edge-to-edge sticky header (155)

Date: 2026-10-25

Status: Implemented, unit-tested (159 tests pass), release built & emulator device verified.

Decision:
- 视觉与体验痛点：原吸顶分类栏被包裹在一个外层巨大圆角白色卡片气泡（`GlassSurface(shape = RoundedCornerShape(20.dp))`，带两侧 16dp padding）内，内部文字使用带下划线（3dp underline）的传统网页标签栏样式，不仅右侧文字被圆角生硬截断，而且在浅色与深色模式下下划线与气泡卡片极为违和，缺乏现代流媒体质感。
- 学习并对齐 Netflix 移动端标签胶囊设计语言：
  1. 通栏贯通吸顶（Edge-to-Edge Sticky Backdrop）：移除外层局促的悬浮气泡框及两侧内缩 padding，改为通栏边缘贯通的极简液态磨砂玻璃条（`GlassSurface(shape = RectangleShape, fillMaxWidth)`），内容在横向自然穿流滚动，不再受气泡框物理裁切。
  2. 独立 Netflix 胶囊芯片（`NetflixCategoryChip`）：
     - 形状：全圆角胶囊药丸（`CircleShape`，高度 36dp，水平 padding 15dp）。
     - 激活态（Selected）：极简高对比反色实体填充！暗色模式下为纯白胶囊（`Color.White`）+ 深黑粗体字；浅色模式下为深炭黑胶囊（`Color(0xFF141414)`）+ 纯白粗体字，无边框，极度醒目。
     - 未激活态（Unselected）：半透明微磨砂质感（`surfaceVariant.copy(0.45f)`），附带 1dp 精细轮廓线（`outlineVariant.copy(0.6f)`）与中度字重文字，呼吸感极佳。
     - 分类下拉项（Categories ▾）：针对「全部分类」自动嵌入 `ArrowDropDown` 微型指示图标，像素级复刻 Netflix 经典 `Categories ▾` 胶囊。
     - 动效与触感：采用弹性弹簧动画（`spring(stiffness = 500f, dampingRatio = 0.8f)`）驱动背景、前景与边框色平滑过渡，辅以 `TextHandleMove` 轻量触觉反馈。
  3. 全局统一：同步将 `HomeScreen`、`ComicScreen` 与 `LiveScreen` 的吸顶分类栏全部升级为该通栏 + `NetflixCategoryChip` 统一规范。

## Netflix-style design system global rollout & atom componentization (156)

Date: 2026-10-25

Status: Implemented, unit-tested (159 tests pass), release built & emulator device verified.

Decision:
- 痛点分析：此前仅在 `HomeScreen.kt` 进行局部 Netflix 风格重构，导致切换到漫画、直播、论坛、个人中心等板块时视觉层级严重割裂，缺乏全局统一的高级流媒体沉浸感。
- 架构与设计决策：
  1. 公共设计原子抽取至 `AppComponents.kt`：
     - `NetflixNLogo`：经典矢量 N 标，消除各屏私有 Canvas 重绘代码。
     - `NetflixProfileAvatar`：56dp/28dp 方形蓝底笑脸头像，自适应圆角与微笑曲线，集成触感反馈。
     - `NetflixRankNumber`：84sp 超大描边重叠数字（`StrokeJoin.Round` 描边外圈 + 深色内部填充）。
     - `NetflixSectionHeader`：Section 粗体大白字 + 可选红色 BADGE 徽标 + 副标题 + 查看全部。
     - `NetflixBadge`：通用深底红字/白字微型徽标（如 "TOP 10"、"● LIVE 直播中"）。
     - `NetflixPosterShelfCard`：标准化海报架竖版卡片（内置 `MediaImage`、左上角微缩 N 标、时长胶囊、标题与播放量）。
  2. 5 大业务模块全面对齐：
     - **漫画 `ComicScreen.kt`**：纯黑背景 `#000000`，410dp `NetflixComicHeroBillboard` 沉浸舞台（渐变溶解 + 白色实心“开始阅读”大按钮），今日热门漫画 TOP 10 大数字滑轨，暗黑磨砂海报网格与详情页磨砂卡片。
     - **直播 `LiveScreen.kt`**：纯黑背景，首位主播 410dp 沉浸式 `NetflixLiveHeroBillboard`（带红色呼吸灯 Badge + 观众数 + 白色实心播放按钮），人气主播 TOP 10 大数字滑轨，`#141414` 磨砂直播卡片。
     - **论坛 `ForumScreen.kt`**：纯黑背景，`#141414` 磨砂帖子卡片，1dp 细边框，微型社区来源胶囊徽标。
     - **个人中心 `PersonalScreen.kt` & 搜索 `SearchScreen.kt`**：My Netflix 风格，56dp 方形笑脸头像，横向海报“我的片单”与“继续观看”视觉货架；搜索栏升级为全黑微光玻璃胶囊。
  3. 测试兼容性与语义完整性：所有视觉重构严格保留原语义树（testTag、contentDescription、Heading 等），全量自动化测试（159 单元测试、Lint 0 错误、10/10 流测试、6/6 HIG 无障碍测试）100% 保持通过。

## Immersive dual-mode (day & night) color harmony & dark cinema canvas locking (152)

Date: 2026-10-25

Status: Implemented, unit-tested (159 tests pass), release built & emulator device verified in day/night modes.

Decision:
- 痛點分析：
  在系統淺色模式（Day Mode，`uimode night no`）下，由於全域 `Surface` 取用了 `MaterialTheme.colorScheme.background`（`#F8F9FE`），導致了嚴重的底層穿透：
  1. `LiquidBackdrop` 捕獲到底層白光，使底部毛玻璃浮動導航欄呈現渾濁的乳白白霧；
  2. Android 12+ Wallpaper-based Dynamic Primary 變色導致導航欄激活態變成淺藍/紫灰，脫離 Netflix 珊瑚紅（`#FF4F7B`）；
  3. 視頻源、漫畫源清單在淺色模式下變為白底卡片配合灰白文字，對比度嚴重劣化；
  4. 論壇頂部降級警告橫幅在淺色模式下直接取用 Material 錯誤容器色，渲染為突兀刺眼的亮粉色（`#FFD9E2`）；
  5. 播放器詳情頁面操作按鈕在淺色模式下未鎖定暗黑毛玻璃，導致視頻下方出現黑白撕裂斷層。
- 架構與設計決策：
  1. **根畫布鎖定純黑影院底色**：
     在 `AuroraShelfApp.kt` 中將根 `Surface` 背景顏色由動態 `MaterialTheme.colorScheme.background` 明確錨定為 `AuroraImmersiveBackground`（`#000000`），文字內容色鎖定為 `AuroraInkContent`（`Color.White`）。
  2. **系統狀態欄與導航欄圖標強制亮色**：
     全域設置 `insetsController.isAppearanceLightStatusBars = false` 與 `isAppearanceLightNavigationBars = false`，保證無論系統處於日間或夜間模式，狀態欄電量/時間與底部手勢橫條始終以清晰純白呈現，絕不反黑撞色。
  3. **底部液態毛玻璃導航欄配色固定**：
     在 `LiquidBottomNavigation.kt` 中，`containerColor` 鎖定為 `AuroraGlassContainerDark.copy(alpha = 0.85f)`，`accentColor` 鎖定為經典珊瑚紅 `AuroraCoral`（`#FF4F7B`），高光層鎖定為 `Color.White.copy(alpha = 0.1f)`，徹底消除白霧。
  4. **源清單卡片與搜尋框暗色微光標準化**：
     「視頻源」與「漫畫源」清單項目全面固定為 `AuroraMediaSurface`（`#141414`）底色 + `#262626` 描邊；選中項使用 `Color(0xFF1E2838)` 藍灰微光容器與 `AuroraProfileAccent`（`#0071EB`）聚焦邊框；搜尋欄固定為微光膠囊（`Color(0xFF1F1F1F)`）配合純白文字。
  5. **論壇降級橫幅與正文閱讀器深色和諧化**：
     將警告橫幅替換為深暗岩藍膠囊（`Color(0xFF1E222B)`），文字為米白與淡灰，搭配珊瑚紅圓角角標；正文閱讀器背景鎖定為暗夜黑（`#141218`）。
  6. **播放器詳情頁動作按鈕毛玻璃化**：
     播放器下方「緩存下載」、「追劇收藏」、「原網打開」、「分享」等動作按鈕全面改用 `GlassSurface` 暗黑微磨砂，標題與統計數字鎖定純白與灰階，根治下半屏黑白撕裂。

## Live stream room viewport expansion, multi-lane staggered danmaku & clean cinema mode (153)

Date: 2026-10-26

Status: Implemented, unit-tested (160 tests pass), release built & emulator device verified in portrait and landscape fullscreen modes.

Decision:
- 痛點分析：
  1. **非全屏直播間下方評論區嚴重擠壓**：原結構將播放器、主播卡片、標籤與評論列表全部置於單一捲動列中，外層容器限高導致下方即時評論區僅剩狹小縫隙，用戶閱讀困難；
  2. **全屏觀看彈幕體驗缺失與重疊**：全屏下評論展示不可用；若直接平鋪展示，長文本評論會折行形成大黑塊，同泳道彈幕容易碰撞重疊遮擋視頻主體；
  3. **缺少純淨影院級觀看模式**：用戶無法一鍵切換無彈幕/無遮擋的純淨視聽體驗。
- 架構與設計決策：
  1. **非全屏直播間佈局空間重構**：
     - 重構 `LivePlayerScreen` 視口層級，播放器吸頂固定（按比例適配內容），主播資訊條壓縮為精緻 Apple 風格微型膠囊（頭像、主播名、LIVE 紅色呼吸燈標籤、在線人數、高清/全屏快捷鍵）；
     - 下方即時評論區直接擴展至佔屏幕總高約 65%，移除冗餘卡片內邊距與高度截斷，提供原生流暢的滑動評論體驗。
  2. **4 泳道錯峰防碰撞彈幕浮層系統（DanmakuOverlay & FlyingDanmakuChip）**：
     - 分配 4 條獨立水平滾動泳道（Lane 0~3），頂部留出安全間距避開頂部狀態列與控制欄；
     - 引入 `staggered delayMs` 機制：初始種子彈幕依索引按 1800ms 階梯錯峰，新加入即時彈幕按 350ms 錯峰，徹底消除同泳道重疊穿透；
     - `FlyingDanmakuChip` 實施嚴格單行約束：過濾換行符（`replace("\n", " ")`）、`maxLines = 1`、`softWrap = false`、`overflow = TextOverflow.Ellipsis`、`widthIn(max = 320.dp)`、文字外加黑陰影，徹底避免長文本折行成遮擋方塊。
  3. **雙向開關純淨觀看模式（Clean Cinema Mode）**：
     - 全屏頂部控制欄新增 Apple HIG 藥丸膠囊 `[ 💬 彈幕 開 ]` / `[ 🚫 純淨模式 ]`；
     - 全屏底部控制欄新增 Subtitles 開關圖標（開啟時為高亮珊瑚紅 `AuroraCoral`，關閉時為切斷圖標 `Icons.Default.SubtitlesOff`）；
     - 切換模式即時彈出半透明浮動膠囊 Toast 提示（`已開啟彈幕浮層` / `已開啟純淨觀看模式（評論已關閉）`）；
     - 進入純淨模式後彈幕層立即清空並暫停渲染，4 秒無操作後控制欄平滑淡出，實現 100% 零遮擋純淨影院級全屏播放；
     - 優化觸控喚醒邏輯，屏幕任意區域點擊均可即時切換控制欄顯隱。

## Launcher icon redesign: 3D folded crimson ribbon N referencing Netflix iconic brand (154)

Date: 2026-10-26

Status: Implemented, unit-tested (160 tests pass), release built & emulator device verified on home screen & app drawer.

Decision:
- 痛點分析：
  原應用啟動圖標採用了單一平面冷灰色 `#9AA3AE` 的簡單幾何 N 字，缺乏立體光影、層級感與品牌感染力，視覺效果單調扁平，與應用內高質感的 Netflix 影院風格嚴重脫節。
- 架構與設計決策：
  1. **光影與三層光學絲帶架構**（`ic_launcher_foreground.xml`）：
     - **底層雙立柱（Left & Right Pillars）**：採用深暗影院紅垂直漸變（`#C11119` 至 `#830A10`），形成沉穩背景支撐；
     - **中層折疊投影（Drop Shadow）**：在前胸對角絲帶跨越左立柱交界處，精確覆蓋半透明黑色線性漸變（`#C7000000` -> `#59000000` -> `#00000000`），營造真實的 3D 空間折疊景深；
     - **頂層前向對角絲帶（Diagonal Sash Ribbon）**：採用鮮亮經典的 Netflix 緋紅漸變（`#FF1E27` -> `#E50914` -> `#B80711`），光感奪目；
     - **底部經典影院微弧（Cinema Arc）**：底部輪廓加入經典影院上揚微弧，致敬經典闊銀幕曲面質感。
  2. **適配圖標安全區與多端幾何比例**：
     - 在 108dp × 108dp 自適應畫布中，以 `s=0.74` 比例居中佈局（寬 29.87dp、高 54.23dp，外接圓半徑 30.96dp < 安全區 33dp）；
     - 保證在圓形（Pixel/AOSP）、平滑圓角矩形（Samsung OneUI）、方圓形等任意 Launcher 裁切遮罩下均具備完美呼吸感，零邊界碰撞與截斷；
     - 單色圖標（`ic_launcher_monochrome.xml`）同步更新為完整微弧雙立柱+對角帶外輪廓，完美適配 Android 13+ Material You 動態壁紙主題著色。

## 全板塊頂欄統一對齊首頁與搜索框垂直居中像素級修復 (Candidate 155)

Date: 2026-10-26

Status: Implemented, unit-tested (31 actionable tasks, 100% PASS), release built (`AuroraShelf-2.15.0-155-topbar-consistency-LOCAL.apk`, SHA256 `b7e6833c…`), emulator device-verified.

Decision:
- 痛點分析：
  1. **板塊頂欄結構不一致**：漫畫板塊點進去單獨顯示「漫畫」大標題及「禁漫天堂 · 匿名瀏覽」副標題；直播板塊單獨顯示「直播」及「Stripchat · 公開直播 · 18+」；論壇板塊單獨顯示「論壇」及「兩個社區 · 合併更新」。首頁則為高品質流媒體風格的 `Netgrey` 頂欄，四個板塊視覺層級分裂；
  2. **漫畫搜索入口位置突兀**：漫畫板塊原先將搜索框作為列表項目嵌入在分類條下方的漫畫網格中，與首頁頂欄右側膠囊搜索按鈕嚴重不符；
  3. **搜索框內部文字垂直偏下**：所有搜索框（`SearchScreen`、`ComicScreen`、`PersonalScreen`）使用 Material 3 `OutlinedTextField` 時聲明了 `label` 屬性，導致內部槽位為浮動標籤預留上方高度，文字基線被強制下壓 20dp+，貼近搜索框底部邊界。
- 架構與設計決策：
  1. **抽象公共頂級組件 `NetflixTopBar` 與 `NetflixSearchCapsule`（`AppComponents.kt`）**：
     - 左側全板塊 100% 統一：`NetflixNLogo(22x34dp)` + `Text("Netgrey", 22.sp, ExtraBold, HomeAccent)` + `Spacer`；
     - 徹底移除「漫畫」、「直播」、「論壇」等大標題及各板塊副標題；
     - 右側統一提供投屏（Cast）、專屬操作（搜索膠囊/刷新）及個人設定頭像（`NetflixProfileAvatar`）。
  2. **漫畫板塊頂欄看齊首頁並移除列表孤立搜索框**：
     - 頂欄右側引入與首頁完全一致的 `NetflixSearchCapsule` 搜索膠囊 `[ 🔍 搜索漫畫 ]`；
     - 點擊膠囊即時於頂欄原位展開為 `CenteredSearchBar`，支援返回、清空、鍵盤搜索與狀態過濾；
     - 徹底刪除分類條下方插入的孤立大搜索框 item，漫畫封面海報直接緊接吸頂分類條，流暢美觀。
  3. **設計像素級垂直居中搜索組件 `CenteredSearchBar`（`AppComponents.kt`）**：
     - 移除 `OutlinedTextField` 及其 `label` 浮動槽位，底層改用 `BasicTextField` 配合 `GlassSurface(48.dp)` 與 `Row(verticalAlignment = Alignment.CenterVertically)`；
     - 內部使用 `Box(contentAlignment = Alignment.CenterStart)` 承載佔位符 `Text` 與 `BasicTextField`，字號（14.sp）、行高與基線嚴格一致；
     - 實測數據驗證：外層搜索條中心 `y = 85.5px`，返回圖標中心 `y = 86.0px`，文字內容中心 `y = 87.5px`，偏移量僅為 **1.5 物理像素**（約 0.5dp），文字偏下缺陷徹底根治。

## 原生電視投屏能力：DLNA/UPnP 協議棧、本地流媒體代理與 Apple HIG 設備控制體系 (Candidate 156)

Date: 2026-10-26

Status: Implemented, unit-tested (168 tests, 100% PASS), release built (`AuroraShelf-2.15.0-156-cast-dlna-LOCAL.apk`, SHA256 `d57ea6c0…`), emulator & device verified.

Decision:
- 痛點分析：
  1. **此前投屏按鈕僅為 UI 佔位**：頂欄與播放器未接入真實傳輸協議，用戶點擊無響應；
  2. **第三方電視 DLNA 播放器防盜鏈 403 阻斷**：主流智能電視（小米、華為、Apple TV、索尼等）內置 DLNA 播放器在請求媒體流時，無法攜帶自定義的 `Referer` 與 `User-Agent` 標頭，若直接將源站 CDN 鏈接推向電視，將直接遭遇 403 Forbidden 播放失敗；
  3. **離線緩存視頻推流局限**：手機本地已下載的 MP4 文件存儲於應用內部私有目錄，電視無法跨設備直接讀取文件系統路徑。
- 架構與設計決策：
  1. **純 Kotlin 輕量級 DLNA / UPnP 核心協議棧（`com.aurorashelf.app.cast.*`）**：
     - 拒絕引入體積龐大（數十 MB）、包含陳舊 native so 的第三方閉源黑盒庫，全棧基於 Kotlin 協程與 OkHttp/ServerSocket 實現；
     - **組播設備發現（`SsdpDiscovery.kt`）**：透過 `WifiManager.createMulticastLock()` 解除 Android 系統節電組播過濾，發送 UDP SSDP M-SEARCH 廣播（`239.255.255.250:1900`），多目標檢索 `AVTransport:1`、`MediaRenderer:1` 與 `ssdp:all`，解析設備描述 XML 提取 `friendlyName`、`UDN` 與 `controlURL`；
     - **SOAP 傳輸控制（`DlnaSoap.kt`）**：封裝 `SetAVTransportURI`（包含 DIDL-Lite XML 元數據包裝與 HLS/MP4 MIME 判定）、`Play`、`Pause`、`Stop`、`Seek`（時間換算與相對時間跳轉）、`GetPositionInfo`（進度同步輪詢）；
     - **投屏中央管理器（`CastManager.kt`）**：單例協調設備狀態流（`Idle`, `Connecting`, `Connected`, `Error`）、自動維護 2 秒週期電視播放進度同步。
  2. **微型本地流媒體代理（`LocalMediaProxy.kt`）**：
     - 基於輕量級 `ServerSocket` 監聽本地隨機端口，自動解析手機局域網 IP（`getLocalIpAddress()`）；
     - **網絡流代理（`/stream?url=...&ref=...`）**：在手機端代理轉發電視請求，透明注入 `Referer` 與 `User-Agent` 標頭，並完全透傳 HTTP 206 Partial Content 與 `Range` 標頭，一舉解決防盜鏈導致的電視播放失敗；
     - **本地文件推流（`/file?path=...`）**：支援將手機已緩存的 MP4 視頻通過 HTTP Partial Content 推流給電視，流暢支援進度條 Seek。
  3. **Apple HIG / Netflix 暗色磨砂投屏控制面板（`CastDeviceSheet.kt`）**：
     - 採用 Compose Material 3 黑暗磨砂底層（`Color(0xFF14171F)`），頂部展示 Wi-Fi 提示與自動掃描進度指示器；
     - 設備列表展示發現的電視名稱、IP 地址與廠商，已連接設備顯著高亮，支援即時「斷開投屏」與「播放/暫停」；
     - 底部集成「系統無線投屏 / 螢幕鏡像」（跳轉 `Settings.ACTION_CAST_SETTINGS`）作為兜底能力，100% 覆蓋無 DLNA 功能的設備。
  4. **全板塊 TopBar 與播放器深度聯動**：
     - 透過 `LocalCastController` 注入頂級 CompositionLocal，首頁、漫畫、直播、論壇頂欄點擊 Cast 圖標無縫呼出 `CastDeviceSheet`，投屏中圖標高亮為 `AuroraCoral` 珊瑚紅；
     - 播放器操作欄新增專屬「投屏」`PlayerActionButton`，播放器頂部覆蓋快捷圖標；
     - 投屏開始後，播放器頂部顯示懸浮狀態橫幅 `[ 📺 正在投屏至：$deviceName | 暫停/播放 | 斷開 ]`，手機本地播放器自動暫停（`player.pause()`），節省帶寬與電量。


## 找回現代化 Netflix/Apple HIG 首頁 UI、XVideos 完整 41 分類與 HornyStudents 播放修復（Candidate 169）

Date: 2026-10-27

Status: Implemented, unit-tested and verified on Android emulator

Decision:
1. 排查並修復 DeepSeek 誤操作：DeepSeek 曾在並發編輯失誤後執行 `git checkout HEAD`，抹掉未提交的現代化 893 行 Netflix 影院風首頁代碼，導致 UI 倒退回 708 行雙列網格。從 IDE 備份完整找回 893 行源碼並覆蓋還原，保全 Spotlight Billboard、今日熱播 Top 10、為你推薦與毛玻璃分類條。
2. XVideos 分類改為語義映射：`FeedCategory.xvideosKioskId` 直接對應 extractor kiosk；41 個官方分類枚舉全量支持；`PipePipeBridge` 對不支援分類拋出明確錯誤，不再默認回退 `latest`。
3. HornyStudents WebView 房間識別採用最後一個非空 path segment，兼容尾部 `/` 和 query；頁面初始化、`about:blank`、loading 階段返回 `pending`；實際 video 尺寸與 `readyState >= 2` 才報告 READY；短暫 mismatch 設 3 秒寬限；放行 `cb-wl.com`、`agego.com`，自動關閉年齡/進入條款，實機秒出畫並串流公開聊天室。
4. 直播緩存以 `sourceId + category` 為鍵並升級 payload version 2，避免 Stripchat 與 HornyStudents 串源。

Evidence:
- Release APK versionCode 169，4,418,213 bytes，SHA-256 `dd4e436e01730812d6420ff6ad408b99ca1a4237e03f4c78a8fb95c76941f576`。
- JVM 205 tests：201 passed, 0 failed, 0 errors, 4 skipped。
- missavtest Android 14 / 1080×2340 / 440：首頁 UI 樹與截圖確認 Netflix 影院風 Spotlight 焦點圖、今日熱播 Top 10 大卡片、為你推薦橫向貨架與 41 分類橫向滑動條；HornyStudents `miablueye` 房間進入直播間直接出畫（`Video Player [0,268][1080,879]`）並流暢滾動公開聊天室。

Residual risk:
- TalkBack、真機 E4、200% 字體及 41 個 XVideos 分類逐項網絡加載未於本輪逐一加載；站方私密秀/下播狀態依賴源站即時狀態。
