package com.llamaquill.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database
{
    private static final String DATA_DIR = "data";
    private static final String DB_FILE = "llamaquill.db";

    private Database()
    {
    }

    public static Connection open() throws SQLException
    {
        ensureDataDir();
        var url = "jdbc:sqlite:./" + DATA_DIR + "/" + DB_FILE;
        var connection = DriverManager.getConnection(url);
        try (Statement stmt = connection.createStatement())
        {
            stmt.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    public static void initialize(Connection connection) throws SQLException
    {
        try (Statement stmt = connection.createStatement())
        {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS stories (
                        id TEXT PRIMARY KEY,
                        title TEXT NOT NULL,
                        system_prompt TEXT NOT NULL,
                        plot_essentials TEXT NOT NULL,
                        author_note TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS blocks (
                        id TEXT PRIMARY KEY,
                        story_id TEXT NOT NULL,
                        role TEXT NOT NULL CHECK (role IN ('assistant','user','image')),
                        text TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        position INTEGER NOT NULL,
                        FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS images (
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
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS story_cards (
                        id TEXT PRIMARY KEY,
                        story_id TEXT NOT NULL,
                        title TEXT NOT NULL,
                        triggers TEXT NOT NULL,
                        content TEXT NOT NULL,
                        pinned INTEGER NOT NULL CHECK (pinned IN (0,1)),
                        FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS app_settings (
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
                        comfy_workflow TEXT NOT NULL DEFAULT 'LlamaQuillChromaHD',
                        comfy_width INTEGER NOT NULL DEFAULT 720,
                        comfy_height INTEGER NOT NULL DEFAULT 720,
                        comfy_batch_size INTEGER NOT NULL DEFAULT 4
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS model_settings (
                        model_name TEXT PRIMARY KEY,
                        active INTEGER NOT NULL CHECK (active IN (0,1)),
                        temperature REAL NOT NULL,
                        top_k INTEGER NOT NULL,
                        top_p REAL NOT NULL,
                        min_p REAL NOT NULL,
                        presence_penalty REAL NOT NULL,
                        frequency_penalty REAL NOT NULL,
                        repetition_penalty REAL NOT NULL
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS app_auto_cards (
                        id INTEGER PRIMARY KEY CHECK (id = 1),
                        cooldown_turns INTEGER NOT NULL,
                        max_cards_per_run INTEGER NOT NULL,
                        candidate_window INTEGER NOT NULL,
                        card_length_limit INTEGER NOT NULL,
                        summarize_instead_of_trim INTEGER NOT NULL CHECK (summarize_instead_of_trim IN (0,1)),
                        use_bulleted_lists INTEGER NOT NULL CHECK (use_bulleted_lists IN (0,1)),
                        candidate_selection_mode TEXT NOT NULL,
                        context_mode TEXT NOT NULL
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS story_auto_cards (
                        story_id TEXT PRIMARY KEY,
                        enabled INTEGER NOT NULL CHECK (enabled IN (0,1)),
                        update_existing INTEGER NOT NULL CHECK (update_existing IN (0,1)),
                        create_new INTEGER NOT NULL CHECK (create_new IN (0,1)),
                        pin_new INTEGER NOT NULL CHECK (pin_new IN (0,1)),
                        preview_first INTEGER NOT NULL CHECK (preview_first IN (0,1)),
                        FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS model_auto_cards (
                        model_name TEXT PRIMARY KEY,
                        create_prompt TEXT NOT NULL,
                        update_prompt TEXT NOT NULL,
                        summarize_prompt TEXT NOT NULL,
                        max_tokens_create INTEGER NOT NULL,
                        max_tokens_update INTEGER NOT NULL,
                        max_tokens_summarize INTEGER NOT NULL,
                        FOREIGN KEY (model_name) REFERENCES model_settings(model_name) ON DELETE CASCADE
                    )
                    """);

            stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_blocks_story_position
                    ON blocks(story_id, position)
                    """);

            stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_cards_story_pinned
                    ON story_cards(story_id, pinned)
                    """);

            stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_images_story_created
                    ON images(story_id, created_at)
                    """);

        }
    }

    private static void ensureDataDir()
    {
        try
        {
            Files.createDirectories(Path.of(DATA_DIR));
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Failed to create data directory", e);
        }
    }
}
