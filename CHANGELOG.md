# Changelog

All notable changes to MigratingMarkets, by PluggLab Studio.

## [2.1.0]

> **A quick note on update frequency:** updates to my plugins will be slower than usual for now. I'm currently busy with a big project for someone else, so MigratingMarkets (and my other plugins) will get fewer releases while that's ongoing. Bug reports are still read and critical issues will still be fixed - this 2.1.0 release is a big one precisely so it can carry things for a while. Thanks for your patience!

### The Caravan Road Update

2.1.0 makes caravans actually *travel* reliably, adds arrival/departure
announcements, directions to the nearest market, an escort boss bar,
PlaceholderAPI placeholders and a much nicer shop window - plus a long
list of fixes, including two money exploits.

### Added
- **Arrival & departure announcements** - chat, an on-screen title and a
  sound when a caravan arrives or leaves, plus a "last market day" warning
  the day before it packs up. Every message is editable (`&` colours,
  `%caravan%` `%stop%` `%hint%` `%days%`) under `announcements.messages`;
  toggles: `announcements.title`, `.sound`, `.arrival-sound`,
  `.departure-sound`, `.last-day-warning`. Players need
  `migratingmarkets.notify` (default: everyone) to receive them.
- **`/market where [id]`** - live action-bar compass (direction arrow +
  distance, optional coordinates) to the nearest caravan that's set up at
  a stop, or a specific one; also points your compass at it.
  `/market stopwhere` ends it. Config: `tracking.enabled`,
  `.duration-seconds`, `.show-coordinates`, `.set-compass-target`.
  Traveling caravans still only reveal a vague direction.
- **Escort boss bar** - players near a traveling caravan see a boss bar
  with the journey's progress and distance left (`escort-bossbar.enabled`,
  `escort-bossbar.color`).
- **Shop window polish** - control bar with caravan info (stop, days left,
  time to next restock), a "How to trade" book, your standing (customer
  level, progress, escort reputation, discount) and a close button;
  ★ level stars, ▲/▼ price-trend arrows, discount shown on the price,
  buy/sell/deny sounds (`gui.*`), a level-up message when you reach a new
  customer level, and configurable filler (`gui.filler-material`).
- **Sell all** - shift + right-click an item in the shop to sell every
  matching item in your inventory at once.
- **Shop editor upgrades** - shift + left-click cycles an item's level,
  number keys 1-9 set it directly, shift + right-click restocks it, and
  a close button. Edits are saved immediately.
- **PlaceholderAPI support** (optional soft-depend):
  `%migratingmarkets_active%`, `_total`, `_nearest`, `_nearest_distance`,
  `_nearest_stop`, and per caravan `_<id>_name|status|state|stop|days_left|progress|level|reputation|title|discount`.
- **New admin commands**: `/caravanadmin info <id>` (route, state, NPC,
  stock overview), `/caravanadmin restock <id|all>`,
  `/caravanadmin goto <id>`. `/caravanadmin list` now shows days left,
  travel progress and whether the NPC is loaded.
- **Tab completion** for `/market where`, every material for `additem`,
  and only owner-created ids for the route-editing commands.
- New `settings`: `sell-price-ratio` (was hard-coded 0.5),
  `allow-selling-damaged-items`, `max-wander-distance`, `travel-speed`,
  `offscreen-blocks-per-second`, `max-travel-minutes`.
- **Update checker restored** (same GitHub check as 1.0, now only reports
  genuinely newer versions; `update-checker.enabled` to turn it off).
- Existing `config.yml` files are upgraded automatically: missing new
  options are added with their defaults, nothing you set is changed, and
  routes/specialties are never touched.
- **Owner-created caravans** (from the earlier 2.1.0 work):
  `/caravanadmin createroute <id> <specialty> <displayName...>`,
  `addwaypoint <id> <name...>`, `removewaypoint <id> <index>`,
  `deleteroute <id>` - saved separately in `custom-routes.yml` so a new
  default `config.yml` can never wipe them. Config-defined routes stay
  read-only from these commands.
- **Per-caravan customer levels** - every player starts at level 1 at each
  caravan and levels up every `purchases-per-customer-level` units bought
  there (to `max-customer-level`). Higher-level items are locked (🔒) until
  then and rejected server-side.
- Two new specialties, **provisioner** (food/potions) and
  **cartographer** (exploration gear) - ~90 items across 6 specialties.

