# MigratingMarkets

Shops in Minecraft usually just sit there forever. I wanted merchants that actually move.

MigratingMarkets adds caravans that walk across your world from stop to stop. They set up a market for a few days, then pack up and head off somewhere else. When they leave, you only get a hint like *"the Blacksmith Caravan headed east"*, so finding them again is part of the fun.

### ⚡ Recommended Hosting

[![SparkedHost](https://i.imgur.com/22G8vsG.gif)](https://billing.sparkedhost.com/aff.php?aff=3360)

---

## What it does

- **Caravans that really travel.** Real villagers walk between stops. No teleporting, and they don't spawn underground or in the air.
- **Prices that react to players.** Buy a lot of something and it gets more expensive. Sell a lot and the price drops. Prices slowly go back to normal and stock refills every market day.
- **Escort reputation.** Kill mobs near a travelling caravan and it remembers you. More reputation means better discounts and a title.
- **Customer levels.** The more you buy from a caravan, the better the items it unlocks for you.
- **Announcements.** Chat, a title and a sound when a caravan arrives or leaves.
- **`/market where`** gives you a compass to a parked caravan.
- **6 caravan types:** blacksmith, enchanter, exotic trader, provisioner, cartographer and fortune. The fortune one sells lucky blocks with [LuckyEngine](https://modrinth.com/plugin/luckyengine).
- **In-game shop editor.** Change what a caravan sells without opening the config.
- **Your own routes.** Make your own caravans and stops with commands.
- Saves everything: positions, prices, stock, reputation. Restarts don't reset anything.

## Economy

Emeralds by default, nothing else needed. If you have **Vault** with an economy plugin, it can use that instead.

## Optional plugins

- **Vault** - use your server's money instead of emeralds
- **PlaceholderAPI** - caravan placeholders for scoreboards, tab and so on
- **[LuckyEngine](https://modrinth.com/plugin/luckyengine)** - turns on the fortune caravan

None of them are required.

## Commands

| Command | What it does |
| --- | --- |
| `/market info` | List all caravans and where they are |
| `/market browse` | Open the shop of the caravan you're standing near |
| `/market track` | Rough direction to each caravan |
| `/market where [id]` | Compass to a parked caravan |
| `/market stopwhere` | Turn the compass off |

Admins get `/caravanadmin` for reload, list, info, restock, goto, forcemove, teleport, the shop editor and making their own routes. The full list is in the docs and on GitHub.

## Supports
Paper and Purpur 1.21.x (and other Paper forks like Pufferfish).
Does **not** work on Spigot, Bukkit or Folia. It uses Paper-only features for the caravan walking and messages.

## Links
- Website: https://plugglab.pl/migratingmarkets/
- Docs: https://plugglab.pl/migratingmarkets/docs/
- Source: https://github.com/plugglab/migrating-markets

Made by Wilczek at PluggLAB. Found a bug or have an idea? Let me know on the Discord: https://discord.gg/ZMje3neEmK
