# DSH — Original Launch Intro Animation (140)

Date: 2026-10-24 18:15 HKT

## Delivered

- `ui/IntroAnimation.kt`: original black-stage intro — three light strokes draw an "N", diagonal sweep, wordmark letter-spacing reveal, fade out. Tokens for intro colors, pure `IntroTiming` timeline, Reduce Motion static fallback, tap/Back skip, accessibility label, system bar icon handling.
- `MainActivity.kt`: overlay above the already-composing app; release-only (`BuildConfig.DEBUG` skips for tests); `rememberSaveable` avoids replay on rotation.
- `IntroAnimationTest` (6 JVM tests).

## Verified

- Visual: mark/wordmark rendered on release; skip works (tap → home in 0.45 s); Reduce Motion shows static complete mark; intro does not delay app loading.
- JVM 156/0/1; device 32/32; lint 0 errors / 19 warnings / 3 hints; release 140 SHA fa63fead…, installed on both emulators.

## Decisions

- No sound, no Netflix assets, <2 s and skippable: copying the 4 s unskippable scored ident would violate HIG F3/G8.
- Android system splash still precedes it (platform behavior).

## Open

- TalkBack manual speech pass; OEM splash behavior; optional sound design explicitly deferred.
