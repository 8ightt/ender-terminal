package dev.enderterminal;

import dev.enderterminal.EnderTerminalConfig.Provider;
import dev.enderterminal.provider.OpenAiCompatibleProvider;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SettingsScreen extends Screen {
	private record Label(int x, int y, String text, int color) {
	}

	private static final int FIELD_W = 320;
	private static final int ROW = 36;
	private static final int LABEL = 0xFFA0A0A0;
	private static final int HINT = 0xFF707070;
	/** Settings scroll between the title and the Save/Cancel row, which stays pinned to the bottom. */
	private static final int CONTENT_TOP = 24;

	private final @Nullable Screen parent;
	private final TerminalSession session = EnderTerminalClient.session();
	private final EnderTerminalConfig edit = session.config().copy();
	private final List<Label> labels = new ArrayList<>();
	private int scroll;
	private int maxScroll;
	/** Result of asking a local server which models it has, shown under the Model field. */
	private volatile String detectedModels = "";

	public SettingsScreen(@Nullable Screen parent) {
		super(Component.literal("Ender Terminal Settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		labels.clear();
		int x = (width - FIELD_W) / 2;
		int y = CONTENT_TOP + 4 - scroll;

		add(CycleButton.<Provider>builder(SettingsScreen::providerLabel, edit.provider)
				.withValues(Provider.values())
				.create(x, y, FIELD_W, 20, Component.literal("Provider"), (b, v) -> {
					edit.provider = v;
					rebuildWidgets();
				}));
		y += 28;

		switch (edit.provider) {
			case NONE -> {
				label(x, y, "Pick a provider above:", LABEL);
				label(x, y + 12, "Anthropic API - pay-per-use API key from console.anthropic.com.", HINT);
				label(x, y + 24, "OpenAI-compatible - OpenAI, OpenRouter, Groq, or a local Ollama / LM Studio.", HINT);
				y += 40;
			}
			case ANTHROPIC_API -> {
				y = field(x, y, "API key (console.anthropic.com > API keys)", edit.anthropicApiKey, true, v -> edit.anthropicApiKey = v);
				y = field(x, y, "Model", edit.anthropicModel, false, v -> edit.anthropicModel = v);
			}
			case OPENAI_COMPATIBLE -> {
				label(x, y, "Presets", LABEL);
				y += 12;
				String[][] presets = {
						{"OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"},
						{"OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"},
						{"Ollama", "http://localhost:11434/v1", "llama3.2"},
						{"LM Studio", "http://localhost:1234/v1", "local-model"},
				};
				int bw = (FIELD_W - 12) / 4;
				for (int i = 0; i < presets.length; i++) {
					String[] p = presets[i];
					add(Button.builder(Component.literal(p[0]), b -> {
						edit.openaiBaseUrl = p[1];
						edit.openaiModel = p[2];
						detectedModels = "";
						rebuildWidgets();
						if (p[1].startsWith("http://localhost")) detectModels(p[0]);
					}).bounds(x + i * (bw + 4), y, bw, 20).build());
				}
				y += 26;
				y = field(x, y, "Base URL", edit.openaiBaseUrl, false, v -> edit.openaiBaseUrl = v);
				y = field(x, y, "API key (leave empty for Ollama / LM Studio)", edit.openaiApiKey, true, v -> edit.openaiApiKey = v);
				y = field(x, y, "Model", edit.openaiModel, false, v -> edit.openaiModel = v);
				if (!detectedModels.isEmpty()) {
					label(x, y - 5, detectedModels, HINT);
					y += 8;
				}
			}
		}

		add(CycleButton.onOffBuilder(edit.shareGameInfo)
				.create(x, y, FIELD_W, 20, Component.literal("Share game info with AI"), (b, v) -> edit.shareGameInfo = v));
		label(x, y + 22, "Position, inventory, view, nearby mobs, mods. See /context.", HINT);
		y += 36;

		add(CycleButton.onOffBuilder(edit.limitHistory)
				.create(x, y, FIELD_W, 20, Component.literal("Remember only last " + EnderTerminalConfig.HISTORY_LIMIT + " messages"),
						(b, v) -> edit.limitHistory = v));
		label(x, y + 22, "Keeps API costs down. Older messages stay on screen.", HINT);
		y += 36;

		y = field(x, y, "System prompt (personality and rules)", edit.systemPrompt, false, v -> edit.systemPrompt = v);

		maxScroll = Math.max(0, y + scroll - contentBottom());
		if (scroll > maxScroll) {
			scroll = maxScroll;
			rebuildWidgets();
			return;
		}

		int bw = (FIELD_W - 4) / 2;
		int by = height - 26;
		addRenderableWidget(Button.builder(Component.literal("Save"), b -> save()).bounds(x, by, bw, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(x + bw + 4, by, bw, 20).build());
	}

	private static Component providerLabel(Provider p) {
		return Component.literal(switch (p) {
			case NONE -> "None";
			case ANTHROPIC_API -> "Anthropic API key";
			case OPENAI_COMPATIBLE -> "OpenAI-compatible";
		});
	}

	private int contentBottom() {
		return height - 32;
	}

	/** Adds a widget only if it lies fully inside the scrollable area. */
	private void add(AbstractWidget widget) {
		if (widget.getY() >= CONTENT_TOP && widget.getY() + widget.getHeight() <= contentBottom()) addRenderableWidget(widget);
	}

	private void label(int x, int y, String text, int color) {
		if (y >= CONTENT_TOP && y + font.lineHeight <= contentBottom()) labels.add(new Label(x, y, text, color));
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		int next = Math.clamp(scroll - (int) Math.signum(scrollY) * 24, 0, maxScroll);
		if (next != scroll) {
			scroll = next;
			rebuildWidgets();
		}
		return true;
	}

	private int field(int x, int y, String name, String value, boolean secret, Consumer<String> onChange) {
		label(x, y, name, LABEL);
		EditBox box = new EditBox(font, x, y + 11, FIELD_W, 18, Component.literal(name));
		box.setMaxLength(4000);
		box.setValue(value);
		box.setResponder(onChange);
		if (secret) box.addFormatter((text, offset) -> FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY));
		add(box);
		return y + ROW;
	}

	/** Asks the local server for its models and fills in the first one. */
	private void detectModels(String serverName) {
		String url = edit.openaiBaseUrl;
		detectedModels = "Looking for installed models...";
		Thread t = new Thread(() -> {
			List<String> models = OpenAiCompatibleProvider.listModels(url, edit.openaiApiKey);
			minecraft.execute(() -> {
				if (!url.equals(edit.openaiBaseUrl)) return;
				if (models.isEmpty()) {
					detectedModels = serverName + " not reachable or has no models. Is it running?";
				} else {
					edit.openaiModel = models.getFirst();
					detectedModels = "Installed: " + String.join(", ", models);
				}
				rebuildWidgets();
			});
		}, "Ender Terminal models");
		t.setDaemon(true);
		t.start();
	}

	private void save() {
		edit.save();
		session.applyConfig(edit);
		onClose();
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		graphics.fill(0, 0, width, height, 0xF20B0B10);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		graphics.centeredText(font, title, width / 2, 10, 0xFFC77DFF);
		for (Label l : labels) graphics.text(font, l.text(), l.x(), l.y(), l.color(), false);
		int arrowX = (width + FIELD_W) / 2 + 6;
		if (scroll > 0) graphics.text(font, "^", arrowX, CONTENT_TOP + 2, 0xFFC77DFF, false);
		if (scroll < maxScroll) graphics.text(font, "v more", arrowX, contentBottom() - font.lineHeight, 0xFFC77DFF, false);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
