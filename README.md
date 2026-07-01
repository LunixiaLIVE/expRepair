# expRepair

Repair items using your XP — passively or on demand.
**Client & server.**

## Features

- Passive or on-demand XP-based item repair
- Per-player login-message toggle
- Integrates with pvpOption to suppress repair during PvP

## Versions & downloads

This repository uses a **branch-per-version** layout: this `main` branch is documentation only — the code for each Minecraft version lives on its own branch, each with its own history and `CHANGELOG.md`.

| Branch | Minecraft | Loaders | Dependencies | Notes |
|--------|-----------|---------|--------------|-------|
| [`multi_26.2`](https://github.com/LunixiaLIVE/expRepair/tree/multi_26.2) | 26.2.x | Fabric · NeoForge | Fabric API *(Fabric only)* | [changelog](https://github.com/LunixiaLIVE/expRepair/blob/multi_26.2/CHANGELOG.md) |
| [`multi_26.1`](https://github.com/LunixiaLIVE/expRepair/tree/multi_26.1) | 26.1, 26.1.1, 26.1.2 | Fabric · NeoForge | Fabric API *(Fabric only)* | [changelog](https://github.com/LunixiaLIVE/expRepair/blob/multi_26.1/CHANGELOG.md) |

The `multi_*` branches each build a single **universal** jar that runs on **both** Fabric and NeoForge (per-loader `-fabric` / `-neoforge` jars are also produced). The 26.x builds are fully standalone — **no Architectury API at runtime**.

## License

MIT
