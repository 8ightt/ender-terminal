package dev.enderterminal;

import com.mojang.blaze3d.platform.InputConstants;
import dev.enderterminal.EnderTerminalConfig.Provider;
import dev.enderterminal.provider.AnthropicApiProvider;
import dev.enderterminal.provider.OpenAiCompatibleProvider;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
	private static final int PROMPT_H = 64;
	private static final int SUGGEST_ROWS = 8;
	private static final int SUGGEST_ROW_H = 12;
	/** Wait this long after the URL or key last changed before asking the server for its models. */
	private static final long FETCH_DELAY_MS = 800;

	private final @Nullable Screen parent;
	private final TerminalSession session = EnderTerminalClient.session();
	private final EnderTerminalConfig edit = session.config().copy();
	private final List<Label> labels = new ArrayList<>();
	private int scroll;
	private int maxScroll;
	private @Nullable MultiLineEditBox promptBox;

	// Model search: the server's model list, filtered by what's typed in the Model field and shown as a dropdown.
	private @Nullable EditBox modelBox;
	private int modelStatusY = -1;
	private String modelStatus = "";
	private List<String> knownModels = List.of();
	private List<String> suggestions = List.of();
	private int selected = -1;
	private int suggestScroll;
	/** Hidden with Esc or after picking, until the text changes again. */
	private boolean suggestDismissed;
	/** The provider/URL/key the model list belongs to; refetched after they change. */
	private String fetchedFor = "";
	private String pendingFor = "";
	private long pendingSince;
	/** After a preset, replace a model the server doesn't have with its first one. */
	private boolean pickFirstModel;

	public SettingsScreen(@Nullable Screen parent) {
		super(Component.literal("Ender Terminal Settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		labels.clear();
		promptBox = null;
		modelBox = null;
		modelStatusY = -1;
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
				y = wrapped(x, y + 12, "Anthropic API - pay-per-use API key from console.anthropic.com.", HINT);
				y = wrapped(x, y, "OpenAI-compatible - OpenAI, OpenRouter, Groq, or a local Ollama / LM Studio.", HINT);
				y += 4;
			}
			case ANTHROPIC_API -> {
				y = field(x, y, "API key (console.anthropic.com > API keys)", edit.anthropicApiKey, true, v -> edit.anthropicApiKey = v);
				y = modelField(x, y, edit.anthropicModel, v -> edit.anthropicModel = v);
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
						pickFirstModel = true;
						pendingSince = 0; // fetch on the next tick instead of waiting for typing to stop
						rebuildWidgets();
					}).bounds(x + i * (bw + 4), y, bw, 20).build());
				}
				y += 26;
				y = field(x, y, "Base URL", edit.openaiBaseUrl, false, v -> edit.openaiBaseUrl = v);
				y = field(x, y, "API key (leave empty for Ollama / LM Studio)", edit.openaiApiKey, true, v -> edit.openaiApiKey = v);
				y = modelField(x, y, edit.openaiModel, v -> edit.openaiModel = v);
			}
		}

		add(CycleButton.onOffBuilder(edit.shareGameInfo)
				.create(x, y, FIELD_W, 20, Component.literal("Share game info with AI"), (b, v) -> edit.shareGameInfo = v));
		label(x, y + 22, "Position, inventory, view, nearby mobs, mods. See /context.", HINT);
		y += 36;

		add(CycleButton.onOffBuilder(edit.limitHistory)
				.create(x, y, FIELD_W, 20, Component.literal("Remember only last " + EnderTerminalConfig.HISTORY_LIMIT + " messages"),
						(b, v) -> edit.limitHistory = v));
		boolean local = edit.provider == Provider.OPENAI_COMPATIBLE && OpenAiCompatibleProvider.localServerName(edit.openaiBaseUrl) != null;
		label(x, y + 22, (local ? "Keeps replies fast on local models." : "Keeps API costs down.") + " Older messages stay on screen.", HINT);
		y += 36;

		label(x, y, "System prompt (personality and rules)", LABEL);
		// Shrinks to the space left when scrolled partly into view, rather than vanishing until it fits.
		int promptH = Math.min(PROMPT_H, contentBottom() - (y + 11));
		if (promptH >= 20) {
			promptBox = MultiLineEditBox.builder().setX(x).setY(y + 11)
					.build(font, FIELD_W, promptH, Component.literal("System prompt"));
			promptBox.setValue(edit.systemPrompt, true);
			promptBox.setValueListener(v -> edit.systemPrompt = v);
			add(promptBox);
		}
		y += 11 + PROMPT_H + 6;

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

	/** Leaves room above Save/Cancel for the "more below" hint. */
	private int contentBottom() {
		return height - 42;
	}

	/** Adds a widget only if it lies fully inside the scrollable area. */
	private void add(AbstractWidget widget) {
		if (widget.getY() >= CONTENT_TOP && widget.getY() + widget.getHeight() <= contentBottom()) addRenderableWidget(widget);
	}

	private void label(int x, int y, String text, int color) {
		if (y >= CONTENT_TOP && y + font.lineHeight <= contentBottom()) labels.add(new Label(x, y, text, color));
	}

	/** Adds a hint wrapped to the field width; returns the y below it. */
	private int wrapped(int x, int y, String text, int color) {
		for (var line : font.getSplitter().splitLines(text, FIELD_W, Style.EMPTY)) {
			label(x, y, line.getString(), color);
			y += 12;
		}
		return y;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (suggestionsShown() && suggestions.size() > SUGGEST_ROWS && (suggestionAt(x, y) >= 0 || modelBox.isMouseOver(x, y))) {
			suggestScroll = Math.clamp(suggestScroll - (int) Math.signum(scrollY), 0, suggestions.size() - SUGGEST_ROWS);
			return true;
		}
		// A long system prompt scrolls inside its own box.
		if (promptBox != null && promptBox.isMouseOver(x, y) && promptBox.mouseScrolled(x, y, scrollX, scrollY)) return true;
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

	/** The Model field, with a status line under it and a search dropdown while it has focus. */
	private int modelField(int x, int y, String value, Consumer<String> onChange) {
		label(x, y, "Model (type to search)", LABEL);
		EditBox box = new EditBox(font, x, y + 11, FIELD_W, 18, Component.literal("Model"));
		box.setMaxLength(200);
		box.setValue(value);
		box.setResponder(v -> {
			onChange.accept(v);
			suggestDismissed = false;
			updateSuggestions();
		});
		add(box);
		if (box.getY() >= CONTENT_TOP && box.getY() + box.getHeight() <= contentBottom()) modelBox = box;
		int statusY = y + 32;
		if (statusY >= CONTENT_TOP && statusY + font.lineHeight <= contentBottom()) modelStatusY = statusY;
		updateSuggestions();
		return y + ROW + 8;
	}

	/** Identifies the server and key the model list comes from, or "" when there's nothing to ask. */
	private String modelSource() {
		return switch (edit.provider) {
			case ANTHROPIC_API -> edit.anthropicApiKey.isBlank() ? "" : "anthropic|" + edit.anthropicApiKey.strip();
			case OPENAI_COMPATIBLE -> edit.openaiBaseUrl.isBlank() ? "" : "openai|" + edit.openaiBaseUrl.strip() + "|" + edit.openaiApiKey.strip();
			default -> "";
		};
	}

	/** Fetches the model list once the provider, URL and key have stopped changing for a moment. */
	@Override
	public void tick() {
		super.tick();
		String source = modelSource();
		if (source.equals(fetchedFor)) return;
		long now = System.currentTimeMillis();
		if (!source.equals(pendingFor)) {
			pendingFor = source;
			// A preset sets pendingSince to 0 so its list loads at once; typing waits for a pause.
			if (pendingSince != 0) pendingSince = now;
		}
		if (now - pendingSince < FETCH_DELAY_MS) return;
		fetchModels(source);
	}

	private void fetchModels(String source) {
		fetchedFor = source;
		pendingSince = System.currentTimeMillis();
		knownModels = List.of();
		updateSuggestions();
		if (source.isEmpty()) {
			modelStatus = edit.provider == Provider.ANTHROPIC_API ? "Enter an API key to list models." : "";
			return;
		}
		Provider provider = edit.provider;
		String url = edit.openaiBaseUrl;
		String key = provider == Provider.ANTHROPIC_API ? edit.anthropicApiKey : edit.openaiApiKey;
		String local = provider == Provider.OPENAI_COMPATIBLE ? OpenAiCompatibleProvider.localServerName(url) : null;
		modelStatus = "Looking for models...";
		Thread t = new Thread(() -> {
			List<String> models = provider == Provider.ANTHROPIC_API
					? AnthropicApiProvider.listModels(key)
					: OpenAiCompatibleProvider.listModels(url, key);
			minecraft.execute(() -> {
				if (!source.equals(fetchedFor)) return;
				knownModels = models;
				if (models.isEmpty()) {
					modelStatus = local != null ? local + " not reachable or has no models. Is it running?"
							: "Couldn't list models. Check the URL and API key.";
				} else {
					modelStatus = models.size() + (models.size() == 1 ? " model" : " models") + " available. Type to search.";
					if (pickFirstModel && provider == Provider.OPENAI_COMPATIBLE && !models.contains(edit.openaiModel)) {
						edit.openaiModel = models.getFirst();
						if (modelBox != null) modelBox.setValue(edit.openaiModel);
						suggestDismissed = true;
					}
				}
				pickFirstModel = false;
				updateSuggestions();
			});
		}, "Ender Terminal models");
		t.setDaemon(true);
		t.start();
	}

	/**
	 * Models containing every typed word, those that start with the text listed first. Empty text, or the name of a
	 * listed model, shows the whole list so it can be browsed.
	 */
	private void updateSuggestions() {
		String text = modelBox == null ? "" : modelBox.getValue().strip().toLowerCase(Locale.ROOT);
		int current = -1;
		for (int i = 0; i < knownModels.size(); i++) if (knownModels.get(i).equalsIgnoreCase(text)) current = i;
		if (current >= 0) {
			suggestions = knownModels;
			selected = -1;
			suggestScroll = 0;
			moveSelection(current + 1);
			return;
		}
		String[] words = text.isEmpty() ? new String[0] : text.split("\\s+");
		List<String> first = new ArrayList<>();
		List<String> rest = new ArrayList<>();
		for (String id : knownModels) {
			String lower = id.toLowerCase(Locale.ROOT);
			boolean all = true;
			for (String w : words) all &= lower.contains(w);
			if (!all) continue;
			// "gpt" should rank "openai/gpt-4o" (OpenRouter's vendor/model ids) with the direct matches.
			if (lower.startsWith(text) || lower.contains("/" + text)) first.add(id);
			else rest.add(id);
		}
		first.addAll(rest);
		suggestions = first;
		selected = -1;
		suggestScroll = 0;
	}

	private boolean suggestionsShown() {
		return modelBox != null && modelBox.isFocused() && !suggestDismissed && !suggestions.isEmpty();
	}

	private int visibleRows() {
		return Math.min(SUGGEST_ROWS, suggestions.size());
	}

	/** Top of the dropdown: below the field, or above it when there's no room below. */
	private int suggestTop() {
		int h = visibleRows() * SUGGEST_ROW_H + 2;
		int below = modelBox.getY() + modelBox.getHeight() + 1;
		return below + h <= height - 30 ? below : modelBox.getY() - 1 - h;
	}

	/** Index of the suggestion under the mouse, or -1. */
	private int suggestionAt(double mx, double my) {
		if (!suggestionsShown()) return -1;
		int top = suggestTop() + 1;
		if (mx < modelBox.getX() || mx >= modelBox.getX() + modelBox.getWidth() || my < top) return -1;
		int row = (int) ((my - top) / SUGGEST_ROW_H);
		return row < visibleRows() ? suggestScroll + row : -1;
	}

	private void pickSuggestion(int index) {
		if (modelBox == null || index < 0 || index >= suggestions.size()) return;
		modelBox.setValue(suggestions.get(index));
		modelBox.moveCursorToEnd(false);
		suggestDismissed = true;
	}

	private void moveSelection(int delta) {
		selected = Math.clamp(selected + delta, 0, suggestions.size() - 1);
		if (selected < suggestScroll) suggestScroll = selected;
		if (selected >= suggestScroll + SUGGEST_ROWS) suggestScroll = selected - SUGGEST_ROWS + 1;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (suggestionsShown()) {
			int key = event.key();
			if (key == InputConstants.KEY_DOWN || key == InputConstants.KEY_UP) {
				moveSelection(key == InputConstants.KEY_DOWN ? 1 : -1);
				return true;
			}
			if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER || key == InputConstants.KEY_TAB) {
				pickSuggestion(Math.max(selected, 0));
				return true;
			}
			if (key == InputConstants.KEY_ESCAPE) {
				suggestDismissed = true;
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int index = suggestionAt(event.x(), event.y());
		if (index >= 0) {
			pickSuggestion(index);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
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
		return true;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		graphics.fill(0, 0, width, height, 0xF20B0B10);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		graphics.centeredText(font, title, width / 2, 10, 0xFFC77DFF);
		for (Label l : labels) graphics.text(font, l.text(), l.x(), l.y(), l.color(), false);
		if (scroll > 0) {
			String up = "^ more";
			graphics.text(font, up, (width + FIELD_W) / 2 - font.width(up), 10, 0xFFC77DFF, false);
		}
		if (scroll < maxScroll) graphics.centeredText(font, Component.literal("v scroll for more v"), width / 2, contentBottom() + 2, 0xFFC77DFF);
		if (modelStatusY >= 0 && !modelStatus.isEmpty()) {
			graphics.text(font, modelStatus, (width - FIELD_W) / 2, modelStatusY, HINT, false);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
		if (suggestionsShown()) drawSuggestions(graphics, mouseX, mouseY);
	}

	/** Drawn after the widgets so it covers whatever sits below the Model field. */
	private void drawSuggestions(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = modelBox.getX();
		int w = modelBox.getWidth();
		int top = suggestTop();
		int rows = visibleRows();
		graphics.fill(x, top, x + w, top + rows * SUGGEST_ROW_H + 2, 0xFF6A4C93);
		graphics.fill(x + 1, top + 1, x + w - 1, top + rows * SUGGEST_ROW_H + 1, 0xFF101018);
		int hovered = suggestionAt(mouseX, mouseY);
		String count = suggestions.size() > rows ? (suggestScroll + rows) + "/" + suggestions.size() : "";
		for (int r = 0; r < rows; r++) {
			int i = suggestScroll + r;
			int ry = top + 1 + r * SUGGEST_ROW_H;
			if (i == selected || i == hovered) graphics.fill(x + 1, ry, x + w - 1, ry + SUGGEST_ROW_H, 0xFF3A2650);
			boolean last = r == rows - 1 && !count.isEmpty();
			int room = w - 8 - (last ? font.width(count) + 6 : 0);
			graphics.text(font, font.plainSubstrByWidth(suggestions.get(i), room), x + 4, ry + 2,
					i == selected ? 0xFFFFFFFF : 0xFFD0D0D0, false);
			if (last) graphics.text(font, count, x + w - 4 - font.width(count), ry + 2, 0xFF8A7AA0, false);
		}
	}
}
