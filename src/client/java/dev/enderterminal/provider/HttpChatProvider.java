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
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Base for providers that stream server-sent events over HTTP and keep the conversation in memory. */
abstract class HttpChatProvider implements ChatProvider {
	protected record Message(String role, String content) {
	}

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

	protected final String systemPrompt;
	private final List<Message> history = new ArrayList<>();
	private volatile Thread worker;
	private volatile Stream<String> body;
	private volatile boolean cancelled;

	protected HttpChatProvider(String systemPrompt) {
		this.systemPrompt = systemPrompt;
	}

	protected abstract HttpRequest buildRequest(List<Message> messages);

	/** Returns the text in one SSE data payload, or null if it carries none. */
	protected abstract String extractText(JsonObject data);

	/** Returns an error message carried by an SSE data payload, or null. */
	protected String extractError(JsonObject data) {
		if (data.has("error")) return errorMessage(data.get("error"));
		return null;
	}

	@Override
	public boolean isBusy() {
		return worker != null;
	}

	@Override
	public synchronized void reset() {
		history.clear();
	}

	@Override
	public void cancel() {
		cancelled = true;
		Stream<String> s = body;
		if (s != null) s.close();
		Thread t = worker;
		if (t != null) t.interrupt();
	}

	@Override
	public void send(String prompt, Listener listener) {
		if (isBusy()) {
			listener.onDone("Still busy with the previous message.");
			return;
		}
		cancelled = false;
		Thread t = new Thread(() -> run(prompt, listener), "Ender Terminal");
		t.setDaemon(true);
		worker = t;
		t.start();
	}

	private void run(String prompt, Listener listener) {
		List<Message> messages;
		synchronized (this) {
			history.add(new Message("user", prompt));
			messages = List.copyOf(history);
		}
		StringBuilder reply = new StringBuilder();
		String error = null;
		try {
			HttpResponse<Stream<String>> res = HTTP.send(buildRequest(messages), HttpResponse.BodyHandlers.ofLines());
			body = res.body();
			if (res.statusCode() / 100 != 2) {
				String text = res.body().collect(Collectors.joining("\n"));
				error = "HTTP " + res.statusCode() + ": " + describeError(text);
				if (res.statusCode() == 404) error += " Check the model name in /settings.";
			} else {
				Iterator<String> it = res.body().iterator();
				while (it.hasNext() && !cancelled) {
					String line = it.next();
					if (!line.startsWith("data:")) continue;
					String payload = line.substring(5).trim();
					if (payload.isEmpty() || payload.equals("[DONE]")) continue;
					JsonObject data;
					try {
						data = JsonParser.parseString(payload).getAsJsonObject();
					} catch (Exception e) {
						continue;
					}
					String err = extractError(data);
					if (err != null) {
						error = err;
						break;
					}
					String text = extractText(data);
					if (text != null && !text.isEmpty()) {
						reply.append(text);
						listener.onText(text);
					}
				}
			}
		} catch (Exception e) {
			if (!cancelled) {
				error = e.getMessage() == null ? e.toString() : e.getMessage();
				if (e instanceof java.net.ConnectException) error = "Could not connect. Check the URL in /settings.";
			}
		} finally {
			body = null;
			worker = null;
		}
		if (cancelled && error == null) error = "Cancelled.";
		synchronized (this) {
			if (error == null && !reply.isEmpty()) {
				history.add(new Message("assistant", reply.toString()));
			} else if (!history.isEmpty()) {
				// Drop the unanswered message so the conversation stays valid (user/assistant alternating).
				history.removeLast();
			}
		}
		listener.onDone(error);
	}

	protected static JsonArray toJson(List<Message> messages) {
		JsonArray arr = new JsonArray();
		for (Message m : messages) {
			JsonObject o = new JsonObject();
			o.addProperty("role", m.role());
			o.addProperty("content", m.content());
			arr.add(o);
		}
		return arr;
	}

	protected static HttpRequest.Builder post(String url, JsonObject body) {
		return HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofMinutes(5))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()));
	}

	private static String describeError(String body) {
		try {
			JsonObject o = JsonParser.parseString(body).getAsJsonObject();
			if (o.has("error")) return errorMessage(o.get("error"));
		} catch (Exception ignored) {
		}
		body = body.strip();
		return body.length() > 300 ? body.substring(0, 300) + "..." : body;
	}

	private static String errorMessage(JsonElement err) {
		if (err.isJsonObject() && err.getAsJsonObject().has("message")) {
			return err.getAsJsonObject().get("message").getAsString();
		}
		return err.toString();
	}

	protected static String trimSlash(String url) {
		url = url.strip();
		while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
		return url;
	}
}
