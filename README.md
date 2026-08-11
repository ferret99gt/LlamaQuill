# LlamaQuill

LlamaQuill is a local-first Windows desktop app for AI-assisted story writing. It combines an
AI Dungeon-inspired adventure workflow with local Ollama text generation, optional ComfyUI scene
images, Story Cards, and a SQLite story database that remains under the user's control.

Version `0.3.0` adds model-specific conversation layouts, inspectable prompt construction, Story
Card context controls, and story cloning. See the [user-facing changelog](CHANGELOG.md) for the
upgrade highlights.

## Download and run

1. Download `LlamaQuill-0.3.0-windows-x64.zip` from
   [GitHub Releases](https://github.com/ferret99gt/LlamaQuill/releases).
2. Optionally compare its SHA-256 hash with the accompanying `.sha256` file.
3. Extract the complete `LlamaQuill` folder and run `LlamaQuill.exe` inside it.

Keep the whole extracted folder together. The executable depends on the adjacent `app` and
`runtime` directories; copying only `LlamaQuill.exe` will not produce a working or updated app.
The release includes its own Microsoft OpenJDK runtime, so users do not need to install Java.

The 0.3.0 build is not code-signed. Windows may therefore display a SmartScreen warning for the
downloaded app.

Runtime requirements:

- 64-bit Windows
- [Ollama](https://ollama.com/) running locally
- [ComfyUI Desktop](https://www.comfy.org/download) running locally only if using **See**

Default endpoints are `http://localhost:11434` for Ollama and `http://localhost:8000` for
ComfyUI. Both can be changed in **Options**.

## Recommended Ollama model

[Latitude Equinox 31B](https://huggingface.co/LatitudeGames/Equinox-31B-GGUF) is the recommended
starting point for LlamaQuill. It is a modern narrative model with strong long-context
continuation behavior:

```powershell
ollama run hf.co/LatitudeGames/Equinox-31B-GGUF:Q4_K_M
```

Latitude publishes Equinox, Muse, and its other AI Dungeon-oriented models in the
[LatitudeGames Hugging Face collection](https://huggingface.co/LatitudeGames/models). Other
Ollama chat models can work as well; LlamaQuill uses the model's Ollama chat template.

### Choosing a GGUF quant

The GGUF filename and file size are useful first estimates of memory demand. For Equinox 31B,
`Q4_K_M` is about 18.7 GB and is the broadly safer first download; `Q6_K` is about 25.2 GB and is
a higher-quality option for a 32 GB-class GPU with enough headroom.

Do not treat the GGUF file size as total runtime memory. Leave room for the context/KV cache,
Ollama, the display, and anything else using the GPU—especially ComfyUI. CPU/RAM offload can make
an oversized model run, but usually increases generation latency. In practice:

- prefer the largest quant that fits fully in the memory you intend to use;
- leave several GB of headroom rather than targeting the exact VRAM capacity;
- budget more headroom when selecting a large context window;
- step down one quant if Ollama offloads unexpectedly or other GPU apps become constrained.

Hugging Face can estimate this for you. Sign in, add your CPU/GPU or Apple Silicon and memory
under [Saved Hardware](https://huggingface.co/settings/hardware), then review the **Hardware
compatibility** section on a GGUF model page. Saved hardware is public by default, but Hugging
Face provides a private visibility option.

Response length and every sampling control have separate enable switches. A disabled control is
omitted from the Ollama request so the model's own default can apply. When Response Length is
disabled, LlamaQuill still reserves 200 estimated tokens inside its prompt budget so the model
has space to answer.

The global **Ollama Model Keep Alive** setting retains the selected model for 5–30 minutes after
each request, reducing reload delays when returning to a story.

## ComfyUI Desktop setup

LlamaQuill's **See** action expects ComfyUI Desktop to be installed and running. It is otherwise
optional.

### Chroma HD

Open ComfyUI Desktop's built-in `Chroma Text to Image` template and let ComfyUI install the
required models. Select `ChromaHD` in LlamaQuill.

### Kroma

For `Kroma`, update ComfyUI to a version with native Krea 2 support, then install:

- `kroma-v0.2-turbo.safetensors` from [lodestones/Kroma](https://huggingface.co/lodestones/Kroma)
  under `ComfyUI/models/diffusion_models/`;
- `qwen3vl_4b_fp8_scaled.safetensors`, the Krea 2 text encoder, under
  `ComfyUI/models/text_encoders/`;
- `qwen_image_vae.safetensors`, the Krea 2 VAE, under `ComfyUI/models/vae/`.

The bundled workflow uses Kroma's native Krea 2 pipeline with its Turbo defaults: 8 steps,
CFG 1.0, Euler/simple sampling, and the model's built-in 1.15 shift.
Kroma is the recommended See workflow; Chroma HD remains available as the established fallback.

The **See** dialog can keep a visual style selected per story. Its protected built-ins are None,
Photo, Realistic, Anime, Digital Illustration, and Painterly, with the same save/update/delete flow
available for custom styles. The selected style prompt is sent after story context and before the
separate optional custom request; choose None to keep the original unstyled prompt flow.

Bundled workflow JSON files live under `src/main/resources/comfyui/` in the source tree.

## Privacy and connectivity

Stories, Story Cards, settings, and generated/imported images are stored in the local SQLite
database. LlamaQuill has no LlamaQuill cloud service and does not send telemetry.

Generation content is sent to the Ollama and ComfyUI endpoints configured in **Options**. The
defaults are local loopback addresses; changing them to remote servers also changes where that
content is sent. Importing an AI Dungeon backup can make HTTPS requests to `aidungeon.com` image
URLs found in that backup so **See** images can be copied into the local database. Ordinary text
imports do not require AI Dungeon access.

## Data, backup, migration, and restore

The default Windows data directory is:

```text
%LOCALAPPDATA%\LlamaQuill
```

It contains:

- `llamaquill.db` — stories, cards, settings, and image bytes;
- `backups\` — manual and automatic pre-migration database backups;
- temporary SQLite `-wal` and `-shm` companions while the app is running.

Use **Options → Back Up Database** before a release upgrade or major experiment. **Check
Database** reports the active path, schema, integrity, and broken image references.

To restore a backup:

1. Close LlamaQuill completely.
2. Preserve the current `llamaquill.db` and any `llamaquill.db-wal` or
   `llamaquill.db-shm` files somewhere outside the data directory.
3. Copy the chosen backup into the data directory and name the copy `llamaquill.db`.
4. Ensure stale `llamaquill.db-wal` and `llamaquill.db-shm` companions are not left beside the
   restored copy, then start LlamaQuill.

`LLAMAQUILL_DATA_DIR` or the `llamaquill.dataDir` Java system property can select an explicit
portable/test location.

Version 0.3.0 still treats an unversioned database as the historical 0.1.0 format. On first launch
or an upgrade from 0.2.0 it:

- copies a legacy `.\data\llamaquill.db` when the stable data location has no database;
- leaves the original legacy database untouched;
- creates a timestamped pre-migration backup;
- migrates transactionally to schema 5 and records application/schema history.

The 0.3.0 migration adds per-model Conversation Layout and Story Card Wrapping settings and
retires the separate Author's Note placement control. Earlier migrations add Story Card types,
player-only notes, and reusable generation presets while removing the retired automatic AutoCards
configuration. If migration fails, LlamaQuill does not replace the source database with a partial
migration.

## AI Dungeon imports

**Import Adventure** accepts AI Dungeon backup ZIPs using the current `metadata.json` plus
`actions-N.json` layout tested in July 2026. It also recognizes the older Story Card and custom
instruction shapes covered by LlamaQuill's import fixtures. Stories, recognized actions, Story
Cards, and supported **See** images are imported as one transaction.

**Import Story Cards** accepts AI Dungeon's JSON card export (`keys` and `value`, with optional
title/type/notes). It can merge new cards while skipping exact duplicates or replace the current
story's cards. Unknown action/card fields are ignored. Keep the original export as a backup:
future AI Dungeon format changes may require a LlamaQuill importer update.

An adventure containing remote images requires those `aidungeon.com` image URLs to remain
available during import. If a required image cannot be downloaded or decoded, the transaction is
rolled back rather than creating a partial adventure.

## Build from source

Developer requirements:

- Microsoft OpenJDK `25.0.4+7-LTS`
- Maven `3.9.x`
- 64-bit Windows for `jpackage` release creation

Run the test suite:

```powershell
mvn clean verify
```

Run CI-equivalent verification: two clean-separated app-image builds, packaged runtime/version
checks, an isolated launcher/database smoke test, release ZIP inspection, and a check that the
build did not mutate tracked source files:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify.ps1
```

Create the release ZIP and SHA-256 file:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\package-release.ps1
```

Artifacts are written to `target\release\`. GitHub Actions runs the same verifier and uploads the
ZIP/checksum as workflow artifacts. Dependabot watches Maven and GitHub Actions dependencies, and
pull requests receive GitHub's dependency vulnerability review.

## License

LlamaQuill is available under the [MIT License](LICENSE). This license applies to LlamaQuill's
repository contents, not to third-party models, model weights, applications, services, trademarks,
or other separately licensed dependencies and assets.

## Credits

- AI Dungeon and Latitude Games for the model ecosystem and workflow inspiration
- LewdLeah's Auto-Cards for ideas and reference points during LlamaQuill's earlier
  automatic-card experiments
- OpenAI Codex models used extensively throughout LlamaQuill's design, implementation, testing,
  and audit history
