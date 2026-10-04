# Ender Terminal


A terminal inside Minecraft for chatting with AI. Press <kbd>`</kbd> or click **Ender Terminal** in the pause menu and ask anything: crafting, redstone, mod recipes, or what to do next.

The terminal knows where you are, what you're holding, what you're looking at and which mods you have installed, so answers fit your game.

## Features

- **Terminal screen** with streaming replies, scrollback, input history and a conversation that survives closing the screen.
- **Bring your own AI**
  - **Anthropic API key**: pay-per-use key from [console.anthropic.com](https://console.anthropic.com).
  - **OpenAI-compatible**: OpenAI, OpenRouter, Groq, or free local models through [Ollama](https://ollama.com) or [LM Studio](https://lmstudio.ai). Presets included.
- **Game awareness**: each message can include a snapshot of your position, dimension, biome, time and weather, health, gear, inventory, the block or mob in view, nearby mobs, and your mod list. Type `/context` to see exactly what is sent, or turn it off in settings.
- **Client-side only**: works in singleplayer and on servers (the server doesn't need it) and never changes your worlds.
- **Chat only**: the AI cannot run commands, read files or change anything on your computer.


## Requirements

- Minecraft **26.3** with [Fabric Loader](https://fabricmc.net/use/) 0.19.5 or newer
- [Fabric API](https://modrinth.com/mod/fabric-api)
- Java 25 (bundled with the official launcher and the Modrinth app)

## Getting started

1. Put `ender-terminal-<version>.jar` and Fabric API in your `mods` folder.
2. Join a world and press <kbd>`</kbd>, or open the pause menu and click **Ender Terminal**.
3. Click **Settings**, choose a provider, then log in or paste your API key.
4. Click **Save** and start typing.

## Controls

| Key / command | Action |
| --- | --- |
| <kbd>Enter</kbd> | Send message |
| <kbd>↑</kbd> / <kbd>↓</kbd> | Previous / next message you typed |
| Mouse wheel, <kbd>PgUp</kbd> / <kbd>PgDn</kbd> | Scroll the conversation |
| <kbd>Ctrl</kbd>+<kbd>C</kbd> or `/cancel` | Stop the current reply |
| `/new` | Start a fresh conversation |
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
| `shareGameInfo` | Attach the game snapshot to messages |
| `systemPrompt` | Personality and rules for the AI |

API keys are stored in plain text in this file. Don't share it.

## Building from source

```sh
./gradlew build
```

The mod jar is written to `build/libs/`. Run `./gradlew runClient` to start a development client with the mod loaded.

## License

[MIT](LICENSE)
