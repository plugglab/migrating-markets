## 2.1.0 - the caravan road update

Took a while, I know. This is everything since 1.0 in one go (2.0 never got released, so it's all in here).

> Heads up: updates are going to be slower for a bit. I'm working on a big project for someone else right now. Bug reports still get read and anything serious still gets fixed, I just won't be pushing updates every week.

### New stuff
- **Arrival and departure announcements.** Chat message, a title on screen and a sound when a caravan shows up or leaves, plus a warning on its last market day. All messages are editable in `announcements.messages`.
- **`/market where`** puts a compass in your action bar that points to a parked caravan. `/market stopwhere` turns it off. Caravans that are still travelling only give a rough direction, so you still have to go look for them.
- **Escort boss bar.** Walk next to a travelling caravan and you'll see how far it is into its trip.
- **A much nicer shop window.** Info bar (stop, days left, next restock), your customer level and reputation, price trend arrows, sounds, and a little "how to trade" book.
- **Sell all.** Shift + right-click an item in the shop to sell every matching item you're carrying.
- **In-game shop editor** (`/caravanadmin edit <id>`). Add, remove and level items without touching the config. Changes save right away.
- **Your own caravans.** `/caravanadmin createroute`, `addwaypoint`, `removewaypoint` and `deleteroute`. They're saved in their own file, so updating the config never wipes them.
- **Customer levels.** Buy enough from a caravan and you level up with it, which unlocks its better items.
- **Two new caravan types:** provisioner (food and potions) and cartographer (exploring gear). Around 90 items across 6 types now.
- **Fortune caravan** that sells lucky blocks if you have [LuckyEngine](https://modrinth.com/plugin/luckyengine) installed.
- **PlaceholderAPI support** (optional). The full list of placeholders is in the README on GitHub.
- **Admin commands:** `info`, `restock` and `goto`, and `list` now shows more. Tab completion works everywhere.
- **Vault support** (optional). Without Vault it uses emeralds like before.
- The update checker is back, and you can turn it off.

### Fixed
- Caravans never actually reaching the next stop. They now walk in short hops, keep moving when nobody is around and always arrive.
- Caravans disappearing when their chunk unloaded, and duplicate caravans piling up.
- The plugin not starting at all on servers without LuckyEngine.
- **Two money exploits:** selling a common lucky block back at the legendary price, and dragging items into the shop window. Damaged gear also doesn't sell for full price anymore.
- Prices and stock jumping onto the wrong items after an item was removed.
- Price changes in `config.yml` getting ignored after the first save.
- Lost reputation and levels for caravans that weren't loaded.
- Shop windows staying open after the caravan left.
- Lots of smaller command bugs, like bad prices being accepted and `forcemove` saying it worked when it didn't.

### Updating from 1.0
Drop in the new jar and restart. Your old saves are read, and your `config.yml` gets the new options added with their defaults without changing anything you set.
