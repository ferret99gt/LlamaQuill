package com.llamaquill.imports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.llamaquill.db.AppPaths;
import com.llamaquill.db.BlockRepository;
import com.llamaquill.db.Database;
import com.llamaquill.db.StoryCardRepository;
import com.llamaquill.db.StoryRepository;
import com.llamaquill.model.Story;
import com.llamaquill.model.StoryCard;
import com.llamaquill.util.Timestamps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class AIDungeonImportsTest
{
    @TempDir
    Path temporaryDirectory;

    @Test
    void cardImportMatchesTitlesCaseInsensitivelyAndPreservesPins() throws Exception
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
                      {"title":"THE KEEP","keys":"castle","value":"Final update"}
                    ]
                    """, StandardCharsets.UTF_8);

            AIDungeonImports importer = new AIDungeonImports(database, stories, blocks, cards, "System");
            assertEquals(2, importer.importStoryCards(input, story.id(), false));

            List<StoryCard> imported = cards.listForStory(story.id());
            assertEquals(1, imported.size());
            assertEquals("Final update", imported.getFirst().content());
            assertEquals("castle", imported.getFirst().triggers());
            assertTrue(imported.getFirst().pinned());
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

            AIDungeonImports importer = new AIDungeonImports(database, stories, blocks, cards, "System");
            assertThrows(Exception.class, () -> importer.importStoryCards(input, story.id(), true));

            List<StoryCard> persisted = cards.listForStory(story.id());
            assertEquals(1, persisted.size());
            assertEquals("Existing", persisted.getFirst().title());
            assertTrue(persisted.getFirst().pinned());
        }
    }
}
