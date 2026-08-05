package com.llamaquill.stories;

import com.llamaquill.db.AppPaths;
import com.llamaquill.db.BlockRepository;
import com.llamaquill.db.Database;
import com.llamaquill.db.StoryRepository;
import com.llamaquill.model.Block;
import com.llamaquill.model.Role;
import com.llamaquill.model.Story;
import com.llamaquill.util.Timestamps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryServiceTest
{
    @TempDir
    Path temporaryDirectory;

    @Test
    void ownsStoryCreationLoadingAndMutationLifecycle() throws Exception
    {
        AppPaths paths = AppPaths.forDirectories(
                temporaryDirectory.resolve("data"),
                temporaryDirectory.resolve("legacy"));
        try (Database database = Database.open(paths))
        {
            StoryRepository stories = new StoryRepository(database);
            BlockRepository blocks = new BlockRepository(database);
            StoryService service = new StoryService(stories, blocks);

            StoryService.StoryDocument initial =
                    service.loadOrCreate("Untitled Story", "Default system");
            assertEquals("Untitled Story", initial.story().title());
            assertEquals("Default system", initial.story().systemPrompt());
            assertTrue(initial.blocks().isEmpty());
            assertEquals(initial.story().id(),
                    service.loadOrCreate("Ignored", "Ignored").story().id());

            Story forcePinned = service.updateForcePinAllStoryCards(initial.story(), true);
            assertTrue(forcePinned.forcePinAllStoryCards());
            assertTrue(stories.findById(forcePinned.id()).orElseThrow().forcePinAllStoryCards());

            Story renamed = service.rename(forcePinned, "  The Archive  ");
            assertEquals("The Archive", renamed.title());
            assertTrue(renamed.forcePinAllStoryCards());
            assertEquals("The Archive", stories.findById(renamed.id()).orElseThrow().title());

            Story detailed = service.updateDetails(renamed, "System", "Plot", "Note");
            assertEquals("System", detailed.systemPrompt());
            assertEquals("Plot", detailed.plotEssentials());
            assertEquals("Note", detailed.authorNote());
            assertEquals(detailed, stories.findById(detailed.id()).orElseThrow());

            Story contextualized = service.updateStoryCardGenerationContext(
                    detailed, "  Keep regenerated cards consistent with the northern setting.  ");
            assertEquals("Keep regenerated cards consistent with the northern setting.",
                    contextualized.storyCardGenerationContext());
            assertEquals(contextualized, stories.findById(contextualized.id()).orElseThrow());

            Story individuallyPinned = service.updateForcePinAllStoryCards(contextualized, false);
            assertFalse(individuallyPinned.forcePinAllStoryCards());
            assertEquals(individuallyPinned, stories.findById(individuallyPinned.id()).orElseThrow());

            Block block = new Block("block", individuallyPinned.id(), Role.ASSISTANT,
                    "Loaded through the service.", Timestamps.now(), 0);
            blocks.insert(block);
            StoryService.StoryDocument loaded = service.reload(individuallyPinned.id(), null);
            assertEquals(individuallyPinned, loaded.story());
            assertEquals(block, loaded.blocks().getFirst());
            assertThrows(UnsupportedOperationException.class, () -> loaded.blocks().add(block));

            Story touched = service.touch(individuallyPinned);
            assertEquals(individuallyPinned.id(), touched.id());
            service.delete(touched);
            assertTrue(stories.findById(touched.id()).isEmpty());
            assertTrue(blocks.listForStory(touched.id()).isEmpty());
        }
    }

    @Test
    void rejectsBlankStoryTitles()
    {
        AppPaths paths = AppPaths.forDirectories(
                temporaryDirectory.resolve("blank-data"),
                temporaryDirectory.resolve("blank-legacy"));
        assertThrows(IllegalArgumentException.class, () ->
        {
            try (Database database = Database.open(paths))
            {
                StoryService service = new StoryService(
                        new StoryRepository(database), new BlockRepository(database));
                service.create("   ", "System");
            }
        });
    }
}
