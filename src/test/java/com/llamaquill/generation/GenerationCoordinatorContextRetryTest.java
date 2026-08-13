package com.llamaquill.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.llamaquill.serviceClients.OllamaChatResult;
import com.llamaquill.serviceClients.OllamaClient;
import com.llamaquill.serviceClients.OllamaContextLimitException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

class GenerationCoordinatorContextRetryTest
{
    @TempDir
    Path temporaryDirectory;

    @Test
    void continueRecompilesAndRetriesOnceAfterAContextLimitError() throws Exception
    {
        Path root = temporaryDirectory.resolve("continue-context-retry");
        try (Database database = Database.open(AppPaths.forDirectories(root.resolve("data"), root.resolve("legacy"))))
        {
            StoryRepository stories = new StoryRepository(database);
            BlockRepository blocks = new BlockRepository(database);
            StoryCardRepository cards = new StoryCardRepository(database);
            Story story = new Story("story", "Story", "Write prose.", "", "", "now", "now");
            stories.insert(story);
            blocks.insert(new Block(
                    "head", story.id(), Role.ASSISTANT, "Long imported story. ".repeat(2_000), "now", 1));
            ContextRejectingOllamaClient ollama = new ContextRejectingOllamaClient();
            GenerationCoordinator coordinator = new GenerationCoordinator(
                    database, blocks, stories, cards, new PromptCompiler(), ollama);

            GenerationCoordinator.ContinueResult result = coordinator.continueStory(
                    story, GenerationSettings.defaults());

            assertEquals(GenerationCoordinator.ResultStatus.APPLIED, result.status());
            assertEquals("Recovered continuation.", result.block().text());
            assertEquals(2, ollama.requests.size());
            assertTrue(totalCharacters(ollama.requests.get(1)) < totalCharacters(ollama.requests.get(0)));
        }
    }

    private static int totalCharacters(List<ChatMessage> messages)
    {
        return messages.stream().mapToInt(message -> message.content().length()).sum();
    }

    private static final class ContextRejectingOllamaClient extends OllamaClient
    {
        private final List<List<ChatMessage>> requests = new ArrayList<>();

        @Override
        public OllamaChatResult chat(List<ChatMessage> messages, GenerationSettings settings,
                Consumer<String> generatedChunkConsumer) throws OllamaContextLimitException
        {
            requests.add(List.copyOf(messages));
            if (requests.size() == 1)
            {
                throw new OllamaContextLimitException(
                        "Ollama returned status 400 for /api/chat: context exceeded",
                        "http://localhost:11434/api/chat", 400, "context exceeded", 8_300, 8_192);
            }
            String content = "Recovered continuation.";
            generatedChunkConsumer.accept(content);
            return new OllamaChatResult(
                    settings.modelName(), content, 7_900, 12, "stop", 1, 1, 1, 1, 0);
        }
    }
}
