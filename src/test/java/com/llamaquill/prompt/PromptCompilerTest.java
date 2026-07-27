package com.llamaquill.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.llamaquill.model.Block;
import com.llamaquill.model.ChatMessage;
import com.llamaquill.model.GenerationSettings;
import com.llamaquill.model.Role;
import com.llamaquill.model.Story;
import com.llamaquill.model.StoryCard;
import com.llamaquill.prompt.PromptContextReport.Component;
import com.llamaquill.prompt.PromptContextReport.Entry;
import com.llamaquill.prompt.PromptContextReport.Status;
import org.junit.jupiter.api.Test;

import java.util.List;

class PromptCompilerTest
{
    @Test
    void compilesEachConsecutiveAssistantRunAsOneChatTurn()
    {
        Story story = story("Write prose.", "", "");
        List<Block> blocks = List.of(
                block("1", Role.ASSISTANT, "The first"),
                block("2", Role.ASSISTANT, " passage."),
                block("3", Role.ASSISTANT, " The second"),
                block("4", Role.ASSISTANT, " passage."),
                block("5", Role.USER, "Continue."),
                block("6", Role.ASSISTANT, "A new"),
                block("7", Role.ASSISTANT, " response."));

        List<ChatMessage> messages = new PromptCompiler()
                .compile(story, blocks, List.of(), GenerationSettings.defaults())
                .messages();

        assertEquals(List.of(
                new ChatMessage("system", "Write prose."),
                new ChatMessage("assistant", "The first passage. The second passage."),
                new ChatMessage("user", "> Continue."),
                new ChatMessage("assistant", "A new response.")),
                messages);
    }

    @Test
    void reservesTwoHundredOutputTokensWhenResponseLengthIsDisabled()
    {
        PromptCompilation compilation = new PromptCompiler().compile(
                story("", "", ""),
                List.of(block("1", Role.ASSISTANT, "Story.")),
                List.of(),
                settings(1024, false, 17, 100));

        PromptBudget budget = compilation.contextReport().budget();
        assertEquals(200, budget.responseReserve());
        assertEquals(PromptBudget.ESTIMATION_SAFETY_RESERVE, budget.estimationSafetyReserve());
        assertEquals(760, budget.inputLimit());
    }

    @Test
    void enabledResponseLengthBecomesTheOutputReserve()
    {
        PromptCompilation compilation = new PromptCompiler().compile(
                story("", "", ""),
                List.of(block("1", Role.ASSISTANT, "Story.")),
                List.of(),
                settings(1024, true, 75, 100));

        PromptBudget budget = compilation.contextReport().budget();
        assertEquals(75, budget.responseReserve());
        assertEquals(885, budget.inputLimit());
    }

    @Test
    void dropsPinnedCardsBeforeTriggeredCards()
    {
        PromptCompiler compiler = exactCompiler();
        StoryCard pinned = card("pinned", "", "P".repeat(40), true);
        StoryCard triggered = card("triggered", "dragon", "T".repeat(40), false);

        PromptCompilation compilation = compiler.compile(
                story("", "", ""),
                List.of(block("1", Role.ASSISTANT, "dragon castle quest")),
                List.of(pinned, triggered),
                settings(350, false, 1, 20));

        assertWithinBudget(compilation);
        assertEquals(Status.DROPPED, cardEntry(compilation, "pinned").status());
        assertEquals(Status.INCLUDED, cardEntry(compilation, "triggered").status());
        assertFalse(messageText(compilation).contains("P".repeat(40)));
        assertTrue(messageText(compilation).contains("T".repeat(40)));
    }

    @Test
    void dropsTheLeastRelevantTriggeredCardFirst()
    {
        PromptCompiler compiler = exactCompiler();
        StoryCard moreRelevant = card("two-matches", "dragon, castle", "A".repeat(40), false);
        StoryCard lessRelevant = card("one-match", "dragon", "B".repeat(40), false);

        PromptCompilation compilation = compiler.compile(
                story("", "", ""),
                List.of(block("1", Role.ASSISTANT, "dragon castle quest")),
                List.of(lessRelevant, moreRelevant),
                settings(350, false, 1, 20));

        assertWithinBudget(compilation);
        assertEquals(Status.INCLUDED, cardEntry(compilation, "two-matches").status());
        assertEquals(List.of("dragon", "castle"), cardEntry(compilation, "two-matches").matchedTriggers());
        assertEquals(Status.DROPPED, cardEntry(compilation, "one-match").status());
    }

    @Test
    void plotEssentialsAndAuthorNoteOutrankStoryCards()
    {
        PromptCompiler compiler = exactCompiler();
        StoryCard pinned = card("pinned", "", "C".repeat(40), true);

        PromptCompilation compilation = compiler.compile(
                story("", "Plot facts remain important.".repeat(2), "Use a tense, immediate voice."),
                List.of(block("1", Role.ASSISTANT, "dragon castle quest")),
                List.of(pinned),
                settings(410, false, 1, 20));

        assertWithinBudget(compilation);
        assertEquals(Status.DROPPED, cardEntry(compilation, "pinned").status());
        assertEquals(Status.INCLUDED, entry(compilation, Component.PLOT_ESSENTIALS).status());
        assertEquals(Status.INCLUDED, entry(compilation, Component.AUTHOR_NOTE).status());
    }

