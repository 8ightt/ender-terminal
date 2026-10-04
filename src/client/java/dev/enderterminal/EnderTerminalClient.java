package dev.enderterminal;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnderTerminalClient implements ClientModInitializer {
	public static final String MOD_ID = "enderterminal";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static TerminalSession session;

	public static TerminalSession session() {
		return session;
	}

	@Override
	public void onInitializeClient() {
		FabricLoader loader = FabricLoader.getInstance();
		EnderTerminalConfig config = EnderTerminalConfig.load(loader.getConfigDir().resolve("enderterminal.json"));
		session = new TerminalSession(config, loader.getGameDir().resolve("enderterminal"));

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		KeyMapping openKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.enderterminal.open", InputConstants.KEY_GRAVE, category));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (openKey.consumeClick()) {
				if (mc.gui.screen() == null) mc.gui.setScreen(new TerminalScreen(null));
			}
		});

		// "Ender Terminal" button in the top-left corner of the Escape menu.
		ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
			if (screen instanceof PauseScreen) {
				SpriteIconButton button = SpriteIconButton.builder(Component.translatable("enderterminal.button"),
								b -> mc.gui.setScreen(new TerminalScreen(screen)), false)
						.sprite(Identifier.fromNamespaceAndPath(MOD_ID, "terminal_button"), 16, 16)
						.width(110)
						.build();
				button.setPosition(4, 4);
				Screens.getWidgets(screen).add(button);
			}
		});
	}
}
