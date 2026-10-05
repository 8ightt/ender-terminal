package dev.enderterminal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Stored at config/enderterminal.json. Missing fields fall back to these defaults. */
public final class EnderTerminalConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public enum Provider {
		NONE, ANTHROPIC_API, OPENAI_COMPATIBLE
	}

	public Provider provider = Provider.NONE;

	public String anthropicApiKey = "";
	public String anthropicModel = "claude-sonnet-5-5";

	public String openaiBaseUrl = "https://api.openai.com/v1";
	public String openaiApiKey = "";
	public String openaiModel = "gpt-4o-mini";

	/** Attach a snapshot of position, inventory, surroundings etc. to each message. */
	public boolean shareGameInfo = false;

	/** Send only the last {@link #HISTORY_LIMIT} messages with each request, to keep API costs down. */
	public boolean limitHistory = true;
	public static final int HISTORY_LIMIT = 30;

	public String systemPrompt = "You are chatting through a small text terminal inside Minecraft Java Edition. "
			+ "Reply in plain text without markdown tables or headings. Keep answers short unless asked for detail.";

	private transient Path file;

	public static EnderTerminalConfig load(Path file) {
		EnderTerminalConfig cfg = null;
		try {
			if (Files.exists(file)) cfg = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), EnderTerminalConfig.class);
		} catch (Exception e) {
			EnderTerminalClient.LOGGER.warn("Could not read {}, using defaults", file, e);
		}
		if (cfg == null) cfg = new EnderTerminalConfig();
		if (cfg.provider == null) cfg.provider = Provider.NONE;
		cfg.file = file;
		cfg.save();
		return cfg;
	}

	public EnderTerminalConfig copy() {
		EnderTerminalConfig c = GSON.fromJson(GSON.toJson(this), EnderTerminalConfig.class);
		c.file = file;
		return c;
	}

	/** Default settings that keep this config's provider and API keys, saved to the same file. */
	public EnderTerminalConfig defaults() {
		EnderTerminalConfig d = new EnderTerminalConfig();
		d.provider = provider;
		d.anthropicApiKey = anthropicApiKey;
		d.openaiApiKey = openaiApiKey;
		d.file = file;
		return d;
	}

	public void save() {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (Exception e) {
			EnderTerminalClient.LOGGER.warn("Could not write {}", file, e);
		}
	}
}
