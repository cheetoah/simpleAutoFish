# AutoFish

A client-side Fabric mod for Minecraft **26.3** that automatically reels in and recasts your fishing rod, with humanised timing and anti-anti-autofish evasion. 

## Building from source

1. Install a JDK 25 (for example Eclipse Temurin 25).
2. Make sure Gradle uses it. Set `JAVA_HOME`, or add this line to `gradle.properties`, using forward slashes:

   ```properties
   org.gradle.java.home=C:/Program Files/Eclipse Adoptium/jdk-25...
   ```
3. Run `gradlew build` (on Linux or macOS, `./gradlew build`).
4. The jar is in `build/libs/`. Use the one without `-sources` in its name.


## Commands

Messages starting with `.af` or `.autofish` are handled by the mod and cancelled before they are sent, so the server never sees them.

| Command                  | What it does                                        |
|--------------------------|-----------------------------------------------------|
| `.af`                    | Show help                                           |
| `.af list`               | List every setting with its value and default       |
| `.af <setting>`          | Show one setting and what it does                   |
| `.af <setting> <value>`  | Change a setting (saved automatically)              |
| `.af reset [setting]`    | Reset one setting, or all of them                   |
| `.af on` / `off` / `toggle` | Turn fishing on or off, same as the keybind      |


Settings are stored in `config/autofish.properties`. Delays are in **ticks** (20 ticks = 1 second).

### Fishing

| Setting          | Default | Description |
|------------------|---------|-------------|
| `min-hook-age`   | 40      | Ticks the bobber must exist before a bite can count, so the cast can settle |
| `bite-speed`     | 0.04    | Downward bobber speed that counts as a bite. Higher means less sensitive |
| `reel-delay`     | 4       | Reaction time between spotting a bite and reeling in |
| `recast-delay`   | 20      | Ticks to wait after reeling in before casting again |
| `cast-cooldown`  | 10      | Ticks to do nothing after casting, so a cast is never double-fired |
| `catch-cooldown` | 10      | Ticks after reeling before another reel is allowed. Stops double-clicks on high ping |
| `hook-timeout`   | 1200    | Reel in and recast if nothing bites after this many ticks. `0` disables it |
| `randomness`     | 30      | Percent each delay may vary up or down. `0` gives exact delays |

### Evasive tactics

| Setting          | Default | Description |
|------------------|---------|-------------|
| `anti-afk`       | on      | Master switch for everything in this table |
| `afk-interval`   | 400     | Average ticks between periodic anti-AFK actions |
| `jiggle`         | on      | Small head jiggle every time you catch something |
| `jiggle-degrees` | 2.0     | Maximum head movement in degrees. Pitch moves half as much |
| `idle-look`      | on      | Head jiggle as one of the periodic actions |
| `strafe`         | on      | Tap A then D (or D then A) so you move slightly and return |
| `strafe-ticks`   | 2       | Ticks each strafe tap is held. Keep this small |
| `sneak-tap`      | on      | Periodic sneak tap, with no movement |
| `jump`           | off     | Periodic jump in place |

## How it works

- **Bite detection.** The mod watches your bobber. Once it has been in the water for `min-hook-age` ticks and is pulled down faster than `bite-speed`, it counts as a bite. After a short random `reel-delay` it reels in.
- **Recasting.** After the bobber is gone, it waits `recast-delay` ticks and casts again.
- **Randomised timing.** Every delay is varied by up to `randomness` percent and re-rolled each time.
- **Dead casts.** If nothing bites within about `hook-timeout` ticks, for example because the bobber landed on land, it reels in and recasts.
- **Head jiggle.** The head moves a few degrees and returns, ending at the original angle. A recast waits for the jiggle to finish.
- **Periodic actions.** Every `afk-interval` ticks (randomised), one enabled action is picked at random: head jiggle, strafe, sneak tap or jump.
- **Strafe.** Both taps last the same number of ticks, so you end up almost where you started. Stand away from ledges, lava and other hazards until you have seen how far it moves you, or turn it off with `.af strafe off`.
