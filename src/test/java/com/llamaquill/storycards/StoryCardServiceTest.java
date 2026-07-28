package com.llamaquill.storycards;

import com.llamaquill.db.AppPaths;
import com.llamaquill.db.Database;
import com.llamaquill.db.StoryCardRepository;
import com.llamaquill.db.StoryRepository;
import com.llamaquill.model.Story;
import com.llamaquill.model.StoryCard;
import com.llamaquill.util.Timestamps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryCardServiceTest
{
    @TempDir
    Path temporaryDirectory;

    @Test
    void ownsCardCreationUpdateOwnershipAndDeletion() throws Exception
    {
        AppPaths paths = AppPaths.forDirectories(
                temporaryDirectory.resolve("data"),
                temporaryDirectory.resolve("legacy"));
        try (Database database = Database.open(paths))
        {
            StoryRepository stories = new StoryRepository(database);
            String now = Timestamps.now();
            stories.insert(new Story("story-a", "A", "", "", "", now, now));
            stories.insert(new Story("story-b", "B", "", "", "", now, now));

            StoryCardRepository repository = new StoryCardRepository(database);
            StoryCardService service = new StoryCardService(repository);
            StoryCard card = new StoryCard("card", "story-a", "Mia", "Mia",
                    "Initial entry", "Character", "", true);

            service.create(card);
            assertEquals(card, service.listForStory("story-a").getFirst());
            assertThrows(SQLException.class, () -> service.create(card));

            StoryCard updated = new StoryCard(card.id(), card.storyId(), card.title(), card.triggers(),
                    "Updated entry", card.type(), "Revision note", false);
            service.update(updated);
            assertEquals(updated, repository.findById(card.id()).orElseThrow());

            StoryCard wrongOwner = new StoryCard(card.id(), "story-b", card.title(), card.triggers(),
                    card.content(), card.type(), card.notes(), card.pinned());
            assertThrows(IllegalArgumentException.class, () -> service.update(wrongOwner));
            assertThrows(IllegalArgumentException.class, () -> service.delete(wrongOwner));

            service.delete(updated);
            assertTrue(repository.findById(card.id()).isEmpty());
            assertThrows(SQLException.class, () -> service.delete(updated));
        }
    }
}
