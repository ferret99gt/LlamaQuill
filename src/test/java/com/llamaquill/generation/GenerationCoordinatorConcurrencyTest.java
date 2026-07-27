package com.llamaquill.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.llamaquill.db.AppPaths;
import com.llamaquill.db.BlockRepository;
import com.llamaquill.db.Database;
import com.llamaquill.db.StoryCardRepository;
import com.llamaquill.db.StoryRepository;
import com.llamaquill.model.Block;
import com.llamaquill.model.ChatMessage;
import com.llamaquill.model.GenerationSettings;
import com.llamaquill.model.Role;
import com.llamaquill.model.Story;
import com.llamaquill.prompt.PromptCompiler;
import com.llamaquill.serviceClients.OllamaClient;
import com.llamaquill.serviceClients.OllamaChatResult;
import com.llamaquill.util.Timestamps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

class GenerationCoordinatorConcurrencyTest
{
    @TempDir
    Path temporaryDirectory;

    @Test
    void continueRejectsAResultWhenTheExpectedHeadChangedDuringGeneration() throws Exception
    {
        try (Fixture fixture = fixture("continue-stale");
             ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor())
        {
            Story story = fixture.createStory("story-a");
            fixture.insertBlock(story, "original-head", Role.ASSISTANT, "Original", 1);

            Future<GenerationCoordinator.ContinueResult> future = executor.submit(
                    () -> fixture.coordinator.continueStory(story, GenerationSettings.defaults(), null));
            fixture.ollama.awaitRequest();
            fixture.insertBlock(story, "intervening-head", Role.USER, "Changed", 2);
            fixture.ollama.complete("Generated response");

            GenerationCoordinator.ContinueResult result = future.get(5, TimeUnit.SECONDS);
            assertEquals(GenerationCoordinator.ResultStatus.STALE, result.status());
            assertEquals(List.of("original-head", "intervening-head"),
                    fixture.blocks.listForStory(story.id()).stream().map(Block::id).toList());
        }
    }

    @Test
    void workForOneStoryNeverUsesTheOtherStoryAsItsTarget() throws Exception
    {
        try (Fixture fixture = fixture("cross-story");
             ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor())
        {
            Story storyA = fixture.createStory("story-a");
            Story storyB = fixture.createStory("story-b");
            fixture.insertBlock(storyA, "a-head", Role.ASSISTANT, "A", 1);
            fixture.insertBlock(storyB, "b-head", Role.ASSISTANT, "B", 1);

            Future<GenerationCoordinator.ContinueResult> future = executor.submit(
                    () -> fixture.coordinator.continueStory(storyA, GenerationSettings.defaults(), null));
            fixture.ollama.awaitRequest();
            fixture.ollama.complete("A continuation");

            GenerationCoordinator.ContinueResult result = future.get(5, TimeUnit.SECONDS);
            assertEquals(GenerationCoordinator.ResultStatus.APPLIED, result.status());
            assertEquals(2, fixture.blocks.listForStory(storyA.id()).size());
            assertEquals(1, fixture.blocks.listForStory(storyB.id()).size());
            assertEquals("B", fixture.blocks.listForStory(storyB.id()).getFirst().text());
        }
    }

    @Test
    void generationTouchDoesNotOverwriteStoryDetailsEditedWhileTheModelRuns() throws Exception
    {
        try (Fixture fixture = fixture("detail-edit");
             ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor())
        {
            Story story = fixture.createStory("story-a");
            fixture.insertBlock(story, "a-head", Role.ASSISTANT, "A", 1);

            Future<GenerationCoordinator.ContinueResult> future = executor.submit(
                    () -> fixture.coordinator.continueStory(story, GenerationSettings.defaults(), null));
            fixture.ollama.awaitRequest();
            Story edited = new Story(story.id(), story.title(), "Edited System Prompt", "Edited Memory", "Edited Note",
                    story.createdAt(), Timestamps.now());
            fixture.stories.update(edited);
            fixture.ollama.complete("Continuation");

            GenerationCoordinator.ContinueResult result = future.get(5, TimeUnit.SECONDS);
            assertEquals(GenerationCoordinator.ResultStatus.APPLIED, result.status());
            Story persisted = fixture.stories.findById(story.id()).orElseThrow();
            assertEquals("Edited System Prompt", persisted.systemPrompt());
            assertEquals("Edited Memory", persisted.plotEssentials());
            assertEquals("Edited Note", persisted.authorNote());
            assertEquals("Edited System Prompt", result.updatedStory().systemPrompt());
        }
    }

