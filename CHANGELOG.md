# expRepair — Changelog

Repair items using your XP — passively or on demand.
Works on client and server.

Format based on [Keep a Changelog](https://keepachangelog.com/); versioning per [SemVer](https://semver.org/).

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
