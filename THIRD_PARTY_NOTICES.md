# Third-party notices

This document lists the open-source libraries, runtime components, and public protocol references integrated or consulted in Netgrey (`AuroraShelf`).

---

## 1. PipePipeExtractor (GNU General Public License v3.0)

The Android client integrates `PipePipeExtractor` source code under `third_party/pipepipe-extractor`. `PipePipeExtractor` is licensed under the **GNU General Public License version 3 (GPL-3.0)**. The complete license text is preserved at `third_party/pipepipe-extractor/LICENSE`.

In compliance with GPLv3, complete corresponding source code for the Android application and its `PipePipeExtractor` integration is published in the official repository (<https://github.com/RandyVae/AuroraShelf>). The extractor is used solely for public video catalog browsing, search, metadata parsing, and playback-stream resolution.

Upstream project: <https://github.com/InfinityLoop1308/PipePipeExtractor>

---

## 2. Spine WebGL Runtime (Spine Runtimes License Agreement)

The built-in offline game integrates the Spine WebGL Runtime (`spine-webgl.min.js`) for 2D skeletal animation rendering. Copyright (c) 2013-2025, Esoteric Software LLC. Distributed under the **Spine Runtimes License Agreement** (last updated April 5, 2025).

Full license text is bundled in both client asset trees at:
- `app/src/main/assets/www/vendor/spine-LICENSE.txt`
- `ios/AuroraShelf/Resources/www/vendor/spine-LICENSE.txt`

Upstream license reference: <http://esotericsoftware.com/spine-editor-license>

---

## 3. AndroidX Media3 / ExoPlayer (Apache License 2.0)

The Android client uses AndroidX Media3 (`media3-exoplayer`, `media3-exoplayer-hls`, `media3-ui`, `media3-database`, `media3-datasource`, v1.11.0) for hardware-accelerated MP4/HLS playback and local offline media caching. Copyright The Android Open Source Project; licensed under the **Apache License 2.0**.

Project: <https://github.com/androidx/media>

---

## 4. OkHttp (Apache License 2.0)

The Android client uses Square OkHttp (transitively and directly in `AuroraHttpClient`, `AccountRepository`, and Coil network transport) for HTTP/2 connection pooling, DoH/custom DNS resolution, and TLS transport. Copyright Square, Inc.; licensed under the **Apache License 2.0**.

Project: <https://github.com/square/okhttp>

---

## 5. Coil 3 (Apache License 2.0)

The Android client uses Coil 3 (`io.coil-kt.coil3:coil-compose` and `coil-network-okhttp`, v3.3.0) for asynchronous image loading, memory/disk caching, and custom header/de-scrambling pipelines. Copyright Coil Contributors; licensed under the **Apache License 2.0**.

Project: <https://github.com/coil-kt/coil>

---

## 6. Jsoup (MIT License)

The Android client uses Jsoup (`org.jsoup:jsoup`, v1.21.2) for HTML parsing, DOM traversal, and sanitization across video, comic, and forum adapters. Copyright 2009-2025 Jonathan Hedley; licensed under the **MIT License**.

Project: <https://jsoup.org/> / <https://github.com/jhy/jsoup>

---

## 7. Haze (Apache License 2.0)

The Android client uses Haze (`dev.chrisbanes.haze:haze` and `haze-materials`, v1.7.3) for hardware-accelerated background blur in Jetpack Compose surfaces. Copyright 2024 Chris Banes and contributors; licensed under the **Apache License 2.0**.

Project: <https://github.com/chrisbanes/haze>

---

## 8. AndroidLiquidGlass / Backdrop & Shapes (Apache License 2.0)

The Android client uses `io.github.kyant0:backdrop` (v2.0.1) and `io.github.kyant0:shapes` (v1.2.1) from AndroidLiquidGlass for real-time optical refraction and liquid bottom-navigation lens effects. Copyright Kyant and contributors; licensed under the **Apache License 2.0**. Portions of the liquid navigation gesture interaction are adapted from the Apache-2.0 sample and attributed in source.

Project: <https://github.com/Kyant0/AndroidLiquidGlass>

---

## 9. Protocol & Public-Site Structure References

The native Kotlin (Android) and Swift (iOS) comic adapters were independently written in Kotlin and Swift without bundling runtime scripts from the following open-source projects, which served as public protocol and document-structure references:

- **wgh136/pica_configs** — MIT License (<https://github.com/wgh136/pica_configs>): endpoint and HTML/JSON structure reference for comic adapters.
- **wgh136/PicaComic** — MIT License (<https://github.com/wgh136/PicaComic>): public Picacg client protocol behavior reference.
- **jiangtian616/JHenTai** — Apache License 2.0 (<https://github.com/jiangtian616/JHenTai>): anonymous public E-Hentai gallery HTML structure reference.
- **hect0x7/JMComic-Crawler-Python** — MIT License (<https://github.com/hect0x7/JMComic-Crawler-Python>): public JMComic API encryption and segmented image de-scrambling algorithm reference.
