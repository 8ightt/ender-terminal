package dev.enderterminal.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.http.HttpRequest;
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