    @Test
    void takeTurnPreservesItsSeedButRejectsAResponseAfterAnInterveningHead() throws Exception
    {
        try (Fixture fixture = fixture("turn-stale");
             ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor())
        {
            Story story = fixture.createStory("story-a");
            fixture.insertBlock(story, "original-head", Role.ASSISTANT, "Original", 1);

            Future<GenerationCoordinator.TurnResult> future = executor.submit(
                    () -> fixture.coordinator.takeTurn(story, "User action", GenerationSettings.defaults(), null));
            fixture.ollama.awaitRequest();
            List<Block> afterSeed = fixture.blocks.listForStory(story.id());
            Block seed = afterSeed.getLast();
            assertEquals(Role.USER, seed.role());
            fixture.insertBlock(story, "intervening-head", Role.USER, "Changed", seed.position() + 1);
            fixture.ollama.complete("Generated response");

            GenerationCoordinator.TurnResult result = future.get(5, TimeUnit.SECONDS);
            assertEquals(GenerationCoordinator.ResultStatus.STALE, result.status());
            assertFalse(result.generated());
            assertEquals(3, fixture.blocks.listForStory(story.id()).size());
            assertTrue(fixture.blocks.listForStory(story.id()).stream().anyMatch(block -> block.id().equals(seed.id())));
        }
    }

    private Fixture fixture(String name) throws Exception
    {
        Path root = temporaryDirectory.resolve(name);
        Database database = Database.open(AppPaths.forDirectories(root.resolve("data"), root.resolve("legacy")));
        StoryRepository stories = new StoryRepository(database);
        BlockRepository blocks = new BlockRepository(database);
        StoryCardRepository cards = new StoryCardRepository(database);
        BlockingOllamaClient ollama = new BlockingOllamaClient();
        GenerationCoordinator coordinator = new GenerationCoordinator(database, blocks, stories, cards,
                new PromptCompiler(), ollama);
        return new Fixture(database, stories, blocks, coordinator, ollama);
    }

    private static final class Fixture implements AutoCloseable
    {
        private final Database database;
        private final StoryRepository stories;
        private final BlockRepository blocks;
        private final GenerationCoordinator coordinator;
        private final BlockingOllamaClient ollama;

        private Fixture(Database database, StoryRepository stories, BlockRepository blocks,
                GenerationCoordinator coordinator, BlockingOllamaClient ollama)
        {
            this.database = database;
            this.stories = stories;
            this.blocks = blocks;
            this.coordinator = coordinator;
            this.ollama = ollama;
        }

        private Story createStory(String id) throws Exception
        {
            String now = Timestamps.now();
            Story story = new Story(id, id, "System", "", "", now, now);
            stories.insert(story);
            return story;
        }

        private void insertBlock(Story story, String id, Role role, String text, int position) throws Exception
        {
            blocks.insert(new Block(id, story.id(), role, text, Timestamps.now(), position));
        }

        @Override
        public void close() throws Exception
        {
            database.close();
        }
    }

    private static final class BlockingOllamaClient extends OllamaClient
    {
        private final CountDownLatch requested = new CountDownLatch(1);
        private final CountDownLatch completed = new CountDownLatch(1);
        private volatile String response;

        @Override
        public OllamaChatResult chat(List<ChatMessage> messages, GenerationSettings settings)
                throws IOException, InterruptedException
        {
            return new OllamaChatResult(settings.modelName(), respond(), 10, 5, "stop",
                    1, 0, 1, 1, 0);
        }

        private String respond() throws InterruptedException
        {
            requested.countDown();
            if (!completed.await(5, TimeUnit.SECONDS))
            {
                throw new IllegalStateException("Test generation was not released.");
            }
            return response;
        }

        private void awaitRequest() throws InterruptedException
        {
            assertTrue(requested.await(5, TimeUnit.SECONDS), "Generation did not start.");
        }

        private void complete(String response)
        {
            this.response = response;
            completed.countDown();
        }
    }
}
