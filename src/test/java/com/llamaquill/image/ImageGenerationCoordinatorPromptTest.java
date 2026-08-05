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
import com.llamaquill.model.AppSettings;
import com.llamaquill.model.Block;
import com.llamaquill.model.ChatMessage;
import com.llamaquill.model.ConversationLayout;
import com.llamaquill.model.GenerationSettings;
import com.llamaquill.model.Role;
import com.llamaquill.model.Story;
import com.llamaquill.model.StoryCardWrappingStyle;
import com.llamaquill.prompt.PromptCompiler;
import com.llamaquill.serviceClients.ComfyUiClient;
import com.llamaquill.serviceClients.OllamaChatResult;
import com.llamaquill.serviceClients.OllamaClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

class ImageGenerationCoordinatorPromptTest
{
    @TempDir
    Path temporaryDirectory;

    @Test
    void seeForcesRoleAwareLayoutAndAppliesThePerRequestResponseLengthChoice() throws Exception
    {
        AppPaths paths = AppPaths.forDirectories(
                temporaryDirectory.resolve("data"), temporaryDirectory.resolve("legacy"));
        try (Database database = Database.open(paths))
        {
            StoryRepository stories = new StoryRepository(database);
            BlockRepository blocks = new BlockRepository(database);
            StoryCardRepository cards = new StoryCardRepository(database);
            Story story = new Story("story", "Story", "System", "World", "Note", "now", "now");
            stories.insert(story);
            blocks.insert(new Block("block", story.id(), Role.ASSISTANT,
                    "Mia stands beneath the station lights.", "now", 1));

            CapturingOllamaClient ollama = new CapturingOllamaClient();
            ImageGenerationCoordinator coordinator = new ImageGenerationCoordinator(
                    database, new ImageRepository(database), blocks, stories, cards,
                    new AuxiliaryGenerationService(new PromptCompiler(), ollama), new ComfyUiClient());
            GenerationSettings flattened = settingsWithResponseLength();

            coordinator.generateImagePromptResult(story, "Show the falling rain.", flattened, true);

            assertEquals(ConversationLayout.FLATTENED, flattened.conversationLayout());
            assertTrue(flattened.responseLengthEnabled());
            assertEquals(ConversationLayout.ROLE_AWARE, ollama.settings.conversationLayout());
            assertFalse(ollama.settings.responseLengthEnabled());
            assertEquals("user", ollama.messages.getLast().role());
            assertTrue(ollama.messages.getLast().content().contains("Show the falling rain."));
            assertTrue(ollama.messages.stream().anyMatch(message -> "assistant".equals(message.role())
                    && message.content().contains("Mia stands beneath the station lights.")));

            coordinator.generateImagePromptResult(story, "", flattened, false);
            assertEquals(ConversationLayout.ROLE_AWARE, ollama.settings.conversationLayout());
            assertTrue(ollama.settings.responseLengthEnabled());
        }
    }

    private static GenerationSettings settingsWithResponseLength()
    {
        return new GenerationSettings(
                GenerationSettings.DEFAULT_MODEL, GenerationSettings.DEFAULT_OLLAMA_HOST, 8192, 1.0,
                true, 150,
                false, 0.8,
                false, 200,
                false, 0.95,
                false, 0.025,
                false, 1.0,
                false, 0.25,
                false, 0.0,
                false, 64,
                false, 1.05,
                4915, 7,
                AppSettings.DEFAULT_OLLAMA_KEEP_ALIVE_MINUTES,
                StoryCardWrappingStyle.NONE,
                ConversationLayout.FLATTENED);
    }

    private static final class CapturingOllamaClient extends OllamaClient
    {
        private List<ChatMessage> messages = List.of();
        private GenerationSettings settings;

        @Override
        public OllamaChatResult chatNonStreaming(List<ChatMessage> messages, GenerationSettings settings)
        {
            this.messages = List.copyOf(messages);
            this.settings = settings;
            return new OllamaChatResult(
                    settings.modelName(), " cinematic rain scene ",
                    100, 12, "stop", 1, 1, 1, 1, 0);
        }
    }
}
