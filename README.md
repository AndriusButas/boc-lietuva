# BOC Lietuva

RuneLite plugin for the Lithuanian OSRS clan **BOC Lietuva**.
*Lietuviškai: BOC Lietuva klano pluginas – kaip įstoti, renginiai, rank'ai ir streamai tiesiai RuneLite'e.*

## Features
- **How to join** – steps, requirements and who to contact (shown first to non-members).
- **Clan events** – *Vyksta dabar* (live) and *Artėjantys renginiai* (upcoming), taken automatically from the
  clan's [Wise Old Man competitions](https://wiseoldman.net/groups/9521/competitions), with the top 3 and
  the player's own place. Extra events (breaks, bingo…) come from [`events.txt`](events.txt).
- **Member view** – clan members see events first, their current clan rank and a checklist for the next rank
  (total level, skill levels and time in the clan are read from the game).
- **Required plugins check** – shows whether Wise Old Man, WikiSync and XP Updater (with its Wise Old Man
  option) are installed and enabled.
- **Stream alerts** – a chatbox message and a LIVE marker when a clan streamer goes live on YouTube.
- **Chat reminders** – on login: live events and missing required plugins; event start and last 24 hours.

## Editing clan content (no plugin update needed)
Changes reach all players within ~15 minutes:
- [`clan.json`](clan.json) – texts, links, required plugins, streamers and the rank ladder.
  A copy is bundled in the plugin as a fallback.
- [`events.txt`](events.txt) – events that are not Wise Old Man competitions (format explained in the file).
- Wise Old Man – competitions created for group 9521 appear automatically.

## Network use
The plugin only makes these requests, without sending any personal or account data:
- `raw.githubusercontent.com` – this repository's `clan.json` and `events.txt` (every 15 min)
- `api.wiseoldman.net` – the clan's public competitions and their leaderboards (every 15 min)
- `www.youtube.com` – the public `/live` page of the clan streamers listed in `clan.json` (every 5 min;
  can be turned off in the plugin settings)

Clan membership, clan rank title, join date and levels are read locally from the game client and are not sent anywhere.

## Run it locally
Requires JDK 11+. Open the folder in IntelliJ IDEA, or from a terminal run `gradlew run`.
This starts a developer RuneLite client with the plugin loaded.
