# J2ME LLM

[简体中文](README.md) | **English**

J2ME LLM is a lightweight OpenAI Chat Completions-compatible client for CLDC 1.1 / MIDP 2.0 phones and Java ME emulators. Version 0.2.0 builds on the v0.1 prototype tested on a Sony Ericsson W995 and adds multiple provider profiles, official presets, on-demand model discovery, independent reasoning controls, memory-conscious streaming, optional multimodal input, and offline configuration packages that can be transferred over Bluetooth.

**Download v0.2.0:** [JAR installer](https://github.com/huayuechenfeng/j2me-llm/releases/download/v0.2.0/J2ME-LLM.jar) · [JAD descriptor](https://github.com/huayuechenfeng/j2me-llm/releases/download/v0.2.0/J2ME-LLM.jad) · [Release notes](https://github.com/huayuechenfeng/j2me-llm/releases/tag/v0.2.0) · [Standalone Windows x64 gateway ZIP](https://github.com/huayuechenfeng/j2me-llm/releases/download/v0.2.0/J2ME-LLM-Gateway-v0.2.0-windows-x64.zip)

> **Development note:** v0.2.0 is the first public release of J2ME LLM; v0.1.0 was an unpublished development and W995 device-testing prototype. Chihoko defined the product direction and performed real-device validation, while design, implementation, testing, and documentation were completed through AI-assisted vibe coding.

## Highlights

- Four isolated profiles: OpenAI, DeepSeek, Kimi, and Custom. Each profile keeps its own API key, endpoints, model, reasoning settings, multimodal flag, model cache, and conversation history.
- Automatic migration from the v0.1 `J2MELLM_CFG` and `J2MELLM_CHAT` stores. Legacy RMS data is retained instead of being deleted.
- Primary and backup RMS records for profile configuration and per-profile conversations, with automatic recovery when the primary record is damaged.
- Model lists are fetched only when requested. The incremental parser reads `data[].id`, caches up to 64 model IDs, and never contacts a provider during startup.
- Reasoning mode (`Auto`, `On`, or `Off`) is independent from whether received reasoning text is expanded or folded in the UI.
- Multimodal support is disabled by default. File APIs, image reads, image decoding, and previews are loaded only when needed.
- Request JSON is measured first and then written directly to the HTTP connection through a 512-byte buffer. Image Base64 is streamed without constructing another full-size encoded string.
- Canvas bubble layouts are cached, and streaming repaints are coalesced to roughly one update every 100 ms.
- Offline `.j2cfg` packages can be generated on Android or a computer, transferred by Bluetooth, imported on the phone, and safely exported for backup.
- An optional Node.js gateway lets legacy TLS clients reach modern HTTPS providers through a trusted local network.

## Provider presets

| Profile | Chat Completions endpoint | Models endpoint | Initial model | Reasoning protocol |
| --- | --- | --- | --- | --- |
| OpenAI | `https://api.openai.com/v1/chat/completions` | `https://api.openai.com/v1/models` | Empty; fetch or enter one | `reasoning_effort` |
| DeepSeek | `https://api.deepseek.com/chat/completions` | `https://api.deepseek.com/models` | `deepseek-v4-flash` | `thinking.type` plus effort |
| Kimi | `https://api.moonshot.cn/v1/chat/completions` | `https://api.moonshot.cn/v1/models` | `kimi-k3` | Model-specific K3/K2 behavior |
| Custom | Editable | Editable or derived from the chat URL | Empty | None, OpenAI effort, thinking object, or always-on |

Built-in profiles reset to their official endpoints. Enable the advanced override only when a compatible gateway is required. Provider models and request fields can change over time, so refresh the catalog on demand and consult the current [OpenAI Models API](https://developers.openai.com/api/reference/resources/models/methods/list), [OpenAI Chat Completions API](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create), [DeepSeek API documentation](https://api-docs.deepseek.com/), and [Kimi model documentation](https://platform.kimi.com/docs/models).

## Installation

Place `J2ME-LLM.jar` and `J2ME-LLM.jad` in the same directory and install through the JAD when possible. The MIDlet requests HTTP and HTTPS access. JSR-75 file read/write permissions are optional and are used only for images and configuration packages.

For a v0.1 upgrade:

1. Do **not** uninstall the existing MIDlet. Uninstalling a Java ME suite usually removes its RMS data.
2. Keep `MIDlet-Name: J2ME LLM` and `MIDlet-Vendor: Chihoko` unchanged, then install the v0.2.0 JAD as an update or replacement.
3. On first launch, verify that the active profile is `Custom (legacy configuration)` and check the endpoint, key, model, and migrated conversation.
4. Export a `.j2cfg` backup after the upgrade succeeds.

RMS ownership belongs to the MIDlet suite, and whether an update is recognized as the same suite still depends on the phone vendor's installer. See the [update and recovery guide](docs/UPDATE_AND_RECOVERY.md) for the complete test matrix.

## Building

The build requires Windows PowerShell and a JDK that provides `java`, `javac`, and `jar`. Downloaded build tools are kept under the project-local `.tools` directory and do not replace the system Java installation.

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\bootstrap.ps1
powershell -ExecutionPolicy Bypass -File .\tools\build.ps1
```

Build outputs:

- `dist/J2ME-LLM.jar`
- `dist/J2ME-LLM.jad`

The release build runs 11 desktop self-tests, compiles with ECJ using Java 1.3 source and CLDC 1.1-compatible class files, and performs Java ME preverification with ProGuard. Do not use `-SkipTests` for release artifacts.

Start MicroEmulator with:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\run-emulator.ps1
```

Run the real RMS upgrade/recovery fixture, which creates v0.1 records, migrates them, corrupts the primary v0.2 record, and verifies backup recovery:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\test-rms-upgrade-recovery.ps1
```

## Offline configuration and Bluetooth transfer

Open [provisioner/index.html](provisioner/index.html) directly on an Android phone or computer. It is a single offline page with no network dependency and does not upload form data.

1. Fill in one or more profiles and generate `J2ME-LLM-config-v2.j2cfg`.
2. Share the file over Bluetooth to the Java ME phone.
3. In J2ME LLM, open `Profiles -> Import configuration` and select the file.
4. After a successful import, delete the plaintext package from the phone, Android downloads, and Bluetooth inbox if it is no longer needed.

The `.j2cfg` envelope uses versioned JSON, a Base64 payload, and CRC-32. This detects transfer corruption but provides **no encryption or source authentication**. API keys remain plaintext. Prefer a revocable, rate-limited gateway token instead of a long-lived account key. See the [provisioner documentation](provisioner/README.md) for the format and offline verification flow.

Configuration export writes `J2ME-LLM-backup-<timestamp>.j2cfg` to the first writable filesystem root. Export uses a temporary file, byte-for-byte verification, and a same-directory rename. Configuration packages contain provider settings but not conversation history.

## Reasoning controls

`Settings -> Reasoning mode` controls fields sent in future requests:

- `Auto`: send no reasoning control and let the model decide.
- `On`: enable reasoning using the selected profile protocol and effort.
- `Off`: use `reasoning_effort: none` for OpenAI-style profiles or `thinking.type: disabled` for supported DeepSeek/Kimi K2-style profiles.
- Kimi K3 and K2.7 Code are treated as always-reasoning models. K3 accepts reasoning effort; K2.7 Code does not receive a guessed switch field.

The chat screen's reasoning command only expands or folds reasoning text already received. It does not change request behavior. Not every provider supports every value; if the server rejects a field, return the profile to `Auto`.

## Multimodal and memory behavior

Multimodal mode is off by default. When enabled on a device with JSR-75, the file picker accepts JPG, PNG, GIF, or WebP files up to 96 KiB and 65,536 pixels. Requests use an `image_url` data URL with `detail: low`. Compatible image responses are treated as an extension and remote downloads are limited to 256 KiB.

Image dimensions are read from headers before decoding. Unknown or oversized images never reach `Image.createImage`. A failed preview or low-memory condition should affect only the image preview, not ordinary text chat.

The active conversation is limited to 32 messages and 49,152 weighted characters. A single message is limited to 24,576 content characters and 8,192 reasoning characters. The saved RMS history keeps up to 24 completed messages per profile.

## HTTPS/TLS gateway for legacy phones

Older phones such as the W995 may be unable to negotiate modern TLS versions or trust current certificate chains. [gateway/server.js](gateway/server.js) stores the real upstream key and accepts a separate device token from the phone. Example local endpoints:

- Chat: `http://192.168.1.10:8787/v1/chat/completions`
- Models: `http://192.168.1.10:8787/v1/models`

Plain HTTP on a LAN is not end-to-end encrypted: the device token, prompts, answers, and images can be observed by other systems on that network. Never expose the gateway port directly to the public internet. The emulator configuration should bind to `127.0.0.1`; bind to a LAN address only for real-device testing and protect it with a firewall. See the [gateway guide](gateway/README.md).

## Repository layout

| Path | Purpose |
| --- | --- |
| `src/` | CLDC 1.1 / MIDP 2.0 application source |
| `tests/` | Desktop self-tests and Java ME stubs |
| `tools/` | Toolchain bootstrap, build, emulator, and RMS recovery scripts |
| `provisioner/` | Offline `.j2cfg` generator |
| `gateway/` | Optional legacy-TLS compatibility gateway |
| `docs/` | User, architecture, development, and recovery documentation |
| `config/` | MIDlet manifest source |

## Documentation

- [User guide (Chinese)](docs/USER_GUIDE.md)
- [Update, migration, and recovery (Chinese)](docs/UPDATE_AND_RECOVERY.md)
- [Architecture, protocols, and memory budgets (Chinese)](docs/ARCHITECTURE.md)
- [Development, testing, and release checklist (Chinese)](docs/DEVELOPMENT.md)
- [Offline provisioning package format (Chinese)](provisioner/README.md)
- [TLS compatibility gateway (Chinese)](gateway/README.md)
- [Changelog](CHANGELOG.md)

## Known limitations

- The core protocol is Chat Completions, not the Responses API. Tool calls and standard image generation are not implemented.
- Reasoning fields and compatible image response formats are not standardized across providers; presets cover only known protocols.
- API keys are stored in RMS without a hardware-backed secure element. Security depends on the device, filesystem permissions, and gateway policy.
- `.j2cfg` packages do not back up chat history. RMS is normally unrecoverable after uninstalling the MIDlet.
- An emulator cannot reproduce W995 TLS, fonts, JSR-75 behavior, heap limits, or image decoders. Real-device regression testing remains required for each release.

Contributions and device compatibility reports are welcome.
