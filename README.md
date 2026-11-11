# MigratingMarkets
**v2.1.0 — by PluggLab Studio** — for Paper and Purpur 1.21.x (not Spigot/Bukkit)

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
  caravan ids, materials and waypoint indices all autocomplete.
- **Arrival & departure announcements** (new in 2.1.0): chat, on-screen
  title and sound when a caravan arrives or leaves, plus a "last market
  day" warning. All texts are editable under `announcements.messages`.
- **`/market where`** (new in 2.1.0): a live action-bar compass with an
  arrow and distance to the nearest caravan that's set up at a stop (or a
  specific one), and your compass points at it too.
- **Escort boss bar** (new in 2.1.0): stand near a traveling caravan to
  see how far it still has to go.
- **Reliable travel** (new in 2.1.0): caravans walk in short hops, get
  unstuck automatically, keep travelling off-screen when nobody is near,
  and always arrive within `max-travel-minutes`.
- **Polished shop window** (new in 2.1.0): info bar (stop, days left,
  next restock), price-trend arrows, level stars, discounts on the price
  tag, sounds, and shift + right-click to sell everything matching.
- **PlaceholderAPI placeholders** (optional, new in 2.1.0) — see below.
- **Update checker**: one async GitHub check on startup, admins are told
  on join (`update-checker.enabled: false` to disable).

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
  `lucky-block-bridge.plugin-name` (default `LuckyEngine`) is installed,
  or the LuckyBlock 2.0 API is available (the LuckyBlock jar registers
  as `LuckyBlock`).
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
| `/market where [id]` | Action-bar directions to the nearest (or a given) caravan at a stop | `migratingmarkets.use` |
| `/market stopwhere` | Stop the directions | `migratingmarkets.use` |
| `/caravanadmin list` | List all caravans with internal state | `migratingmarkets.admin` |
| `/caravanadmin info <id>` | Route, state, NPC and stock details for one caravan | `migratingmarkets.admin` |
| `/caravanadmin restock <id\|all>` | Refill stock immediately | `migratingmarkets.admin` |
| `/caravanadmin goto <id>` | Teleport yourself to a caravan | `migratingmarkets.admin` |
| `/caravanadmin reload` | Reload config and respawn caravans | `migratingmarkets.admin` |
| `/caravanadmin forcemove <id>` | Force a caravan to depart immediately | `migratingmarkets.admin` |
| `/caravanadmin teleport <id> <waypointIndex>` | Snap a caravan to a specific waypoint | `migratingmarkets.admin` |
| `/caravanadmin stop` | Despawn all caravans and save state (markets stay closed until `reload`) | `migratingmarkets.admin` |
| `/caravanadmin edit <id>` | Open the in-game shop editor GUI | `migratingmarkets.admin` |
| `/caravanadmin additem <id> <material> <price> <level> [category...]` | Add an item without opening the GUI | `migratingmarkets.admin` |
| `/caravanadmin removeitem <id> <slot>` | Remove an item by slot number | `migratingmarkets.admin` |
| `/caravanadmin setprice <id> <slot> <price>` | Change an item's base price | `migratingmarkets.admin` |
| `/caravanadmin setlevel <id> <slot> <level>` | Change an item's level (recalculates its stock cap) | `migratingmarkets.admin` |
| `/caravanadmin createroute <id> <specialty> <displayName...>` | Create a brand-new owner-run caravan | `migratingmarkets.admin` |
| `/caravanadmin addwaypoint <id> <name...>` | Add a stop at your current location (custom routes only) | `migratingmarkets.admin` |
| `/caravanadmin removewaypoint <id> <index>` | Remove a stop (custom routes only) | `migratingmarkets.admin` |
| `/caravanadmin deleteroute <id>` | Permanently delete an owner-created caravan | `migratingmarkets.admin` |

Players can also just right-click a caravan's villager NPC to open its
shop directly (no command needed) when it's set up at a stop.

`/cadmin` is an alias for `/caravanadmin`.

## Permissions

| Permission | Default | What it does |
|---|---|---|
| `migratingmarkets.use` | everyone | Trade with caravans, use `/market` |
| `migratingmarkets.notify` | everyone | Receive arrival/departure announcements |
| `migratingmarkets.admin` | op | `/caravanadmin`, shop editor, update notices (includes the two above) |

## Trading

In the shop window:
- **Left-click** buy 1 · **Shift + left-click** buy 8
- **Right-click** sell the stack in your hand · **Shift + right-click**
  sell every matching item in your inventory
- Sell price is `settings.sell-price-ratio` (default 50%) of the current
  price. Damaged tools/armour can't be sold unless
  `settings.allow-selling-damaged-items: true`.
- ▲ / ▼ next to a price means it's currently above / below the usual
  price because of recent trading.

## PlaceholderAPI

If PlaceholderAPI is installed, these placeholders are available
(`<id>` is a caravan id, e.g. `eastern-loop`):

