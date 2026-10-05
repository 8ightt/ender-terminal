package dev.enderterminal.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Any /chat/completions endpoint: OpenAI, OpenRouter, Groq, Ollama, LM Studio, vLLM, ... */
public final class OpenAiCompatibleProvider extends HttpChatProvider {
	private final String baseUrl;
	private final String apiKey;
	private final String model;

	public OpenAiCompatibleProvider(String baseUrl, String apiKey, String model, String systemPrompt) {
		super(systemPrompt);
		this.baseUrl = trimSlash(baseUrl);
		this.apiKey = apiKey.strip();
		this.model = model.strip();
	}

	@Override
	public String name() {
		return model + " @ " + baseUrl.replaceFirst("^https?://", "");
	}

	/**
	 * Lists model ids from {@code GET /models}, which OpenAI, Ollama and LM Studio all support.
	 * Blocks for up to a few seconds; call off the render thread. Returns an empty list on any failure.
	 */
	public static List<String> listModels(String baseUrl, String apiKey) {
		try (HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
			HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(trimSlash(baseUrl) + "/models")).timeout(Duration.ofSeconds(5)).GET();
			if (!apiKey.isBlank()) req.header("Authorization", "Bearer " + apiKey.strip());
			HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
			if (res.statusCode() / 100 != 2) return List.of();
			List<String> ids = new ArrayList<>();
			for (JsonElement el : JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonArray("data")) {
				ids.add(el.getAsJsonObject().get("id").getAsString());
			}
			return ids;
		} catch (Exception e) {
			return List.of();
		}
	}

	/** Names the local server when the URL points at its default port, since the usual cause is that it isn't running. */
	@Override
	protected String connectError() {
		String local = localServerName(baseUrl);
		if (local == null) return super.connectError();
		return "Could not reach " + local + " at " + baseUrl.replaceFirst("^https?://", "") + ". Is it running? "
				+ (local.equals("Ollama") ? "Start the Ollama app or run 'ollama serve'." : "Start the server in LM Studio's Developer tab.");
	}

	/** "Ollama" or "LM Studio" for a localhost URL on their default port, else null. */
	public static String localServerName(String baseUrl) {
		String host = baseUrl.strip().replaceFirst("^https?://", "");
		if (!host.startsWith("localhost") && !host.startsWith("127.0.0.1")) return null;
		if (host.matches("[^/]*:11434(/.*)?")) return "Ollama";
		if (host.matches("[^/]*:1234(/.*)?")) return "LM Studio";
		return null;
	}

	@Override
	public String accountInfo() {
		return apiKey.length() < 8 ? null : "key ..." + apiKey.substring(apiKey.length() - 4);
	}

	@Override
	protected HttpRequest buildRequest(List<Message> messages) {
		JsonArray msgs = new JsonArray();
		if (!systemPrompt.isBlank()) {
			JsonObject sys = new JsonObject();
			sys.addProperty("role", "system");
			sys.addProperty("content", systemPrompt);
			msgs.add(sys);
		}
		msgs.addAll(toJson(messages));
		JsonObject body = new JsonObject();
		body.addProperty("model", model);
		body.addProperty("stream", true);
		body.add("messages", msgs);
		HttpRequest.Builder req = post(baseUrl + "/chat/completions", body);
		if (!apiKey.isEmpty()) req.header("Authorization", "Bearer " + apiKey);
		return req.build();
	}

	@Override
	protected String extractText(JsonObject data) {
		if (!data.has("choices") || data.getAsJsonArray("choices").isEmpty()) return null;
		JsonObject choice = data.getAsJsonArray("choices").get(0).getAsJsonObject();
		JsonObject delta = choice.getAsJsonObject("delta");
		if (delta == null || !delta.has("content") || delta.get("content").isJsonNull()) return null;
		return delta.get("content").getAsString();
	}
}
