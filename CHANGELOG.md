# expRepair — Changelog

Repair items using your XP — passively or on demand.
Works on client and server.

Format based on [Keep a Changelog](https://keepachangelog.com/); versioning per [SemVer](https://semver.org/).

## [1.9.5] — 2026-09-23

Minecraft 26.3 support, shipped as one jar for every 26.x release so far. No gameplay changes.

### Added
- Minecraft **26.3** support.

### Changed
- **One jar for Minecraft 26.1 through 26.3** — 26.1, 26.1.1, 26.1.2, 26.2 and 26.3 — on both Fabric and NeoForge, replacing the separate 26.1.x and 26.2.x jars. The mod's code is identical on all of them, so there is nothing to pick between.
- The jar is now named `_MC-26.1-3`, and the code lives on the `multi_26.1-3` branch (`multi_26.1` and `multi_26.2` are retired).

### Requirements
- **Java 25**, Minecraft 26.1–26.3. Fabric: Fabric Loader ≥ 0.19.3 + Fabric API for your Minecraft version. NeoForge: 26.1.2.76 or newer *(no Fabric API)*.

## [1.9.4] — 2026-07-23

Housekeeping release — metadata and packaging polish. No gameplay changes.

### Added
- **Website link** in the mod list (Mod Menu on Fabric, the mods screen on NeoForge) pointing to the mod-suite hub.

### Changed
- Renamed the combined output jar from `-universal` to `-multi` for consistent naming across all versions.

### Requirements
- **Java 25**, Minecraft 26.1.x. Fabric: Fabric Loader ≥ 0.19.3 + Fabric API. NeoForge: 26.1.2.76 *(no Fabric API)*.

## [1.9.3] — 2026-07-01

First multi-loader release for **Minecraft 26.x** (the 26.1.x line).

### Added
- **Fabric + NeoForge** support from a single **universal** jar (per-loader `-fabric` / `-neoforge` jars are also produced).
- Minecraft **26.1, 26.1.1, and 26.1.2** compatibility.

### Changed
- **No Architectury API required** — expRepair is now fully standalone. Events are wired natively (Fabric API on Fabric, the NeoForge event bus on NeoForge).
- Version pinned to the **26.1.x** line; the jar will not load on a different minor version.

### Dependencies
- **Fabric jar:** Minecraft 26.1.x, Fabric Loader >= 0.19.3, Fabric API 0.153.0+26.1.2
- **NeoForge jar:** Minecraft 26.1.x, NeoForge 26.1.2.76  *(no Fabric API, no Architectury)*
