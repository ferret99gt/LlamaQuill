package com.llamaquill.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.llamaquill.db.AppPaths;
import com.llamaquill.db.BlockRepository;
import com.llamaquill.db.Database;
import com.llamaquill.db.ImageRepository;
import com.llamaquill.db.StoryCardRepository;
import com.llamaquill.db.StoryRepository;
import com.llamaquill.generation.AuxiliaryGenerationService;
import com.llamaquill.model.Block;
import com.llamaquill.model.Role;
import com.llamaquill.model.Story;
import com.llamaquill.prompt.PromptCompiler;
import com.llamaquill.serviceClients.ComfyUiClient;
import com.llamaquill.serviceClients.OllamaClient;
import com.llamaquill.util.Timestamps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Base64;

class ImageGenerationCoordinatorConcurrencyTest
{
    private static final byte[] ONE_PIXEL_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @TempDir
    Path temporaryDirectory;

    @Test
    void staleImageInsertionLeavesBothTheBlockAndImageTablesUntouched() throws Exception
    {
        AppPaths paths = AppPaths.forDirectories(
                temporaryDirectory.resolve("data"), temporaryDirectory.resolve("legacy"));
        try (Database database = Database.open(paths))
        {
            StoryRepository stories = new StoryRepository(database);
            BlockRepository blocks = new BlockRepository(database);
            StoryCardRepository cards = new StoryCardRepository(database);
            ImageRepository images = new ImageRepository(database);
            String now = Timestamps.now();
            Story story = new Story("story", "Story", "", "", "", now, now);
            stories.insert(story);
            Block head = new Block("head", story.id(), Role.ASSISTANT, "Text", now, 1);
            blocks.insert(head);

            ImageGenerationCoordinator coordinator = new ImageGenerationCoordinator(
                    database, images, blocks, stories, cards,
                    new AuxiliaryGenerationService(new PromptCompiler(), new OllamaClient()), new ComfyUiClient());
            ImageGenerationCoordinator.PendingImage pending = new ImageGenerationCoordinator.PendingImage(
                    ONE_PIXEL_PNG, "image/png", "{}");

            ImageGenerationCoordinator.ImageMutationResult stale = coordinator.insertOrReplaceImage(
                    story, "different-head", pending, "Prompt", null);
            assertTrue(stale.stale());
            assertEquals(1, blocks.listForStory(story.id()).size());
            assertEquals(0, count(database, "images"));

            ImageGenerationCoordinator.ImageMutationResult applied = coordinator.insertOrReplaceImage(
                    story, head.id(), pending, "Prompt", null);
            assertFalse(applied.stale());
            assertEquals(2, blocks.listForStory(story.id()).size());
            assertEquals(1, count(database, "images"));
        }
    }

    private static int count(Database database, String table) throws Exception
    {
        return database.withConnection(connection ->
        {
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table))
            {
                return result.next() ? result.getInt(1) : 0;
            }
        });
    }
}
