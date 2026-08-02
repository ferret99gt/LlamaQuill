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
    private static final int INITIAL_0_2_0_SCHEMA = 2;
    private static final int OLLAMA_HARDENING_SCHEMA = 3;
    private static final int STORY_CARD_SCHEMA = 4;
    private static final int PROMPT_LAYOUT_SCHEMA = 5;
    private static final int STORY_CARD_PRESET_SELECTION_SCHEMA = 6;
    private static final int CURRENT_SCHEMA = AppVersion.DATABASE_SCHEMA;
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private DatabaseMigrator()
    {
    }

    static MigrationReport migrate(Connection connection, AppPaths paths) throws SQLException
    {
        int declaredVersion = readUserVersion(connection);
        if (declaredVersion > CURRENT_SCHEMA)
        {
            throw new SQLException("Database schema " + declaredVersion
                    + " is newer than this LlamaQuill version supports (" + CURRENT_SCHEMA + ").");
        }

        boolean fresh = !hasApplicationTables(connection);
        if (fresh)
        {
            runMigrationTransaction(connection, () ->
            {
                createCurrentSchema(connection);
                recordMigration(connection, CURRENT_SCHEMA, "fresh");
                setUserVersion(connection, CURRENT_SCHEMA);
                validateDatabase(connection);
            });
            enableWal(connection);
            return new MigrationReport(UNVERSIONED, CURRENT_SCHEMA, Optional.empty(), true);
        }

        int sourceVersion = declaredVersion == UNVERSIONED ? VERSION_0_1_0 : declaredVersion;
        if (sourceVersion == CURRENT_SCHEMA)
        {
            if (needsCurrentSchemaNormalization(connection))
            {
                checkpoint(connection);
                Path backup = createBackup(paths);
                runMigrationTransaction(connection, () ->
                {
                    normalizeSchemaThreeFoundation(connection);
                    migrateFromVersionThree(connection);
                    migrateFromVersionFour(connection);
                    migrateFromVersionFive(connection);
                    migrateFromVersionSix(connection);
                    recordMigration(connection, CURRENT_SCHEMA,
                            AppVersion.CURRENT + "-schema-" + CURRENT_SCHEMA + "-provisional");
                    setUserVersion(connection, CURRENT_SCHEMA);
                    validateDatabase(connection);
                });
                enableWal(connection);
                return new MigrationReport(sourceVersion, CURRENT_SCHEMA, Optional.of(backup), false);
            }
            validateDatabase(connection);
            enableWal(connection);
            return new MigrationReport(sourceVersion, CURRENT_SCHEMA, Optional.empty(), false);
        }
        if (sourceVersion != VERSION_0_1_0 && sourceVersion != INITIAL_0_2_0_SCHEMA
                && sourceVersion != OLLAMA_HARDENING_SCHEMA && sourceVersion != STORY_CARD_SCHEMA
                && sourceVersion != PROMPT_LAYOUT_SCHEMA
                && sourceVersion != STORY_CARD_PRESET_SELECTION_SCHEMA)
        {
            throw new SQLException("Unsupported database migration source: " + sourceVersion);
        }

        checkpoint(connection);
        Path backup = createBackup(paths);
        runMigrationTransaction(connection, () ->
        {
            if (sourceVersion == VERSION_0_1_0)
            {
                migrateFromVersionOne(connection);
            }
            else if (sourceVersion < STORY_CARD_SCHEMA)
            {
                normalizeSchemaThreeFoundation(connection);
            }
            if (sourceVersion < STORY_CARD_SCHEMA)
            {
                migrateFromVersionThree(connection);
            }
            if (sourceVersion < PROMPT_LAYOUT_SCHEMA)
            {
                migrateFromVersionFour(connection);
            }
            if (sourceVersion < STORY_CARD_PRESET_SELECTION_SCHEMA)
            {
                migrateFromVersionFive(connection);
            }
            migrateFromVersionSix(connection);
            String sourceLabel = sourceVersion == VERSION_0_1_0
                    ? AppVersion.FIRST_MIGRATION_SOURCE
                    : AppVersion.CURRENT + "-schema-" + sourceVersion;
            recordMigration(connection, CURRENT_SCHEMA, sourceLabel);
            setUserVersion(connection, CURRENT_SCHEMA);
            validateDatabase(connection);
        });
        enableWal(connection);
        return new MigrationReport(sourceVersion, CURRENT_SCHEMA, Optional.of(backup), false);
    }

    private static void migrateFromVersionOne(Connection connection) throws SQLException
    {
        createCoreTables(connection);
        migrateAppAndModelSettings(connection);
        createImagesTable(connection);
        rebuildBlocks(connection);
        createIndexesAndMetadata(connection);
        dropLegacyGenerationSettings(connection);
    }

    private static void normalizeSchemaThreeFoundation(Connection connection) throws SQLException
    {
        if (needsSchemaThreeSettingsNormalization(connection))
        {
            migrateFromInitialVersionTwo(connection);
        }
    }

    private static void migrateFromInitialVersionTwo(Connection connection) throws SQLException
    {
        int legacyContextLimit = readLegacyContextLimit(connection);
        rebuildAppSettings(connection);
        addModelSettingColumns(connection, legacyContextLimit);
    }

    private static void migrateFromVersionThree(Connection connection) throws SQLException
    {
        createCoreTables(connection);
        createImagesTable(connection);
        rebuildStoryCardsForSchemaFour(connection);
        createStoryCardCommandPresetsTable(connection);
        dropAutoCardsTables(connection);
        createIndexesAndMetadata(connection);
    }

    private static void migrateFromVersionFour(Connection connection) throws SQLException
    {
        rebuildAppSettings(connection);
        addModelSettingColumns(connection, readLegacyContextLimit(connection));
        createIndexesAndMetadata(connection);
    }

    private static void migrateFromVersionFive(Connection connection) throws SQLException
    {
        addColumnIfMissing(connection, "app_settings", "selected_story_card_command_preset_id",
                "TEXT NOT NULL DEFAULT 'builtin:condensed'");
    }

    private static void migrateFromVersionSix(Connection connection) throws SQLException
    {
        addColumnIfMissing(connection, "stories", "story_card_generation_context",
                "TEXT NOT NULL DEFAULT ''");
    }

    private static boolean needsSchemaThreeSettingsNormalization(Connection connection) throws SQLException
    {
        if (!tableExists(connection, "app_settings") || !tableExists(connection, "model_settings"))
        {
            return true;
        }
        Set<String> appColumns = columns(connection, "app_settings");
        Set<String> modelColumns = columns(connection, "model_settings");
        return !appColumns.contains("min_story_percent")
                || !appColumns.contains("ollama_keep_alive_minutes")
                || appColumns.contains("context_limit")
                || appColumns.contains("min_story_window")
                || !modelColumns.contains("context_limit")
                || !modelColumns.contains("prompt_token_scale")
                || !modelColumns.contains("typical_p_enabled")
                || !modelColumns.contains("typical_p")
                || !modelColumns.contains("repeat_last_n_enabled")
                || !modelColumns.contains("repeat_last_n");
    }

    private static boolean needsCurrentSchemaNormalization(Connection connection) throws SQLException
    {
        if (needsSchemaThreeSettingsNormalization(connection)
                || !tableExists(connection, "story_cards")
                || !tableExists(connection, "story_card_command_presets"))
        {
            return true;
        }
        Set<String> storyCardColumns = columns(connection, "story_cards");
        Set<String> storyColumns = columns(connection, "stories");
        Set<String> appColumns = columns(connection, "app_settings");
        Set<String> modelColumns = columns(connection, "model_settings");
        return !storyCardColumns.contains("type")
                || !storyCardColumns.contains("notes")
                || !storyColumns.contains("story_card_generation_context")
                || appColumns.contains("an_placement")
                || !appColumns.contains("selected_story_card_command_preset_id")
                || !modelColumns.contains("story_card_wrapping_style")
                || !modelColumns.contains("conversation_layout")
                || tableExists(connection, "app_auto_cards")
                || tableExists(connection, "story_auto_cards")
                || tableExists(connection, "model_auto_cards");
    }

    private static void createCurrentSchema(Connection connection) throws SQLException
    {
        createCoreTables(connection);
        createImagesTable(connection);
        createAppSettingsTable(connection);
        createModelSettingsTable(connection);
        createStoryCardCommandPresetsTable(connection);
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
                    story_card_generation_context TEXT NOT NULL DEFAULT '',
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
                    type TEXT NOT NULL DEFAULT '',
                    notes TEXT NOT NULL DEFAULT '',
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
        execute(connection, appSettingsTableSql("app_settings"));
    }

    private static void createModelSettingsTable(Connection connection) throws SQLException
    {
        execute(connection, """
                CREATE TABLE IF NOT EXISTS model_settings (
                    model_name TEXT PRIMARY KEY,
                    active INTEGER NOT NULL CHECK (active IN (0,1)),
                    context_limit INTEGER NOT NULL DEFAULT 8192,
                    prompt_token_scale REAL NOT NULL DEFAULT 1.0,
                    temperature_enabled INTEGER NOT NULL DEFAULT 0 CHECK (temperature_enabled IN (0,1)),
                    temperature REAL NOT NULL,
                    top_k_enabled INTEGER NOT NULL DEFAULT 0 CHECK (top_k_enabled IN (0,1)),
                    top_k INTEGER NOT NULL,
                    top_p_enabled INTEGER NOT NULL DEFAULT 0 CHECK (top_p_enabled IN (0,1)),
                    top_p REAL NOT NULL,
                    min_p_enabled INTEGER NOT NULL DEFAULT 0 CHECK (min_p_enabled IN (0,1)),
                    min_p REAL NOT NULL,
                    typical_p_enabled INTEGER NOT NULL DEFAULT 0 CHECK (typical_p_enabled IN (0,1)),
                    typical_p REAL NOT NULL DEFAULT 1.0,
                    presence_penalty_enabled INTEGER NOT NULL DEFAULT 0 CHECK (presence_penalty_enabled IN (0,1)),
                    presence_penalty REAL NOT NULL,
                    frequency_penalty_enabled INTEGER NOT NULL DEFAULT 0 CHECK (frequency_penalty_enabled IN (0,1)),
                    frequency_penalty REAL NOT NULL,
                    repeat_last_n_enabled INTEGER NOT NULL DEFAULT 0 CHECK (repeat_last_n_enabled IN (0,1)),
                    repeat_last_n INTEGER NOT NULL DEFAULT 64,
                    repetition_penalty_enabled INTEGER NOT NULL DEFAULT 0 CHECK (repetition_penalty_enabled IN (0,1)),
                    repetition_penalty REAL NOT NULL,
                    story_card_wrapping_style TEXT NOT NULL DEFAULT 'NONE'
                        CHECK (story_card_wrapping_style IN ('NONE','BRACES','BRACKETS')),
                    conversation_layout TEXT NOT NULL DEFAULT 'ROLE_AWARE'
                        CHECK (conversation_layout IN ('ROLE_AWARE','FLATTENED','FLATTENED_WITH_PREFILL'))
                )
                """);
    }

    private static void createStoryCardCommandPresetsTable(Connection connection) throws SQLException
    {
        execute(connection, """
                CREATE TABLE IF NOT EXISTS story_card_command_presets (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL COLLATE NOCASE UNIQUE,
                    command TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )
                """);
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
        if (tableExists(connection, "story_cards") && columns(connection, "story_cards").contains("type"))
        {
            execute(connection, """
                    CREATE INDEX IF NOT EXISTS idx_cards_story_type_title
                    ON story_cards(story_id, type COLLATE NOCASE, pinned DESC, title COLLATE NOCASE)
                    """);
        }
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
        int legacyContextLimit = readLegacyContextLimit(connection);

        if (hadAppSettings)
        {
            rebuildAppSettings(connection);
        }
        else if (hadGenerationSettings)
        {
            createAppSettingsTable(connection);
            execute(connection, """
                    INSERT INTO app_settings (
                        id, ollama_url, comfyui_url, selected_model,
                        response_length_enabled, response_length,
                        min_story_percent, story_card_lookback,
                        comfy_workflow, comfy_width, comfy_height, comfy_batch_size,
                        ollama_keep_alive_minutes
                    )
                    SELECT id, 'http://localhost:11434', 'http://localhost:8000',
                           'hf.co/LatitudeGames/Muse-12B-GGUF:BF16',
                           1, response_length,
                           MAX(10, MIN(100, ROUND(min_story_window * 100.0 / MAX(1, context_limit)))),
                           story_card_lookback,
                           'ChromaHD', 720, 720, 4, 5
                    FROM generation_settings
                    WHERE id = 1
                    """);
        }
        else
        {
            createAppSettingsTable(connection);
        }

        boolean hadModelSettings = tableExists(connection, "model_settings");
        createModelSettingsTable(connection);
        if (hadModelSettings)
        {
            addModelSettingColumns(connection, legacyContextLimit);
        }
        else if (hadGenerationSettings)
        {
            Set<String> columns = columns(connection, "generation_settings");
            String minP = columns.contains("min_p") ? "min_p" : "0.025";
            String repetitionPenalty = columns.contains("repetition_penalty") ? "repetition_penalty" : "1.05";
            execute(connection, """
                    INSERT INTO model_settings (
                        model_name, active, context_limit, prompt_token_scale,
                        temperature_enabled, temperature,
                        top_k_enabled, top_k,
                        top_p_enabled, top_p,
                        min_p_enabled, min_p,
                        presence_penalty_enabled, presence_penalty,
                        frequency_penalty_enabled, frequency_penalty,
                        repetition_penalty_enabled, repetition_penalty
                    )
                    SELECT 'hf.co/LatitudeGames/Muse-12B-GGUF:BF16', 1,
                           context_limit, 1.0,
                           1, temperature,
                           1, top_k,
                           1, top_p,
                           1, %s,
                           1, presence_penalty,
                           1, frequency_penalty,
                           1, %s
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

    private static void rebuildAppSettings(Connection connection) throws SQLException
    {
        boolean existed = tableExists(connection, "app_settings");
        Set<String> oldColumns = existed ? columns(connection, "app_settings") : Set.of();
        execute(connection, "DROP TABLE IF EXISTS app_settings_v6");
        execute(connection, appSettingsTableSql("app_settings_v6"));
        if (existed)
        {
            execute(connection, """
                    INSERT INTO app_settings_v6 (
                        id, ollama_url, comfyui_url, selected_model,
                        response_length_enabled, response_length,
                        min_story_percent, story_card_lookback,
                        comfy_workflow, comfy_width, comfy_height, comfy_batch_size,
                        ollama_keep_alive_minutes, selected_story_card_command_preset_id
                    )
                    SELECT %s, %s, %s, %s, %s, %s,
                           MAX(10, MIN(100, %s)),
                           %s, %s, %s, %s, %s, %s, %s
                    FROM app_settings
                    WHERE %s = 1
                    """.formatted(
                    value(oldColumns, "id", "1"),
                    value(oldColumns, "ollama_url", "'http://localhost:11434'"),
                    value(oldColumns, "comfyui_url", "'http://localhost:8000'"),
                    value(oldColumns, "selected_model", "'hf.co/LatitudeGames/Muse-12B-GGUF:BF16'"),
                    value(oldColumns, "response_length_enabled", "1"),
                    value(oldColumns, "response_length", "200"),
                    oldColumns.contains("min_story_percent")
                            ? "min_story_percent"
                            : "ROUND((" + value(oldColumns, "min_story_window", "4915")
                                    + ") * 100.0 / MAX(1, "
                                    + value(oldColumns, "context_limit", "8192") + "))",
                    value(oldColumns, "story_card_lookback", "5"),
                    value(oldColumns, "comfy_workflow", "'ChromaHD'"),
                    value(oldColumns, "comfy_width", "720"),
                    value(oldColumns, "comfy_height", "720"),
                    value(oldColumns, "comfy_batch_size", "4"),
                    value(oldColumns, "ollama_keep_alive_minutes", "5"),
                    value(oldColumns, "selected_story_card_command_preset_id", "'builtin:condensed'"),
                    value(oldColumns, "id", "1")));
            execute(connection, "DROP TABLE app_settings");
        }
        execute(connection, "ALTER TABLE app_settings_v6 RENAME TO app_settings");
    }

    private static void rebuildStoryCardsForSchemaFour(Connection connection) throws SQLException
    {
        boolean existed = tableExists(connection, "story_cards");
        Set<String> oldColumns = existed ? columns(connection, "story_cards") : Set.of();
        execute(connection, "DROP TABLE IF EXISTS story_cards_v4");
        execute(connection, """
                CREATE TABLE story_cards_v4 (
                    id TEXT PRIMARY KEY,
                    story_id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    triggers TEXT NOT NULL,
                    content TEXT NOT NULL,
                    type TEXT NOT NULL DEFAULT '',
                    notes TEXT NOT NULL DEFAULT '',
                    pinned INTEGER NOT NULL CHECK (pinned IN (0,1)),
                    FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE
                )
                """);
        if (existed)
        {
            execute(connection, """
                    INSERT INTO story_cards_v4 (
                        id, story_id, title, triggers, content, type, notes, pinned
                    )
                    SELECT id, story_id, title, triggers, content, %s, %s, pinned
                    FROM story_cards
                    """.formatted(
                    value(oldColumns, "type", "''"),
                    value(oldColumns, "notes", "''")));
            execute(connection, "DROP TABLE story_cards");
        }
        execute(connection, "ALTER TABLE story_cards_v4 RENAME TO story_cards");
    }

    private static void dropAutoCardsTables(Connection connection) throws SQLException
    {
        execute(connection, "DROP TABLE IF EXISTS app_auto_cards");
        execute(connection, "DROP TABLE IF EXISTS story_auto_cards");
        execute(connection, "DROP TABLE IF EXISTS model_auto_cards");
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

    private static String appSettingsTableSql(String table)
    {
        return """
                CREATE TABLE IF NOT EXISTS %s (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    ollama_url TEXT NOT NULL,
                    comfyui_url TEXT NOT NULL DEFAULT 'http://localhost:8000',
                    selected_model TEXT NOT NULL,
                    response_length_enabled INTEGER NOT NULL DEFAULT 0
                        CHECK (response_length_enabled IN (0,1)),
                    response_length INTEGER NOT NULL,
                    min_story_percent INTEGER NOT NULL DEFAULT 60
                        CHECK (min_story_percent BETWEEN 10 AND 100),
                    story_card_lookback INTEGER NOT NULL,
                    comfy_workflow TEXT NOT NULL DEFAULT 'ChromaHD',
                    comfy_width INTEGER NOT NULL DEFAULT 720,
                    comfy_height INTEGER NOT NULL DEFAULT 720,
                    comfy_batch_size INTEGER NOT NULL DEFAULT 4,
                    ollama_keep_alive_minutes INTEGER NOT NULL DEFAULT 5
                        CHECK (ollama_keep_alive_minutes BETWEEN 5 AND 30),
                    selected_story_card_command_preset_id TEXT NOT NULL DEFAULT 'builtin:condensed'
                )
                """.formatted(table);
    }

    private static void dropLegacyGenerationSettings(Connection connection) throws SQLException
    {
        execute(connection, "DROP TABLE IF EXISTS generation_settings");
    }

    private static void addModelSettingColumns(Connection connection, int legacyContextLimit) throws SQLException
    {
        if (!tableExists(connection, "model_settings"))
        {
            createModelSettingsTable(connection);
            return;
        }
        addColumnIfMissing(connection, "model_settings", "context_limit",
                "INTEGER NOT NULL DEFAULT " + Math.max(1024, legacyContextLimit));
        addColumnIfMissing(connection, "model_settings", "prompt_token_scale",
                "REAL NOT NULL DEFAULT 1.0");
        addColumnIfMissing(connection, "model_settings", "temperature_enabled",
                "INTEGER NOT NULL DEFAULT 1 CHECK (temperature_enabled IN (0,1))");
        addColumnIfMissing(connection, "model_settings", "top_k_enabled",
                "INTEGER NOT NULL DEFAULT 1 CHECK (top_k_enabled IN (0,1))");
        addColumnIfMissing(connection, "model_settings", "top_p_enabled",
                "INTEGER NOT NULL DEFAULT 1 CHECK (top_p_enabled IN (0,1))");
        addColumnIfMissing(connection, "model_settings", "min_p_enabled",
                "INTEGER NOT NULL DEFAULT 1 CHECK (min_p_enabled IN (0,1))");
        addColumnIfMissing(connection, "model_settings", "typical_p_enabled",
                "INTEGER NOT NULL DEFAULT 0 CHECK (typical_p_enabled IN (0,1))");
        addColumnIfMissing(connection, "model_settings", "typical_p",
                "REAL NOT NULL DEFAULT 1.0");
        addColumnIfMissing(connection, "model_settings", "presence_penalty_enabled",
                "INTEGER NOT NULL DEFAULT 1 CHECK (presence_penalty_enabled IN (0,1))");
        addColumnIfMissing(connection, "model_settings", "frequency_penalty_enabled",
                "INTEGER NOT NULL DEFAULT 1 CHECK (frequency_penalty_enabled IN (0,1))");
        addColumnIfMissing(connection, "model_settings", "repeat_last_n_enabled",
                "INTEGER NOT NULL DEFAULT 0 CHECK (repeat_last_n_enabled IN (0,1))");
        addColumnIfMissing(connection, "model_settings", "repeat_last_n",
                "INTEGER NOT NULL DEFAULT 64");
        addColumnIfMissing(connection, "model_settings", "repetition_penalty_enabled",
                "INTEGER NOT NULL DEFAULT 1 CHECK (repetition_penalty_enabled IN (0,1))");
        addColumnIfMissing(connection, "model_settings", "story_card_wrapping_style",
                "TEXT NOT NULL DEFAULT 'NONE' "
                        + "CHECK (story_card_wrapping_style IN ('NONE','BRACES','BRACKETS'))");
        addColumnIfMissing(connection, "model_settings", "conversation_layout",
                "TEXT NOT NULL DEFAULT 'ROLE_AWARE' "
                        + "CHECK (conversation_layout IN ('ROLE_AWARE','FLATTENED','FLATTENED_WITH_PREFILL'))");
    }

    private static int readLegacyContextLimit(Connection connection) throws SQLException
    {
        if (tableExists(connection, "app_settings") && columns(connection, "app_settings").contains("context_limit"))
        {
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT context_limit FROM app_settings WHERE id = 1"))
            {
                if (result.next())
                {
                    return Math.max(1024, result.getInt(1));
                }
            }
        }
        if (tableExists(connection, "generation_settings")
                && columns(connection, "generation_settings").contains("context_limit"))
        {
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT context_limit FROM generation_settings WHERE id = 1"))
            {
                if (result.next())
                {
                    return Math.max(1024, result.getInt(1));
                }
            }
        }
        return 8192;
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
