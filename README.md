# MigratingMarkets
**by PluggLab Studio** — for Paper/Spigot 1.21.x

An advanced traveling-caravan economy plugin. Instead of a static shop
or a merchant that just teleports on a timer, each caravan is a real
NPC that walks between waypoints, sets up a live market with its own
supply/demand pricing, and rewards players who protect it on the road.

## Features

- **Multiple specialized caravans** (blacksmith, enchanter, exotic
  trader, plus an optional fortune-teller caravan for lucky blocks) —
  each with its own route and item pool, fully configurable in
  `config.yml`.
- **Item levels & stock limits**: every item has a level (1-5). Higher
  levels restock deeper each market-day (stock cap = level ×
  `stock-per-level`), so low-level goods are always available in small
  quantities while rare, high-level items are genuinely limited per
  visit.
- **Real movement on solid ground**: caravans resolve their Y position
  against actual terrain height (not the raw config value), so they
  never spawn floating or buried. While parked, the villager's AI stays
  on so it wanders naturally instead of standing frozen.
- **Live supply & demand pricing**: buying an item nudges its price up,
  selling nudges it down, and prices decay back toward baseline once
  per in-game "market day", when stock also fully replenishes.
- **Escort reputation system**: kill hostile mobs near a *traveling*
  caravan to build reputation with it specifically. Reputation tiers
  unlock discounts (configurable) the next time that caravan sets up
  shop.
- **Vague destination hints**: when a caravan departs, players get a
  rough compass direction ("headed due east"), not exact coordinates —
  encourages scouting instead of checking a wiki.
- **Persistent state**: caravan position, days-at-stop, live prices,
  stock levels, and every player's escort reputation are saved to
  `caravan-state.yml` and restored on restart.
- **Emerald or Vault economy**: defaults to physical emeralds (no
  dependency), or set `economy.provider: vault` in config.yml to charge
  through whatever economy plugin Vault has registered. Falls back to
  emeralds automatically with a warning if Vault isn't found.
- **Real LuckyBlock integration**: the `fortune` specialty sells actual
  lucky block items (COMMON/RARE/EPIC/LEGENDARY) via PluggLab Studio's
  LuckyBlock 2.0 developer API — real, sellable `ItemStack`s, not a
  console-command guess. See "LuckyBlock bridge" below.
- **Properly colored chat**: all broadcast/command output goes through
  Adventure Components correctly (no more raw `§` characters showing up
  in chat).
- **Tab completion** on both `/market` and `/caravanadmin` — subcommands,
  caravan ids, and waypoint indices all autocomplete.

## LuckyBlock bridge

This plugin integrates directly with PluggLab Studio's **LuckyBlock
2.0**'s real developer API
(`com.lucky.luckyblock.api.LuckyBlockAPI`) — not a guessed console
command. The API jar is bundled at `libs/LuckyBlock-2.0.jar` and wired
in as a system-scoped Maven dependency (see `pom.xml`), so it's
available at compile time; the actual LuckyBlock plugin provides the
implementation at runtime via `LuckyBlockAPI.getOrNull()`.

What this means in practice:
- The `fortune-caravan` route only spawns if a plugin named
  `lucky-block-bridge.plugin-name` (default `LuckyBlock`) is installed.
- Items in the `fortune` specialty use a `rarity` field
  (`COMMON`/`RARE`/`EPIC`/`LEGENDARY`) instead of `give-command`. Buying
  one calls `LuckyBlockAPI.buildLuckyBlockItem(rarity, amount)` and
  hands over the real item — it keeps its own name/lore from LuckyBlock
  and can be sold back like anything else.
- If you update LuckyBlock to a newer version, replace the jar in
  `libs/` and bump the version in `pom.xml` to match.

There's also a separate, generic fallback (`give-command` on an item,
paired with `lucky-block-bridge.give-command-template`) for bridging to
some *other*, unconfirmed command-driven plugin the same way earlier
versions of this doc described — it dispatches a console command
instead of using a real API, and items built this way can't be sold
back. Use `rarity` instead whenever you're actually running LuckyBlock.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/market info` | List all caravans and their status | `migratingmarkets.use` |
| `/market browse` | Open the GUI of a caravan you're standing near | `migratingmarkets.use` |
| `/market track` | See rough whereabouts / days remaining per caravan | `migratingmarkets.use` |
| `/caravanadmin list` | List all caravans with internal state | `migratingmarkets.admin` |
| `/caravanadmin reload` | Reload config and respawn caravans | `migratingmarkets.admin` |
| `/caravanadmin forcemove <id>` | Force a caravan to depart immediately | `migratingmarkets.admin` |
| `/caravanadmin teleport <id> <waypointIndex>` | Snap a caravan to a specific waypoint | `migratingmarkets.admin` |
| `/caravanadmin stop` | Despawn all caravans and save state | `migratingmarkets.admin` |

Players can also just right-click a caravan's villager NPC to open its
shop directly (no command needed) when it's set up at a stop.

## Configuration

Everything lives in `config.yml`:
- `settings` — timing (minutes per market day, days per stop),
  interaction/escort radius, broadcast toggle, and pricing tuning
  (shift per trade, decay per day, min/max multiplier).
- `routes` — define as many caravans as you want; each has a
  `display-name`, a `specialty` key (matching one under `specialties`),
  and an ordered list of `waypoints` (world/x/y/z/name).
- `specialties` — named item pools (material, base-price, category)
  that any route can reference. Reuse one specialty across multiple
  routes if you want, e.g. two different blacksmith caravans on
  different loops.
- `escort-reputation.tiers` — threshold (kills near a traveling
  caravan), discount, and a title shown to the player.

## Building

This project ships as source only — building requires internet access
to pull the Paper API dependency, which wasn't available in the
environment that generated this code, so it hasn't been compiled here.
To build the jar yourself:

```bash
mvn clean package
```

The output jar will be at `target/MigratingMarkets-1.0.0.jar`. Drop it
into your server's `plugins/` folder and restart.

Requires Java 21 and Paper (or a Paper fork) for Minecraft 1.21.x —
the plugin uses `Mob#getPathfinder()` and Adventure's `Component` API,
both of which are Paper-specific (not available on plain Spigot/Bukkit
without Paper's patches).

## Notes on extending

- Vault support is already built — see `economy/EconomyProvider`,
  `EmeraldEconomyProvider`, and `VaultEconomyProvider`. Swapping is a
  one-line config change (`economy.provider: vault`), no code edits
  needed as long as Vault + an economy plugin are installed.
- Want caravans to be attackable/lootable while traveling? The
  traveling-state entity is already a normal (non-invulnerable)
  villager — hook into `EntityDamageEvent` for further mechanics.
- Waypoints are just data — a `/caravanadmin` subcommand to add/remove
  waypoints at runtime would be a natural next step if you want to
  design routes in-game instead of in `config.yml`.
- Adding a new specialty or item is just config — no rebuild needed:
  drop a new entry under `specialties`, give it a `level`, and
  optionally reference it from a route.
