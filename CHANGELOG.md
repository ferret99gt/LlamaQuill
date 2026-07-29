# Changelog

This changelog focuses on what changes for people using LlamaQuill. The Git history contains the
implementation-level record.

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
