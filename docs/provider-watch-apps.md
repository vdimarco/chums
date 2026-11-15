# Separate agent apps for Pixel Watch

Three unofficial Wear OS apps share the existing Chums client and hub protocol.
Higgsfield generated the concept sheet and three production artwork assets:
sculpted ivory on graphite for Codex, organic terracotta on espresso for Claude,
and liquid chrome on black for Grokbot. The artwork is bundled locally under
`wear/app/src/main/assets/providers`; no image service or network request is
needed to render a home screen. Native text and accessible buttons sit above it.
Every action has a separate touch target of at least 48dp. Provider colors carry
through history, settings and voice screens. Generation IDs, source URLs and
prompts are recorded in `design/provider-watch/higgsfield/provenance.json`.
The concepts and production screenshots are distinct artifacts: screenshots named
`*-higgsfield.png` under `wear/screenshots/providers` show the implemented apps.

They install alongside ai migos and each other, with separate private connection
settings, launcher icons and tiles. They retain live voice, streamed content cards,
conversation history, read-aloud replies, and hold-to-allow / tap-to-deny approvals.

| Module | Launcher name | Android package | Existing hub agent |
| --- | --- | --- | --- |
| codex | Codex Watch | club.chums.wear.codex | noodle (Codex) |
| claude | Claude Watch | club.chums.wear.claude | squint (Claude Code) |
| grokbot | Grokbot | club.chums.wear.grokbot | stretch (Grok via Hermes ACP) |

The provider selection is fixed in each build, including voice and tile launches.
The multi-agent app retains its provider picker. The fixed mappings target the
existing cloud hub configuration; another hub must use the same agent assignments.
These apps access the existing agent sessions, not ChatGPT or Claude consumer app
history. Grokbot uses the existing Grok backend, not the Grok Bot desktop UI.
The agents run on the hub computer or cloud; no coding runtime runs on the watch.

## Build

From `wear/`, with the Android SDK and JDK configured:

```sh
./gradlew :codex:assembleDebug :claude:assembleDebug :grokbot:assembleDebug
./gradlew :app:testDebugUnitTest :codex:testDebugUnitTest :claude:testDebugUnitTest :grokbot:testDebugUnitTest :codex:lintDebug :claude:lintDebug :grokbot:lintDebug
```

Each APK is under `wear/<module>/build/outputs/apk/debug/<module>-debug.apk`.
Shared Android settings and dependencies live in `wear/provider.gradle`; shared
source stays in `wear/app/src`. No credentials are embedded in the APKs.

## Install

On the watch, enable Developer options, ADB debugging and Wireless debugging.
Choose Pair new device, run `adb pair IP:PAIRING_PORT`, then
`adb connect IP:DEBUGGING_PORT` using the main debugging screen’s port.
Confirm the watch is listed by `adb devices`.
From the repository root, for a local hub using `~/.chums/config.json`:

```sh
python3 tools/install-wear.py --app codex --serial IP:DEBUGGING_PORT
python3 tools/install-wear.py --app claude --serial IP:DEBUGGING_PORT
python3 tools/install-wear.py --app grokbot --serial IP:DEBUGGING_PORT
```

For a remote hub, install the APK with `adb install -r PATH_TO_APK`, then enter its
HTTPS address and matching watch token in each app's connection screen. The helper
also accepts `--url https://YOUR_HUB` when the local config contains that hub's token.
Do not use a local hub token for a different cloud hub.

Open each app, verify its provider name, tap its primary talk button, allow
microphone access, and speak a short message. The history icon opens replies,
Type a message, and approval requests. Empty typed messages are rejected; an
unconfirmed send retains its draft. Commands are never automatically resent.
The home status distinguishes hub connectivity, agent availability, and approvals. Each
app has its own tile. Voice transcription and read-aloud use the existing OpenAI
Realtime service even for Claude and Grok; the selected agent supplies the answers.

## Validation and limits

Debug builds, shared unit tests, provider selection tests, and lint were checked.
All three home screens were launched and captured on the Wear OS emulator.
`python3 tools/test-provider-watch-ui.py --serial emulator-5580` checks accessible
controls, minimum touch sizes, history, settings and Back navigation. It installs
the debug APKs and replaces their connection preferences with an offline preview
on the specified emulator; it refuses physical-device serials. No physical
watch was connected for installation or microphone/battery testing.
The macOS doctor cannot run on this Linux machine because `/bin/zsh` is absent.
No backend login, account billing or live model call was changed or verified.
The existing project notes report a Grok HTTP 402 account restriction and a Claude
cloud credential setup requirement; those must be resolved on the hub if still present.
These are sideloadable debug apps, not signed Play Store releases.


## Feature parity audit — 2026-11-15

This is a watch companion to the configured agent hub, not a full replica of the
consumer or desktop applications. No specific reference app version was supplied.
The local reverse-engineer-anything skill recommends normal source inspection for
available source trees; no decompilation or original-app runtime comparison was done.

| Capability | Current evidence / remaining work |
| --- | --- |
| Separate apps and fixed provider routing | Built; unit and emulator checks |
| Voice input and spoken agent responses | Implemented; deterministic routing/approval/error/cancellation checks pass; live accounts and microphone unverified |
| Typed messages | Native composer with empty-input validation, retained failed-send draft, existing `/v1/say` protocol |
| Agent replies, approvals, status | Implemented through existing hub state; foreground polling only |
| Streamed cards and images | Implemented in live voice; attachments in history still require phone/desktop |
| Consumer ChatGPT/Claude/Grok history | Not integrated |
| Multiple sessions, project/file browser, model selection | Not exposed by these watch interfaces |
| Desktop editors, terminal, diff/review workflows | Run remotely; no equivalent watch UI |
| Background notifications / task completion alerts | Not implemented |
| Physical watch audio, connectivity transitions, battery, accessibility at larger font sizes | Requires device verification |

The published scope is the Android clients and artwork. Existing local cloud/hub
extensions are separate work and are not part of this client commit. Voice needs a
compatible deployed hub providing `/v1/realtime/session`, `/v1/say`, `/v1/state` and
`/v1/verdict`; the upstream hub alone is not evidence of live-voice readiness.
