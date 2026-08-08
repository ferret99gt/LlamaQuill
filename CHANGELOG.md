# Changelog

This changelog focuses on what changes for people using LlamaQuill. The Git history contains the
implementation-level record.

## Unreleased

- When Ollama reports the exact prompt and context token counts for an oversized chat request,
  LlamaQuill now recompiles against the corrected budget and retries once. This also lets the
  existing per-model token calibration learn from the successful retry.
- The one-shot Prompt window can force Role-aware Turns for that request without changing the
  model's saved conversation layout. The override is enabled by default so utility instructions
  remain a distinct final user turn instead of blending into flattened story prose.
- The most recently selected Story Card command preset is now saved globally and restored when
  another Story Card is opened, including built-in presets such as Basic Prose Prompt.
- Story Card generation now always uses Role-aware Turns for its request, keeping the command in a
  distinct final user turn even when the model's saved conversation layout is Flattened.
- Additional Generation Context is now saved with its story, restored in every Story Card dialog,
  and copied when cloning Story Details.
- Story Card generation can ignore the saved Response Length for a single request. The new option
  is enabled by default and does not change the saved application setting.
- Story Cards can compare the current entry with the most recently replaced entry recorded in
  Notes, using a synchronized side-by-side word diff sized for smaller displays.
- See prompt generation now always uses Role-aware Turns and can ignore the saved Response Length
  for an individual request. The new non-persistent option is enabled by default.
- Each story can treat every Story Card as pinned during prompt compilation from the Story Cards
  tab, without changing any card's individual Pinned value.
- Story Card Additional Generation Context now saves when the field loses focus or its dialog is
  closed, independently of creating or updating a card.

## 0.3.0

Version 0.3.0 focuses on how story context is assembled, inspected, and adapted to different
Ollama model families. Existing databases are backed up and migrated automatically to schema 5.

### Conversation and prompt layout

- Each Ollama model can use one of three conversation layouts: **Role-aware Turns**, **Flattened**,
  or **Flattened with Prefill**. Role-aware Turns remains the default.
- Flattened layouts reproduce the useful parts of AI Dungeon-style context formatting, including
  `World Lore:`, `Recent story:`, `> ` user turns, and a bracketed Author's Note.
- Flattened with Prefill keeps the newest model response as assistant prefill on Continue. The
  ordinary Flattened layout instead applies a conservative visible-story whitespace join.
- Author's Note placement is now automatic and aligned with the continuation boundary. The old
  block-position spinner has been removed.
- **View Last Context** shows the exact role/message sequence and text from the most recently
  compiled request, making Story Card activation, ordering, and trimming inspectable without
  exposing private story data outside the app.

### Story Cards and context

- Plot Essentials and active Story Cards now form one coherent lore message separated by the
  `World Lore:` marker.
- Pinned-but-untriggered cards are considered first and dropped before contextually triggered
  cards when space is tight. Triggered cards are ordered so the most recently relevant entry is
  closest to the recent story.
- Per-model **Story Card Wrapping Style** can leave entries unwrapped or consistently surround
  them with braces or brackets. Existing outer wrappers are normalized instead of doubled.
- AI Dungeon's default lowercase card types import as their familiar sentence-case names while
  custom type spelling remains untouched.

### Story workflow and diagnostics

- **Clone Story** can copy story details, Story Cards, the initial block, or the complete adventure.
  Its defaults provide a lightweight reusable-scenario workflow without introducing a separate
  scenario object.
- The one-shot **Prompt** window can override Response Length for that request. The override is on
  by default, avoiding repeated model-settings changes for open-ended utility prompts.
- The status bar reports processed prompt tokens, full request duration, and model generation
  duration. Prompt estimates also calibrate against Ollama's returned token counts during a
  session.
- Saving AI Instructions, Plot Essentials, or Author's Note no longer steals the user's intended
  story selection.

### Packaging and maintenance

- The self-contained Windows release remains on Microsoft OpenJDK and JavaFX 25 LTS. Dependabot
  may propose 25.x fixes, but JavaFX 26 major upgrades are intentionally deferred.
- GitHub build actions have been updated to their Node 24-compatible major versions.

## 0.2.0

Version 0.2.0 is the first formally tracked LlamaQuill release. Existing unversioned databases
are treated as 0.1.0 and migrated automatically after a backup is created.

