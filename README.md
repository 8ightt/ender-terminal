# Ender Terminal

![Ender Terminal: ask AI anything, right inside Minecraft](docs/images/banner.png)

A terminal inside Minecraft for chatting with AI. Press <kbd>`</kbd> or click **Ender Terminal** in the pause menu and ask anything: crafting, redstone, mod recipes, or what to do next.

The terminal knows where you are, what you're holding, what you're looking at and which mods you have installed, so answers fit your game.

## Features

- **Terminal screen** with streaming replies, scrollback, input history, click-to-copy, and a conversation that is remembered between game sessions. Singleplayer pauses while it is open.
- **Bring your own AI**
  - **Anthropic API key**: pay-per-use key from [console.anthropic.com](https://console.anthropic.com).
  - **OpenAI-compatible**: OpenAI, OpenRouter, Groq, or free local models through [Ollama](https://ollama.com) or [LM Studio](https://lmstudio.ai). Presets included.
  - The Model field searches the provider's model list as you type.
- **Game awareness** (opt-in): turn on **Share game info** in settings and each message includes a snapshot of your position, dimension, biome, time and weather, health, gear, inventory, the block or mob in view, nearby mobs, and your mod list. Type `/context` to see exactly what would be sent.
- **Client-side only**: works in singleplayer and on servers (the server doesn't need it) and never changes your worlds.
- **Chat only**: the AI cannot run commands, read files or change anything on your computer.

| | | |
| --- | --- | --- |
| ![Knows your game](docs/images/feature-knows-your-game.png) | ![Use your own AI](docs/images/feature-use-your-own-ai.png) | ![Safe](docs/images/feature-safe.png) |

## Privacy

- **Nothing is sent anywhere until you choose a provider** in settings. A fresh install has no provider.
- **What is sent:** the messages you type, and the game snapshot only if **Share game info** is on (it is off by default). It goes **only to the provider you chose** (Anthropic, OpenAI, OpenRouter, Groq or your own endpoint), under that provider's privacy policy.
- **What is not sent:** no telemetry, no analytics, nothing to the mod author. The mod has no servers or accounts of its own.
- **Local models** through Ollama or LM Studio keep everything on your own computer.
- Your API key is stored only in `config/enderterminal.json` on your computer.
- Your conversation is saved only on your computer, in `enderterminal/conversation.json`, so it continues after a restart. `/new` clears it.

## Requirements

- Minecraft **26.3** with [Fabric Loader](https://fabricmc.net/use/) 0.19.5 or newer
- [Fabric API](https://modrinth.com/mod/fabric-api)
- Java 25 (bundled with the official launcher and the Modrinth app)

## Getting started

1. Put `ender-terminal-<version>.jar` and Fabric API in your `mods` folder.
2. Join a world and press <kbd>`</kbd>, or open the pause menu and click **Ender Terminal**.
3. Click **Settings**, choose a provider and paste your API key, or pick the Ollama preset.
4. Click **Save** and start typing.

## Controls

| Key / command | Action |
| --- | --- |
| <kbd>Enter</kbd> | Send message |
| <kbd>↑</kbd> / <kbd>↓</kbd> | Previous / next message you typed |
| Mouse wheel, <kbd>PgUp</kbd> / <kbd>PgDn</kbd> | Scroll the conversation |
| <kbd>Ctrl</kbd>+<kbd>C</kbd> or `/cancel` | Stop the current reply |
| `/new` | Start a fresh conversation (also clears the saved one) |
| `/copy` | Copy the last reply |
| Click a message | Copy that message |
| `/context` | Show the game info that gets sent |
| `/settings` | Open settings |
| `/clear` | Clear the screen |
| <kbd>Esc</kbd> | Close the terminal |

The open key can be changed in **Options → Controls → Key Binds → Ender Terminal**.

## Configuration

Settings are saved in `config/enderterminal.json`. Everything can be changed in-game, but the file can also be edited while the game is closed.

| Field | Meaning |
| --- | --- |
| `provider` | `NONE`, `ANTHROPIC_API` or `OPENAI_COMPATIBLE` |
| `anthropicApiKey`, `anthropicModel` | Anthropic API settings |
| `openaiBaseUrl`, `openaiApiKey`, `openaiModel` | OpenAI-compatible endpoint settings |
| `shareGameInfo` | Attach the game snapshot to messages (default `false`) |
| `limitHistory` | Send only the last 30 messages with each request, to keep API costs down (default `true`) |
| `systemPrompt` | Personality and rules for the AI |

API keys are stored in plain text in this file. Don't share it.

## Building from source

Requires **Java 25** (JDK). Gradle downloads everything else on the first build.

```sh
# macOS / Linux
./gradlew build

# Windows
gradlew.bat build
```

- The mod jar is written to `build/libs/`.
- Run `gradlew runClient` (or `./gradlew runClient`) to start a development client with the mod loaded.

## License

Ender Terminal is licensed under the [GNU LGPL-3.0](LICENSE).

- **Modpacks and addon mods** can use Ender Terminal under any license.
- **Modified versions** of Ender Terminal itself must be shared under the LGPL-3.0 as well.
- The LGPL builds on the [GNU GPL-3.0](COPYING), which is included alongside it.
