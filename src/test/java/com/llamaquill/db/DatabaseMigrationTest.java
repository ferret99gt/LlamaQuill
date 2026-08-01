package com.llamaquill.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.llamaquill.AppVersion;
import com.llamaquill.model.AppSettings;
import com.llamaquill.model.Block;
import com.llamaquill.model.ConversationLayout;
import com.llamaquill.model.ModelSettings;
import com.llamaquill.model.Role;
import com.llamaquill.model.Story;
import com.llamaquill.model.StoryCard;
import com.llamaquill.model.StoryCardCommandPreset;
import com.llamaquill.model.StoryCardWrappingStyle;
import com.llamaquill.util.Timestamps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

class DatabaseMigrationTest
{
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsACompleteCurrentDatabase() throws Exception
    {
        AppPaths paths = paths("fresh");
        try (Database database = Database.open(paths))
        {
            assertTrue(database.startupReport().migration().freshDatabase());
            assertEquals(0, database.startupReport().migration().sourceSchema());
            assertEquals(AppVersion.DATABASE_SCHEMA, userVersion(database));
            assertEquals("0.3.0", scalarText(database,
                    "SELECT app_version FROM schema_migrations WHERE schema_version = "
                            + AppVersion.DATABASE_SCHEMA));
            assertFalse(columns(database, "app_settings").contains("use_ollama_templates"));
            assertFalse(columns(database, "app_settings").contains("an_placement"));
            assertTrue(columns(database, "story_cards").containsAll(List.of("type", "notes")));
            assertTrue(columns(database, "model_settings").containsAll(
                    List.of("story_card_wrapping_style", "conversation_layout")));
            assertTrue(tableExists(database, "story_card_command_presets"));
            assertFalse(tableExists(database, "app_auto_cards"));
            assertFalse(tableExists(database, "story_auto_cards"));
            assertFalse(tableExists(database, "model_auto_cards"));
            assertEquals("ok", scalarText(database, "PRAGMA integrity_check"));
            assertEquals(1, scalarInt(database, "PRAGMA foreign_keys"));
            Database.Diagnostics diagnostics = database.diagnostics();
            assertTrue(diagnostics.healthy());
            assertEquals("wal", diagnostics.journalMode());

            AppSettingsRepository appSettings = new AppSettingsRepository(database);
            AppSettings expectedSettings = new AppSettings(
                    "http://settings-ollama:11434",
                    "http://settings-comfy:8000",
                    "settings-model",
                    false,
                    240,
                    55,
                    12,
                    "SettingsWorkflow",
                    1024,
                    768,
                    2,
                    17);
            appSettings.save(expectedSettings);
            assertEquals(expectedSettings, appSettings.load().orElseThrow());
            assertTrue(columns(database, "app_settings").contains("ollama_keep_alive_minutes"));

            ModelSettingsRepository modelSettings = new ModelSettingsRepository(database);
            ModelSettings expectedModelSettings = new ModelSettings(
                    "settings-model",
                    true,
                    16384,
                    1.25,
                    false, 0.7,
                    true, 0,
                    false, 0.92,
                    true, 0.0,
                    true, 0.73,
                    false, 0.0,
                    true, 0.0,
                    true, -1,
                    false, 1.05,
                    StoryCardWrappingStyle.BRACKETS,
                    ConversationLayout.FLATTENED_WITH_PREFILL);
            modelSettings.save(expectedModelSettings);
            assertEquals(expectedModelSettings, modelSettings.load("settings-model").orElseThrow());

            StoryRepository stories = new StoryRepository(database);
            String now = Timestamps.now();
            stories.insert(new Story("backup-story", "Backup", "", "", "", now, now));
            Path backup = database.createBackup();
            assertTrue(Files.isRegularFile(backup));
            try (Connection backupConnection = DriverManager.getConnection("jdbc:sqlite:" + backup);
                 Statement statement = backupConnection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM stories WHERE id = 'backup-story'"))
            {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            }
        }
    }

    @Test
    void migratesSchemaFourPromptOptionsAndDropsAuthorNotePlacement() throws Exception
    {
        AppPaths paths = paths("schema-four-prompt-options");
        AppSettings expectedAppSettings = new AppSettings(
                "http://schema-four:11434", "http://comfy:8000", "schema-four-model",
                false, 222, 61, 9, "ChromaHD", 720, 720, 4, 11);
        try (Database database = Database.open(paths))
        {
            new AppSettingsRepository(database).save(expectedAppSettings);
            new ModelSettingsRepository(database).save(ModelSettings.defaults("schema-four-model"));
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + paths.databaseFile());
             Statement statement = connection.createStatement())
        {
            statement.execute("ALTER TABLE app_settings ADD COLUMN an_placement INTEGER NOT NULL DEFAULT 3");
            statement.execute("ALTER TABLE model_settings DROP COLUMN story_card_wrapping_style");
            statement.execute("ALTER TABLE model_settings DROP COLUMN conversation_layout");
            statement.execute("PRAGMA user_version = 4");
        }

        try (Database database = Database.open(paths))
        {
            Database.StartupReport report = database.startupReport();
            assertEquals(4, report.migration().sourceSchema());
            assertEquals(AppVersion.DATABASE_SCHEMA, report.migration().targetSchema());
            assertTrue(Files.isRegularFile(report.migration().backup().orElseThrow()));
            assertFalse(columns(database, "app_settings").contains("an_placement"));
            assertEquals(expectedAppSettings, new AppSettingsRepository(database).load().orElseThrow());
            ModelSettings migrated = new ModelSettingsRepository(database)
                    .load("schema-four-model").orElseThrow();
            assertEquals(StoryCardWrappingStyle.NONE, migrated.storyCardWrappingStyle());
            assertEquals(ConversationLayout.ROLE_AWARE, migrated.conversationLayout());
        }
    }

