# expRepair — Changelog

Repair items using your XP — passively or on demand.
Works on client and server.

Format based on [Keep a Changelog](https://keepachangelog.com/); versioning per [SemVer](https://semver.org/).

## [1.9.3] — 2026-07-01

Multi-loader release for **Minecraft 1.21.11** (the latest 1.21 patch).

### Added
- **Fabric + NeoForge** support from a single **universal** jar (per-loader `-fabric` / `-neoforge` jars are also produced).

### Changed
- **No Architectury API required** — expRepair is now fully standalone. Events are wired natively (Fabric API on Fabric, the NeoForge event bus on NeoForge).

### Dependencies
- **Fabric jar:** Minecraft 1.21.11, Fabric Loader >= 0.19.2, Fabric API 0.141.3+1.21.11
- **NeoForge jar:** Minecraft 1.21.11, NeoForge 21.11.42  *(no Fabric API, no Architectury)*
