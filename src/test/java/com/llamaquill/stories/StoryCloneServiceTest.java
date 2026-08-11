package com.llamaquill.stories;

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

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryCloneServiceTest
{
    @TempDir
    Path temporaryDirectory;

    @Test
    void defaultsCopyReusableStoryContentButNotTheWholeAdventure()
    {
        Story source = story("source");
        StoryCloneRequest defaults = StoryCloneRequest.defaultsFor(source);

        assertEquals("Source - Clone", defaults.newName());
        assertTrue(defaults.includeStoryDetails());
        assertTrue(defaults.includeStoryCards());
        assertTrue(defaults.includeInitialBlock());
        assertFalse(defaults.includeAllBlocks());
    }

    @Test
    void clonesDetailsCardsAndOnlyTheInitialBlockAtomically() throws Exception
    {
        try (Fixture fixture = fixture("initial"))
        {
            Story source = fixture.insertSource();
            fixture.cards.insert(new StoryCard(
                    "card", source.id(), "Mia", "Mia", "Quiet observer", "Character", "Private note", true));
            fixture.blocks.insert(block("first", source.id(), Role.ASSISTANT, "Opening", 0));
            fixture.blocks.insert(block("second", source.id(), Role.USER, "Later turn", 1));

            Story clone = fixture.service.cloneStory(source.id(),
                    new StoryCloneRequest("  Scenario Copy  ", true, true, true, false));

            assertEquals("Scenario Copy", clone.title());
            assertEquals(source.systemPrompt(), clone.systemPrompt());
            assertEquals(source.plotEssentials(), clone.plotEssentials());
            assertEquals(source.authorNote(), clone.authorNote());
            assertEquals(source.storyCardGenerationContext(), clone.storyCardGenerationContext());
            assertTrue(clone.forcePinAllStoryCards());
            assertEquals(source.selectedSeePromptPresetId(), clone.selectedSeePromptPresetId());
            assertNotEquals(source.id(), clone.id());

            List<StoryCard> clonedCards = fixture.cards.listForStory(clone.id());
            assertEquals(1, clonedCards.size());
            StoryCard clonedCard = clonedCards.getFirst();
            assertNotEquals("card", clonedCard.id());
            assertEquals(clone.id(), clonedCard.storyId());
            assertEquals("Mia", clonedCard.title());
            assertEquals("Private note", clonedCard.notes());
            assertTrue(clonedCard.pinned());

            List<Block> clonedBlocks = fixture.blocks.listForStory(clone.id());
            assertEquals(1, clonedBlocks.size());
            assertEquals("Opening", clonedBlocks.getFirst().text());
            assertEquals(0, clonedBlocks.getFirst().position());
            assertEquals(2, fixture.blocks.listForStory(source.id()).size());
        }
    }

    @Test
    void allBlocksWinsAndCreatesIndependentCopiesOfStoryImages() throws Exception
    {
        try (Fixture fixture = fixture("all"))
        {
            Story source = fixture.insertSource();
            StoryImage sourceImage = new StoryImage(
                    "image", source.id(), "A moonlit archive", "image/png", 2, 3, 7,
                    "{\"workflow\":true}", new byte[] { 1, 2, 3 }, Timestamps.now());
            fixture.images.insert(sourceImage);
            fixture.blocks.insert(block("first", source.id(), Role.ASSISTANT, "Opening", 0));
            fixture.blocks.insert(block("image-block", source.id(), Role.IMAGE, sourceImage.id(), 1));

            Story clone = fixture.service.cloneStory(source.id(),
                    new StoryCloneRequest("Blank Detail Copy", false, false, false, true));

            assertEquals("", clone.systemPrompt());
            assertEquals("", clone.plotEssentials());
            assertEquals("", clone.authorNote());
            assertEquals("", clone.storyCardGenerationContext());
            assertFalse(clone.forcePinAllStoryCards());
            assertEquals("builtin:none", clone.selectedSeePromptPresetId());
            assertTrue(fixture.cards.listForStory(clone.id()).isEmpty());

            List<Block> clonedBlocks = fixture.blocks.listForStory(clone.id());
            assertEquals(2, clonedBlocks.size());
            assertEquals("Opening", clonedBlocks.getFirst().text());
            Block clonedImageBlock = clonedBlocks.getLast();
            assertEquals(Role.IMAGE, clonedImageBlock.role());
            assertNotEquals(sourceImage.id(), clonedImageBlock.text());

            StoryImage clonedImage = fixture.images.findById(clonedImageBlock.text()).orElseThrow();
            assertEquals(clone.id(), clonedImage.storyId());
            assertEquals(sourceImage.prompt(), clonedImage.prompt());
            assertEquals(sourceImage.workflowJson(), clonedImage.workflowJson());
            assertEquals(sourceImage.batchSize(), clonedImage.batchSize());
            assertArrayEquals(sourceImage.imageBytes(), clonedImage.imageBytes());
            assertTrue(fixture.images.findById(sourceImage.id()).isPresent());
        }
    }

    @Test
    void rollsBackTheCloneWhenAnImageBlockHasNoImage() throws Exception
    {
        try (Fixture fixture = fixture("rollback"))
        {
            Story source = fixture.insertSource();
            fixture.blocks.insert(block("broken", source.id(), Role.IMAGE, "missing-image", 0));

            assertThrows(SQLException.class, () -> fixture.service.cloneStory(source.id(),
                    new StoryCloneRequest("Broken Copy", true, true, true, false)));
            assertEquals(List.of(source), fixture.stories.listAll());
        }
    }

    private Fixture fixture(String name) throws Exception
    {
        AppPaths paths = AppPaths.forDirectories(
                temporaryDirectory.resolve(name + "-data"),
                temporaryDirectory.resolve(name + "-legacy"));
        return new Fixture(Database.open(paths));
    }

    private static Story story(String id)
    {
        String now = Timestamps.now();
        return new Story(id, "Source", "System", "Plot", "Author note",
                "Keep generated cards grounded in the source story.", true, "builtin:painterly", now, now);
    }

    private static Block block(String id, String storyId, Role role, String text, int position)
    {
        return new Block(id, storyId, role, text, Timestamps.now(), position);
    }

    private static final class Fixture implements AutoCloseable
    {
        private final Database database;
        private final StoryRepository stories;
        private final BlockRepository blocks;
        private final StoryCardRepository cards;
        private final ImageRepository images;
        private final StoryCloneService service;

        private Fixture(Database database)
        {
            this.database = database;
            stories = new StoryRepository(database);
            blocks = new BlockRepository(database);
            cards = new StoryCardRepository(database);
            images = new ImageRepository(database);
            service = new StoryCloneService(database, stories, blocks, cards, images);
        }

        private Story insertSource() throws SQLException
        {
            Story source = story("source");
            stories.insert(source);
            return source;
        }

        @Override
        public void close() throws SQLException
        {
            database.close();
        }
    }
}