    @Test
    void reopensSchemaFourWithoutRemigratingOrLosingCardMetadata() throws Exception
    {
        AppPaths paths = paths("schema-four-reopen");
        try (Database database = Database.open(paths))
        {
            String now = Timestamps.now();
            new StoryRepository(database).insert(
                    new Story("story", "Story", "", "", "", now, now));
            new StoryCardRepository(database).insert(
                    new StoryCard("card", "story", "Mia", "Mia", "Entry",
                            "Character", "Player-only note", true));
            new StoryCardCommandPresetRepository(database).insert(
                    new StoryCardCommandPreset("preset", "Custom", "Write {{title}}.", now, now));
        }

        try (Database database = Database.open(paths))
        {
            Database.StartupReport report = database.startupReport();
            assertFalse(report.migration().freshDatabase());
            assertEquals(AppVersion.DATABASE_SCHEMA, report.migration().sourceSchema());
            assertEquals(AppVersion.DATABASE_SCHEMA, report.migration().targetSchema());
            assertTrue(report.migration().backup().isEmpty());
            StoryCard card = new StoryCardRepository(database).findById("card").orElseThrow();
            assertEquals("Character", card.type());
            assertEquals("Player-only note", card.notes());
            assertEquals(1, new StoryCardCommandPresetRepository(database).listAll().size());
        }
    }

    @Test
    void normalizesAnEarlySchemaFourDatabaseMissingKeepAlive() throws Exception
    {
        AppPaths paths = paths("schema-four-keep-alive-normalization");
        try (Database database = Database.open(paths))
        {
            new AppSettingsRepository(database).save(AppSettings.defaults());
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + paths.databaseFile());
             Statement statement = connection.createStatement())
        {
            statement.execute("ALTER TABLE app_settings DROP COLUMN ollama_keep_alive_minutes");
        }

