package dev.enderterminal;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class TerminalScreen extends Screen {
	private static final int PAD = 8;
	/** Header row sits below the top edge so HUD overlays from other mods (minimap coordinates etc.) don't cover it. */
	private static final int TOP = 18;
	private static final int INPUT_HEIGHT = 20;
	private static final Identifier FRAME = Identifier.fromNamespaceAndPath(EnderTerminalClient.MOD_ID, "terminal_frame");

	private final @Nullable Screen parent;
	private final TerminalSession session = EnderTerminalClient.session();
	private EditBox input;
	private int scroll; // lines scrolled up from the bottom
	private int historyIndex = -1;
	private String draft = "";
	// Layout of the last frame, so a click can be matched to the message under the mouse.
	private final List<Integer> lineEntry = new ArrayList<>();
	private final List<String> entryText = new ArrayList<>();
	private int firstLine;
	private int lastLine;
	private int firstLineY;
	private long copiedUntil;

	public TerminalScreen(@Nullable Screen parent) {
		super(Component.translatable("enderterminal.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		String keep = input == null ? "" : input.getValue();
		input = new EditBox(font, PAD, height - PAD - INPUT_HEIGHT, width - PAD * 2, INPUT_HEIGHT, Component.literal("Message"));
		input.setMaxLength(8000);
		input.setValue(keep);
		addRenderableWidget(input);
		addRenderableWidget(Button.builder(Component.literal("Settings"),
				b -> minecraft.gui.setScreen(new SettingsScreen(this))).bounds(width - PAD - 60, TOP - 4, 60, 16).build());
		setInitialFocus(input);
	}

	@Override
	public void tick() {
		String copy = session.copyRequested;
		if (copy != null) {
			session.copyRequested = null;
			copy(copy);
		}
		if (session.openSettingsRequested) {
			session.openSettingsRequested = false;
			minecraft.gui.setScreen(new SettingsScreen(this));
		}
	}

	/** Pauses singleplayer like the game menu; replies keep streaming in the background. Multiplayer can't pause. */
	@Override
	public boolean isPauseScreen() {
		return true;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			session.submit(input.getValue());
			input.setValue("");
			historyIndex = -1;
			scroll = 0;
			return true;
		}
		if (event.hasControlDown() && key == InputConstants.KEY_C && session.isBusy() && input.getValue().isEmpty()) {
			session.cancel();
			return true;
		}
		if (key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN) {
			browseHistory(key == InputConstants.KEY_UP);
			return true;
		}
		if (key == InputConstants.KEY_PAGEUP) {
			scroll += outputLines() / 2;
			return true;
		}
		if (key == InputConstants.KEY_PAGEDOWN) {
			scroll = Math.max(0, scroll - outputLines() / 2);
			return true;
		}
		return super.keyPressed(event);
	}

	private void browseHistory(boolean older) {
		List<String> history = session.history();
		if (history.isEmpty()) return;
		if (historyIndex == -1) {
			if (!older) return;
			draft = input.getValue();
			historyIndex = history.size() - 1;
		} else if (older) {
			historyIndex = Math.max(0, historyIndex - 1);
		} else if (++historyIndex >= history.size()) {
			historyIndex = -1;
			input.setValue(draft);
			return;
		}
		input.setValue(history.get(historyIndex));
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		scroll = Math.max(0, scroll + (int) Math.signum(scrollY) * 3);
		return true;
	}

	private void copy(String text) {
		minecraft.keyboardHandler.setClipboard(text);
		copiedUntil = System.currentTimeMillis() + 1500;
	}

	/** Index of the message under the mouse, or -1. */
	private int entryAt(double mouseX, double mouseY) {
		if (mouseX < PAD || mouseX > width - PAD || mouseY < outputTop() || mouseY >= outputBottom()) return -1;
		int line = firstLine + (int) Math.floor((mouseY - firstLineY) / font.lineHeight);
		if (line < firstLine || line >= lastLine || line >= lineEntry.size()) return -1;
		int entry = lineEntry.get(line);
		return entryText.get(entry) == null ? -1 : entry;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			int entry = entryAt(event.x(), event.y());
			if (entry >= 0) {
				copy(entryText.get(entry));
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private int outputTop() {
		return TOP + font.lineHeight + 8;
	}

	private int outputBottom() {
		return height - PAD - INPUT_HEIGHT - 6;
	}

	private int outputLines() {
		return Math.max(1, (outputBottom() - outputTop()) / font.lineHeight);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		graphics.fill(0, 0, width, height, 0xC0000000);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FRAME, 2, TOP - 8, width - 4, height - TOP + 6);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		String status = System.currentTimeMillis() < copiedUntil ? "copied to clipboard"
				: session.isBusy() ? "thinking" + ".".repeat((int) (System.currentTimeMillis() / 400 % 4)) + "  (Ctrl+C to stop)" : "ready";
		int hx = PAD;
		graphics.text(font, "Ender Terminal", hx, TOP, 0xFFC77DFF);
		hx += font.width("Ender Terminal");
		String provider = "  |  " + session.providerName();
		graphics.text(font, provider, hx, TOP, 0xFFB0B0B0);
		hx += font.width(provider);
		String account = session.accountInfo();
		if (account != null) {
			graphics.text(font, "  |  " + account, hx, TOP, account.equals("not logged in") ? TerminalSession.ERROR : 0xFF6BFF8A);
		}
		graphics.text(font, status, width - PAD - 66 - font.width(status), TOP, TerminalSession.SYSTEM);
		graphics.fill(PAD, outputTop() - 3, width - PAD, outputTop() - 2, 0xFF3A2650);

		List<FormattedCharSequence> lines = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();
		lineEntry.clear();
		entryText.clear();
		int wrapWidth = width - PAD * 2;
		for (TerminalSession.Entry entry : session.snapshot()) {
			String text = entry.text().toString();
			if (text.isEmpty() && entry.color() != TerminalSession.ASSISTANT) continue;
			int index = entryText.size();
			if (text.isEmpty()) {
				// The reply that is still on its way.
				entryText.add(null);
				lines.add(Component.literal("thinking" + ".".repeat((int) (System.currentTimeMillis() / 400 % 4))).getVisualOrderText());
				colors.add(TerminalSession.SYSTEM);
				lineEntry.add(index);
				continue;
			}
			entryText.add(entry.color() == TerminalSession.USER && text.startsWith("> ") ? text.substring(2) : text);
			for (String paragraph : text.split("\n", -1)) {
				List<FormattedCharSequence> wrapped = font.split(Component.literal(paragraph), wrapWidth);
				if (wrapped.isEmpty()) wrapped = List.of(FormattedCharSequence.EMPTY);
				for (FormattedCharSequence line : wrapped) {
					lines.add(line);
					colors.add(entry.color());
					lineEntry.add(index);
				}
			}
		}

		int visible = outputLines();
		scroll = Math.min(scroll, Math.max(0, lines.size() - visible));
		int end = lines.size() - scroll;
		int start = Math.max(0, end - visible);
		int y = outputBottom() - (end - start) * font.lineHeight;
		firstLine = start;
		lastLine = end;
		firstLineY = y;
		int hovered = entryAt(mouseX, mouseY);
		graphics.enableScissor(PAD, outputTop(), width - PAD, outputBottom());
		for (int i = start; i < end; i++) {
			if (lineEntry.get(i) == hovered) graphics.fill(PAD - 2, y - 1, width - PAD + 2, y + font.lineHeight - 1, 0x30C77DFF);
			graphics.text(font, lines.get(i), PAD, y, colors.get(i), false);
			y += font.lineHeight;
		}
		graphics.disableScissor();
		if (scroll > 0) {
			String more = "-- " + scroll + " more lines below (scroll / PgDn) --";
			graphics.text(font, more, width - PAD - font.width(more), outputBottom() - font.lineHeight, 0xFFFFD25A);
		}

		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
