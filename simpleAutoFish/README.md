# Mirror (Fabric, client-side)

Two instances of the same jar talk over `127.0.0.1:25599`.

## Setup
1. `gradle build` (needs Gradle 8.x + JDK 21), copy `build/libs/mirror-0.1.0.jar` into both instances' `mods/`.
2. Launch the **display** instance first, then the **source** instance (either order works, they reconnect).
3. After first launch each instance has `config/mirror.properties`:
   - Source (joins DonutSMP): `role=source`
   - Display (singleplayer you record): `role=display`, and `cloneWorld=true` to also mirror world/entities.
4. Source joins the server. Open the singleplayer world on the display instance.

## What it does
- Source forwards every inbound play packet; display re-applies them.
  - `cloneWorld=false`: only chat, titles, scoreboard, tab header, boss bars, containers/slots.
  - `cloneWorld=true`: everything (chunks, entities, player list, etc.), integrated-server packets are dropped.
- On display, chat and `/commands` are sent to the source account rather than the integrated server.

## Known gaps / not verified
- Not compiled or run. Yarn names and packet classes are for 1.21.4 and may need changes on other versions.
- `replayTo()` exists but is not yet triggered on relay connect, so start the display before the source joins, or rejoin.
- Display movement and container clicks are not forwarded to the source, so the view follows the source player.
