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
import com.llamaquill.model.ConversationLayout;
import com.llamaquill.model.GenerationSettings;
import com.llamaquill.model.Role;
import com.llamaquill.model.Story;
import com.llamaquill.prompt.PromptCompiler;
import com.llamaquill.serviceClients.OllamaChatResult;
import com.llamaquill.serviceClients.OllamaClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

class StoryPromptCoordinatorTest
{
    @TempDir
    Path temporaryDirectory;

    @Test
    void oneShotOverridesUseADistinctRoleAwareUserTurnWithoutChangingSavedSettings() throws Exception
    {
        Path root = temporaryDirectory.resolve("role-aware-one-shot");
        try (Database database = Database.open(AppPaths.forDirectories(root.resolve("data"), root.resolve("legacy"))))
        {
            StoryRepository stories = new StoryRepository(database);
            BlockRepository blocks = new BlockRepository(database);
            StoryCardRepository cards = new StoryCardRepository(database);
            Story story = new Story("story", "Story", "Narrate.", "", "", "now", "now");
            stories.insert(story);
            blocks.insert(new Block("head", story.id(), Role.ASSISTANT, "Existing story prose.", "now", 1));
            CapturingOllamaClient ollama = new CapturingOllamaClient();
            StoryPromptCoordinator coordinator = new StoryPromptCoordinator(
                    blocks, cards, new AuxiliaryGenerationService(new PromptCompiler(), ollama));
            GenerationSettings savedSettings = GenerationSettings.defaults()
                    .withConversationLayout(ConversationLayout.FLATTENED);

            coordinator.generateResponse(
                    story, "Analyze the story.", "List every character.", savedSettings, true, true);

            assertEquals(ConversationLayout.FLATTENED, savedSettings.conversationLayout());
            assertEquals(ConversationLayout.ROLE_AWARE, ollama.settings.conversationLayout());
            assertFalse(ollama.settings.responseLengthEnabled());
            assertEquals(List.of("system", "assistant", "user"),
                    ollama.messages.stream().map(ChatMessage::role).toList());
            assertEquals("List every character.", ollama.messages.getLast().content());

            coordinator.generateResponse(
                    story, "Analyze the story.", "List every character.", savedSettings, false, false);

            assertEquals(ConversationLayout.FLATTENED, ollama.settings.conversationLayout());
            assertEquals(List.of("system", "user"),
                    ollama.messages.stream().map(ChatMessage::role).toList());
            assertTrue(ollama.messages.getLast().content().endsWith("List every character."));
        }
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
                    settings.modelName(), "Character list", 100, 12, "stop", 1, 1, 1, 1, 0);
        }
    }
}
