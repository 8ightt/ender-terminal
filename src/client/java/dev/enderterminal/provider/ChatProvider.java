package dev.enderterminal.provider;

public interface ChatProvider {
	interface Listener {
		void onText(String chunk);

		/** Called once when the reply ends. {@code error} is null on success. */
		void onDone(String error);
	}

	/** Short label for the terminal header, e.g. "Anthropic API (claude-sonnet-5-5)". */
	String name();

	/** Sends a message in the ongoing conversation. Must not block; replies arrive on a background thread. */
	void send(String prompt, Listener listener);

	boolean isBusy();

	void cancel();

	/** Forgets the conversation so the next message starts fresh. */
	void reset();

	/** Account or key shown in the terminal header, e.g. a masked API key. Null if unknown. */
	default String accountInfo() {
		return null;
	}

	/** Re-reads {@link #accountInfo()} in the background where that needs a lookup. */
	default void refreshAccount() {
	}
}
