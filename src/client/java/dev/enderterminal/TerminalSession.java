package dev.enderterminal;

import dev.enderterminal.provider.AnthropicApiProvider;
import dev.enderterminal.provider.ChatProvider;
import dev.enderterminal.provider.OpenAiCompatibleProvider;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Scrollback, input history and the active provider. Outlives the screen so closing it loses nothing. */
public final class TerminalSession {
	public static final int USER = 0xFF7FD7FF;
	public static final int ASSISTANT = 0xFFE6E6E6;
	public static final int SYSTEM = 0xFF8A8A8A;
	public static final int ERROR = 0xFFFF6B6B;

	public record Entry(StringBuilder text, int color) {
	}

	private final Path baseDir;
	private final List<Entry> entries = new ArrayList<>();
	private final List<String> history = new ArrayList<>();
	private EnderTerminalConfig config;
	private @Nullable ChatProvider provider;
	/** The mod list goes out once per conversation; it doesn't change while playing. */
	private boolean modsSent;
	/** Set by /settings so the terminal screen can open the settings screen. */
	public boolean openSettingsRequested;

	public TerminalSession(EnderTerminalConfig config, Path baseDir) {
		this.baseDir = baseDir;
		applyConfig(config);
		printHelp();
	}

	public EnderTerminalConfig config() {
		return config;
	}

	public Path baseDir() {
		return baseDir;
	}

	/** Switches to the provider described by {@code config}. Starts a new conversation. */
	public void applyConfig(EnderTerminalConfig config) {
		if (provider != null) provider.cancel();
		this.config = config;
		this.provider = createProvider(config, baseDir);
		this.modsSent = false;
		if (provider != null) provider.refreshAccount();
		if (!entries.isEmpty()) add(provider == null ? "No AI provider selected." : "Now using " + provider.name() + ". New conversation.", SYSTEM);
	}

	public static @Nullable ChatProvider createProvider(EnderTerminalConfig c, Path baseDir) {
		String system = c.shareGameInfo ? (c.systemPrompt + "\n\n" + GameContext.SYSTEM_NOTE).strip() : c.systemPrompt;
		return switch (c.provider) {
			case NONE -> null;
			case ANTHROPIC_API -> new AnthropicApiProvider(c.anthropicApiKey, c.anthropicModel, system);
			case OPENAI_COMPATIBLE -> new OpenAiCompatibleProvider(c.openaiBaseUrl, c.openaiApiKey, c.openaiModel, system);
		};
	}

	/** Account or key for the header, or null. */
	public @Nullable String accountInfo() {
		return provider == null ? null : provider.accountInfo();
	}

	public String providerName() {
		return provider == null ? "no provider" : provider.name();
	}

	public synchronized List<Entry> snapshot() {
		List<Entry> copy = new ArrayList<>(entries.size());
		for (Entry e : entries) copy.add(new Entry(new StringBuilder(e.text()), e.color()));
		return copy;
	}

	public List<String> history() {
		return history;
	}

	public boolean isBusy() {
		return provider != null && provider.isBusy();
	}

	public void cancel() {
		if (provider != null) provider.cancel();
	}

	private synchronized Entry add(String text, int color) {
		Entry entry = new Entry(new StringBuilder(text), color);
		entries.add(entry);
		if (entries.size() > 500) entries.removeFirst();
		return entry;
	}

	/** Appends to a specific entry; other lines (errors, notices) may have been added after it. */
	private synchronized void append(Entry entry, String text) {
		entry.text().append(text);
	}

	private void printHelp() {
		if (provider == null) {
			add("No AI provider set up yet. Click Settings (top right) or type /settings.", ERROR);
		} else {
			add("Using " + provider.name() + ".", SYSTEM);
		}
		add("Commands: /settings, /new (fresh conversation), /context (game info sent), /clear, /cancel, /help", SYSTEM);
	}

	public void submit(String raw) {
		String input = raw.strip();
		if (input.isEmpty()) return;
		if (history.isEmpty() || !history.getLast().equals(input)) history.add(input);

		switch (input) {
			case "/settings" -> {
				openSettingsRequested = true;
				return;
			}
			case "/clear" -> {
				synchronized (this) {
					entries.clear();
				}
				return;
			}
			case "/new" -> {
				synchronized (this) {
					entries.clear();
				}
				if (provider != null) provider.reset();
				modsSent = false;
				add("Started a new conversation.", SYSTEM);
				return;
			}
			case "/cancel" -> {
				cancel();
				return;
			}
			case "/context" -> {
				String ctx = GameContext.snapshot(true);
				add(!config.shareGameInfo ? "Game info sharing is off (see /settings)." : ctx == null ? "Not in a world." : ctx, SYSTEM);
				return;
			}
			case "/help" -> {
				printHelp();
				return;
			}
			default -> {
			}
		}

		if (provider == null) {
			add("No AI provider set up yet. Click Settings (top right) or type /settings.", ERROR);
			return;
		}
		if (provider.isBusy()) {
			add("Wait for the current reply, or type /cancel.", ERROR);
			return;
		}
		String prompt = input;
		if (config.shareGameInfo) {
			String ctx = GameContext.snapshot(!modsSent);
			if (ctx != null) {
				prompt = ctx + "\n\n" + input;
				modsSent = true;
			}
		}
		add("> " + input, USER);
		Entry reply = add("", ASSISTANT);
		provider.send(prompt, new ChatProvider.Listener() {
			@Override
			public void onText(String chunk) {
				append(reply, chunk);
			}

			@Override
			public void onDone(String error) {
				synchronized (TerminalSession.this) {
					if (reply.text().isEmpty()) entries.remove(reply);
				}
				if (error != null) add(error, ERROR);
			}
		});
	}
}
