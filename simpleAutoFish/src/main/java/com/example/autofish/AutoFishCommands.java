package com.example.autofish;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Handles chat lines that start with ".af" or ".autofish". Not sent to server.
 */
public final class AutoFishCommands {
	private final AutoFishClient mod;

	public AutoFishCommands(AutoFishClient mod) {
		this.mod = mod;
	}

	public boolean handle(String message) {
		String[] args = message.trim().split("\\s+");
		String head = args[0].toLowerCase(Locale.ROOT);
		if (!head.equals(".af") && !head.equals(".autofish")) {
			return false;
		}

		if (args.length == 1) {
			help();
			return true;
		}

		String sub = args[1].toLowerCase(Locale.ROOT);
		switch (sub) {
			case "help", "?" -> help();
			case "list" -> list();
			case "toggle" -> {
				mod.setEnabled(!mod.isEnabled());
			}
			case "on" -> mod.setEnabled(true);
			case "off" -> mod.setEnabled(false);
			case "reset" -> reset(args);
			default -> setting(sub, args);
		}
		return true;
	}

	private void setting(String name, String[] args) {
		Config.Setting s = Config.find(name);
		if (s == null) {
			say("Unknown setting or command '" + name + "'. Try .af list");
			return;
		}
		if (args.length == 2) {
			say(s.name + " = " + s.display() + "  (default " + s.defaultDisplay() + ")  " + s.description);
			return;
		}
		String error = s.set(args[2]);
		if (error != null) {
			say(error);
			return;
		}
		Config.save();
		say(s.name + " set to " + s.display());
	}

	private void reset(String[] args) {
		if (args.length == 2) {
			Config.resetAll();
			Config.save();
			say("All settings reset to defaults");
			return;
		}
		Config.Setting s = Config.find(args[2]);
		if (s == null) {
			say("Unknown setting '" + args[2] + "'");
			return;
		}
		s.reset();
		Config.save();
		say(s.name + " reset to " + s.display());
	}

	private void list() {
		say("AutoFish settings (delays are in ticks, 20 = 1s):");
		for (Config.Setting s : Config.all()) {
			say(" " + s.name + " = " + s.display() + "  (default " + s.defaultDisplay() + ")");
		}
		say("Use .af <setting> for a description.");
	}

	private void help() {
		say("AutoFish commands (never sent to the server):");
		say(" .af list              show every setting");
		say(" .af <setting>         show one setting and what it does");
		say(" .af <setting> <value> change it");
		say(" .af reset [setting]   restore defaults");
		say(" .af on | off | toggle turn fishing on or off");
	}

	static void say(String text) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.sendSystemMessage(Component.literal("[AutoFish] " + text));
		}
	}
}
