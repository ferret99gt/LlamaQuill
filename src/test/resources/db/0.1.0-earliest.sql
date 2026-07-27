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
    role TEXT NOT NULL CHECK (role IN ('assistant','user')),
    text TEXT NOT NULL,
    created_at TEXT NOT NULL,
    position INTEGER NOT NULL,
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

CREATE TABLE generation_settings (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    context_limit INTEGER NOT NULL,
    response_length INTEGER NOT NULL,
    temperature REAL NOT NULL,
    top_k INTEGER NOT NULL,
    top_p REAL NOT NULL,
    presence_penalty REAL NOT NULL,
    frequency_penalty REAL NOT NULL,
    min_story_window INTEGER NOT NULL,
    story_card_lookback INTEGER NOT NULL,
    an_placement INTEGER NOT NULL
);

INSERT INTO stories VALUES (
    'story-1', 'Legacy Story', 'System', 'Memory', 'Note',
    '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z'
);
INSERT INTO blocks VALUES (
    'block-2', 'story-1', 'assistant', 'Second by timestamp',
    '2026-01-01T00:00:02Z', 1
);
INSERT INTO blocks VALUES (
    'block-1', 'story-1', 'user', 'First by timestamp',
    '2026-01-01T00:00:01Z', 1
);
INSERT INTO story_cards VALUES (
    'card-1', 'story-1', 'Legacy Card', 'legacy', 'Preserve this', 1
);
INSERT INTO generation_settings VALUES (
    1, 8192, 200, 0.7, 100, 0.9, 0.2, 0.1, 7000, 5, 2
);
