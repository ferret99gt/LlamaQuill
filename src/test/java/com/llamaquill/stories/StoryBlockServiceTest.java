package com.llamaquill.stories;

import com.llamaquill.db.AppPaths;
import com.llamaquill.db.BlockRepository;
import com.llamaquill.db.Database;
import com.llamaquill.db.ImageRepository;
import com.llamaquill.db.StoryRepository;
import com.llamaquill.model.Block;
import com.llamaquill.model.Role;
import com.llamaquill.model.Story;
import com.llamaquill.model.StoryImage;
import com.llamaquill.util.Timestamps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryBlockServiceTest
{
    @TempDir
    Path temporaryDirectory;

    @Test
    void ownsBlockEditsDeletesAndLinkedImageCleanup() throws Exception
    {
        AppPaths paths = AppPaths.forDirectories(
                temporaryDirectory.resolve("data"),
                temporaryDirectory.resolve("legacy"));
        try (Database database = Database.open(paths))
        {
            StoryRepository stories = new StoryRepository(database);
            BlockRepository blocks = new BlockRepository(database);
            ImageRepository images = new ImageRepository(database);
            StoryBlockService service = new StoryBlockService(database, blocks, images::deleteById);
            Story story = story("story");
            stories.insert(story);

            Block text = block("text", story.id(), Role.ASSISTANT, "Before", 0);
            blocks.insert(text);
            service.updateText(text, "After");
            assertEquals("After", blocks.findById(text.id()).orElseThrow().text());

            StoryImage image = new StoryImage("image", story.id(), "A room", "image/png",
                    1, 1, "{}", new byte[] { 1, 2, 3 }, Timestamps.now());
            images.insert(image);
            Block imageBlock = block("image-block", story.id(), Role.IMAGE, image.id(), 1);
            blocks.insert(imageBlock);

            List<Block> remaining = service.deleteHead(story, imageBlock);
            assertEquals(List.of(blocks.findById(text.id()).orElseThrow()), remaining);
            assertTrue(images.findById(image.id()).isEmpty());

            assertTrue(service.delete(story, text).isEmpty());
            assertTrue(blocks.listForStory(story.id()).isEmpty());
        }
    }

    @Test
    void rejectsStaleOrCrossStoryMutations() throws Exception
    {
        AppPaths paths = AppPaths.forDirectories(
                temporaryDirectory.resolve("stale-data"),
                temporaryDirectory.resolve("stale-legacy"));
        try (Database database = Database.open(paths))
        {
            StoryRepository stories = new StoryRepository(database);
            BlockRepository blocks = new BlockRepository(database);
            ImageRepository images = new ImageRepository(database);
            StoryBlockService service = new StoryBlockService(database, blocks, images::deleteById);
            Story first = story("first");
            Story second = story("second");
            stories.insert(first);
            stories.insert(second);

            Block older = block("older", first.id(), Role.ASSISTANT, "Older", 0);
            Block head = block("head", first.id(), Role.ASSISTANT, "Head", 1);
            blocks.insert(older);
            blocks.insert(head);

            assertThrows(SQLException.class, () -> service.deleteHead(first, older));
            assertEquals(2, blocks.listForStory(first.id()).size());
            assertThrows(IllegalArgumentException.class, () -> service.delete(second, head));

            blocks.deleteById(head.id());
            assertThrows(SQLException.class, () -> service.updateText(head, "Missing"));
            assertThrows(SQLException.class, () -> service.delete(first, head));
        }
    }

    private static Story story(String id)
    {
        String now = Timestamps.now();
        return new Story(id, id, "System", "", "", now, now);
    }

    private static Block block(String id, String storyId, Role role, String text, int position)
    {
        return new Block(id, storyId, role, text, Timestamps.now(), position);
    }
}
