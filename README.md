# LlamaQuill

LlamaQuill is a local-first JavaFX desktop client for AI-assisted story writing.

It is built around:
- `Ollama` for text generation
- `ComfyUI Desktop` for image generation
- a SQLite story database stored locally by the app

LlamaQuill is intentionally a thick client. It does not run its own web server or require a browser UI.
It is also intentionally narrow in scope: story writing, story memory, story cards, and optional scene image generation. The goal is a focused desktop tool for narrative workflow rather than a general-purpose local AI workbench.
This codebase was developed with substantial assistance from the Codex extension in Cursor.

## Requirements

- Java 25
- Ollama running locally
- ComfyUI Desktop running locally (optional, for See feature)

Default local endpoints:
- Ollama: `http://localhost:11434`
- ComfyUI: `http://localhost:8000`

Both can be changed in the app's Options panel.

## Ollama setup

LlamaQuill is designed around Latitude's AI Dungeon models distributed through Hugging Face and pulled through Ollama.

Latitude model list:
- `https://huggingface.co/LatitudeGames/models`

Example pull command for Muse 12B Q8:

```powershell
ollama pull hf.co/LatitudeGames/Muse-12B-GGUF:Q8_0
```

Any compatible model available through Ollama can be selected inside LlamaQuill once pulled. Latitude's models are suggested but you can try any model that honors ChatML style.

## ComfyUI Desktop setup

LlamaQuill's "See" action expects ComfyUI Desktop to be installed and running. If you won't use See, it's optional.

### Default Chroma HD workflow

For the default Chroma HD path, install the models required by ComfyUI Desktop's built-in:
- `Chroma Text to Image`

template.

The simplest path is to open that template inside ComfyUI Desktop and let ComfyUI install the required models for it.

### Chroma2 experimental workflow

For the experimental Chroma2 workflow:
- install the ComfyUI Desktop template/models for `Flux.2 [Klein] 4B`
- you can skip the actual Flux.2 base model downloads
- instead, download the Chroma2 Kaleidoscope model from:
  - `https://huggingface.co/lodestones/Chroma2-Kaleidoscope/`

LlamaQuill workflow files live under:
- `src/main/resources/comfyui/`

The workflow dropdown in the app scans that folder and exposes available workflow JSON files by filename.

## Build

From the project root:

```powershell
mvn -DskipTests package
```

## Notes

- LlamaQuill stores stories, cards, settings, and inserted images locally.
- It supports AI Dungeon adventure import and story card import.
- It includes Auto Cards, image generation through ComfyUI, and one-shot story-context prompting.
- It is deliberately targeted and opinionated. You can tune models and workflows, but the application is not trying to expose every possible backend feature or become a generic prompt lab.

## Credits

- AI Dungeon and Latitude Games for the model ecosystem and workflow inspiration
- LewdLeah's Auto-Cards for ideas and reference points around automatic story card generation
