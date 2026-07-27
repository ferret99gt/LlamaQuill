package com.llamaquill.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.llamaquill.AppVersion;
import com.llamaquill.model.Block;
import com.llamaquill.model.Role;
import com.llamaquill.model.Story;
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
    void createsACompleteVersionTwoDatabase() throws Exception
    {
        AppPaths paths = paths("fresh");
        try (Database database = Database.open(paths))
        {
            assertTrue(database.startupReport().migration().freshDatabase());
            assertEquals(0, database.startupReport().migration().sourceSchema());
            assertEquals(AppVersion.DATABASE_SCHEMA, userVersion(database));
            assertEquals("0.2.0", scalarText(database,
                    "SELECT app_version FROM schema_migrations WHERE schema_version = 2"));
            assertEquals("ok", scalarText(database, "PRAGMA integrity_check"));
            assertEquals(1, scalarInt(database, "PRAGMA foreign_keys"));
            Database.Diagnostics diagnostics = database.diagnostics();
            assertTrue(diagnostics.healthy());
            assertEquals("wal", diagnostics.journalMode());

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
    void migratesTheEarliestUnversionedDatabaseWithoutLosingRows() throws Exception
    {
        AppPaths paths = paths("earliest");
        createFixture(paths.legacyDatabaseFile(), "/db/0.1.0-earliest.sql");

        try (Database database = Database.open(paths))
        {
            Database.StartupReport report = database.startupReport();
            assertEquals(paths.legacyDatabaseFile(), report.pathPreparation().copiedLegacyDatabase().orElseThrow());
            assertEquals(1, report.migration().sourceSchema());
            assertEquals(2, report.migration().targetSchema());
            assertTrue(Files.isRegularFile(report.migration().backup().orElseThrow()));
            assertTrue(Files.isRegularFile(paths.legacyDatabaseFile()));

            StoryRepository stories = new StoryRepository(database);
            BlockRepository blocks = new BlockRepository(database);
            assertEquals("Legacy Story", stories.findById("story-1").orElseThrow().title());
            List<Block> migratedBlocks = blocks.listForStory("story-1");
            assertEquals(List.of(1, 2), migratedBlocks.stream().map(Block::position).toList());
            assertEquals(1, scalarInt(database, "SELECT COUNT(*) FROM story_cards WHERE id = 'card-1'"));
            assertEquals(8192, scalarInt(database, "SELECT context_limit FROM app_settings WHERE id = 1"));
            assertEquals(1, scalarInt(database,
                    "SELECT COUNT(*) FROM model_settings WHERE model_name LIKE 'hf.co/%'"));

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
            assertEquals(0, scalarInt(database, "SELECT use_ollama_templates FROM app_settings WHERE id = 1"));
            assertEquals(6, scalarInt(database, "SELECT cooldown_turns FROM app_auto_cards WHERE id = 1"));
            assertEquals("Proper Noun Heuristics",
                    scalarText(database, "SELECT candidate_selection_mode FROM app_auto_cards WHERE id = 1"));
            assertEquals(0, scalarInt(database,
                    "SELECT preview_first FROM story_auto_cards WHERE story_id = 'story-late'"));
            assertFalse(columns(database, "app_auto_cards").contains("run_mode"));
            assertFalse(columns(database, "model_auto_cards").contains("temperature_override"));
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
}