    @Test
    void preservesTheProtectedStoryFloorWhileOptionalContextCanBeDropped()
    {
        PromptCompiler compiler = exactCompiler();
        StoryCard pinned = card("large-card", "", "C".repeat(100), true);

        PromptCompilation compilation = compiler.compile(
                story("", "", ""),
                List.of(block("1", Role.ASSISTANT, "S".repeat(100))),
                List.of(pinned),
                settings(450, false, 1, 80));

        assertWithinBudget(compilation);
        Entry story = entry(compilation, Component.STORY);
        assertEquals(Status.TRIMMED, story.status());
        assertTrue(story.includedEstimatedTokens() >= 80);
        assertEquals(Status.DROPPED, cardEntry(compilation, "large-card").status());
    }

    @Test
    void pathologicalContextStillProducesAWithinBudgetPromptAndTruncatesSystemLast()
    {
        PromptCompiler compiler = exactCompiler();
        StoryCard pinned = card("card", "", "C".repeat(40), true);

        PromptCompilation compilation = compiler.compile(
                story("SYSTEM-".repeat(20), "PLOT-".repeat(25), "AUTHOR-".repeat(10)),
                List.of(block("1", Role.ASSISTANT, "OLD-STORY-".repeat(10) + "NEWEST-TAIL")),
                List.of(pinned),
                settings(300, false, 1, 80));

        assertWithinBudget(compilation);
        assertEquals(Status.TRIMMED, entry(compilation, Component.SYSTEM).status());
        assertEquals(Status.DROPPED, entry(compilation, Component.STORY).status());
        assertEquals(Status.DROPPED, entry(compilation, Component.PLOT_ESSENTIALS).status());
        assertEquals(Status.DROPPED, entry(compilation, Component.AUTHOR_NOTE).status());
        assertEquals(Status.DROPPED, cardEntry(compilation, "card").status());
        assertEquals(1, compilation.messages().size());
        assertEquals("system", compilation.messages().getFirst().role());
        assertTrue(compilation.messages().getFirst().content().startsWith("SYSTEM-"));
    }

    @Test
    void emergencyStoryTrimmingRetainsTheNewestContinuationTail()
    {
        PromptCompiler compiler = exactCompiler();
        String storyText = "VERY-BEGINNING-" + "OLD-CONTEXT-".repeat(20) + "NEWEST-TAIL";

        PromptCompilation compilation = compiler.compile(
                story("", "", ""),
                List.of(block("1", Role.ASSISTANT, storyText)),
                List.of(),
                settings(300, false, 1, 100));

        assertWithinBudget(compilation);
        String includedStory = compilation.messages().getLast().content();
        assertTrue(includedStory.endsWith("NEWEST-TAIL"));
        assertFalse(includedStory.contains("VERY-BEGINNING-"));
        assertTrue(includedStory.length() < storyText.length());
        assertEquals(Status.TRIMMED, entry(compilation, Component.STORY).status());
    }

    private static PromptCompiler exactCompiler()
    {
        PromptCompiler compiler = new PromptCompiler();
        compiler.setTokenEstimator(text -> text == null || text.isBlank() ? 0 : text.length());
        return compiler;
    }

    private static void assertWithinBudget(PromptCompilation compilation)
    {
        assertTrue(compilation.estimatedTokens() <= compilation.contextReport().budget().inputLimit(),
                () -> compilation.estimatedTokens() + " > " + compilation.contextReport().budget().inputLimit());
    }

    private static Entry entry(PromptCompilation compilation, Component component)
    {
        return compilation.contextReport().entries(component).getFirst();
    }

    private static Entry cardEntry(PromptCompilation compilation, String id)
    {
        return compilation.contextReport().entries().stream()
                .filter(entry -> entry.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static String messageText(PromptCompilation compilation)
    {
        return compilation.messages().stream().map(ChatMessage::content).reduce("", (left, right) -> left + right);
    }

    private static Story story(String system, String plotEssentials, String authorNote)
    {
        return new Story("story", "Test", system, plotEssentials, authorNote, "now", "now");
    }

    private static StoryCard card(String id, String triggers, String content, boolean pinned)
    {
        return new StoryCard(id, "story", id, triggers, content, pinned);
    }

    private static Block block(String id, Role role, String text)
    {
        return new Block(id, "story", role, text, "now", Integer.parseInt(id));
    }

    private static GenerationSettings settings(int contextLimit, boolean responseLengthEnabled, int responseLength,
            int minStoryWindow)
    {
        return new GenerationSettings(
                GenerationSettings.DEFAULT_MODEL, GenerationSettings.DEFAULT_OLLAMA_HOST, contextLimit, 1.0,
                responseLengthEnabled, responseLength,
                false, 0.8,
                false, 200,
                false, 0.95,
                false, 0.025,
                false, 0.25,
                false, 0.0,
                false, 1.05,
                minStoryWindow,
                7,
                3);
    }
}