### Story generation

- Ollama chat templates are now the single generation path. The old raw ChatML
  `/api/generate` path and **Use Ollama Templates** option have been removed.
- Responses stream visibly into the story while they are generated. Failed and empty generations
  do not leave partial assistant prose behind.
- Consecutive model continuations remain seamless, including continuations that begin in the
  middle of a sentence.
- Long assistant-only runs receive a short generation-only user cue before their newest one or
  two blocks. This keeps the final assistant continuation small without adding a visible or
  persisted story action.
- When joined model responses omit boundary whitespace, LlamaQuill adds a conservative separator
  while preserving existing whitespace and obvious punctuation or hyphenated joins.
- Prompt budgeting now reserves response space and deliberately prioritizes the system
  instruction, Plot Essentials, recent story, and contextually triggered Story Cards. Ollama no
  longer gets first chance to trim an arbitrary beginning of the prompt.
- Retry keeps the existing response visible until replacement text arrives. At a user-authored
  story head, Retry behaves like Continue.
- Story scrolling, editing, long passages, and image blocks are substantially more stable during
  live generation and ordinary navigation.

### Ollama models and controls

- Models are discovered and validated through Ollama, with model-specific context settings.
- Response Length, Temperature, Top K, Top P, Min P, Typical P, Presence Penalty, Frequency
  Penalty, Repetition Penalty, and Repeat Last N each have independent enable switches. Disabled
  values allow model defaults to apply; valid zero values can still be sent intentionally.
- Model context selection is validated against discovered model metadata, and the protected
  recent-story allocation scales with the selected context window.
- A global 5–30 minute keep-alive setting can keep the model loaded between slower-paced turns.
- Ollama failures now produce concise UI errors with expandable diagnostics instead of relying on
  full response JSON in the terminal.
- Thinking remains disabled for 0.2.0 because it consumes the narrative response budget and has
  repeatedly harmed continuation behavior in tested reasoning models.

### Story Cards

- Story Card creation and editing now use one unified workflow with **Generate Entry with AI**.
- Cards support organizational types, a custom type, player-only Notes, and Pinning.
- AI entry generation uses the current story context and can include extra generation-only
  context to activate related cards.
- Three protected built-in command presets—Basic List, Basic Prose, and Condensed—are included.
  Custom presets can be saved globally and reused across stories.
- Generated entries are plain text rather than model-produced JSON. Empty triggers default to the
  card title when the card is saved.
- Optional generation logging preserves prior entries with timestamps in Notes.
- The unreliable unattended AutoCards candidate-selection system and its per-story/global tuning
  controls have been retired. Manual AI-assisted card generation remains.

### Data safety and diagnostics

- The database now lives at `%LOCALAPPDATA%\LlamaQuill\llamaquill.db` by default instead of
  depending on the process working directory.
- A legacy `.\data\llamaquill.db` is copied—not moved—into the stable location when needed.
- Schema migrations are versioned, backed up first, and applied transactionally.
- **Back Up Database** creates a consistent manual backup. **Check Database** reports the active
  file, schema, SQLite integrity, foreign-key problems, and broken image references.
- Story operations, imports, Retry replacement, and linked image deletion use stricter
  story/session boundaries to avoid stale background work changing the wrong story.
- Pending story/detail edits are flushed on story switches and app shutdown.

### Imports and images

- AI Dungeon adventure backup imports support current chunked action exports, legacy card shapes,
  custom instructions, and **See** image downloads.
- Adventure imports are transactional: a failed block, card, or required image does not leave a
  partial story.
- AI Dungeon Story Card imports preserve type/notes, handle optional titles, avoid exact
  duplicates, and can merge or replace.
- The experimental Chroma2 Kaleidoscope ComfyUI workflow is selectable alongside Chroma HD.
- Large-story image blocks no longer overlap neighboring prose while scrolling.

### Packaging

- The Windows download is a complete self-contained app image with Microsoft OpenJDK 25.0.4 LTS.
  No separate Java installation is required.
- Release downloads include a SHA-256 checksum. The entire extracted `LlamaQuill` folder must be
  kept together; the `.exe` is not a standalone file.
- LlamaQuill is released under the MIT License. Third-party models, applications, services, and
  assets retain their own terms.

## 0.1.0

Historical baseline covering all LlamaQuill work before internal version tracking began.
