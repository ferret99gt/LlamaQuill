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
                        role TEXT NOT NULL CHECK (role IN ('assistant','user')),
                        text TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        position INTEGER NOT NULL,
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
                    CREATE TABLE IF NOT EXISTS generation_settings (
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
