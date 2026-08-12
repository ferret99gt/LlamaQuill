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
import com.llamaquill.model.ImageRatio;
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

            String stylePrompt = "Use a restrained watercolor style.";
            coordinator.generateImagePromptResult(
                    story, stylePrompt, "Show the falling rain.", flattened, true);

            assertEquals(ConversationLayout.FLATTENED, flattened.conversationLayout());
            assertTrue(flattened.responseLengthEnabled());
            assertEquals(ConversationLayout.ROLE_AWARE, ollama.settings.conversationLayout());
            assertFalse(ollama.settings.responseLengthEnabled());
            assertEquals("user", ollama.messages.getLast().role());
            String finalPrompt = ollama.messages.getLast().content();
            assertTrue(finalPrompt.contains("Show the falling rain."));
            assertTrue(finalPrompt.indexOf("# Style preset") < finalPrompt.indexOf("# User specific request"));
            assertTrue(finalPrompt.indexOf(stylePrompt) < finalPrompt.indexOf("Show the falling rain."));
            int sceneContextIndex = -1;
            for (int i = 0; i < ollama.messages.size(); i++)
            {
                ChatMessage message = ollama.messages.get(i);
                if ("assistant".equals(message.role())
                        && message.content().contains("Mia stands beneath the station lights."))
                {
                    sceneContextIndex = i;
                    break;
                }
            }
            assertTrue(sceneContextIndex >= 0);
            assertTrue(sceneContextIndex < ollama.messages.size() - 1);

            coordinator.generateImagePromptResult(story, "", flattened, false);
            assertEquals(ConversationLayout.ROLE_AWARE, ollama.settings.conversationLayout());
            assertTrue(ollama.settings.responseLengthEnabled());
            assertFalse(ollama.messages.getLast().content().contains("# Style preset"));
        }
    }

    @Test
    void imageGenerationUsesDialogOptionsInsteadOfTheCapturedGlobalDefaults() throws Exception
    {
        AppPaths paths = AppPaths.forDirectories(
                temporaryDirectory.resolve("image-options-data"),
                temporaryDirectory.resolve("image-options-legacy"));
        try (Database database = Database.open(paths))
        {
            StoryRepository stories = new StoryRepository(database);
            BlockRepository blocks = new BlockRepository(database);
            StoryCardRepository cards = new StoryCardRepository(database);
            CapturingComfyUiClient comfy = new CapturingComfyUiClient();
            ImageGenerationCoordinator coordinator = new ImageGenerationCoordinator(
                    database, new ImageRepository(database), blocks, stories, cards,
                    new AuxiliaryGenerationService(new PromptCompiler(), new OllamaClient()), comfy);
            AppSettings defaults = AppSettings.defaults().toBuilder()
                    .comfyDimension(720)
                    .comfyRatio(ImageRatio.PORTRAIT_9_16)
                    .comfyBatchSize(6)
                    .build();

            coordinator.generateImages(defaults, "Portrait prompt");
            assertEquals(408, comfy.width);
            assertEquals(720, comfy.height);
            assertEquals(6, comfy.batchSize);

            coordinator.generateImages(defaults, "Local override", 720, 480, 2);
            assertEquals(720, comfy.width);
            assertEquals(480, comfy.height);
            assertEquals(2, comfy.batchSize);
            assertEquals(ComfyUiClient.ImageOutputMode.PREVIEW, comfy.outputMode);

            coordinator.generateImages(defaults.toBuilder().comfySaveImages(true).build(),
                    "Permanent output", 720, 480, 2);
            assertEquals(ComfyUiClient.ImageOutputMode.PERMANENT, comfy.outputMode);
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

    private static final class CapturingComfyUiClient extends ComfyUiClient
    {
        private int width;
        private int height;
        private int batchSize;
        private ImageOutputMode outputMode;

        @Override
        public GenerationResult generateImages(
                String workflowTemplateJson, String promptText, int width, int height, int batchSize,
                ImageOutputMode outputMode)
        {
            this.width = width;
            this.height = height;
            this.batchSize = batchSize;
            this.outputMode = outputMode;
            return new GenerationResult("{}", List.of());
        }
    }
}
