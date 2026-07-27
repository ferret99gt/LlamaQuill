CREATE TABLE app_settings (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    ollama_url TEXT NOT NULL,
    comfyui_url TEXT NOT NULL DEFAULT 'http://localhost:8000',
    selected_model TEXT NOT NULL,
    use_ollama_templates INTEGER NOT NULL DEFAULT 0 CHECK (use_ollama_templates IN (0,1)),
    context_limit INTEGER NOT NULL,
    response_length INTEGER NOT NULL,
    min_story_window INTEGER NOT NULL,
    story_card_lookback INTEGER NOT NULL,
    an_placement INTEGER NOT NULL,
    comfy_workflow TEXT NOT NULL DEFAULT 'ChromaHD',
    comfy_width INTEGER NOT NULL DEFAULT 720,
    comfy_height INTEGER NOT NULL DEFAULT 720,
    comfy_batch_size INTEGER NOT NULL DEFAULT 4
);

CREATE TABLE schema_migrations (
    schema_version INTEGER PRIMARY KEY,
    app_version TEXT NOT NULL,
    source_version TEXT NOT NULL,
    applied_at TEXT NOT NULL
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

INSERT INTO app_settings VALUES (
    1, 'http://schema-two-ollama:11434', 'http://schema-two-comfy:8000', 'schema-two-model', 1,
    24576, 180, 12000, 16, 5, 'SchemaTwoWorkflow', 1280, 960, 3
);

INSERT INTO schema_migrations VALUES (
    2, '0.2.0', '0.1.0', '2026-07-26T00:00:00Z'
);

INSERT INTO model_settings VALUES (
    'schema-two-model', 1, 0.55, 0, 0.9, 0.02, 0.0, 0.0, 1.1
);

PRAGMA user_version = 2;
