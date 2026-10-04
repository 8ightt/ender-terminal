package dev.enderterminal;

import dev.enderterminal.provider.AnthropicApiProvider;
import dev.enderterminal.provider.ChatProvider;
import dev.enderterminal.provider.OpenAiCompatibleProvider;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
		boolean restored = load();
		printHelp();
		if (restored) add("Restored your last conversation. Type /new to start fresh.", SYSTEM);
	}

	private Path conversationFile() {
		return baseDir.resolve("conversation.json");
	}

	/**
	 * Saves what you and the AI said, plus what the provider remembers, so the conversation survives a restart.
	 * Notices and errors are left out; they only matter in the moment.
	 */
	public void save() {
		JsonObject root = new JsonObject();
		root.addProperty("provider", config.provider.name());
		JsonArray saved = new JsonArray();
		synchronized (this) {
			for (Entry e : entries) {
				if ((e.color() != USER && e.color() != ASSISTANT) || e.text().isEmpty()) continue;
				JsonObject o = new JsonObject();
				o.addProperty("role", e.color() == USER ? "user" : "assistant");
				o.addProperty("text", e.text().toString());
				saved.add(o);
			}
		}
		root.add("entries", saved);
		JsonObject state = provider == null ? null : provider.saveState();
		if (state != null) root.add("state", state);
		try {
			Files.createDirectories(baseDir);
			Files.writeString(conversationFile(), root.toString(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			EnderTerminalClient.LOGGER.warn("Could not save conversation", e);
		}
	}

	private boolean load() {
		Path file = conversationFile();
		if (!Files.exists(file)) return false;
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			// Another provider can't continue this conversation; start fresh rather than mix them.
			if (!config.provider.name().equals(root.has("provider") ? root.get("provider").getAsString() : "")) return false;
			JsonArray saved = root.getAsJsonArray("entries");
			if (saved != null) {
				for (JsonElement el : saved) {
					JsonObject o = el.getAsJsonObject();
					add(o.get("text").getAsString(), "user".equals(o.get("role").getAsString()) ? USER : ASSISTANT);
				}
			}
			// Restored even when the screen was cleared with /clear: the AI still remembers.
			if (provider != null && root.has("state")) provider.loadState(root.getAsJsonObject("state"));
			return saved != null && !saved.isEmpty();
		} catch (Exception e) {
			EnderTerminalClient.LOGGER.warn("Could not load saved conversation", e);
			return false;
		}
	}

	/** Text of the last reply, or null if there is none yet. */
	public synchronized @Nullable String lastReply() {
		for (int i = entries.size() - 1; i >= 0; i--) {
			Entry e = entries.get(i);
			if (e.color() == ASSISTANT && !e.text().isEmpty()) return e.text().toString();
		}
		return null;
	}

	/** Set when /copy wants the screen to put text on the clipboard. */
	public @Nullable String copyRequested;

	public EnderTerminalConfig config() {
		return config;
	}

	public Path baseDir() {
		return baseDir;
	}

	/**
	 * Switches to the provider described by {@code config}. The conversation carries over when the provider type
	 * stays the same (e.g. a new model or toggled setting); a different type starts a new conversation.
	 */
	public void applyConfig(EnderTerminalConfig config) {
		ChatProvider old = provider;
		boolean sameKind = old != null && this.config != null && this.config.provider == config.provider;
		JsonObject carried = sameKind ? old.saveState() : null;
		if (old != null) old.cancel();
		this.config = config;
		this.provider = createProvider(config, baseDir);
		if (provider != null) provider.refreshAccount();
		if (carried != null) provider.loadState(carried);
		else this.modsSent = false;
		if (entries.isEmpty()) return;
		if (provider == null) add("No AI provider selected.", SYSTEM);
		else if (sameKind) add("Settings saved. Now using " + provider.name() + ".", SYSTEM);
		else add("Now using " + provider.name() + ". New conversation.", SYSTEM);
		save();
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
		add("Commands: /settings, /new (fresh conversation), /copy (last reply), /context (game info), /clear, /cancel, /help", SYSTEM);
		add("Tip: click any message to copy it.", SYSTEM);
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
				save();
				return;
			}
			case "/new" -> {
				synchronized (this) {
					entries.clear();
				}
				if (provider != null) provider.reset();
				modsSent = false;
				save();
				add("Started a new conversation.", SYSTEM);
				return;
			}
			case "/copy" -> {
				String last = lastReply();
				if (last == null) {
					add("No reply to copy yet.", SYSTEM);
				} else {
					copyRequested = last;
				}
				return;
			}
			case "/cancel" -> {
				cancel();
				return;
			}
			case "/context" -> {
				String ctx = GameContext.snapshot(true);
				if (ctx == null) {
					add("Not in a world.", SYSTEM);
				} else {
					add(config.shareGameInfo ? "Sent with each message:" : "Game info sharing is off. Turn it on in /settings to send this with each message:", SYSTEM);
					add(ctx, SYSTEM);
				}
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
				save();
			}
		});
	}
}
