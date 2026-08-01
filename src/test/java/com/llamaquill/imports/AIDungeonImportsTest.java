package com.llamaquill.imports;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.llamaquill.db.AppPaths;
import com.llamaquill.db.BlockRepository;
import com.llamaquill.db.Database;
import com.llamaquill.db.ImageRepository;
import com.llamaquill.db.StoryCardRepository;
import com.llamaquill.db.StoryRepository;
import com.llamaquill.model.Block;
import com.llamaquill.model.Role;
import com.llamaquill.model.Story;
import com.llamaquill.model.StoryCard;
import com.llamaquill.model.StoryImage;
import com.llamaquill.util.Timestamps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class AIDungeonImportsTest
{
    private static final byte[] ONE_PIXEL_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @TempDir
    Path temporaryDirectory;

    @Test
    void mergeCardImportPreservesPinsAndDistinctSameTitleCards() throws Exception
    {
        AppPaths paths = AppPaths.forDirectories(
                temporaryDirectory.resolve("data"), temporaryDirectory.resolve("legacy"));
        try (Database database = Database.open(paths))
        {
            StoryRepository stories = new StoryRepository(database);
            BlockRepository blocks = new BlockRepository(database);
            StoryCardRepository cards = new StoryCardRepository(database);
            String now = Timestamps.now();
            Story story = new Story("story", "Story", "", "", "", now, now);
            stories.insert(story);
            cards.insert(new StoryCard("existing", story.id(), "The Keep", "old", "Old content", true));

            Path input = temporaryDirectory.resolve("cards.json");
            Files.writeString(input, """
                    [
                      {"title":"the keep","keys":"keep","value":"First update"},
                      {"title":"THE KEEP","keys":"castle","value":"Final update"},
                      {"title":"Duplicate existing","keys":"old","value":"Old content"}
                    ]
                    """, StandardCharsets.UTF_8);

            AIDungeonImports importer = new AIDungeonImports(
                    database, stories, blocks, cards, new ImageRepository(database), "System");
            assertEquals(2, importer.importStoryCards(input, story.id(), false));

            List<StoryCard> imported = cards.listForStory(story.id());
            assertEquals(3, imported.size());
            StoryCard original = imported.stream()
                    .filter(card -> card.id().equals("existing"))
                    .findFirst()
                    .orElseThrow();
            assertEquals("Old content", original.content());
            assertEquals("old", original.triggers());
            assertTrue(original.pinned());
            assertTrue(imported.stream().anyMatch(card -> card.content().equals("First update")));
            assertTrue(imported.stream().anyMatch(card -> card.content().equals("Final update")));
        }
    }

    @Test
    void replaceImportRollsBackTheDeleteAndEarlierInsertsWhenAnyCardFails() throws Exception
    {
        AppPaths paths = AppPaths.forDirectories(
                temporaryDirectory.resolve("rollback-data"), temporaryDirectory.resolve("rollback-legacy"));
        try (Database database = Database.open(paths))
        {
            StoryRepository stories = new StoryRepository(database);
            BlockRepository blocks = new BlockRepository(database);
            StoryCardRepository cards = new StoryCardRepository(database);
            String now = Timestamps.now();
            Story story = new Story("story", "Story", "", "", "", now, now);
            stories.insert(story);
            cards.insert(new StoryCard("existing", story.id(), "Existing", "old", "Preserve", true));
            database.useConnection(connection ->
            {
                try (java.sql.Statement statement = connection.createStatement())
                {
                    statement.execute("""
                            CREATE TRIGGER inject_card_failure
                            BEFORE INSERT ON story_cards
                            WHEN NEW.title = 'Fail'
                            BEGIN
                                SELECT RAISE(ABORT, 'injected card failure');
                            END
                            """);
                }
            });

            Path input = temporaryDirectory.resolve("rollback-cards.json");
            Files.writeString(input, """
                    [
                      {"title":"First","keys":"first","value":"Would be inserted"},
                      {"title":"Fail","keys":"fail","value":"Triggers rollback"}
                    ]
                    """, StandardCharsets.UTF_8);

            AIDungeonImports importer = new AIDungeonImports(
                    database, stories, blocks, cards, new ImageRepository(database), "System");
            assertThrows(Exception.class, () -> importer.importStoryCards(input, story.id(), true));

            List<StoryCard> persisted = cards.listForStory(story.id());
            assertEquals(1, persisted.size());
            assertEquals("Existing", persisted.getFirst().title());
            assertTrue(persisted.getFirst().pinned());
        }
    }

    @Test
    void replaceCardImportPreservesSameTitleVariantsAndAcceptsOptionalTitles() throws Exception
    {
        AppPaths paths = AppPaths.forDirectories(
                temporaryDirectory.resolve("variant-data"), temporaryDirectory.resolve("variant-legacy"));
        try (Database database = Database.open(paths))
        {
            StoryRepository stories = new StoryRepository(database);
            BlockRepository blocks = new BlockRepository(database);
            StoryCardRepository cards = new StoryCardRepository(database);
            String now = Timestamps.now();
            Story story = new Story("story", "Story", "", "", "", now, now);
            stories.insert(story);
            cards.insert(new StoryCard("existing", story.id(), "Existing", "old", "Old card", true));

            Path input = temporaryDirectory.resolve("variant-cards.json");
            Files.writeString(input, "\uFEFF" + """
                    [
                      {"title":"Shared","keys":"Mia","value":"First card","type":"character","notes":"Imported note"},
                      {"title":"Shared","keys":["Emily","friend"],"value":"Second card","type":"Mutated Humans"},
                      {"title":"Duplicate label","keys":["Emily","friend"],"value":"Second card","type":"Mutated Humans"},
                      {"keys":"","value":""}
                    ]
                    """, StandardCharsets.UTF_8);

            AIDungeonImports importer = new AIDungeonImports(
                    database, stories, blocks, cards, new ImageRepository(database), "System");
            assertEquals(3, importer.importStoryCards(input, story.id(), true));

            List<StoryCard> imported = cards.listForStory(story.id());
            assertEquals(3, imported.size());
            assertEquals(2, imported.stream().filter(card -> card.title().equals("Shared")).count());
            StoryCard typed = imported.stream()
                    .filter(card -> card.content().equals("First card"))
                    .findFirst()
                    .orElseThrow();
            assertEquals("Character", typed.type());
            assertEquals("Imported note", typed.notes());
            assertTrue(imported.stream().anyMatch(card -> card.type().equals("Mutated Humans")));
            StoryCard titleless = imported.stream()
                    .filter(card -> card.title().equals("Untitled"))
                    .findFirst()
                    .orElseThrow();
            assertEquals("", titleless.triggers());
            assertEquals("", titleless.content());
            assertTrue(imported.stream().anyMatch(card -> card.triggers().equals("Emily, friend")));
        }
    }

    @Test
    void adventureImportSupportsNestedBackupsLegacyCardsAndOrderedActions() throws Exception
    {
        AppPaths paths = AppPaths.forDirectories(
                temporaryDirectory.resolve("adventure-data"), temporaryDirectory.resolve("adventure-legacy"));
        try (Database database = Database.open(paths))
        {
            StoryRepository stories = new StoryRepository(database);
            BlockRepository blocks = new BlockRepository(database);
            StoryCardRepository cards = new StoryCardRepository(database);
            AIDungeonImports importer = new AIDungeonImports(
                    database, stories, blocks, cards, new ImageRepository(database), "Default system");

            Path archive = writeZip("adventure.zip", Map.of(
                    "backup/metadata.json", """
                            {
                              "adventure": {
                                "title": "Nested Adventure",
                                "memory": "Plot facts",
                                "authorsNote": "Write tersely"
                              },
                              "state": {
                                "instructions": {"custom": {"text": "Imported system"}},
                                "worldInfo": [
                                  {
                                    "keys": ["Mia", {"text": "friend"}],
                                    "entry": "Mia is an old friend.",
                                    "type": "character",
                                    "notes": "Old friend from home.",
                                    "pinned": true
                                  },
                                  {
                                    "title": "Castle",
                                    "keys": "castle",
                                    "entry": "The castle overlooks the valley."
                                  },
                                  {
                                    "title": "Duplicate",
                                    "keys": "castle",
                                    "entry": "The castle overlooks the valley."
                                  }
                                ]
                              }
                            }
                            """,
                    "backup/actions-10.json", """
                            {
                              "partNumber": 2,
                              "actions": [
                                {"type": "continue", "text": " continuation"}
                              ]
                            }
                            """,
                    "backup/actions-2.json", """
                            {
                              "partNumber": 1,
                              "actions": [
                                {"type": "start", "text": "Opening scene."},
                                {"type": "do", "text": "> You open the door"},
                                {"type": "say", "text": "> You say \\"Hello\\""},
                                {"type": "do", "text": "> Mia opens the window."},
                                {"type": "say", "text": "> Mia says \\"Wait.\\""},
                                {"type": "story", "text": "The room falls silent."},
                                {"type": "continue", "rawText": "Raw fallback."},
                                {"type": "see", "text": "A discarded image prompt"},
                                {"type": "future-action", "text": "Unknown action"}
                              ]
                            }
                            """));

            Story imported = importer.importAdventure(archive);

            assertEquals("Nested Adventure", imported.title());
            assertEquals("Imported system", imported.systemPrompt());
            assertEquals("Plot facts", imported.plotEssentials());
            assertEquals("Write tersely", imported.authorNote());

            List<StoryCard> importedCards = cards.listForStory(imported.id());
            assertEquals(2, importedCards.size());
            StoryCard untitled = importedCards.stream()
                    .filter(card -> card.title().equals("Untitled"))
                    .findFirst()
                    .orElseThrow();
            assertEquals("Mia, friend", untitled.triggers());
            assertEquals("Character", untitled.type());
            assertEquals("Old friend from home.", untitled.notes());
            assertTrue(untitled.pinned());

            List<Block> importedBlocks = blocks.listForStory(imported.id());
            assertEquals(8, importedBlocks.size());
            assertEquals(List.of(
                    Role.ASSISTANT,
                    Role.USER,
                    Role.USER,
                    Role.USER,
                    Role.USER,
                    Role.ASSISTANT,
                    Role.ASSISTANT,
                    Role.ASSISTANT), importedBlocks.stream().map(Block::role).toList());
            assertEquals("You open the door", importedBlocks.get(1).text());
            assertEquals("You say \"Hello\"", importedBlocks.get(2).text());
            assertEquals("Mia opens the window.", importedBlocks.get(3).text());
            assertEquals("Mia says \"Wait.\"", importedBlocks.get(4).text());
            assertEquals("Raw fallback.", importedBlocks.get(6).text());
            assertEquals(" continuation", importedBlocks.get(7).text());
        }
    }

    @Test
    void adventureImportUsesTheSelectedScenarioAiInstructions() throws Exception
    {
        AppPaths paths = AppPaths.forDirectories(
                temporaryDirectory.resolve("scenario-data"), temporaryDirectory.resolve("scenario-legacy"));
        try (Database database = Database.open(paths))
        {
            StoryRepository stories = new StoryRepository(database);
            BlockRepository blocks = new BlockRepository(database);
            StoryCardRepository cards = new StoryCardRepository(database);
            AIDungeonImports importer = new AIDungeonImports(
                    database, stories, blocks, cards, new ImageRepository(database), "Default system");

            Path archive = writeZip("scenario-instructions.zip", Map.of(
                    "metadata.json", """
                            {
                              "adventure": {"title": "Scenario Instructions"},
                              "state": {
                                "instructions": {
                                  "type": "scenario",
                                  "scenario": "AI Instructions:\\n- use second-person deep POV\\n- continue the narration",
                                  "custom": "Inactive custom draft"
                                }
                              }
                            }
                            """));

            Story imported = importer.importAdventure(archive);

            assertEquals("""
                    AI Instructions:
                    - use second-person deep POV
                    - continue the narration""", imported.systemPrompt());
        }
    }

    @Test
    void adventureImportUsesCustomInstructionsWhenCustomIsSelected() throws Exception
    {
        AppPaths paths = AppPaths.forDirectories(
                temporaryDirectory.resolve("custom-data"), temporaryDirectory.resolve("custom-legacy"));
        try (Database database = Database.open(paths))
        {
            StoryRepository stories = new StoryRepository(database);
            BlockRepository blocks = new BlockRepository(database);
            StoryCardRepository cards = new StoryCardRepository(database);
            AIDungeonImports importer = new AIDungeonImports(
                    database, stories, blocks, cards, new ImageRepository(database), "Default system");

            Path archive = writeZip("custom-instructions.zip", Map.of(
                    "metadata.json", """
                            {
                              "adventure": {"title": "Custom Instructions"},
                              "state": {
                                "instructions": {
                                  "type": "custom",
                                  "scenario": "Inactive scenario instructions",
                                  "custom": "Live custom instructions"
                                }
                              }
                            }
                            """));

            Story imported = importer.importAdventure(archive);

            assertEquals("Live custom instructions", imported.systemPrompt());
        }
    }

    @Test
    void adventureImportDoesNotResurrectInactiveInstructionsForAnEmptySelectedMode() throws Exception
    {
        AppPaths paths = AppPaths.forDirectories(
                temporaryDirectory.resolve("default-data"), temporaryDirectory.resolve("default-legacy"));
        try (Database database = Database.open(paths))
        {
            StoryRepository stories = new StoryRepository(database);
            BlockRepository blocks = new BlockRepository(database);
            StoryCardRepository cards = new StoryCardRepository(database);
            AIDungeonImports importer = new AIDungeonImports(
                    database, stories, blocks, cards, new ImageRepository(database), "Default system");

            Path archive = writeZip("default-instructions.zip", Map.of(
                    "metadata.json", """
                            {
                              "adventure": {"title": "Model Default Instructions"},
                              "state": {
                                "instructions": {
                                  "type": "model-default",
                                  "scenario": "Inactive scenario instructions",
                                  "custom": "Inactive custom instructions"
                                }
                              }
                            }
                            """));

            Story imported = importer.importAdventure(archive);

            assertEquals("Default system", imported.systemPrompt());
        }
    }

    @Test
    void adventureImportCopiesSeeImagesIntoTheDatabaseInActionOrder() throws Exception
    {
        AppPaths paths = AppPaths.forDirectories(
                temporaryDirectory.resolve("image-data"), temporaryDirectory.resolve("image-legacy"));
        try (Database database = Database.open(paths))
        {
            StoryRepository stories = new StoryRepository(database);
            BlockRepository blocks = new BlockRepository(database);
            StoryCardRepository cards = new StoryCardRepository(database);
            ImageRepository images = new ImageRepository(database);
            List<String> requestedUrls = new ArrayList<>();
            AIDungeonImports importer = new AIDungeonImports(
                    database, stories, blocks, cards, images, "System", imageUrl ->
                    {
                        requestedUrls.add(imageUrl);
                        return new AIDungeonImports.DownloadedAdventureImage(
                                ONE_PIXEL_PNG, "image/png", 1, 1);
                    });

            Path archive = writeZip("image-adventure.zip", Map.of(
                    "metadata.json", """
                            {"adventure": {"title": "Illustrated Adventure"}}
                            """,
                    "actions-1.json", """
                            {
                              "partNumber": 1,
                              "actions": [
                                {"type": "start", "text": "Opening scene."},
                                {
                                  "type": "see",
                                  "text": "A moonlit castle above the sea.",
                                  "imageUrl": "https://images.aidungeon.com/generated/example.png"
                                },
                                {"type": "continue", "text": "The story continues."}
                              ]
                            }
                            """));

            Story imported = importer.importAdventure(archive);

            assertEquals(List.of("https://images.aidungeon.com/generated/example.png"), requestedUrls);
            List<Block> importedBlocks = blocks.listForStory(imported.id());
            assertEquals(List.of(Role.ASSISTANT, Role.IMAGE, Role.ASSISTANT),
                    importedBlocks.stream().map(Block::role).toList());

            StoryImage importedImage = images.findById(importedBlocks.get(1).text()).orElseThrow();
            assertEquals(imported.id(), importedImage.storyId());
            assertEquals("A moonlit castle above the sea.", importedImage.prompt());
            assertEquals("image/png", importedImage.mimeType());
            assertEquals(1, importedImage.width());
            assertEquals(1, importedImage.height());
            assertEquals("", importedImage.workflowJson());
            assertArrayEquals(ONE_PIXEL_PNG, importedImage.imageBytes());
        }
    }

    @Test
    void adventureImportRollsBackStoryCardsAndBlocksTogether() throws Exception
    {
        AppPaths paths = AppPaths.forDirectories(
                temporaryDirectory.resolve("adventure-rollback-data"),
                temporaryDirectory.resolve("adventure-rollback-legacy"));
        try (Database database = Database.open(paths))
        {
            StoryRepository stories = new StoryRepository(database);
            BlockRepository blocks = new BlockRepository(database);
            StoryCardRepository cards = new StoryCardRepository(database);
            ImageRepository images = new ImageRepository(database);
            database.useConnection(connection ->
            {
                try (java.sql.Statement statement = connection.createStatement())
                {
                    statement.execute("""
                            CREATE TRIGGER inject_block_failure
                            BEFORE INSERT ON blocks
                            WHEN NEW.text = 'Fail'
                            BEGIN
                                SELECT RAISE(ABORT, 'injected block failure');
                            END
                            """);
                }
            });

            Path archive = writeZip("rollback-adventure.zip", Map.of(
                    "metadata.json", """
                            {
                              "adventure": {"title": "Must Roll Back"},
                              "state": {
                                "storyCards": [
                                  {"title": "Temporary", "keys": "temp", "entry": "Temporary card"}
                                ]
                              }
                            }
                            """,
                    "actions-1.json", """
                            {
                              "partNumber": 1,
                              "actions": [
                                {
                                  "type": "see",
                                  "text": "Temporary image",
                                  "imageUrl": "https://images.aidungeon.com/generated/rollback.png"
                                },
                                {"type": "continue", "text": "Fail"}
                              ]
                            }
                            """));

            AIDungeonImports importer = new AIDungeonImports(
                    database, stories, blocks, cards, images, "System",
                    imageUrl -> new AIDungeonImports.DownloadedAdventureImage(
                            ONE_PIXEL_PNG, "image/png", 1, 1));
            assertThrows(Exception.class, () -> importer.importAdventure(archive));
            assertTrue(stories.listAll().isEmpty());
            assertEquals(0, countRows(database, "story_cards"));
            assertEquals(0, countRows(database, "blocks"));
            assertEquals(0, countRows(database, "images"));
        }
    }

    @Test
    void malformedActionChunkDoesNotCreateAPartialAdventure() throws Exception
    {
        AppPaths paths = AppPaths.forDirectories(
                temporaryDirectory.resolve("malformed-data"), temporaryDirectory.resolve("malformed-legacy"));
        try (Database database = Database.open(paths))
        {
            StoryRepository stories = new StoryRepository(database);
            BlockRepository blocks = new BlockRepository(database);
            StoryCardRepository cards = new StoryCardRepository(database);
            Path archive = writeZip("malformed-adventure.zip", Map.of(
                    "metadata.json", """
                            {"adventure": {"title": "Malformed"}}
                            """,
                    "actions-1.json", """
                            {"partNumber": 1, "notActions": []}
                            """));

            AIDungeonImports importer = new AIDungeonImports(
                    database, stories, blocks, cards, new ImageRepository(database), "System");
            assertThrows(Exception.class, () -> importer.importAdventure(archive));
            assertTrue(stories.listAll().isEmpty());
        }
    }

    private Path writeZip(String fileName, Map<String, String> entries) throws Exception
    {
        Path archive = temporaryDirectory.resolve(fileName);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive), StandardCharsets.UTF_8))
        {
            for (Map.Entry<String, String> entry : entries.entrySet())
            {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return archive;
    }

    private int countRows(Database database, String table) throws Exception
    {
        return database.withConnection(connection ->
        {
            try (java.sql.Statement statement = connection.createStatement();
                    java.sql.ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table))
            {
                return result.getInt(1);
            }
        });
    }
}
