# DSH — Forum/Video Loading Round 3 + HIG Close-out

Date: 2026-10-24 16:30 HKT

## Delivered

- Forum: progressive first-source paint, 12h first-page snapshot, page N+1 prefetch, shared OkHttp pool, startup warm-up removed after trade-off review.
- Video: shared 60s single-flight detail-page cache (GET 2→1), ExoPlayer LoadControl start tuning, subtitle button/menu/disable.
- HIG: 200% search entry icon-only (no truncation), OEM high-contrast candidate keys, search/offline-cache semantics, accessibility device tests 3→6.

## Measured (Release 138, same emulator)

- Forum cold first post 4.98 s (baseline 5.83/6.55), warm snapshot 2.04 s, page-2 prefetch 953 ms.
- Video tap→READY 4.2 s (baseline 7.1 s; network variance caveat), detail-page GET 2→1.

## Verified

- JVM 140/0/1; full device suite 32/32 PASS; lint 0 errors / 19 warnings / 3 hints; release 138 SHA 7f9a9b0c…, installed + cold start OK.
- 200% font search entry contentDescription verified, no truncated text.

## Parallel work / exclusions

- Another session is doing Netgrey branding (137-netgrey from separate worktree). Its uncommitted changes to `res/`, `HomeScreen` title, `docs/verification.md`, `.ai/PROJECT_STATE.md`, `.ai/TASKS.md` were preserved and excluded from this round's commits. HomeScreen's HIG changes (search icon, headings) remain uncommitted together with the brand title.
- Subtitle menu visual verification blocked by sample content: 4 random videos had no text track (button hidden by design), though one earlier video logged `text=1`.

## Follow-up

- Consolidate Netgrey branding + 138 code into one candidate after the brand session finishes; then rerun connected suite on the combined build.
- Forum snapshot hides refresh errors (UI only shows error when list empty) — add a top error banner.
- TalkBack manual pass, unknown OEM keys, E4 real-device evidence remain open.
