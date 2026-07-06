<div align="center">

# 🔧 expRepair

### Repair items using your XP — passively or on demand.

![](https://img.shields.io/badge/Fabric-DBA463?style=for-the-badge&logoColor=white)&nbsp;![](https://img.shields.io/badge/NeoForge-F16436?style=for-the-badge&logoColor=white)&nbsp;

[![](https://img.shields.io/badge/Download_on-Modrinth-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white)](https://modrinth.com/project/exprepair)&nbsp;[![](https://img.shields.io/badge/Download_on-CurseForge-F16436?style=for-the-badge&logo=curseforge&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods/exprepair)

![](https://img.shields.io/badge/Minecraft-26.x_%7C_1.21.x-62B47A?style=flat-square) ![](https://img.shields.io/badge/Side-Single_Player_%26_Server-8E44AD?style=flat-square) ![](https://img.shields.io/badge/Fabric_API-required_on_Fabric-4A90D9?style=flat-square) ![](https://img.shields.io/badge/License-MIT-blue?style=flat-square)

</div>

---

> [!NOTE]
> expRepair turns experience into durability. Enchant your gear with **Mending**, then let it top itself
> off automatically as you play — or repair the tool in your hand on demand with a sneak + right-click. No
> XP orbs to chase, no anvils, no grindstones. Per-version code and changelog live on the
> [`multi_*`](#-versions--downloads) branches.

## ✨ Features

Vanilla Mending only heals the item you're holding, and only when an XP orb happens to land — so your
armor rots while you fight and your off-hand shield never sees a repair. expRepair fixes that by spending
your **stored** XP the moment gear needs it:

- **Two repair modes, one at a time.** Every player picks **Passive** (hands-off, always-on) or **Manual**
  (deliberate, on-demand). Turning one on turns the other off, so there's never any doubt about what's
  spending your levels.
- **Passive repair — the whole loadout.** Once per second, every damaged **Mending** item you have
  equipped — main hand, off-hand, and all four armor slots — is topped off from your XP pool, up to a
  configurable budget. Your kit stays healthy in the background while you mine, fight, or fly.
- **Manual repair — just the tool in hand.** Sneak + right-click in the air while holding a damaged
  Mending item and it repairs on the spot, spending up to the same per-action XP budget. Nothing happens
  passively, so you decide exactly when your levels get used.
- **A protective XP floor.** Set a **threshold** and passive repair will never spend XP that would drop you
  below that many levels — bank enough for that next enchant while the overflow keeps your gear alive.
- **Mending is the key.** Only items enchanted with **Mending** are ever repaired — the mod respects the
  vanilla enchant as the opt-in, so nothing touches gear you didn't intend to auto-repair.
- **Survival only, and only while alive.** Creative and spectator players are skipped, and no XP is spent
  on the death screen — your levels are only ever converted while you're actually playing.
- **A clickable login summary.** On join you get a compact status line — passive/manual state, your XP
  threshold, and inline buttons to toggle each — which every player can switch off with one command.
- **Full admin control.** Operators set server-wide defaults, force a mode on or off for any player, and
  can **block** either mode entirely so nobody can enable it. Config **hot-reloads** with no restart.
- **Plays nice with PvP mods.** expRepair exposes a suppression hook that companion mods (e.g. pvpOption)
  can use to pause repairs mid-fight, so combat mods can keep XP-repair from kicking in during a duel.
- **Server-side.** Everything runs on the server — a **vanilla client** can connect and it just works, and
  it runs the same in single-player.

## 🔧 How it works

XP is converted to durability at a fixed, transparent rate: **1 XP point restores 2 durability**. Each
repair action spends at most `maxXpPerRepair` XP (default **8**, so up to **16 durability**), drawn from
your **total** experience — levels *and* the progress bar — not just loose orbs.

- **Passive** fires on a **1-second** tick. The `maxXpPerRepair` budget is a **total per tick**, shared
  across every equipped Mending item in slot order (hand, off-hand, head, chest, legs, feet) until the
  budget or the damage runs out. Before spending, it subtracts your **threshold** floor: if your XP is at
  or below that level, passive repair simply waits until you've earned more.
- **Manual** applies the full `maxXpPerRepair` budget to the **single** Mending item in your hand, per
  sneak + right-click. It ignores the threshold — you asked for it, so it spends what it can.
- Partially-damaged items get partial repairs; the overlay tells you whether the item was **fully
  repaired** or by how much durability, and manual repair warns you when you're out of XP.

Per-player choices (mode, threshold, login-message toggle) persist to `config/exprepair/playerdata.json`,
and server settings live in `config/exprepair.json`.

## ⌨️ Commands

The base command is **`/exprepair`**, with **`/er`** as a shortcut alias. Running it bare opens a clickable
help screen.

### 👤 Player

| Command | What it does |
|---|---|
| `/exprepair` | Clickable help — every command with inline buttons. |
| `/exprepair passive` | Toggle **passive** repair on/off (turns manual off). |
| `/exprepair manual` | Toggle **manual** repair on/off (turns passive off). |
| `/exprepair threshold` | Show your current XP floor. |
| `/exprepair threshold <levels>` | Set the XP floor passive repair won't spend below (`0` clears it). |
| `/exprepair status` | Show your passive, manual, and threshold settings. |
| `/exprepair serverdefaults` | View the server's default settings for new players. |
| `/exprepair loginmessage` | Toggle the on-join status message for yourself. |
| `/exprepair version` | Show the installed mod version. |

### 🛡️ Admin

All admin commands require **game-master permission** (op level 2+).

| Command | What it does |
|---|---|
| `/exprepair admin` | View server-wide defaults and allow-flags. |
| `/exprepair admin <player>` | View that player's live settings. |
| `/exprepair admin <player> reset` | Reset a player to the current server defaults. |
| `/exprepair admin <player> passive on\|off` | Force passive on/off for a player. |
| `/exprepair admin <player> manual on\|off` | Force manual on/off for a player. |
| `/exprepair admin <player> threshold <levels>` | Set a player's XP floor. |
| `/exprepair admin passive on\|off` | Set the **default** passive state for new players. |
| `/exprepair admin passive allow on\|off [silent]` | Allow or **block** passive repair server-wide. |
| `/exprepair admin manual on\|off` | Set the **default** manual state for new players. |
| `/exprepair admin manual allow on\|off [silent]` | Allow or **block** manual repair server-wide. |
| `/exprepair admin threshold <levels>` | Set the default XP floor for new players. |
| `/exprepair admin maxXpPerRepair <xp>` | Set the max XP spent per repair action (min `1`). |
| `/exprepair admin reload [silent]` | Re-read `exprepair.json` from disk — no restart. |

> [!TIP]
> Add `silent` to `allow`, `reload`, and the like to apply the change **without** broadcasting it to
> everyone online — handy for quiet mid-session tweaks.

## 💡 Use cases

- **Set-and-forget survivalist.** Enchant your armor and tools with Mending, run `/exprepair passive`, and
  your whole loadout maintains itself from ambient XP while you play — no more mid-cave gear failures.
- **XP-hungry enchanter.** You're saving levels for a big enchant. Run `/exprepair threshold 30` so passive
  repair only ever spends the XP **above** level 30 — your gear still heals, but your enchanting fund is
  protected.
- **Deliberate hardcore player.** You want repairs on *your* terms. Flip to `/exprepair manual` and top off
  the tool in your hand with a sneak + right-click exactly when you choose — no XP leaves your bar
  otherwise.
- **PvP-focused server.** Admins run `/exprepair admin passive allow off` to keep passive auto-repair out
  of the arena, or lean on the PvP-suppression hook so a combat mod pauses repairs during fights — while
  still letting players repair freely elsewhere.

## ⚙️ Configuration

Server settings live in **`config/exprepair.json`**, created on first launch and editable in-game via
`/exprepair admin …`. Changes made through commands save immediately; edits made on disk apply with
`/exprepair admin reload` — no restart required.

| Key | Default | Meaning |
|---|:---:|---|
| `maxXpPerRepair` | `8` | Max XP spent per repair action — per tick for passive, per click for manual. 1 XP = 2 durability, so `8` = up to 16 durability. Minimum `1`. |
| `defaultPassive` | `false` | Whether new players start with passive repair on. |
| `defaultManual` | `false` | Whether new players start with manual repair on. |
| `defaultThreshold` | `0` | Default XP-level floor for new players (`0` = no floor). |
| `allowPassive` | `true` | Master switch — if `false`, **no** player can use passive repair. |
| `allowManual` | `true` | Master switch — if `false`, **no** player can use manual repair. |

> [!NOTE]
> `defaultPassive` and `defaultManual` can't both be on — the two modes are mutually exclusive, so if both
> are set to `true`, manual wins and passive is forced off. Per-player state (chosen mode, threshold, and
> login-message toggle) is stored separately in `config/exprepair/playerdata.json`.

## 📦 Versions &amp; downloads

> [!NOTE]
> This repo uses a **branch-per-version** layout. This `main` branch is **documentation only** — the code for each Minecraft version lives on its own branch, each with an independent history and its own `CHANGELOG.md`.

| Branch | Minecraft | Loaders | Dependencies | Log |
|:------:|:---------:|:-------:|:------------:|:---:|
| [`multi_26.2`](https://github.com/LunixiaLIVE/expRepair/tree/multi_26.2) | 26.2.x | Fabric · NeoForge | Fabric API *(Fabric only)* | [📄](https://github.com/LunixiaLIVE/expRepair/blob/multi_26.2/CHANGELOG.md) |
| [`multi_26.1`](https://github.com/LunixiaLIVE/expRepair/tree/multi_26.1) | 26.1, 26.1.1, 26.1.2 | Fabric · NeoForge | Fabric API *(Fabric only)* | [📄](https://github.com/LunixiaLIVE/expRepair/blob/multi_26.1/CHANGELOG.md) |
| [`multi_1.21.11`](https://github.com/LunixiaLIVE/expRepair/tree/multi_1.21.11) | 1.21.11 | Fabric · NeoForge | Fabric API *(Fabric only)* | [📄](https://github.com/LunixiaLIVE/expRepair/blob/multi_1.21.11/CHANGELOG.md) |
| [`multi_1.21.5`](https://github.com/LunixiaLIVE/expRepair/tree/multi_1.21.5) | 1.21.5–1.21.10 | Fabric · NeoForge | Fabric API *(Fabric only)* | [📄](https://github.com/LunixiaLIVE/expRepair/blob/multi_1.21.5/CHANGELOG.md) |

> [!TIP]
> Every `multi_*` branch builds **one jar that runs on both Fabric and NeoForge**. On 26.x that's a shared universal jar (Minecraft is unobfuscated there); on 1.21.x it's a jar-in-jar bundle (`-multi.jar`) with the Fabric and NeoForge builds nested inside, each loader picking its own. Per-loader `-fabric` / `-neoforge` jars are produced too (`build/staging/`). Fully self-contained — **no extra library mods to install**.

<details>
<summary>🛠️ <b>Building from source</b></summary>

Each code branch is a self-contained Gradle project. Grab the branch for your Minecraft version:

```bash
git clone -b multi_26.2 https://github.com/LunixiaLIVE/expRepair.git
cd expRepair
./gradlew build
```

The universal jar lands in `build/libs/` — drop it into your `mods/` folder on either loader.
</details>

## 📄 License

Released under the **MIT License**.

<div align="center"><sub>⛏️ Part of <a href="https://github.com/LunixiaLIVE/Lunixia-Minecraft-QOL-Mods">Lunixia's Minecraft QOL Mods</a>.</sub></div>
