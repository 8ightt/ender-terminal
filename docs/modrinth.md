![Ender Terminal: ask AI anything, right inside Minecraft](https://raw.githubusercontent.com/8ightt/ender-terminal/main/docs/images/banner.png)

**Ender Terminal** adds a terminal inside Minecraft for chatting with AI. Press <kbd>`</kbd> or click **Ender Terminal** in the pause menu and ask anything: crafting, redstone, mod recipes, or what to do next.

It knows where you are, what you're holding, what you're looking at and which mods you have, so answers fit *your* game.

## Features

### 🖥️ A real terminal
Streaming replies, scrollback, input history with <kbd>↑</kbd> <kbd>↓</kbd>, <kbd>Ctrl</kbd>+<kbd>C</kbd> to stop a reply, click any message to copy it, and your conversation is remembered between game sessions.

### 🧠 Knows your game (opt-in)
Turn on **Share game info** in settings and each message includes a snapshot of your position, biome, time and weather, health, gear with enchantments and durability, inventory, the block or mob you're looking at, nearby mobs and your mod list. Type `/context` to see exactly what would be sent. Off by default.

### 🔑 Use your own AI
- **Anthropic API key** (Claude models)
- **OpenAI-compatible**: OpenAI, OpenRouter, Groq, or **free local models** with Ollama or LM Studio. Presets included, and installed local models are detected automatically.

Nothing is preconfigured: the mod never contacts any service until you choose a provider.

### 🛡️ Safe
- Client-side only: works in singleplayer and on servers, and the server doesn't need it.
- Never changes your worlds.
- Chat only: the AI cannot run commands or touch files on your computer.

## 🔒 Privacy
- **Nothing is sent anywhere until you choose a provider** in settings. A fresh install has no provider.
- **What is sent:** the messages you type, and the game snapshot only if **Share game info** is on (off by default). It goes **only to the provider you chose** (Anthropic, OpenAI, OpenRouter, Groq or your own endpoint), under that provider's privacy policy.
- **What is not sent:** no telemetry, no analytics, nothing to the mod author. The mod has no servers or accounts of its own.
- **Local models** through Ollama or LM Studio keep everything on your own computer.
- Your API key is stored only in `config/enderterminal.json` on your computer.
- Your conversation is saved only on your computer, in `enderterminal/conversation.json`, so it continues after a restart. `/new` clears it.

## Getting started
1. Install **Fabric API** alongside Ender Terminal.
2. Join a world and press <kbd>`</kbd> (or use the pause menu button).
3. Click **Settings**, choose a provider and paste your API key, or pick the Ollama preset.
4. Save and start typing.

## Commands
| Command | Action |
| --- | --- |
| `/settings` | Open settings |
| `/new` | Start a fresh conversation (also clears the saved one) |
| `/copy` | Copy the last reply |
| Click a message | Copy that message |
| `/context` | Show the game info that gets sent |
| `/cancel` | Stop the current reply |
| `/clear` | Clear the screen |

The open key can be changed in **Options → Controls → Key Binds → Ender Terminal**.

## Notes
- API keys are stored in plain text in `config/enderterminal.json`. Don't share that file.
- To keep API costs down, only the last 30 messages are sent with each request by default. Turn off "Remember only last 30 messages" in settings to send the whole conversation.
- Local models run much faster on a dedicated GPU. On CPU only, use a small model (3B) and consider turning off game info.

Source code and issues on [GitHub](https://github.com/8ightt/ender-terminal). Licensed under LGPL-3.0.
