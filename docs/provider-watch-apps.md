# Separate agent apps for Pixel Watch

Three unofficial Wear OS apps share the existing Chums client and hub protocol.
Each app has its own skin, defined in `wear/app/src/main/java/club/chums/wear/Skin.java`:
clay orange on warm charcoal with serif headings for Claude, ivory on graphite in
monospace with squared corners for Codex, and white on black with hairline edges
for Grokbot. Shared components live in `Ui.java`. The home dial puts the current
task in the centre, one talk button in the bottom row, and a bezel ring that shows
state: a sweeping arc while working, amber when the agent needs you, dashed when
the hub is unreachable. Conversations use chat bubbles and an amber approval card.
Every action has a separate touch target of at least 48dp. Screenshots named
`*-redesign.png` under `wear/screenshots/providers` show the implemented apps.
The earlier Higgsfield artwork stays under `wear/app/src/main/assets/providers`
with its provenance in `design/provider-watch/higgsfield/provenance.json`, but
the home screens no longer draw it.

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
| Agent replies, approvals, status | Implemented through existing hub state; foreground polling plus optional scheduled background checks |
| Streamed cards and images | Implemented in live voice; history cards and authenticated artifact previews are now supported; previews require a compatible hub/proxy |
| Consumer ChatGPT/Claude/Grok history | Not integrated |
| Model selection and tool activity | Agent workspace uses the hub catalog and provider-specific model endpoint; unavailable capabilities are shown explicitly |
| Multiple sessions and project/file browser | Not exposed by the current hub/watch interfaces |
| Desktop editors, terminal, diff/review workflows | Run remotely; no equivalent watch UI |
| Background notifications / task completion alerts | Optional 15-minute scheduled checks, deduplicated per hub/provider; Android can delay delivery |
| Physical watch audio, connectivity transitions and battery | Requires device verification |

The published scope is the Android clients and artwork. Existing local cloud/hub
extensions are separate work and are not part of this client commit. Voice needs a
compatible deployed hub providing `/v1/realtime/session`, `/v1/say`, `/v1/state` and
`/v1/verdict`; the upstream hub alone is not evidence of live-voice readiness.


## Version 0.2.0 verification and installation status

- Added Agent workspace, hub-provided model selection, recent tool activity, history
  cards, and authenticated attachment previews. Model changes require confirmation.
- Optional background checks notify about terminal replies and approvals without
  showing message content. Enabling checks records a baseline; failures retain
  the cursor and disabling checks cancels the job. Android schedules these checks,
  so they are not instant push alerts. See [JobScheduler timing](https://developer.android.com/reference/android/app/job/JobInfo.Builder).
- Large text settings (130% and above) use a scrollable provider-themed home with
  full-size controls. Connection updates preserve existing preferences and drafts.
- `tools/test-provider-workspace.py` checks real app navigation against a local
  fixture: model routing, notification delivery/disable and authenticated previews.
  `tools/test-provider-large-text.py` checks all home controls at 150% text size.
  Both scripts refuse physical-device serials.
- The cloud image relay correction is deployed as
  `dpl_CQ6BS5bAcgKnbasH8iVFXPY7nvFc` on `chums-watch.vercel.app`.
  `tools/test-cloud-relay.mjs` checks JSON and binary responses and the size limit.
  The deployed source hashes were verified against the prior production files;
  only the relay was changed. A first release failed because returned source data
  was truncated, was rolled back, then replaced with the verified release.
  Unauthenticated production requests correctly return HTTP 401. An authenticated
  cloud probe remains blocked: the connector does not return the token value,
  and the local config targets a different hub.
- Physical installation is pending an accessible wireless-debugging endpoint.
  The previous endpoint was unreachable. Live account authentication, microphone,
  battery and consumer-history integration remain unresolved; this is not a
  declaration of full feature parity.
