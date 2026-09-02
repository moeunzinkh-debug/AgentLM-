# AgentLM

A Compose-only Android chat client that talks to **real models** — either your own inference
server (OpenAI-compatible: Ollama, LM Studio, llama.cpp, vLLM, PrivateLM) or Google Gemini — and
downloads **real weights** from Hugging Face with byte-accurate, resumable progress.

There is no simulated reply generator and no fake progress bar anywhere in this app. If nothing is
configured, the app says so and links to the screen that fixes it.

---

## 1. Make answers real

Open **Settings → Engines & Keys** and either

* **Cloud (Gemini)** — put the key in `.env` as `GEMINI_API_KEY=...` (read through
  `secrets.properties`/`gradle.properties` at build time, see `app/build.gradle.kts`), or paste it
  into the profile. The default model id is already filled in; edit it only if you want a
  different Gemini revision.
* **Local / LAN (OpenAI-compatible)** — base URL `http://192.168.1.20:11434/v1` (Ollama) or
  `http://127.0.0.1:1234/v1` (LM Studio), model id as reported by that server, key only if the
  server needs one. `localhost`/`10.0.2.2`/`127.0.0.1` are allowed in release builds; other
  cleartext hosts work in debug builds (`app/src/main/res/xml/network_security_config.xml`).
* **On-device (LiteRT-LM)** — see §3. Downloaded GGUF/Safetensors are offered as an engine as soon
  as one is on disk.

Use **Test** on a profile to see the exact HTTP status and body. Requests are ordered *active
engine → on-device → every other ready engine*, and a failing engine is only replaced while no
token has been displayed yet (never mid-sentence).

## 2. Make downloads real

**Model Hub** searches `huggingface.co/api/models?q=…&config=true` (your `hf-router` key is sent
as a bearer token when present, so gated repos work), then resolves the repository tree
`/api/models/{id}/tree/main?recursive=true` to get the *actual* weight files: real byte sizes, real
quantization, and the single file that fits this phone (`pickFor` = largest Q4_K_M under the
device's resident budget).

Download = OkHttp `GET` on `https://huggingface.co/{repo}/resolve/main/{file}` into
`<cacheDir>/models/<file>.part`, `Range` resume when `.part` exists, `fsync`'d writes, atomic
rename, and a `manifest.json` that survives process death. Progress is real bytes and a real
windowed speed; `Settings → Storage` reports the same files.

## 3. Not freezing while tokens stream

Design rules (ported from the `orailnoor/cross-platform-llm-client` / PrivateLM sources and adapted
to Compose) — full write-up in [`docs/REAL-AGENT.md`](docs/REAL-AGENT.md):

| Problem | Fix in this repo |
| --- | --- |
| One recomposition + full relayout per token | Tokens only ever go into `ChatViewModel.streamingText`; the `LazyColumn` is written **twice per turn** (placeholder, then commit). |
| Re-parsing Markdown on every frame | Plain text while streaming, Markdown after `Done` (opt-in "live Markdown"), results memoised in an LRU (`MarkdownParseCache`). |
| Follow-scroll animation storm | `derivedStateOf` near-bottom gate + `scrollToItem` while streaming; 150 ms budget; pauses the moment you scroll up. |
| Prefill hang / stalled socket | `ResponseStreamer`: prefill watchdog, inter-token idle timeout, hard wall-clock cap. The partial text is kept, never discarded. |
| Token-bomb memory | `maxResponseChars` / `maxLines` guard inside the stream loop, plus `sanitizeStreamedText` (control chars, zero-width, long-token splitting, CRLF) before every repaint. |
| Model keeps eating RAM | `Settings → Response Tuning` releases the loaded model after N idle seconds or on background; failures in GPU→CPU load retry are surfaced with real diagnostics. |
| Reply too long for the phone | Token cap derived from measured RAM − KV-cache budget (`ResponseBudgetAdvisor`), clamped to the active `SafetyMode`. |

## 4. Response Tuning (Settings tab 2)

The place that "defines the response": persona/system prompt per agent (`Personas`), plus

* **Max response tokens / lines / context turns / KV budget** — sliders, each labelled with the
  device-derived ceiling and why it exists;
* **UI smoothness** — coalescing interval & min flush chars, live Markdown, auto-follow;
* **Anti-hang limits** — prefill, inter-token idle, hard turn cap, background release & keep-alive;
* **Safety mode** — `Safe` (3–4 GB devices), `Balanced`, `Turbo` (flagships); each mode re-clamps tokens, cadence and Markdown policy.

Everything persists in `SharedPreferences` as JSON (`RuntimeSettingsRepository`) and is clamped to
the current device on every load.

## 5. Optional on-device runtime

`LiteRT-LM` (`com.google.ai.edge.litertlm:litertlm-android`) is compiled in only when asked, so a
missing artifact can never break the default build:

```bash
./gradlew assembleDebug -Pagentlm.nativeEngine=true
```

That adds the `app/src/litertlm/java` source set (Java 17 target) and the `uses-native-library`
manifest entries. Reflection is limited to instantiating `com.example.litertlm.LiteRtLmBackend`;
the same class can also be wired into a Flutter/other host, which is how the reference app does it.

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest      # Robolectric screenshot test on the new ViewModel ctor
```

Requirements: JDK 17, Android SDK 36 (compileSdk `36.1`), Kotlin 2.2.10, AGP 9.1.1.
