package de.truthorlie.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import de.truthorlie.Net;

public class TruthOrLieClient implements ClientModInitializer {
	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("truthorlie-client.properties");
	}

	private static void loadSettings() {
		try {
			Path f = file();
			if (Files.exists(f)) {
				Properties p = new Properties();
				p.load(Files.newBufferedReader(f));
				ClientState.hudScale = Math.max(0.5f, Math.min(2f, Float.parseFloat(p.getProperty("scale", "1.0"))));
				ClientState.hudEnabled = Boolean.parseBoolean(p.getProperty("enabled", "true"));
			}
		} catch (Exception ignored) {
			// Standardwerte nutzen
		}
	}

	private static void saveSettings() {
		try {
			Properties p = new Properties();
			p.setProperty("scale", String.valueOf(ClientState.hudScale));
			p.setProperty("enabled", String.valueOf(ClientState.hudEnabled));
			try (var w = Files.newBufferedWriter(file())) {
				p.store(w, "Wahr oder Luege - HUD");
			}
		} catch (Exception ignored) {
			// nicht kritisch
		}
	}

	@Override
	public void onInitializeClient() {
		loadSettings();
		ClientPlayNetworking.registerGlobalReceiver(Net.StatePayload.TYPE, (payload, context) -> ClientState.applyState(payload.data()));
		ClientPlayNetworking.registerGlobalReceiver(Net.BannerPayload.TYPE, (payload, context) -> ClientState.applyBanner(payload.data()));

		ClientTickEvents.END_CLIENT_TICK.register(client -> ClientState.tick++);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientState.reset());

		// Nach den Misc-Overlays: erbt damit die Regel "F1 blendet das HUD aus" und liegt unter Hotbar, Chat und Fadenkreuz.
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, Identifier.fromNamespaceAndPath("truthorlie", "hud"), TolHud::render);

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommands.literal("tolhud")
						.executes(context -> {
							context.getSource().sendFeedback(Component.literal("/tolhud on | off | size <50-200>  (aktuell " + Math.round(ClientState.hudScale * 100) + "%)"));
							return Command.SINGLE_SUCCESS;
						})
						.then(ClientCommands.literal("on").executes(context -> {
							ClientState.hudEnabled = true;
							saveSettings();
							context.getSource().sendFeedback(Component.literal("HUD an."));
							return Command.SINGLE_SUCCESS;
						}))
						.then(ClientCommands.literal("off").executes(context -> {
							ClientState.hudEnabled = false;
							saveSettings();
							context.getSource().sendFeedback(Component.literal("HUD aus."));
							return Command.SINGLE_SUCCESS;
						}))
						.then(ClientCommands.literal("size").then(ClientCommands.argument("prozent", IntegerArgumentType.integer(50, 200)).executes(context -> {
							int pct = IntegerArgumentType.getInteger(context, "prozent");
							ClientState.hudScale = pct / 100f;
							saveSettings();
							context.getSource().sendFeedback(Component.literal("HUD-Größe: " + pct + "%"));
							return Command.SINGLE_SUCCESS;
						})))
						.then(ClientCommands.literal("scores").executes(context -> {
							ClientState.scoresEnabled = !ClientState.scoresEnabled;
							context.getSource().sendFeedback(Component.literal("Rangliste " + (ClientState.scoresEnabled ? "an." : "aus.")));
							return Command.SINGLE_SUCCESS;
						}))));
	}
}
