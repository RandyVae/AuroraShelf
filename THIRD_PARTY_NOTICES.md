# Third-party notices

## PipePipeExtractor

This project includes PipePipeExtractor source code under
`third_party/pipepipe-extractor`. PipePipeExtractor is licensed under the GNU
General Public License version 3. Its license text is preserved at
`third_party/pipepipe-extractor/LICENSE`.

The integrated code is used only for public video catalog, search, metadata,
and playback-source extraction. Login, membership, and payment features are
not enabled in AuroraShelf. AuroraShelf's separate offline cache is built on
AndroidX Media3 and does not reuse a source application's download feature.

## Haze

AuroraShelf uses Haze 1.7.3 for hardware-accelerated background blur in its
Compose interface. Haze is Copyright 2024 Chris Banes and contributors and is
licensed under the Apache License 2.0.

Project: <https://github.com/chrisbanes/haze>

## pica_configs

The native comic-source adapters use public endpoint and document-structure
information from wgh136/pica_configs. The project is licensed under the MIT
License. AuroraShelf does not download or execute its JavaScript at runtime.

Project: <https://github.com/wgh136/pica_configs>

## PicaComic protocol reference

The Picacg adapter was independently implemented from the public protocol
behavior documented by PicaComic. No GPL-licensed Haka Comic source code is
copied into AuroraShelf. PicaComic is licensed under the MIT License.

Project: <https://github.com/wgh136/PicaComic>

## E-Hentai public-site reference

The E-Hentai adapter parses the anonymously accessible public HTML site. The
implementation was informed by the public behavior documented by JHenTai,
licensed under the Apache License 2.0. AuroraShelf does not include private
ExHentai cookies or bypass access controls.

Project: <https://github.com/jiangtian616/JHenTai>

## JMComic protocol reference

The JMComic adapter was independently implemented from the public protocol and
image-scrambling behavior documented by JMComic-Crawler-Python. That project is
licensed under the MIT License.

Project: <https://github.com/hect0x7/JMComic-Crawler-Python>

## AndroidLiquidGlass / Backdrop

AuroraShelf uses Backdrop 2.0.1 and Shapes 1.2.1 from
AndroidLiquidGlass. Copyright Kyant and contributors; licensed under the
Apache License 2.0. Portions of the liquid bottom-navigation interaction are
adapted from the project's Apache-licensed sample and are marked in source.

Project: <https://github.com/Kyant0/AndroidLiquidGlass>
