package com.llamaquill.storycards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.llamaquill.db.AppPaths;
import com.llamaquill.db.BlockRepository;
import com.llamaquill.db.Database;
import com.llamaquill.db.StoryCardRepository;
import com.llamaquill.db.StoryRepository;
import com.llamaquill.generation.AuxiliaryGenerationService;
import com.llamaquill.model.Block;
import com.llamaquill.model.ChatMessage;
import com.llamaquill.model.GenerationSettings;
import com.llamaquill.model.Role;
import com.llamaquill.model.Story;
import com.llamaquill.model.StoryCard;
import com.llamaquill.prompt.PromptCompiler;
import com.llamaquill.prompt.PromptContextReport;
import com.llamaquill.serviceClients.OllamaChatResult;
import com.llamaquill.serviceClients.OllamaClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

class StoryCardGenerationCoordinatorTest
{
    @TempDir
    Path temporaryDirectory;

    @Test
    void usesFullBudgetedContextSavedSelfReferenceAndPlainTextResponse() throws Exception
    {
        AppPaths paths = AppPaths.forDirectories(
                temporaryDirectory.resolve("data"), temporaryDirectory.resolve("legacy"));
        try (Database database = Database.open(paths))
        {
            StoryRepository stories = new StoryRepository(database);
            BlockRepository blocks = new BlockRepository(database);
            StoryCardRepository cards = new StoryCardRepository(database);
            Story story = new Story("story", "Story", "System", "Plot facts", "Write tightly", "now", "now");
            stories.insert(story);
            blocks.insert(new Block("block", story.id(), Role.ASSISTANT,
                    "Mia meets a guild member.", "now", 1));
            StoryCard mia = new StoryCard(
                    "mia", story.id(), "Mia", "Mia", "Saved Mia entry.",
                    "Character", "PRIVATE NOTES", true);
            StoryCard rogue = new StoryCard(
                    "rogue", story.id(), "Rogue", "rogue", "Rogue class lore.",
                    "Class", "", false);
            cards.insert(mia);
            cards.insert(rogue);

            CapturingOllamaClient ollama = new CapturingOllamaClient();
            AuxiliaryGenerationService auxiliary = new AuxiliaryGenerationService(new PromptCompiler(), ollama);
            StoryCardGenerationCoordinator coordinator =
                    new StoryCardGenerationCoordinator(blocks, cards, auxiliary);
            StoryCardGenerationRequest request = new StoryCardGenerationRequest(
                    mia.id(),
                    "Mia",
                    "",
                    "Rewrite {{title}} from this saved entry: {{entry}}",
                    "Mia is a rogue.");

            StoryCardGenerationCoordinator.Result result =
                    coordinator.generate(story, request, GenerationSettings.defaults());

            assertEquals("Fresh Mia entry.", result.entry());
            assertEquals("Mia", result.resolvedTriggers());
            String completePrompt = ollama.messages.stream()
                    .map(ChatMessage::content)
                    .reduce("", String::concat);
            assertEquals(2, occurrences(completePrompt, "Saved Mia entry."));
            assertEquals(1, result.compilation().contextReport()
                    .entries(PromptContextReport.Component.FORCED_STORY_CARD).size());
            assertTrue(result.compilation().contextReport()
                    .entries(PromptContextReport.Component.PINNED_STORY_CARD).isEmpty());
            assertTrue(completePrompt.contains("Rogue class lore."));
            assertFalse(completePrompt.contains("PRIVATE NOTES"));
            assertFalse(completePrompt.contains("Character"));
            assertEquals("user", ollama.messages.getLast().role());
            assertTrue(ollama.messages.getLast().content().contains("# Story Card Command"));
            assertTrue(ollama.messages.getLast().content().contains("# Additional Generation Context"));
        }
    }

    private static int occurrences(String text, String target)
    {
        return (text.length() - text.replace(target, "").length()) / target.length();
    }

    private static final class CapturingOllamaClient extends OllamaClient
    {
        private List<ChatMessage> messages = List.of();

        @Override
        public OllamaChatResult chatNonStreaming(List<ChatMessage> messages, GenerationSettings settings)
        {
            this.messages = List.copyOf(messages);
            return new OllamaChatResult(
                    settings.modelName(), " Fresh Mia entry. ",
                    100, 12, "stop", 1, 1, 1, 1, 0);
        }
    }
}
