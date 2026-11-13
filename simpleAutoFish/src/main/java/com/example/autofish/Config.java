package com.example.autofish;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * All tunable values. Every setting can be changed in game with the .af command and is
 * saved to config/autofish.properties. Delays are in ticks (20 ticks = 1 second).
 */
public final class Config {
	public enum Kind { INT, DOUBLE, BOOL }

	public static final class Setting {
		public final String name;
		public final String description;
		public final Kind kind;
		public final double def;
		public final double min;
		public final double max;
		private double value;

		private Setting(String name, String description, Kind kind, double def, double min, double max) {
			this.name = name;
			this.description = description;
			this.kind = kind;
			this.def = def;
			this.min = min;
			this.max = max;
			this.value = def;
			ALL.add(this);
		}

		public int asInt() {
			return (int) Math.round(value);
		}

		public double asDouble() {
			return value;
		}

		public boolean asBool() {
			return value != 0;
		}

		public String display() {
			return format(value);
		}

		public String defaultDisplay() {
			return format(def);
		}

		private String format(double v) {
			return switch (kind) {
				case BOOL -> v != 0 ? "on" : "off";
				case INT -> Integer.toString((int) Math.round(v));
				case DOUBLE -> String.format(Locale.ROOT, "%.3f", v);
			};
		}

		public void reset() {
			value = def;
		}

		/** Returns null on success, otherwise an error message. */
		public String set(String text) {
			double parsed;
			if (kind == Kind.BOOL) {
				switch (text.toLowerCase(Locale.ROOT)) {
					case "on", "true", "yes", "1" -> parsed = 1;
					case "off", "false", "no", "0" -> parsed = 0;
					default -> {
						return "Expected on/off for " + name;
					}
				}
			} else {
				try {
					parsed = Double.parseDouble(text);
				} catch (NumberFormatException e) {
					return "'" + text + "' is not a number";
				}
				if (kind == Kind.INT) {
					parsed = Math.round(parsed);
				}
				if (parsed < min || parsed > max) {
					return name + " must be between " + format(min) + " and " + format(max);
				}
			}
			value = parsed;
			return null;
		}
	}

	private static final List<Setting> ALL = new ArrayList<>();

	// ---- Fishing ----
	public static final Setting MIN_HOOK_AGE = new Setting("min-hook-age",
			"Ticks the bobber must exist before a bite can count (lets the cast settle)", Kind.INT, 40, 0, 600);
	public static final Setting BITE_SPEED = new Setting("bite-speed",
			"Downward bobber speed that counts as a bite (positive number, bigger = less sensitive)", Kind.DOUBLE, 0.04, 0.005, 0.5);
	public static final Setting REEL_DELAY = new Setting("reel-delay",
			"Reaction time between seeing a bite and reeling in (ticks)", Kind.INT, 4, 0, 20);
	public static final Setting RECAST_DELAY = new Setting("recast-delay",
			"Ticks to wait after reeling in before casting again", Kind.INT, 20, 1, 400);
	public static final Setting CAST_COOLDOWN = new Setting("cast-cooldown",
			"Ticks to do nothing after casting, so a cast is never double-fired", Kind.INT, 10, 0, 200);
	public static final Setting CATCH_COOLDOWN = new Setting("catch-cooldown",
			"Ticks after reeling before another reel is allowed (stops double-clicks on high ping)", Kind.INT, 10, 0, 200);
	public static final Setting HOOK_TIMEOUT = new Setting("hook-timeout",
			"Reel in and recast if nothing bites after this many ticks (0 = never)", Kind.INT, 1200, 0, 24000);
	public static final Setting RANDOMNESS = new Setting("randomness",
			"Percent each delay may vary up or down (0 = exact delays)", Kind.INT, 30, 0, 90);

	// ---- Anti-AFK ----
	public static final Setting ANTI_AFK = new Setting("anti-afk",
			"Master switch for all the anti-AFK actions below", Kind.BOOL, 1, 0, 1);
	public static final Setting AFK_INTERVAL = new Setting("afk-interval",
			"Average ticks between anti-AFK actions", Kind.INT, 400, 40, 12000);
	public static final Setting JIGGLE = new Setting("jiggle",
			"Small head jiggle every time you catch something", Kind.BOOL, 1, 0, 1);
	public static final Setting JIGGLE_DEGREES = new Setting("jiggle-degrees",
			"Maximum head movement in degrees (pitch moves half as much)", Kind.DOUBLE, 2.0, 0.1, 20);
	public static final Setting IDLE_LOOK = new Setting("idle-look",
			"Also jiggle the head as one of the periodic anti-AFK actions", Kind.BOOL, 1, 0, 1);
	public static final Setting STRAFE = new Setting("strafe",
			"Periodically tap A then D (or D then A) so you move slightly and return", Kind.BOOL, 1, 0, 1);
	public static final Setting STRAFE_TICKS = new Setting("strafe-ticks",
			"Ticks each strafe tap is held (keep small so you never leave your spot)", Kind.INT, 2, 1, 6);
	public static final Setting SNEAK_TAP = new Setting("sneak-tap",
			"Periodically tap sneak (no movement)", Kind.BOOL, 1, 0, 1);
	public static final Setting JUMP = new Setting("jump",
			"Periodically jump in place", Kind.BOOL, 0, 0, 1);

	private Config() {
	}

	public static List<Setting> all() {
		return Collections.unmodifiableList(ALL);
	}

	public static Setting find(String name) {
		for (Setting s : ALL) {
			if (s.name.equalsIgnoreCase(name)) {
				return s;
			}
		}
		return null;
	}

	public static void resetAll() {
		ALL.forEach(Setting::reset);
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("autofish.properties");
	}

	public static void load() {
		Path path = file();
		if (!Files.exists(path)) {
			return;
		}
		Properties props = new Properties();
		try (InputStream in = Files.newInputStream(path)) {
			props.load(in);
		} catch (IOException e) {
			return;
		}
		for (Setting s : ALL) {
			String text = props.getProperty(s.name);
			if (text != null) {
				s.set(text.trim()); // invalid values are ignored and keep the default
			}
		}
	}

	public static void save() {
		Properties props = new Properties();
		for (Setting s : ALL) {
			props.setProperty(s.name, s.kind == Kind.BOOL ? (s.asBool() ? "on" : "off") : s.display());
		}
		try (OutputStream out = Files.newOutputStream(file())) {
			props.store(out, "AutoFish settings (edit in game with .af)");
		} catch (IOException ignored) {
			// Not being able to save is not worth crashing the game over.
		}
	}
}