| Placeholder | Value |
|---|---|
| `%migratingmarkets_active%` / `_total%` | Caravans set up at a stop / loaded |
| `%migratingmarkets_nearest%` | Nearest set-up caravan's name |
| `%migratingmarkets_nearest_distance%` / `_nearest_stop%` | Distance to it / its stop |
| `%migratingmarkets_<id>_status%` | `At <stop>` or `Traveling` |
| `%migratingmarkets_<id>_name%` / `_state%` / `_stop%` | Name / `AT_MARKET`/`TRAVELING` / stop |
| `%migratingmarkets_<id>_days_left%` / `_progress%` | Market-days left / travel progress (0-100) |
| `%migratingmarkets_<id>_level%` | The player's customer level there |
| `%migratingmarkets_<id>_reputation%` / `_title%` / `_discount%` | The player's escort standing there |

## Creating your own caravans in-game

You're not limited to what's in `config.yml` — build a whole new
caravan without touching a file:

```
/caravanadmin createroute nightmarket blacksmith &8Night Market
/caravanadmin addwaypoint nightmarket Town Square
/caravanadmin addwaypoint nightmarket Old Bridge
/caravanadmin edit nightmarket
```

- `createroute <id> <specialty> <displayName...>` — `specialty` can be
  any specialty already defined under `specialties:` in `config.yml`
  (gives it a themed starter inventory), or literally any other word
  (e.g. `custom`) for a caravan that starts with an empty shop you fill
  in entirely through the editor GUI or `additem`.
- The caravan won't actually spawn an NPC until it has at least one
  stop — `addwaypoint` uses wherever you're standing when you run it.
- `removewaypoint`/`deleteroute` only work on caravans you created this
  way; routes defined in `config.yml` are read-only from these commands
  by design — edit those in the file and run `/caravanadmin reload`.
- Owner-created caravans are saved to their own `custom-routes.yml`,
  completely separate from `config.yml`. A future plugin update that
  ships a new default config can never wipe these out.

## Editing shops in-game

Run `/caravanadmin edit <id>` (tab-complete the id) to open the shop
editor for a caravan. It looks like the player-facing shop, but:

- **Left-click an empty slot** while holding an item in your main hand
  to add it — it's created at price 10, level 1, category "custom".
  Fine-tune it right after with `/caravanadmin setprice`/`setlevel`
  using the slot number shown in the item's name (e.g. `[12] Diamond`).
- **Right-click an existing item** to remove it immediately.
- **Shift + left-click** cycles an item's level; **number keys 1-9**
  set it directly; **shift + right-click** restocks it.
- Changes are live on the running caravan right away, and persist to
  `caravan-state.yml` on the next save/restart — including items added
  this way that don't exist in `config.yml` at all, so you don't need
  to touch the config file for one-off additions.

Everything edited in game is saved right away and survives restarts and
`/caravanadmin reload`. Items you edited or added in game keep their
in-game values; items you never touched always follow `config.yml`, so
changing a `base-price` there and running `/caravanadmin reload` works.
Items you removed in game stay removed even though they're still in
`config.yml`.

## Customer levels — gating high-level items

Every item has a level (1-5, set in `config.yml` or via `setlevel`).
Separately, every *player* has a customer level **at each caravan**,
starting at 1, that rises as they buy more from that specific caravan
— every `purchases-per-customer-level` units purchased (default 8)
grants +1 level, up to `max-customer-level` (default 5).

An item is locked — shown greyed out with a 🔒, no buy/sell info shown,
and rejected server-side even if clicked — whenever its level is above
the player's current customer level at that caravan. This is what
actually restricts high-level goods to loyal, repeat customers instead
of anyone being able to buy a level-5 item on their first visit. A
player's level (and progress to the next one) is shown in the "Your
Standing" item in the shop GUI.

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
- `announcements` — title/sound toggles, sounds, last-day warning and
  every announcement text (`&` colours; `%caravan%`, `%stop%`, `%hint%`,
  `%days%`).
- `tracking` — `/market where` duration, coordinates, compass target.
- `escort-bossbar` — on/off and colour.
- `gui` — shop sounds and filler material.
- `update-checker.enabled`.

When you update from an older version, any of these new options that
are missing from your `config.yml` are added automatically with their
default values; nothing you already set is changed.

## Building

```bash
mvn clean package
```

The output jar is `target/MigratingMarkets-2.1.0.jar`. Drop it into your
server's `plugins/` folder and restart. The build needs
`libs/LuckyBlock-2.0.jar` (LuckyBlock's API, compile-time only — it is
not bundled into the plugin jar).

Requires Java 21 and Paper (or a Paper fork) for Minecraft 1.21.x —
the plugin uses `Mob#getPathfinder()` and Adventure's `Component` API,
both of which are Paper-specific (not available on plain Spigot/Bukkit
without Paper's patches). Optional: Vault (+ an economy plugin),
LuckyBlock 2.0, PlaceholderAPI.

## Notes on extending

- Vault support is already built — see `economy/EconomyProvider`,
  `EmeraldEconomyProvider`, and `VaultEconomyProvider`. Swapping is a
  one-line config change (`economy.provider: vault`), no code edits
  needed as long as Vault + an economy plugin are installed.
- Want caravans to be attackable/lootable while traveling? The
  traveling-state entity is already a normal (non-invulnerable)
  villager — hook into `EntityDamageEvent` for further mechanics.
- Adding a new specialty or item is just config — no rebuild needed:
  drop a new entry under `specialties`, give it a `level`, and
  optionally reference it from a route.
