package dev.enderterminal.provider;

import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;

/** Anthropic Messages API with an API key from console.anthropic.com. */
public final class AnthropicApiProvider extends HttpChatProvider {
	private final String apiKey;
	private final String model;

	public AnthropicApiProvider(String apiKey, String model, String systemPrompt) {
		super(systemPrompt);
		this.apiKey = apiKey.strip();
		this.model = model.strip();
	}

	@Override
	public String name() {
		return "Anthropic API (" + model + ")";
	}

	/** Model ids available to this API key, newest first. Blocks; call off the render thread. Empty on any failure. */
	public static List<String> listModels(String apiKey) {
		if (apiKey.isBlank()) return List.of();
		return fetchModelIds(HttpRequest.newBuilder(URI.create("https://api.anthropic.com/v1/models?limit=1000"))
				.timeout(Duration.ofSeconds(5))
				.header("x-api-key", apiKey.strip())
				.header("anthropic-version", "2023-06-01")
				.GET().build());
	}

	@Override
	public String accountInfo() {
		return apiKey.length() < 8 ? null : "key ..." + apiKey.substring(apiKey.length() - 4);
	}

	@Override
	protected HttpRequest buildRequest(List<Message> messages) {
		JsonObject body = new JsonObject();
		body.addProperty("model", model);
		body.addProperty("max_tokens", 4096);
		body.addProperty("stream", true);
		if (!systemPrompt.isBlank()) body.addProperty("system", systemPrompt);
		body.add("messages", toJson(messages));
		return post("https://api.anthropic.com/v1/messages", body)
				.header("x-api-key", apiKey)
				.header("anthropic-version", "2023-06-01")
				.build();
	}

	@Override
	protected String extractText(JsonObject data) {
		if (!"content_block_delta".equals(data.has("type") ? data.get("type").getAsString() : "")) return null;
		JsonObject delta = data.getAsJsonObject("delta");
		if (delta == null || !delta.has("text")) return null;
		return delta.get("text").getAsString();
	}
}
