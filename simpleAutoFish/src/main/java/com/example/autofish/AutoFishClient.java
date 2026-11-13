package com.example.autofish;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class AutoFishClient implements ClientModInitializer {
	public static final String MOD_ID = "simpleautofish";

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(MOD_ID, "main"));

	private static KeyMapping toggleKey;

	private boolean enabled = false;

	// Fishing state. Timers count down once per tick; -1 means "not running".
	private int recastTimer = -1;
	private int reelTimer = -1;
	private int hookLimit = -1;
	private int cooldown = 0;
	private int catchCooldown = 0;

	// Anti-AFK state.
	private int afkTimer = 0;
	private final ArrayDeque<float[]> lookQueue = new ArrayDeque<>();
	private KeyMapping tapFirst;
	private KeyMapping tapSecond;
	private int tapPhase = 0; // 0 = idle, 1 = holding first key, 2 = holding second key
	private int tapTicks = 0;
	private int tapSecondTicks = 0;

	@Override
	public void onInitializeClient() {
		Config.load();

		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.autofish.toggle",
				InputConstants.KEY_V,
				CATEGORY));

		AutoFishCommands commands = new AutoFishCommands(this);
		ClientSendMessageEvents.ALLOW_CHAT.register(message -> !commands.handle(message));
		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean value) {
		Minecraft mc = Minecraft.getInstance();
		enabled = value;
		resetState(mc);
		if (afkTimer <= 0) {
			afkTimer = rnd(Config.AFK_INTERVAL.asInt());
		}
		Player player = mc.player;
		if (player != null) {
			player.sendOverlayMessage(
					Component.translatable(enabled ? "autofish.enabled" : "autofish.disabled"));
		}
	}

	private void resetState(Minecraft mc) {
		recastTimer = -1;
		reelTimer = -1;
		hookLimit = -1;
		cooldown = 0;
		catchCooldown = 0;
		afkTimer = enabled ? rnd(Config.AFK_INTERVAL.asInt()) : 0;
		lookQueue.clear();
		abortTap();
	}

	private void onTick(Minecraft mc) {
		Player player = mc.player;
		if (player == null || mc.gameMode == null) {
			if (enabled) {
				enabled = false;
				resetState(mc);
			}
			return;
		}

		while (toggleKey.consumeClick()) {
			setEnabled(!enabled);
		}

		if (!enabled) {
			return;
		}

		// Timers that must run every tick, whatever else is happening.
		if (catchCooldown > 0) {
			catchCooldown--;
		}

		tickLook(player);
		tickTap();
		tickAntiAfk(player);

		if (cooldown > 0) {
			cooldown--;
			return;
		}

		// Only fish while a fishing rod is in the main hand.
		if (!player.getMainHandItem().is(Items.FISHING_ROD)) {
			recastTimer = -1;
			reelTimer = -1;
			hookLimit = -1;
			return;
		}

		FishingHook hook = player.fishing;

		if (hook != null && !hook.isRemoved()) {
			watchHook(mc, player, hook);
			return;
		}

		// No bobber out.
		reelTimer = -1;
		hookLimit = -1;
		if (recastTimer < 0) {
			recastTimer = rnd(Config.RECAST_DELAY.asInt());
		}
		if (recastTimer > 0) {
			recastTimer--;
			return;
		}
		if (!lookQueue.isEmpty()) {
			return; // let a head jiggle finish before casting so the aim returns to normal
		}

		useRod(mc, player); // cast
		recastTimer = -1;
		cooldown = rnd(Config.CAST_COOLDOWN.asInt());
	}

	private void watchHook(Minecraft mc, Player player, FishingHook hook) {
		recastTimer = -1;

		if (hookLimit < 0) {
			int timeout = Config.HOOK_TIMEOUT.asInt();
			hookLimit = timeout > 0 ? rnd(timeout) : Integer.MAX_VALUE;
		}

		// Not yet reacting to a bite: look for one, or give up on a dead cast.
		if (reelTimer < 0) {
			if (catchCooldown > 0) {
				return;
			}
			boolean bite = hook.tickCount >= Config.MIN_HOOK_AGE.asInt()
					&& hook.isInWater()
					&& isBite(hook);
			if (bite) {
				reelTimer = rnd(Config.REEL_DELAY.asInt());
			} else if (hook.tickCount > hookLimit) {
				// Nothing bit for ages (bobber on land or stuck): reel in, recast and count as activity.
				reel(mc, player, false);
				return;
			} else {
				return;
			}
		}

		// Reacting to a bite: wait out the reaction delay, then reel in.
		if (reelTimer > 0) {
			reelTimer--;
			return;
		}
		reel(mc, player, true);
	}

	private void reel(Minecraft mc, Player player, boolean caughtSomething) {
		useRod(mc, player);
		reelTimer = -1;
		hookLimit = -1;
		catchCooldown = rnd(Config.CATCH_COOLDOWN.asInt());
		if (caughtSomething && Config.ANTI_AFK.asBool() && Config.JIGGLE.asBool()) {
			queueJiggle();
		}
	}


	// Anti anti auto fish

	private void tickAntiAfk(Player player) {
		if (!Config.ANTI_AFK.asBool()) {
			return;
		}
		if (afkTimer > 0) {
			afkTimer--;
			return;
		}
		if (tapPhase != 0 || !lookQueue.isEmpty()) {
			return; // wait for the current action to finish
		}

		Minecraft mc = Minecraft.getInstance();
		List<Runnable> actions = new ArrayList<>();
		if (Config.IDLE_LOOK.asBool()) {
			actions.add(this::queueJiggle);
		}
		if (Config.STRAFE.asBool()) {
			actions.add(() -> {
				boolean leftFirst = ThreadLocalRandom.current().nextBoolean();
				int ticks = rnd(Config.STRAFE_TICKS.asInt());
				// Same number of ticks each way, so you end up (almost) where you started.
				startTap(leftFirst ? mc.options.keyLeft : mc.options.keyRight, ticks,
						leftFirst ? mc.options.keyRight : mc.options.keyLeft, ticks);
			});
		}
		if (Config.SNEAK_TAP.asBool()) {
			actions.add(() -> startTap(mc.options.keyShift, 1 + ThreadLocalRandom.current().nextInt(3), null, 0));
		}
		if (Config.JUMP.asBool()) {
			actions.add(() -> startTap(mc.options.keyJump, 1, null, 0));
		}

		if (!actions.isEmpty()) {
			actions.get(ThreadLocalRandom.current().nextInt(actions.size())).run();
		}
		afkTimer = rnd(Config.AFK_INTERVAL.asInt());
	}

	/** Small mouse jiggle . */
	private void queueJiggle() {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		float max = (float) Config.JIGGLE_DEGREES.asDouble();
		float yaw = (r.nextFloat() * 2f - 1f) * max;
		float pitch = (r.nextFloat() * 2f - 1f) * max * 0.5f;
		int steps = 2 + r.nextInt(3);
		for (int i = 0; i < steps; i++) {
			lookQueue.add(new float[] {yaw / steps, pitch / steps});
		}
		for (int i = 0; i < steps; i++) {
			lookQueue.add(new float[] {-yaw / steps, -pitch / steps});
		}
	}

	private void tickLook(Player player) {
		float[] step = lookQueue.poll();
		if (step != null) {
			player.setYRot(player.getYRot() + step[0]);
			player.setXRot(player.getXRot() + step[1]);
		}
	}

	private void startTap(KeyMapping first, int firstTicks, KeyMapping second, int secondTicks) {
		abortTap();
		tapFirst = first;
		tapSecond = second;
		tapTicks = Math.max(1, firstTicks);
		tapSecondTicks = secondTicks;
		tapPhase = 1;
	}

	private void tickTap() {
		if (tapPhase == 0) {
			return;
		}
		KeyMapping active = tapPhase == 1 ? tapFirst : tapSecond;
		active.setDown(true);
		if (--tapTicks > 0) {
			return;
		}
		active.setDown(false);
		if (tapPhase == 1 && tapSecond != null) {
			tapPhase = 2;
			tapTicks = Math.max(1, tapSecondTicks);
		} else {
			tapPhase = 0;
		}
	}

	/** Releases any held key. */
	private void abortTap() {
		if (tapFirst != null) {
			tapFirst.setDown(false);
		}
		if (tapSecond != null) {
			tapSecond.setDown(false);
		}
		tapFirst = null;
		tapSecond = null;
		tapPhase = 0;
		tapTicks = 0;
	}


	// Helpers

	/** Returns base varied by +/- the configured randomness. A base of 0 stays 0. */
	private static int rnd(int base) {
		if (base <= 0) {
			return 0;
		}
		double spread = Config.RANDOMNESS.asInt() / 100.0;
		double low = base * (1.0 - spread);
		double high = base * (1.0 + spread);
		double value = low + ThreadLocalRandom.current().nextDouble() * (high - low);
		return Math.max(1, (int) Math.round(value));
	}

	private static boolean isBite(FishingHook hook) {
		Vec3 motion = hook.getDeltaMovement();
		return motion.y < -Config.BITE_SPEED.asDouble();
	}

	private static void useRod(Minecraft mc, Player player) {
		// No arm-swing clientside. cba to figure that out.
		mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
	}
}