### Fixed
- **Caravans spawning infinitely** - NPCs are now tagged and
  non-persistent, leftovers are removed on startup *and* when a chunk
  containing one loads later, and untagged duplicates left behind by v1.0
  are recognised and cleaned up too.
- **Caravans never arriving** - the villager was told to path straight to
  a stop 150+ blocks away (beyond vanilla pathfinding range), and when its
  chunk unloaded the caravan was stuck on the road forever. It now walks
  in short hops, gets nudged forward when stuck, keeps travelling
  off-screen when nobody is around, and always arrives within
  `max-travel-minutes`. Its road position is saved across restarts.
- **Parked caravans vanishing** after their chunk unloaded (until the next
  restart) - they now respawn as soon as someone comes back, and are kept
  on a leash near their stop.
- **Server failed to enable without LuckyBlock** - the LuckyBlock API was
  called directly in `onEnable`, throwing `NoClassDefFoundError` on any
  server without it. All LuckyBlock calls are now guarded.
- **Fortune caravan never spawned with LuckyBlock installed** - the
  LuckyBlock 2.0 jar registers as `LuckyBlock`, but the route required
  `LuckyEngine`. It now also counts as present when the LuckyBlock API is
  live, and `LuckyBlock` was added to `softdepend` so it loads first.
- **Exploit: lucky block sell-back** - any lucky block sold at whichever
  slot you clicked (buy COMMON, sell as LEGENDARY). Sell-back now checks
  the rarity.
- **Exploit: dragging items into the shop/editor window** wasn't
  cancelled. All drags into either window are now blocked.
- **Exploit: damaged tools/armour** sold back at full price (now refused
  unless `allow-selling-damaged-items: true`).
- **Saved shop data attached to the wrong items** - saved state was
  matched by list position, so removing one item shifted every later
  item's price/stock onto a different material. Items are now matched by
  identity, and items an admin removed stay removed.
- **config.yml price/level changes ignored** - the first save froze every
  item's price and level forever. Only items edited in game now override
  `config.yml`.
- **Saved progress wiped for unloaded caravans** - every save rewrote the
  state file from loaded caravans only, deleting reputation and customer
  levels for e.g. the fortune caravan while LuckyBlock was missing. Their
  data is now kept (deleted custom routes are still cleaned up).
- **Upgrading from 1.0 lost live prices/stock** - the 1.0 save format is
  now read.
- Caravans with a single stop "departed" to the same spot every few days;
  they now simply stay open.
- Stale shop windows: players could keep trading after the caravan left,
  was reloaded/deleted or an admin edited the shop (slot numbers shifted).
  Windows now close in all of those cases.
- `/market info`, `/market track` and `/caravanadmin list` crashed on an
  owner-created caravan with no stops yet.
- Owner-created route ids weren't validated (a `.` in the id corrupted
  the save files) and were case-sensitive in some commands but not
  others (`addwaypoint NightMarket` claimed the route was from config.yml).
- Removing a stop before the current one made the caravan jump to a
  different stop; removing the current stop left the NPC in place.
- `/caravanadmin forcemove` said "Forced to depart" even when the caravan
  couldn't move.
- `additem`/`setprice`/`setlevel` accepted zero/negative prices and
  levels; a warning is shown for levels above `max-customer-level`.
- Escort tier-up message printed raw `&` colour codes.
- The interact handler fired twice (once per hand), let `migratingmarkets.use`
  be bypassed by right-clicking the NPC, and didn't stop name tags/leads
  or vanilla trading on traveling caravans. Players can no longer kill a
  traveling caravan, and caravan villagers can't be converted (lightning,
  zombies).
- Refunds for undeliverable items (LuckyBlock missing) were rounded down
  with the emerald economy, losing up to one emerald.
- A missing or partial `settings:` section produced zero values (e.g. a
  0-tick market-day timer); every option now has a safe default.
- Direction hints and `/market` lookups no longer force-load far-away
  chunks synchronously.
- The periodic save now writes off the main thread, atomically (no more
  half-written `caravan-state.yml` after a crash).
- Removed a broken `util/UpdateChecker.java` that had been copied from
  another plugin (wrong package/classes) and replaced it with the real one.
- `plugin.yml` usage listed a non-existent `spawn` subcommand.

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
