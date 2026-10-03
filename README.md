# Local LLM Chat for Android

Chat with large language models on Android: fully on-device with LiteRT-LM or
Gemini Nano, or against an Ollama server on your network. The Android counterpart of
[Local LLM Chat for iOS](https://github.com/vesc0/local-llm-chat-ios).

## Features

- **Three inference engines**: LiteRT-LM for on-device generation, Gemini Nano
  (Android's built-in model) with nothing to download, or Ollama over the local
  network for heavier models.
- **Multimodal input**: attach images (sent to vision-capable Ollama models) and
  PDF or text documents (extracted and appended to the prompt).
- **Reasoning models**: `<think>` traces are separated from the answer and shown
  in a collapsible section with elapsed thinking time.
- **On-device model management**: download any Hugging Face repository that
  contains a `.litertlm` file by id, then select or delete what you have downloaded.
- **Persistent history**: conversations, attachments, and settings survive relaunch.

## Requirements

- Android 12 (API 31) or newer
- Android Studio with JDK 17 or newer

Gemini Nano needs a device with AICore support, such as a recent Pixel or Galaxy flagship.

## Getting started

```bash
git clone https://github.com/vesc0/local-llm-chat-android.git
```

Open the folder in Android Studio, wait for Gradle to sync, pick a device, and run.
From the command line, `./gradlew installDebug` builds and installs on a connected device.

## Testing

```bash
./gradlew testDebugUnitTest
```

## Configuration

### Ollama

1. Start Ollama on your machine and make sure it listens on your LAN
   (`OLLAMA_HOST=0.0.0.0 ollama serve`).
2. In the app, open Settings, choose **Ollama**, and enter the host, for example
   `http://192.168.1.10:11434`.
3. Pick a model under **Select Ollama Model**.

### LiteRT-LM

1. In Settings, choose **LiteRT**.
2. Under **Manage Models**, enter a Hugging Face repo id, for example
   `litert-community/Qwen2.5-1.5B-Instruct`, and download it.
3. Tap the downloaded model to select it.

Gated repositories that require a Hugging Face login aren't supported.

### Gemini Nano

Choose **Gemini Nano** in Settings. The model is managed by the system and
downloads on first use when the device supports it.

## Project structure

```
app/src/main/java/com/localllm/chat/
  models/    serializable domain types and reasoning-trace parsing
  services/  inference backends, persistence, attachments, model downloads
  ui/        ChatViewModel and Compose screens
app/src/test/  unit tests
```

Inference backends implement `ChatService`, which exposes a single
`stream(messages): Flow<String>`. `ChatViewModel` receives a store and a service
factory through its constructor, so tests substitute stubs without touching the
network or the file system.

## Dependencies

- [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM): on-device inference
- [ML Kit GenAI Prompt API](https://developers.google.com/ml-kit/genai/prompt/android): Gemini Nano
- [OkHttp](https://square.github.io/okhttp/): Ollama and Hugging Face networking
- [Multiplatform Markdown Renderer](https://github.com/mikepenz/multiplatform-markdown-renderer): message formatting
- [Coil](https://coil-kt.github.io/coil/): image loading
- [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android): PDF text extraction

Versions are pinned in `gradle/libs.versions.toml`.
