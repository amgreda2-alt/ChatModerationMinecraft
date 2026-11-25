# ChatModeration

Chat moderation plugin for **Spigot / Paper 1.21.11** (Java 21): anti-spam, word filter, temporary and permanent mutes that survive restarts, global chat control and staff bypass.

## Build

```bash
mvn clean package
# -> target/ChatModeration-1.0.0.jar  (copy to your server's plugins/ folder)
```
Requires JDK 21 and Maven. The Paper API is pulled from `repo.papermc.io`.

## Commands

| Command | Permission | Description |
|---|---|---|
| `/mute <player> <duration> <reason>` | `chatmoderation.mute` | Mute a player. Duration: `30s`, `10m`, `2h`, `1d`, `1w`, combined `1h30m`, or `perm`. Reason is optional (defaults to config). |
| `/unmute <player>` | `chatmoderation.unmute` | Remove an active mute. |
| `/mutecheck <player>` (`/checkmute`) | `chatmoderation.check` | Reason, staff, issue time, expiry, time remaining. |
| `/clearchat` (`/cc`) | `chatmoderation.clear` | Clear global chat. |
| `/chat toggle` | `chatmoderation.toggle` | Enable/disable global chat (state persists in `state.yml`). |
| `/chat reload` | `chatmoderation.admin` | Reload `config.yml`. Active mutes are kept. |
| (no command) | `chatmoderation.bypass` | Skips cooldown, spam checks, word filter and disabled chat; sees chat after `/clearchat`; cannot be muted (`mute.protect-bypass`). |

All permissions default to `op`.

## How it works

* **Chat pipeline** (`ChatListener`): mute -> bypass -> global chat on/off -> cooldown -> rate limit -> repeated message -> character flood -> caps -> blocked words.
* **Spam**: rate limit (N messages in X s), identical-message detection (ignores case/spacing, compares against the last N messages), flood (`heyyyyyyy`), excessive caps, configurable cooldown. Optional **auto-mute** after repeated violations.
* **Word filter**: configurable list; detects case, accents, leetspeak (`sh1t`), stretched letters (`shiiit`) and spaced letters (`s h i t`). `BLOCK` or `CENSOR` mode. Whole-word matching by default (no "class" false positive).
* **Mutes**: stored in `plugins/ChatModeration/mutes.yml` (UUID, name, reason, staff, created, expires). Saved on every change with an atomic write and again on shutdown; loaded on startup (expired ones are dropped). A repeating task removes expired mutes and tells the player; expiry is also checked lazily on chat.
* Muted players also cannot use `/msg`, `/me`, etc. (`mute.blocked-commands`).
* All messages are in `config.yml` under `messages:`. A message set to `""` is not sent.

## Files

```
src/main/java/dev/chatmoderation/
  ChatModerationPlugin.java   lifecycle, state, expiry task
  ChatListener.java           chat + command + quit events
  MuteManager.java / MuteEntry.java   mutes + persistence
  Settings.java / Messages.java       config access
  command/                    Mute, Unmute, MuteCheck, ClearChat, Chat
  util/                       DurationUtil, WordFilter, PlayerChatData (no Bukkit dependency)
src/main/resources/plugin.yml, config.yml
```

## Test / demo checklist (for the video, 2 players)

1. Start server with the jar; show a clean console. Join as **Staff** (op) and **Player2**.
2. **Cooldown**: Player2 sends two messages quickly -> cooldown message.
3. **Rate / spam**: Player2 sends 5+ messages fast -> rate message; keep going -> auto-mute.
4. **Repeat**: send `hello` twice (after the cooldown) -> blocked.
5. **Flood / caps**: `heyyyyyyyyy`, `STOP SHOUTING EVERYONE`.
6. **Blocked words**: `shit`, `sh1t`, `s h i t`; then change `action: CENSOR`, `/chat reload`, repeat.
7. **Mute**: `/mute Player2 2m spamming` -> Player2 cannot chat or `/msg`; `/mutecheck Player2`.
8. **Auto-unmute**: `/mute Player2 20s test`, wait -> "mute expired" message and chat works.
9. **Unmute**: mute again then `/unmute Player2`.
10. **Persistence**: `/mute Player2 1h restart-test` and `/mute Player2 perm` (separately), `/mutecheck`, **stop the server**, show `mutes.yml`, start again, join, `/mutecheck Player2` and try to chat.
11. **Clear chat**: `/clearchat` (Player2's chat is cleared, staff with bypass keeps history).
12. **Global chat**: `/chat toggle` -> Player2 blocked, staff can still talk; toggle back.
13. **Bypass**: remove `chatmoderation.bypass` from staff (or use a non-op) and show the restrictions apply; add it back and show they don't.
14. **Permissions**: as a non-op, run each command -> no-permission message.
15. **Reload**: edit a message in `config.yml`, `/chat reload`.
