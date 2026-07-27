package com.llamaquill.db;

import com.llamaquill.AppVersion;
import com.llamaquill.util.Timestamps;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public final class DatabaseMigrator
{
    private static final int UNVERSIONED = 0;
    private static final int VERSION_0_1_0 = 1;
    private static final int VERSION_0_2_0 = AppVersion.DATABASE_SCHEMA;
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private DatabaseMigrator()
    {
    }

    static MigrationReport migrate(Connection connection, AppPaths paths) throws SQLException
    {
        int declaredVersion = readUserVersion(connection);
        if (declaredVersion > VERSION_0_2_0)
        {
            throw new SQLException("Database schema " + declaredVersion
                    + " is newer than this LlamaQuill version supports (" + VERSION_0_2_0 + ").");
        }

        boolean fresh = !hasApplicationTables(connection);
        if (fresh)
        {
            runMigrationTransaction(connection, () ->
            {
                createCurrentSchema(connection);
                recordMigration(connection, VERSION_0_2_0, "fresh");
                setUserVersion(connection, VERSION_0_2_0);
                validateDatabase(connection);
            });
            enableWal(connection);
            return new MigrationReport(UNVERSIONED, VERSION_0_2_0, Optional.empty(), true);
        }

        int sourceVersion = declaredVersion == UNVERSIONED ? VERSION_0_1_0 : declaredVersion;
        if (sourceVersion == VERSION_0_2_0)
        {
            validateDatabase(connection);
            enableWal(connection);
            return new MigrationReport(sourceVersion, VERSION_0_2_0, Optional.empty(), false);
        }
        if (sourceVersion != VERSION_0_1_0)
        {
            throw new SQLException("Unsupported database migration source: " + sourceVersion);
        }

        checkpoint(connection);
        Path backup = createBackup(paths);
        runMigrationTransaction(connection, () ->
        {
            migrateFromVersionOne(connection);
            recordMigration(connection, VERSION_0_2_0, AppVersion.FIRST_MIGRATION_SOURCE);
            setUserVersion(connection, VERSION_0_2_0);
            validateDatabase(connection);
        });
        enableWal(connection);
        return new MigrationReport(sourceVersion, VERSION_0_2_0, Optional.of(backup), false);
    }

    private static void migrateFromVersionOne(Connection connection) throws SQLException
    {
        createCoreTables(connection);
        migrateAppAndModelSettings(connection);
        createImagesTable(connection);
        rebuildBlocks(connection);
        rebuildAutoCardsTables(connection);
        createIndexesAndMetadata(connection);
        dropLegacyGenerationSettings(connection);
    }

    private static void createCurrentSchema(Connection connection) throws SQLException
    {
        createCoreTables(connection);
        createImagesTable(connection);
        createAppSettingsTable(connection);
        createModelSettingsTable(connection);
        createAutoCardsTables(connection);
        createIndexesAndMetadata(connection);
    }

    private static void createCoreTables(Connection connection) throws SQLException
    {
        execute(connection, """
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
        execute(connection, blocksTableSql("blocks"));
        execute(connection, """
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
    }

    private static void createImagesTable(Connection connection) throws SQLException
    {
        execute(connection, """
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
    }

    private static void createAppSettingsTable(Connection connection) throws SQLException
    {
        execute(connection, """
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
                    comfy_workflow TEXT NOT NULL DEFAULT 'ChromaHD',
                    comfy_width INTEGER NOT NULL DEFAULT 720,
                    comfy_height INTEGER NOT NULL DEFAULT 720,
                    comfy_batch_size INTEGER NOT NULL DEFAULT 4
                )
                """);
    }

    private static void createModelSettingsTable(Connection connection) throws SQLException
    {
        execute(connection, """
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
    }

    private static void createAutoCardsTables(Connection connection) throws SQLException
    {
        execute(connection, appAutoCardsTableSql("app_auto_cards"));
        execute(connection, storyAutoCardsTableSql("story_auto_cards"));
        execute(connection, modelAutoCardsTableSql("model_auto_cards"));
    }

    private static void createIndexesAndMetadata(Connection connection) throws SQLException
    {
        execute(connection, """
                CREATE INDEX IF NOT EXISTS idx_blocks_story_position
                ON blocks(story_id, position)
                """);
        execute(connection, """
                CREATE INDEX IF NOT EXISTS idx_cards_story_pinned
                ON story_cards(story_id, pinned)
                """);
        execute(connection, """
                CREATE INDEX IF NOT EXISTS idx_images_story_created
                ON images(story_id, created_at)
                """);
        execute(connection, """
                CREATE TABLE IF NOT EXISTS schema_migrations (
                    schema_version INTEGER PRIMARY KEY,
                    app_version TEXT NOT NULL,
                    source_version TEXT NOT NULL,
                    applied_at TEXT NOT NULL
                )
                """);
    }

    private static void migrateAppAndModelSettings(Connection connection) throws SQLException
    {
        boolean hadAppSettings = tableExists(connection, "app_settings");
        boolean hadGenerationSettings = tableExists(connection, "generation_settings");

        createAppSettingsTable(connection);
        if (hadAppSettings)
        {
            addColumnIfMissing(connection, "app_settings", "comfyui_url",
                    "TEXT NOT NULL DEFAULT 'http://localhost:8000'");
            addColumnIfMissing(connection, "app_settings", "use_ollama_templates",
                    "INTEGER NOT NULL DEFAULT 0 CHECK (use_ollama_templates IN (0,1))");
            addColumnIfMissing(connection, "app_settings", "comfy_workflow",
                    "TEXT NOT NULL DEFAULT 'ChromaHD'");
            addColumnIfMissing(connection, "app_settings", "comfy_width",
                    "INTEGER NOT NULL DEFAULT 720");
            addColumnIfMissing(connection, "app_settings", "comfy_height",
                    "INTEGER NOT NULL DEFAULT 720");
            addColumnIfMissing(connection, "app_settings", "comfy_batch_size",
                    "INTEGER NOT NULL DEFAULT 4");
        }
        else if (hadGenerationSettings)
        {
            execute(connection, """
                    INSERT INTO app_settings (
                        id, ollama_url, comfyui_url, selected_model, use_ollama_templates,
                        context_limit, response_length, min_story_window, story_card_lookback, an_placement,
                        comfy_workflow, comfy_width, comfy_height, comfy_batch_size
                    )
                    SELECT id, 'http://localhost:11434', 'http://localhost:8000',
                           'hf.co/LatitudeGames/Muse-12B-GGUF:BF16', 0,
                           context_limit, response_length, min_story_window, story_card_lookback, an_placement,
                           'ChromaHD', 720, 720, 4
                    FROM generation_settings
                    WHERE id = 1
                    """);
        }

        boolean hadModelSettings = tableExists(connection, "model_settings");
        createModelSettingsTable(connection);
        if (!hadModelSettings && hadGenerationSettings)
        {
            Set<String> columns = columns(connection, "generation_settings");
            String minP = columns.contains("min_p") ? "min_p" : "0.025";
            String repetitionPenalty = columns.contains("repetition_penalty") ? "repetition_penalty" : "1.05";
            execute(connection, """
                    INSERT INTO model_settings (
                        model_name, active, temperature, top_k, top_p, min_p,
                        presence_penalty, frequency_penalty, repetition_penalty
                    )
                    SELECT 'hf.co/LatitudeGames/Muse-12B-GGUF:BF16', 1,
                           temperature, top_k, top_p, %s,
                           presence_penalty, frequency_penalty, %s
                    FROM generation_settings
                    WHERE id = 1
                    """.formatted(minP, repetitionPenalty));
        }
    }

    private static void rebuildBlocks(Connection connection) throws SQLException
    {
        if (!tableExists(connection, "blocks"))
        {
            execute(connection, blocksTableSql("blocks"));
            return;
        }

        execute(connection, "DROP TABLE IF EXISTS blocks_v2");
        execute(connection, blocksTableSql("blocks_v2"));
        execute(connection, """
                INSERT INTO blocks_v2 (id, story_id, role, text, created_at, position)
                SELECT id, story_id,
                       CASE WHEN role IN ('assistant','user','image') THEN role ELSE 'assistant' END,
                       text, created_at,
                       ROW_NUMBER() OVER (PARTITION BY story_id ORDER BY position, created_at, id)
                FROM blocks
                """);
        execute(connection, "DROP TABLE blocks");
        execute(connection, "ALTER TABLE blocks_v2 RENAME TO blocks");
    }

    private static void rebuildAutoCardsTables(Connection connection) throws SQLException
    {
        rebuildAppAutoCards(connection);
        rebuildStoryAutoCards(connection);
        rebuildModelAutoCards(connection);
    }

    private static void rebuildAppAutoCards(Connection connection) throws SQLException
    {
        boolean existed = tableExists(connection, "app_auto_cards");
        Set<String> oldColumns = existed ? columns(connection, "app_auto_cards") : Set.of();
        execute(connection, "DROP TABLE IF EXISTS app_auto_cards_v2");
        execute(connection, appAutoCardsTableSql("app_auto_cards_v2"));
        if (existed)
        {
            execute(connection, """
                    INSERT INTO app_auto_cards_v2 (
                        id, cooldown_turns, max_cards_per_run, candidate_window, card_length_limit,
                        summarize_instead_of_trim, use_bulleted_lists, candidate_selection_mode, context_mode
                    )
                    SELECT %s, %s, %s, %s, %s, %s, %s, %s, %s
                    FROM app_auto_cards
                    """.formatted(
                    value(oldColumns, "id", "1"),
                    value(oldColumns, "cooldown_turns", "8"),
                    value(oldColumns, "max_cards_per_run", "3"),
                    value(oldColumns, "candidate_window", "12"),
                    value(oldColumns, "card_length_limit", "3200"),
                    value(oldColumns, "summarize_instead_of_trim", "1"),
                    value(oldColumns, "use_bulleted_lists", "0"),
                    value(oldColumns, "candidate_selection_mode", "'Proper Noun Heuristics'"),
                    value(oldColumns, "context_mode", "'Full Story Context'")));
            execute(connection, "DROP TABLE app_auto_cards");
        }
        execute(connection, "ALTER TABLE app_auto_cards_v2 RENAME TO app_auto_cards");
    }

    private static void rebuildStoryAutoCards(Connection connection) throws SQLException
    {
        boolean existed = tableExists(connection, "story_auto_cards");
        Set<String> oldColumns = existed ? columns(connection, "story_auto_cards") : Set.of();
        execute(connection, "DROP TABLE IF EXISTS story_auto_cards_v2");
        execute(connection, storyAutoCardsTableSql("story_auto_cards_v2"));
        if (existed)
        {
            execute(connection, """
                    INSERT INTO story_auto_cards_v2 (
                        story_id, enabled, update_existing, create_new, pin_new, preview_first
                    )
                    SELECT story_id, %s, %s, %s, %s, %s
                    FROM story_auto_cards
                    """.formatted(
                    value(oldColumns, "enabled", "0"),
                    value(oldColumns, "update_existing", "1"),
                    value(oldColumns, "create_new", "1"),
                    value(oldColumns, "pin_new", "0"),
                    value(oldColumns, "preview_first", "0")));
            execute(connection, "DROP TABLE story_auto_cards");
        }
        execute(connection, "ALTER TABLE story_auto_cards_v2 RENAME TO story_auto_cards");
    }

    private static void rebuildModelAutoCards(Connection connection) throws SQLException
    {
        boolean existed = tableExists(connection, "model_auto_cards");
        Set<String> oldColumns = existed ? columns(connection, "model_auto_cards") : Set.of();
        execute(connection, "DROP TABLE IF EXISTS model_auto_cards_v2");
        execute(connection, modelAutoCardsTableSql("model_auto_cards_v2"));
        if (existed)
        {
            execute(connection, """
                    INSERT INTO model_auto_cards_v2 (
                        model_name, create_prompt, update_prompt, summarize_prompt,
                        max_tokens_create, max_tokens_update, max_tokens_summarize
                    )
                    SELECT model_name, %s, %s, %s, %s, %s, %s
                    FROM model_auto_cards
                    WHERE model_name IN (SELECT model_name FROM model_settings)
                    """.formatted(
                    value(oldColumns, "create_prompt", "''"),
                    value(oldColumns, "update_prompt", "''"),
                    value(oldColumns, "summarize_prompt", "''"),
                    value(oldColumns, "max_tokens_create", "512"),
                    value(oldColumns, "max_tokens_update", "512"),
                    value(oldColumns, "max_tokens_summarize", "512")));
            execute(connection, "DROP TABLE model_auto_cards");
        }
        execute(connection, "ALTER TABLE model_auto_cards_v2 RENAME TO model_auto_cards");
    }

    private static String blocksTableSql(String table)
    {
        return """
                CREATE TABLE IF NOT EXISTS %s (
                    id TEXT PRIMARY KEY,
                    story_id TEXT NOT NULL,
                    role TEXT NOT NULL CHECK (role IN ('assistant','user','image')),
                    text TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE,
                    UNIQUE (story_id, position)
                )
                """.formatted(table);
    }

    private static String appAutoCardsTableSql(String table)
    {
        return """
                CREATE TABLE IF NOT EXISTS %s (
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
                """.formatted(table);
    }

    private static String storyAutoCardsTableSql(String table)
    {
        return """
                CREATE TABLE IF NOT EXISTS %s (
                    story_id TEXT PRIMARY KEY,
                    enabled INTEGER NOT NULL CHECK (enabled IN (0,1)),
                    update_existing INTEGER NOT NULL CHECK (update_existing IN (0,1)),
                    create_new INTEGER NOT NULL CHECK (create_new IN (0,1)),
                    pin_new INTEGER NOT NULL CHECK (pin_new IN (0,1)),
                    preview_first INTEGER NOT NULL CHECK (preview_first IN (0,1)),
                    FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE
                )
                """.formatted(table);
    }

    private static String modelAutoCardsTableSql(String table)
    {
        return """
                CREATE TABLE IF NOT EXISTS %s (
                    model_name TEXT PRIMARY KEY,
                    create_prompt TEXT NOT NULL,
                    update_prompt TEXT NOT NULL,
                    summarize_prompt TEXT NOT NULL,
                    max_tokens_create INTEGER NOT NULL,
                    max_tokens_update INTEGER NOT NULL,
                    max_tokens_summarize INTEGER NOT NULL,
                    FOREIGN KEY (model_name) REFERENCES model_settings(model_name) ON DELETE CASCADE
                )
                """.formatted(table);
    }

    private static void dropLegacyGenerationSettings(Connection connection) throws SQLException
    {
        execute(connection, "DROP TABLE IF EXISTS generation_settings");
    }

    private static void addColumnIfMissing(Connection connection, String table, String column, String definition)
            throws SQLException
    {
        if (!columns(connection, table).contains(column))
        {
            execute(connection, "ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private static String value(Set<String> columns, String column, String defaultSql)
    {
        return columns.contains(column) ? column : defaultSql;
    }

    private static Set<String> columns(Connection connection, String table) throws SQLException
    {
        Set<String> columns = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA table_info(" + table + ")"))
        {
            while (result.next())
            {
                columns.add(result.getString("name"));
            }
        }
        return columns;
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException
    {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1
                FROM sqlite_master
                WHERE type = 'table' AND name = ?
                """))
        {
            statement.setString(1, table);
            try (ResultSet result = statement.executeQuery())
            {
                return result.next();
            }
        }
    }

    private static boolean hasApplicationTables(Connection connection) throws SQLException
    {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT 1
                     FROM sqlite_master
                     WHERE type = 'table'
                       AND name NOT LIKE 'sqlite_%'
                     LIMIT 1
                     """))
        {
            return result.next();
        }
    }

    private static int readUserVersion(Connection connection) throws SQLException
    {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA user_version"))
        {
            return result.next() ? result.getInt(1) : UNVERSIONED;
        }
    }

    private static void setUserVersion(Connection connection, int version) throws SQLException
    {
        execute(connection, "PRAGMA user_version = " + version);
    }

    private static void recordMigration(Connection connection, int schemaVersion, String sourceVersion)
            throws SQLException
    {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO schema_migrations (schema_version, app_version, source_version, applied_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(schema_version) DO UPDATE SET
                    app_version = excluded.app_version,
                    source_version = excluded.source_version,
                    applied_at = excluded.applied_at
                """))
        {
            statement.setInt(1, schemaVersion);
            statement.setString(2, AppVersion.CURRENT);
            statement.setString(3, sourceVersion);
            statement.setString(4, Timestamps.now());
            statement.executeUpdate();
        }
    }

    private static void runMigrationTransaction(Connection connection, SqlMigration migration) throws SQLException
    {
        boolean oldAutoCommit = connection.getAutoCommit();
        execute(connection, "PRAGMA foreign_keys = OFF");
        try
        {
            connection.setAutoCommit(false);
            migration.run();
            connection.commit();
        }
        catch (SQLException | RuntimeException | Error e)
        {
            try
            {
                connection.rollback();
            }
            catch (SQLException rollbackFailure)
            {
                e.addSuppressed(rollbackFailure);
            }
            throw e;
        }
        finally
        {
            connection.setAutoCommit(oldAutoCommit);
            execute(connection, "PRAGMA foreign_keys = ON");
        }
    }

    private static void validateDatabase(Connection connection) throws SQLException
    {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA foreign_key_check"))
        {
            if (result.next())
            {
                throw new SQLException("Foreign-key validation failed for table " + result.getString(1)
                        + ", row " + result.getString(2) + ".");
            }
        }

        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA integrity_check"))
        {
            if (!result.next() || !"ok".equalsIgnoreCase(result.getString(1)))
            {
                throw new SQLException("SQLite integrity check failed.");
            }
        }
    }

    private static void checkpoint(Connection connection) throws SQLException
    {
        execute(connection, "PRAGMA wal_checkpoint(FULL)");
    }

    private static void enableWal(Connection connection) throws SQLException
    {
        execute(connection, "PRAGMA journal_mode = WAL");
    }

    private static Path createBackup(AppPaths paths) throws SQLException
    {
        String timestamp = BACKUP_TIMESTAMP.format(LocalDateTime.now(ZoneOffset.UTC));
        Path backup = paths.backupDirectory().resolve(
                "llamaquill-pre-" + AppVersion.CURRENT + "-" + timestamp + ".db");
        try
        {
            Files.copy(paths.databaseFile(), backup, StandardCopyOption.COPY_ATTRIBUTES);
            return backup;
        }
        catch (IOException e)
        {
            throw new SQLException("Failed to create pre-migration backup: " + backup, e);
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException
    {
        try (Statement statement = connection.createStatement())
        {
            statement.execute(sql);
        }
    }

    @FunctionalInterface
    private interface SqlMigration
    {
        void run() throws SQLException;
    }

    public record MigrationReport(int sourceSchema, int targetSchema, Optional<Path> backup, boolean freshDatabase)
    {
        public MigrationReport
        {
            backup = backup == null ? Optional.empty() : backup;
        }
    }
}
