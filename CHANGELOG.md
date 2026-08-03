# Changelog

All notable changes to MigratingMarkets, by PluggLab Studio.

## 1.0

### Added

- Real integration with PluggLab Studio's **LuckyBlock 2.0** plugin via
  its actual developer API (`com.lucky.luckyblock.api.LuckyBlockAPI`),
  bundled at `libs/LuckyBlock-2.0.jar` and wired in as a system-scoped
  Maven dependency. The `fortune` specialty's items now use a `rarity`
  field (`COMMON`/`RARE`/`EPIC`/`LEGENDARY`) and hand over a real,
  sellable lucky block `ItemStack` via `LuckyBlockAPI.buildLuckyBlockItem`
  — no console commands involved for this integration.
- `LuckyBlockBridge` also keeps a generic `dispatchGiveCommand(...)`
  fallback for bridging to some _other_, unconfirmed command-driven
  plugin via an item's `give-command` in config.yml, separate from the
  real LuckyBlock integration above.
- Tab completion (`TabCompleter`) for both `/market` and `/caravanadmin`
  — subcommands, caravan ids, and valid waypoint indices all autocomplete
  now. Tab suggestions for `/caravanadmin` are hidden from anyone without
  `migratingmarkets.admin`.
- Item levels (1-5) across all specialties, with per-item stock caps
  (`level × stock-per-level`) that fully restock each market-day.
- Vault economy support (`economy.provider: vault|emerald`) behind a new
  `EconomyProvider` abstraction (`EmeraldEconomyProvider` /
  `VaultEconomyProvider`), with automatic fallback to emeralds if Vault
  or an economy plugin isn't found.
- `ChatColorUtil` to correctly parse `&`/`§` codes into real Adventure
  `Component`s.

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
- `lucky-block-bridge.plugin-name` corrected from the placeholder
  `LuckyEngine` to the bridged plugin's real registered name,
  `LuckyBlock` (confirmed from its own `plugin.yml`).

### Fixed

- No `TabCompleter` was ever registered on either command, so pressing
  Tab did nothing at all.
- Admin command messages were concatenating a caravan's raw `&`-coded
  display name as plain text, so `&6Eastern Caravan` would print
  literally instead of being colored.
- `EntityDeathEvent#getKiller()` doesn't exist on `Entity` — the escort
  listener was calling it on the wrong type and failed to compile
  (`Monster`/`LivingEntity` has it, `Entity` doesn't).

## [1.0.0] - initial release

- Multiple specialized traveling caravans (blacksmith, enchanter, exotic
  trader) following configurable routes between waypoints.
- Live supply/demand pricing that shifts on buy/sell and decays back to
  baseline once per market-day.
- Escort reputation system: killing hostile mobs near a traveling
  caravan builds reputation with it, unlocking discounts.
- Vague directional departure hints instead of exact coordinates.
- Full state persistence (`caravan-state.yml`) across restarts.
- Emerald-based buy/sell economy.