        try (Database database = Database.open(paths))
        {
            Database.StartupReport report = database.startupReport();
            assertEquals(AppVersion.DATABASE_SCHEMA, report.migration().sourceSchema());
            assertEquals(AppVersion.DATABASE_SCHEMA, report.migration().targetSchema());
            assertTrue(Files.isRegularFile(report.migration().backup().orElseThrow()));
            assertTrue(columns(database, "app_settings").contains("ollama_keep_alive_minutes"));
            assertEquals(AppSettings.DEFAULT_OLLAMA_KEEP_ALIVE_MINUTES,
                    new AppSettingsRepository(database).load().orElseThrow().ollamaKeepAliveMinutes());
        }
    }

    @Test
    void migratesTheEarliestUnversionedDatabaseWithoutLosingRows() throws Exception
    {
        AppPaths paths = paths("earliest");
        createFixture(paths.legacyDatabaseFile(), "/db/0.1.0-earliest.sql");

        try (Database database = Database.open(paths))
        {
            Database.StartupReport report = database.startupReport();
            assertEquals(paths.legacyDatabaseFile(), report.pathPreparation().copiedLegacyDatabase().orElseThrow());
            assertEquals(1, report.migration().sourceSchema());
            assertEquals(AppVersion.DATABASE_SCHEMA, report.migration().targetSchema());
            assertTrue(Files.isRegularFile(report.migration().backup().orElseThrow()));
            assertTrue(Files.isRegularFile(paths.legacyDatabaseFile()));

            StoryRepository stories = new StoryRepository(database);
            BlockRepository blocks = new BlockRepository(database);
            assertEquals("Legacy Story", stories.findById("story-1").orElseThrow().title());
            List<Block> migratedBlocks = blocks.listForStory("story-1");
            assertEquals(List.of(1, 2), migratedBlocks.stream().map(Block::position).toList());
            assertEquals(1, scalarInt(database, "SELECT COUNT(*) FROM story_cards WHERE id = 'card-1'"));
            assertEquals("", scalarText(database, "SELECT type FROM story_cards WHERE id = 'card-1'"));
            assertEquals("", scalarText(database, "SELECT notes FROM story_cards WHERE id = 'card-1'"));
            assertEquals(8192, scalarInt(database,
                    "SELECT context_limit FROM model_settings WHERE model_name = "
                            + "'hf.co/LatitudeGames/Muse-12B-GGUF:BF16'"));
            assertEquals(85, scalarInt(database, "SELECT min_story_percent FROM app_settings WHERE id = 1"));
            assertEquals(1, scalarInt(database,
                    "SELECT response_length_enabled FROM app_settings WHERE id = 1"));
            assertEquals(1, scalarInt(database,
                    "SELECT COUNT(*) FROM model_settings WHERE model_name LIKE 'hf.co/%'"));
            assertEquals(7, enabledModelOptionCount(database,
                    "hf.co/LatitudeGames/Muse-12B-GGUF:BF16"));
            assertEquals(5, scalarInt(database,
                    "SELECT ollama_keep_alive_minutes FROM app_settings WHERE id = 1"));

            int nextPosition = blocks.nextPosition("story-1");
            blocks.insert(new Block("image-block", "story-1", Role.IMAGE, "image-id", Timestamps.now(), nextPosition));
            assertThrows(SQLException.class, () -> blocks.insert(
                    new Block("duplicate-position", "story-1", Role.USER, "duplicate", Timestamps.now(), nextPosition)));
            assertEquals(AppVersion.DATABASE_SCHEMA, userVersion(database));
        }
    }

    @Test
    void migratesLateVersionOneSettingsAndRemovesObsoleteRequiredColumns() throws Exception
    {
        AppPaths paths = paths("late");
        createFixture(paths.databaseFile(), "/db/0.1.0-late.sql");

        try (Database database = Database.open(paths))
        {
            assertEquals("http://legacy-ollama:11434",
                    scalarText(database, "SELECT ollama_url FROM app_settings WHERE id = 1"));
            assertFalse(columns(database, "app_settings").contains("use_ollama_templates"));
            assertFalse(tableExists(database, "app_auto_cards"));
            assertFalse(tableExists(database, "story_auto_cards"));
            assertFalse(tableExists(database, "model_auto_cards"));
            assertTrue(tableExists(database, "story_card_command_presets"));
            assertTrue(columns(database, "story_cards").containsAll(List.of("type", "notes")));
            assertEquals(1, scalarInt(database,
                    "SELECT response_length_enabled FROM app_settings WHERE id = 1"));
            assertEquals(7, enabledModelOptionCount(database, "legacy-model"));
            assertEquals(5, scalarInt(database,
                    "SELECT ollama_keep_alive_minutes FROM app_settings WHERE id = 1"));
        }
    }

    @Test
    void migratesInitialVersionTwoSettingsAndRemovesTheTemplateToggle() throws Exception
    {
        AppPaths paths = paths("schema-two");
        createFixture(paths.databaseFile(), "/db/0.2.0-schema-2.sql");

        try (Database database = Database.open(paths))
        {
            Database.StartupReport report = database.startupReport();
            assertEquals(2, report.migration().sourceSchema());
            assertEquals(AppVersion.DATABASE_SCHEMA, report.migration().targetSchema());
            assertTrue(Files.isRegularFile(report.migration().backup().orElseThrow()));
            assertEquals("http://schema-two-ollama:11434",
                    scalarText(database, "SELECT ollama_url FROM app_settings WHERE id = 1"));
            assertEquals("schema-two-model",
                    scalarText(database, "SELECT selected_model FROM app_settings WHERE id = 1"));
            assertEquals(24576, scalarInt(database,
                    "SELECT context_limit FROM model_settings WHERE model_name = 'schema-two-model'"));
            assertEquals(49, scalarInt(database, "SELECT min_story_percent FROM app_settings WHERE id = 1"));
            assertEquals("SchemaTwoWorkflow",
                    scalarText(database, "SELECT comfy_workflow FROM app_settings WHERE id = 1"));
            assertFalse(columns(database, "app_settings").contains("use_ollama_templates"));
            assertEquals(1, scalarInt(database,
                    "SELECT response_length_enabled FROM app_settings WHERE id = 1"));
            assertEquals(0, scalarInt(database,
                    "SELECT top_k FROM model_settings WHERE model_name = 'schema-two-model'"));
            assertEquals(1, scalarInt(database,
                    "SELECT top_k_enabled FROM model_settings WHERE model_name = 'schema-two-model'"));
            assertEquals(7, enabledModelOptionCount(database, "schema-two-model"));
            assertEquals(5, scalarInt(database,
                    "SELECT ollama_keep_alive_minutes FROM app_settings WHERE id = 1"));
            assertEquals(AppVersion.DATABASE_SCHEMA, userVersion(database));
        }
    }

    @Test
    void normalizesAnUnreleasedProvisionalSchemaThreeDatabase() throws Exception
    {
        AppPaths paths = paths("provisional-schema-three");
        createFixture(paths.databaseFile(), "/db/0.2.0-schema-2.sql");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + paths.databaseFile());
             Statement statement = connection.createStatement())
        {
            statement.execute("PRAGMA user_version = 3");
        }

        try (Database database = Database.open(paths))
        {
            Database.StartupReport report = database.startupReport();
            assertEquals(3, report.migration().sourceSchema());
            assertEquals(AppVersion.DATABASE_SCHEMA, report.migration().targetSchema());
            assertTrue(Files.isRegularFile(report.migration().backup().orElseThrow()));
            assertFalse(columns(database, "app_settings").contains("context_limit"));
            assertFalse(columns(database, "app_settings").contains("min_story_window"));
            assertEquals(49, scalarInt(database, "SELECT min_story_percent FROM app_settings WHERE id = 1"));
            assertEquals(24576, scalarInt(database,
                    "SELECT context_limit FROM model_settings WHERE model_name = 'schema-two-model'"));
            assertEquals(1, scalarInt(database,
                    "SELECT prompt_token_scale FROM model_settings WHERE model_name = 'schema-two-model'"));
            assertEquals(0, scalarInt(database,
                    "SELECT typical_p_enabled FROM model_settings WHERE model_name = 'schema-two-model'"));
            assertEquals(1, scalarInt(database,
                    "SELECT typical_p FROM model_settings WHERE model_name = 'schema-two-model'"));
            assertEquals(0, scalarInt(database,
                    "SELECT repeat_last_n_enabled FROM model_settings WHERE model_name = 'schema-two-model'"));
            assertEquals(64, scalarInt(database,
                    "SELECT repeat_last_n FROM model_settings WHERE model_name = 'schema-two-model'"));
        }
    }

    @Test
    void rollsBackRepositoryWorkAsOneUnit() throws Exception
    {
        AppPaths paths = paths("rollback");
        try (Database database = Database.open(paths))
        {
            StoryRepository stories = new StoryRepository(database);
            Story story = new Story("rollback-story", "Rollback", "", "", "",
                    Timestamps.now(), Timestamps.now());

            assertThrows(SQLException.class, () -> database.inTransaction(connection ->
            {
                stories.insert(story);
                throw new SQLException("Injected transaction failure");
            }));

            assertTrue(stories.findById(story.id()).isEmpty());
        }
    }

    private AppPaths paths(String name) throws IOException
    {
        Path root = temporaryDirectory.resolve(name);
        Path data = root.resolve("stable-data");
        Path legacy = root.resolve("working-directory").resolve("data");
        Files.createDirectories(data);
        Files.createDirectories(legacy);
        return AppPaths.forDirectories(data, legacy);
    }

    private static void createFixture(Path databaseFile, String resource) throws Exception
    {
        Files.createDirectories(databaseFile.getParent());
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
             Statement statement = connection.createStatement())
        {
            for (String sql : readResource(resource).split(";"))
            {
                if (!sql.isBlank())
                {
                    statement.execute(sql);
                }
            }
        }
    }

    private static String readResource(String resource) throws IOException
    {
        try (InputStream input = DatabaseMigrationTest.class.getResourceAsStream(resource))
        {
            assertNotNull(input, "Missing test fixture " + resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int userVersion(Database database) throws SQLException
    {
        return scalarInt(database, "PRAGMA user_version");
    }

    private static int scalarInt(Database database, String sql) throws SQLException
    {
        return database.withConnection(connection ->
        {
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(sql))
            {
                return result.next() ? result.getInt(1) : 0;
            }
        });
    }

    private static String scalarText(Database database, String sql) throws SQLException
    {
        return database.withConnection(connection ->
        {
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(sql))
            {
                return result.next() ? result.getString(1) : null;
            }
        });
    }

    private static int enabledModelOptionCount(Database database, String modelName) throws SQLException
    {
        return scalarInt(database, """
                SELECT temperature_enabled + top_k_enabled + top_p_enabled + min_p_enabled
                       + typical_p_enabled + presence_penalty_enabled + frequency_penalty_enabled
                       + repeat_last_n_enabled + repetition_penalty_enabled
                FROM model_settings
                WHERE model_name = '%s'
                """.formatted(modelName.replace("'", "''")));
    }

    private static List<String> columns(Database database, String table) throws SQLException
    {
        return database.withConnection(connection ->
        {
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("PRAGMA table_info(" + table + ")"))
            {
                java.util.ArrayList<String> names = new java.util.ArrayList<>();
                while (result.next())
                {
                    names.add(result.getString("name"));
                }
                return names;
            }
        });
    }

    private static boolean tableExists(Database database, String table) throws SQLException
    {
        return scalarInt(database, """
                SELECT COUNT(*)
                FROM sqlite_master
                WHERE type = 'table' AND name = '%s'
                """.formatted(table.replace("'", "''"))) == 1;
    }
}
