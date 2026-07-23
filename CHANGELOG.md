# expRepair — Changelog

Repair items using your XP — passively or on demand.
Works on client and server.

Format based on [Keep a Changelog](https://keepachangelog.com/); versioning per [SemVer](https://semver.org/).

## [1.9.4] — 2026-07-23

**Fixes NeoForge loading on the 1.21.x back-port.**

### Fixed
- **NeoForge builds now load correctly.** The previous combined jar bundled Fabric-mapped classes NeoForge couldn't resolve, crashing on startup. The mod now ships as a **jar-in-jar bundle** (`-multi.jar`) so each loader loads its own correctly-mapped build. Fabric was unaffected.
- Corrected a broken source-repository link in the mod metadata.

### Changed
- Tightened the NeoForge Minecraft version range to **exactly 1.21.11**, so both loaders declare the same coverage.

### Added
- **Website link** in the mod list pointing to the mod-suite hub.

### Requirements
- **Java 21**, Minecraft 1.21.11. Fabric: Fabric Loader ≥ 0.19.2 + Fabric API. NeoForge: 21.11.42 *(no Fabric API)*.

## [1.9.3] — 2026-07-01

Multi-loader release for **Minecraft 1.21.11** (the latest 1.21 patch).

### Added
- **Fabric + NeoForge** support from a single **universal** jar (per-loader `-fabric` / `-neoforge` jars are also produced).

### Changed
- **No Architectury API required** — expRepair is now fully standalone. Events are wired natively (Fabric API on Fabric, the NeoForge event bus on NeoForge).

### Dependencies
- **Fabric jar:** Minecraft 1.21.11, Fabric Loader >= 0.19.2, Fabric API 0.141.3+1.21.11
- **NeoForge jar:** Minecraft 1.21.11, NeoForge 21.11.42  *(no Fabric API, no Architectury)*
