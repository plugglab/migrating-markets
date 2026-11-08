# Changelog

All notable changes to MigratingMarkets, by PluggLab Studio.

## [2.1.0]

### Fixed
- **Caravans spawning infinitely.** Entities were marked `persistent(true)`
  with no tag and no cleanup — any restart that skipped a clean
  `onDisable` (crash, `/stop` mid-tick, `/reload`) left the old NPCs
  saved in the world forever, while `initialize()` spawned a fresh set
  on top every time. Duplicates piled up across restarts. Fixed by:
  tagging every spawned entity with a `PersistentDataContainer` key,
  setting `persistent(false)` so the plugin fully owns each entity's
  lifecycle instead of the world saving it, and running a cleanup pass
  on startup that removes any tagged entity left over from a previous
  run before spawning anything new.
- **Item levels didn't actually gate anything.** Level only ever
  controlled stock cap — any player could buy a level-5 item on their
  very first visit. Added a real customer-level system: each player
  starts at customer level 1 *per caravan* and levels up (to
  `max-customer-level`, default 5) every `purchases-per-customer-level`
  units they buy from that specific caravan. Items above the player's
  current level show locked (🔒, greyed out, no buy/sell info) in the
  GUI and are rejected server-side if clicked anyway. Purchase counts
  persist across restarts.

### Added
- **Owner-created caravans.** Server owners can now build entirely new
  caravans in-game without ever touching `config.yml`:
  - `/caravanadmin createroute <id> <specialty> <displayName...>` —
    specialty can be any existing one (for a themed starter shop) or
    any other word for an empty shop built entirely via the editor.
  - `/caravanadmin addwaypoint <id> <name...>` — adds a stop at your
    current location; the caravan spawns automatically once it has its
    first stop.
  - `/caravanadmin removewaypoint <id> <index>` /
    `/caravanadmin deleteroute <id>` — remove a stop or the whole
    caravan.
  - Persisted separately in `custom-routes.yml` so a plugin update that
    ships a new default `config.yml` can never wipe out anything an
    owner built. Config-defined routes can't be edited through these
    commands (by design — edit those in `config.yml` and `/caravanadmin
    reload`); only owner-created ones can.
- Expanded the item catalog further: two new specialties,
  **provisioner** (food/potions) and **cartographer** (exploration
  gear), each with their own caravan route and a full level 1-5 spread
  — the catalog is now ~90 items across 6 specialties.

## [2.0.0]

### Added
- **In-game shop editor GUI** (`/caravanadmin edit <id>`): left-click an
  empty slot while holding an item to add it to that caravan's shop
  (starts at price 10, level 1); right-click an existing item to remove
  it. Every item shows its slot number so it lines up with the
  slot-targeting commands below.
- New admin commands for fine-tuning shop contents without touching
  config.yml: `additem <id> <material> <price> <level> [category...]`,
  `removeitem <id> <slot>`, `setprice <id> <slot> <price>`,
  `setlevel <id> <slot> <level>` — all fully tab-completable.
- `MarketItem` price, category, and level are now mutable at runtime
  (previously fixed at construction from config.yml).
- Full per-item persistence: price, level, category, stock, and live
  price multiplier are now saved and restored for *every* item —
  including ones added at runtime via the editor or `additem`, which
  are fully reconstructed from `caravan-state.yml` on restart even
  though they don't exist in config.yml. Previously only the price
  multiplier and stock count were persisted, so admin edits and
  runtime-added items would have been lost on restart.
- Real integration with PluggLab Studio's **LuckyBlock** plugin via its
  actual developer API (`com.lucky.luckyblock.api.LuckyBlockAPI`),
  bundled at `libs/LuckyBlock-2.0.jar` and wired in as a system-scoped
  Maven dependency. The `fortune` specialty's items use a `rarity` field
  (`COMMON`/`RARE`/`EPIC`/`LEGENDARY`) and hand over a real, sellable
  lucky block `ItemStack` via `LuckyBlockAPI.buildLuckyBlockItem` — no
  console commands involved for this integration.
- `LuckyBlockBridge` also keeps a generic `dispatchGiveCommand(...)`
  fallback for bridging to some *other*, unconfirmed command-driven
  plugin via an item's `give-command` in config.yml, separate from the
  real LuckyBlock integration above.
- Tab completion (`TabCompleter`) for both `/market` and `/caravanadmin`
  — subcommands, caravan ids, item slots, and waypoint indices all
  autocomplete now. Tab suggestions for `/caravanadmin` are hidden from
  anyone without `migratingmarkets.admin`.
- Item levels (1-5) across all specialties, with per-item stock caps
  (`level × stock-per-level`) that fully restock each market-day.
- Vault economy support (`economy.provider: vault|emerald`) behind a new
  `EconomyProvider` abstraction (`EmeraldEconomyProvider` /
  `VaultEconomyProvider`), with automatic fallback to emeralds if Vault
  or an economy plugin isn't found.
- `ChatColorUtil` to correctly parse `&`/`§` codes into real Adventure
  `Component`s.
- Startup log line stating plainly whether the LuckyBlock bridge is
  active, and if not, what to check.

### Changed
- Massively expanded the item catalog (blacksmith/enchanter/exotic went
  from a handful of items each to ~20 each).
- `Waypoint` now resolves its Y coordinate against actual terrain height
  instead of trusting the configured value, so caravans can't spawn
  floating or buried.
- Caravan villagers keep their AI enabled while parked so they wander
  naturally instead of standing frozen.
- All chat/broadcast output rebuilt with proper Adventure `Component`
  composition — previously, raw `§` codes were being concatenated into
  `Component.text()` strings, which doesn't parse them (this is why
  chat showed literal section-sign junk).
- Route/plugin-presence checks switched from `getPlugin(name) != null`
  to `isPluginEnabled(name)` — the former can return true for a plugin
  that's registered but failed to actually enable.

### Fixed
- No `TabCompleter` was ever registered on either command, so pressing
  Tab did nothing at all.
- Admin command messages were concatenating a caravan's raw `&`-coded
  display name as plain text, so `&6Eastern Caravan` would print
  literally instead of being colored.
- `EntityDeathEvent#getKiller()` doesn't exist on `Entity` — the escort
  listener was calling it on the wrong type and failed to compile
  (`Monster`/`LivingEntity` has it, `Entity` doesn't).
- `lucky-block-bridge.plugin-name` / `requires-plugin` corrected from
  the guessed placeholder to whatever name the bridged plugin is
  actually registered under on the live server — this is a config value
  you may need to adjust for your own install; see the README's
  "LuckyBlock bridge" section.

## [1.0.0] - initial release
- Multiple specialized traveling caravans (blacksmith, enchanter, exotic
  trader) following configurable routes between waypoints.
- Real NPC movement via Paper's pathfinder between waypoints.
- Live supply/demand pricing that shifts on buy/sell and decays back to
  baseline once per market-day.
- Escort reputation system: killing hostile mobs near a traveling
  caravan builds reputation with it, unlocking discounts.
- Vague directional departure hints instead of exact coordinates.
- Full state persistence (`caravan-state.yml`) across restarts.
- Emerald-based buy/sell economy.
- Admin commands: `reload`, `list`, `forcemove`, `teleport`, `stop`.
