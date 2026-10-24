# DSH — Pre-rendered Cinematic Launch Film (142)

Date: 2026-10-24 20:10 HKT

## Context

The Compose-drawn intros (stroke N, spiral, flat ribbon N) were rejected as "cheap / no artistry". Switched from runtime drawing to an offline-rendered film asset with real art direction.

## Delivered

- 6.6 s / 1080×2400 / 30 fps / H.264+AAC film (~3.4 MB): light blade carves a Didot-sculpted platinum N out of darkness; anamorphic streaks, thin-film iridescent edges, Didot wide-tracked warm-silver wordmark, single deep-crimson hairline, grain/halation/filmic grade.
- Original synthesized score (drone, Dm add9 pad, whoosh, metallic shimmer, sub, reverb).
- App integration: ExoPlayer PlayerView full-bleed (system bars hidden), start-frame poster, lockup poster for Reduce Motion, tap/Back skip, audio toggle/silent respect, 9.5 s safety timeout, brand veil over app's first frame.
- Debug builds still skip the intro for tests.

## Verified

- JVM 154/0/1; device 32/32; lint 0 errors / 19 warnings / 3 hints.
- Release 142 SHA 460b4e47…, both emulators installed; full-bleed playback, skip, Reduce Motion verified; screenshots docs/screenshots/launch-film*.png; clip captures/intro-142-film.mp4.

## Open

- 2.5D renderer ceiling; a real 3D/motion-design pipeline would be the next tier.
- Real-device brightness/colour and TalkBack not verified.
- Parallel Netgrey branding still uncommitted.
