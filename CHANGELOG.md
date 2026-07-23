# expRepair — Changelog

Repair items using your XP — passively or on demand.
Works on client and server.

Format based on [Keep a Changelog](https://keepachangelog.com/); versioning per [SemVer](https://semver.org/).

## [1.9.4] — 2026-07-23

**Fixes NeoForge loading on the 1.21.x back-port.**

### Fixed
- **NeoForge builds now load correctly.** The previous combined jar bundled Fabric-mapped classes NeoForge couldn't resolve, crashing on startup. The mod now ships as a **jar-in-jar bundle** (`-multi.jar`) so each loader loads its own correctly-mapped build. Fabric was unaffected.
- Corrected a broken source-repository link in the mod metadata.

### Added
- **Website link** in the mod list pointing to the mod-suite hub.

### Requirements
- **Java 21**, Minecraft 1.21.5–1.21.10. Fabric: Fabric Loader ≥ 0.19.2 + Fabric API. NeoForge: 21.5.97 *(no Fabric API)*.

## [1.9.3] — 2026-07-02

Multi-loader release for **Minecraft 1.21.5 – 1.21.10** (a single jar covering that range).

### Added
- **Fabric + NeoForge** support from a single **universal** jar (per-loader `-fabric` / `-neoforge` jars are also produced).
- **Minecraft 1.21.5 through 1.21.10** compatibility in one jar (every patch in the range compiles clean on both loaders).

### Notes
- **Floor is 1.21.5:** Minecraft refactored `ClickEvent`/`HoverEvent` into sealed records at 1.21.5 (expRepair posts clickable chat links). Older 1.21.0–1.21.4 use the previous constructor API and would need a separate build.
- **Ceiling is 1.21.10:** 1.21.11 replaced `CommandSourceStack.hasPermission(int)` with a new `permissions()` API — a hard break on both loaders — so 1.21.11 lives on the separate `multi_1.21.11` branch.
- NeoForge for 1.21.6 / 1.21.7 / 1.21.9 is beta-channel only; the jar runs fine, those loader builds are simply marked beta upstream.

### Changed
- **No Architectury API required** — expRepair is fully standalone. Events are wired natively (Fabric API on Fabric, the NeoForge event bus on NeoForge).

### Dependencies
- **Fabric jar:** Minecraft 1.21.5–1.21.10, Fabric Loader >= 0.19.2, Fabric API *(Fabric only)*
- **NeoForge jar:** Minecraft 1.21.5–1.21.10, NeoForge 21.5.x–21.10.x  *(no Fabric API, no Architectury)*
