CREATE TABLE stories (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    system_prompt TEXT NOT NULL,
    plot_essentials TEXT NOT NULL,
    author_note TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE blocks (
    id TEXT PRIMARY KEY,
    story_id TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('assistant','user','image')),
    text TEXT NOT NULL,
    created_at TEXT NOT NULL,
    position INTEGER NOT NULL,
    FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE
);

CREATE TABLE images (
    id TEXT PRIMARY KEY,
    story_id TEXT NOT NULL,
    prompt TEXT NOT NULL,
    mime_type TEXT NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    workflow_json TEXT NOT NULL,
    image_bytes BLOB NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE
);

CREATE TABLE story_cards (
    id TEXT PRIMARY KEY,
    story_id TEXT NOT NULL,
    title TEXT NOT NULL,
    triggers TEXT NOT NULL,
    content TEXT NOT NULL,
    pinned INTEGER NOT NULL CHECK (pinned IN (0,1)),
    FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE
);

CREATE TABLE app_settings (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    ollama_url TEXT NOT NULL,
    comfyui_url TEXT NOT NULL DEFAULT 'http://localhost:8000',
    selected_model TEXT NOT NULL,
    context_limit INTEGER NOT NULL,
    response_length INTEGER NOT NULL,
    min_story_window INTEGER NOT NULL,
    story_card_lookback INTEGER NOT NULL,
    an_placement INTEGER NOT NULL,
    comfy_workflow TEXT NOT NULL DEFAULT 'LlamaQuillChromaHD',
    comfy_width INTEGER NOT NULL DEFAULT 720,
    comfy_height INTEGER NOT NULL DEFAULT 720,
    comfy_batch_size INTEGER NOT NULL DEFAULT 4
);

CREATE TABLE model_settings (
    model_name TEXT PRIMARY KEY,
    active INTEGER NOT NULL CHECK (active IN (0,1)),
    temperature REAL NOT NULL,
    top_k INTEGER NOT NULL,
    top_p REAL NOT NULL,
    min_p REAL NOT NULL,
    presence_penalty REAL NOT NULL,
    frequency_penalty REAL NOT NULL,
    repetition_penalty REAL NOT NULL
);

CREATE TABLE app_auto_cards (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    run_mode TEXT NOT NULL,
    min_gap_seconds INTEGER NOT NULL,
    default_enabled INTEGER NOT NULL CHECK (default_enabled IN (0,1)),
    cooldown_turns INTEGER NOT NULL,
    max_cards_per_run INTEGER NOT NULL,
    candidate_window INTEGER NOT NULL,
    card_length_limit INTEGER NOT NULL,
    summarize_instead_of_trim INTEGER NOT NULL CHECK (summarize_instead_of_trim IN (0,1)),
    verbosity TEXT NOT NULL,
    logging_level TEXT NOT NULL
);

CREATE TABLE story_auto_cards (
    story_id TEXT PRIMARY KEY,
    enabled INTEGER NOT NULL CHECK (enabled IN (0,1)),
    update_existing INTEGER NOT NULL CHECK (update_existing IN (0,1)),
    create_new INTEGER NOT NULL CHECK (create_new IN (0,1)),
    pin_new INTEGER NOT NULL CHECK (pin_new IN (0,1)),
    FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE
);

CREATE TABLE model_auto_cards (
    model_name TEXT PRIMARY KEY,
    create_prompt TEXT NOT NULL,
    update_prompt TEXT NOT NULL,
    summarize_prompt TEXT NOT NULL,
    max_tokens_create INTEGER NOT NULL,
    max_tokens_update INTEGER NOT NULL,
    max_tokens_summarize INTEGER NOT NULL,
    temperature_override REAL,
    FOREIGN KEY (model_name) REFERENCES model_settings(model_name) ON DELETE CASCADE
);

INSERT INTO stories VALUES (
    'story-late', 'Late Legacy Story', 'System', 'Memory', 'Note',
    '2026-04-01T00:00:00Z', '2026-04-01T00:00:00Z'
);
INSERT INTO app_settings VALUES (
    1, 'http://legacy-ollama:11434', 'http://legacy-comfy:8000', 'legacy-model',
    16384, 300, 9000, 8, 4, 'ChromaHD', 1024, 768, 2
);
INSERT INTO model_settings VALUES (
    'legacy-model', 1, 0.65, 80, 0.92, 0.03, 0.2, 0.05, 1.1
);
INSERT INTO app_auto_cards VALUES (
    1, 'automatic', 10, 1, 6, 2, 10, 2400, 1, 'verbose', 'info'
);
INSERT INTO story_auto_cards VALUES (
    'story-late', 1, 1, 1, 1
);
INSERT INTO model_auto_cards VALUES (
    'legacy-model', 'create', 'update', 'summarize', 256, 384, 128, 0.5
);
