package com.llamaquill.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.llamaquill.model.Block;
import com.llamaquill.model.ChatMessage;
import com.llamaquill.model.ConversationLayout;
import com.llamaquill.model.GenerationSettings;
import com.llamaquill.model.Role;
import com.llamaquill.model.Story;
import com.llamaquill.model.StoryCard;
import com.llamaquill.model.StoryCardWrappingStyle;
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
    void mergesPlotAndLoreIntoOneLeadUserTurnWithNewestTriggersLast()
    {
        StoryCard recentPinned = card("recent-pinned", "RecentName", "Recent pinned lore.", true);
        StoryCard olderTriggered = card("older-triggered", "OldName", "Older triggered lore.", false);
        StoryCard pinnedOnly = card("pinned-only", "AbsentName", "Pinned background lore.", true);

        PromptCompilation compilation = new PromptCompiler().compile(
                story("Narrate.", "Plot facts verbatim.", ""),
                List.of(
                        block("1", Role.ASSISTANT, "OldName arrived."),
                        block("2", Role.ASSISTANT, " RecentName arrived.")),
                List.of(recentPinned, olderTriggered, pinnedOnly),
                settings(2048, false, 1, 100));

        assertEquals(List.of(
                new ChatMessage("system", "Narrate."),
                new ChatMessage("user", """
                        Plot facts verbatim.

                        World Lore:
                        Pinned background lore.
                        Older triggered lore.
                        Recent pinned lore."""),
                new ChatMessage("assistant", "OldName arrived. RecentName arrived.")),
                compilation.messages());
        assertEquals(Component.PINNED_STORY_CARD, cardEntry(compilation, "pinned-only").component());
        assertEquals(Component.TRIGGERED_STORY_CARD, cardEntry(compilation, "recent-pinned").component());
        assertEquals(List.of("RecentName"), cardEntry(compilation, "recent-pinned").matchedTriggers());
    }

    @Test
    void appliesTheSelectedModelWrappingToEachLoreEntryOnlyAtCompilation()
    {
        StoryCard first = card("first", "", "First entry.", true);
        StoryCard legacyWrapped = card("second", "", "[Second entry.]", true);

        PromptCompilation compilation = new PromptCompiler().compile(
                story("", "Plot.", ""),
                List.of(block("1", Role.ASSISTANT, "Story.")),
                List.of(first, legacyWrapped),
                settings(2048, false, 1, 100, StoryCardWrappingStyle.BRACES));

        assertEquals("Plot.\n\nWorld Lore:\n{First entry.}\n{Second entry.}",
                compilation.messages().getFirst().content());
        assertEquals("First entry.", first.content());
        assertEquals("[Second entry.]", legacyWrapped.content());
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
        assertEquals(134, budget.estimationSafetyReserve());
        assertEquals(690, budget.inputLimit());
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
        assertEquals(145, budget.estimationSafetyReserve());
        assertEquals(804, budget.inputLimit());
    }

    @Test
    void reservesProportionalHeadroomForColdModelTokenEstimates()
    {
        PromptCompilation compilation = new PromptCompiler().compile(
                story("", "", ""),
                List.of(block("1", Role.ASSISTANT, "Story.")),
                List.of(),
                settings(16_384, false, 17, 100));

        PromptBudget budget = compilation.contextReport().budget();
        int worstAllowedActualInput = (int) Math.ceil(
                budget.inputLimit() * (1.0 + PromptBudget.TOKEN_ESTIMATION_UNDERSHOOT_ALLOWANCE))
                + PromptBudget.FIXED_ESTIMATION_SAFETY_RESERVE;

        assertEquals(200, budget.responseReserve());
        assertEquals(1_530, budget.estimationSafetyReserve());
        assertEquals(14_654, budget.inputLimit());
        assertTrue(worstAllowedActualInput + budget.responseReserve() <= budget.contextLimit());
    }

    @Test
    void tightensTheRetryBudgetFromOllamasMeasuredContextError()
    {
        PromptBudget budget = PromptBudget.from(settings(50_176, false, 17, 100));

        assertEquals(45_374, budget.inputLimit());
        assertEquals(44_870, budget.correctedInputLimit(45_374, 50_472, 50_176));
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
                settings(390, false, 1, 20));

        assertWithinBudget(compilation);
        assertEquals(Status.DROPPED, cardEntry(compilation, "pinned").status());
        assertEquals(Status.INCLUDED, cardEntry(compilation, "triggered").status());
        assertFalse(messageText(compilation).contains("P".repeat(40)));
        assertTrue(messageText(compilation).contains("T".repeat(40)));
    }

    @Test
    void dropsTheLeastRecentlyTriggeredCardFirst()
    {
        PromptCompiler compiler = exactCompiler();
        StoryCard newer = card("newer", "castle", "A".repeat(40), false);
        StoryCard older = card("older", "dragon", "B".repeat(40), false);

        PromptCompilation compilation = compiler.compile(
                story("", "", ""),
                List.of(block("1", Role.ASSISTANT, "dragon castle quest")),
                List.of(newer, older),
                settings(390, false, 1, 20));

        assertWithinBudget(compilation);
        assertEquals(Status.INCLUDED, cardEntry(compilation, "newer").status());
        assertEquals(List.of("castle"), cardEntry(compilation, "newer").matchedTriggers());
        assertEquals(Status.DROPPED, cardEntry(compilation, "older").status());
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
                settings(420, false, 1, 20));

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

    @Test
    void auxiliaryGenerationForcesASavedCardOnceAndEndsWithTheTask()
    {
        StoryCard saved = new StoryCard(
                "mia", "story", "Mia", "Mia, thief", "Mia is a careful thief.",
                "Character", "PRIVATE PLAYER NOTE", true);
        ChatMessage task = new ChatMessage("user", "Generate Mia now.");

        PromptCompilation compilation = new PromptCompiler().compile(
                story("Narrate.", "", ""),
                List.of(block("1", Role.ASSISTANT, "Mia enters.")),
                List.of(saved),
                settings(2048, false, 1, 100),
                new PromptAuxiliaryInput(List.of(task), "", saved));

        String text = messageText(compilation);
        assertEquals(1, occurrences(text, "Mia is a careful thief."));
        assertFalse(text.contains("Character"));
        assertFalse(text.contains("PRIVATE PLAYER NOTE"));
        assertEquals(task, compilation.messages().getLast());
        assertEquals(Status.INCLUDED, entry(compilation, Component.FORCED_STORY_CARD).status());
    }

    @Test
    void additionalGenerationContextCanTriggerCardsWithoutEnteringTheAdventure()
    {
        StoryCard rogue = card("rogue", "rogue", "Rogue class lore.", false);

        PromptCompilation compilation = new PromptCompiler().compile(
                story("", "", ""),
                List.of(block("1", Role.ASSISTANT, "A stranger arrives.")),
                List.of(rogue),
                settings(2048, false, 1, 100),
                new PromptAuxiliaryInput(
                        List.of(new ChatMessage("user", "Create Mia, a rogue.")),
                        "Mia is a rogue.",
                        null));

        assertTrue(messageText(compilation).contains("Rogue class lore."));
        assertEquals(List.of("rogue"), cardEntry(compilation, "rogue").matchedTriggers());
        assertEquals("Create Mia, a rogue.", compilation.messages().getLast().content());
    }

    @Test
    void auxiliarySystemInstructionsRemainInTheSingleLeadingSystemTurn()
    {
        PromptCompilation compilation = new PromptCompiler().compile(
                story("Story system.", "", ""),
                List.of(block("1", Role.ASSISTANT, "Story text.")),
                List.of(),
                settings(2048, false, 1, 100),
                new PromptAuxiliaryInput(
                        List.of(
                                new ChatMessage("system", "Auxiliary behavior."),
                                new ChatMessage("user", "Perform the task.")),
                        "",
                        null));

        assertEquals(List.of("system", "assistant", "user"),
                compilation.messages().stream().map(ChatMessage::role).toList());
        assertEquals("Story system.\n\nAuxiliary behavior.",
                compilation.messages().getFirst().content());
        assertEquals("Perform the task.", compilation.messages().getLast().content());
    }

    @Test
    void storyTailUserMessageSplitsAnAssistantRunWithoutBecomingTheFinalTurn()
    {
        PromptCompilation compilation = new PromptCompiler().compile(
                story("Narrate.", "", ""),
                List.of(
                        block("1", Role.ASSISTANT, "Older response."),
                        block("2", Role.ASSISTANT, "Newest "),
                        block("3", Role.ASSISTANT, "continuation.")),
                List.of(),
                settings(2048, false, 1, 100),
                new PromptAuxiliaryInput(
                        List.of(), "", null, "Continue from last response.", 2));

        assertEquals(List.of("system", "assistant", "user", "assistant"),
                compilation.messages().stream().map(ChatMessage::role).toList());
        assertEquals("Older response.", compilation.messages().get(1).content());
        assertEquals("Continue from last response.", compilation.messages().get(2).content());
        assertEquals("Newest continuation.", compilation.messages().getLast().content());
        assertEquals(Status.INCLUDED,
                compilation.contextReport().entries(Component.AUXILIARY_TASK).getFirst().status());
    }

    @Test
    void authorNoteEndsTheAssistantMergeBeforeARealUserBoundary()
    {
        PromptCompilation compilation = new PromptCompiler().compile(
                story("Narrate.", "", "Keep the danger immediate."),
                List.of(
                        block("1", Role.ASSISTANT, "Earlier response."),
                        block("2", Role.USER, "Move left."),
                        block("3", Role.ASSISTANT, "First response."),
                        block("4", Role.ASSISTANT, " Second response.")),
                List.of(),
                settings(2048, false, 1, 100));

        assertEquals(List.of(
                new ChatMessage("system", "Narrate."),
                new ChatMessage("assistant",
                        "Earlier response.\n\nAuthor's Note: Keep the danger immediate."),
                new ChatMessage("user", "> Move left."),
                new ChatMessage("assistant", "First response. Second response.")),
                compilation.messages());
    }

    @Test
    void authorNoteEndsTheAssistantMergeBeforeAnEphemeralBoundary()
    {
        PromptCompilation compilation = new PromptCompiler().compile(
                story("Narrate.", "", "Keep the danger immediate."),
                List.of(
                        block("1", Role.ASSISTANT, "Earlier response."),
                        block("2", Role.USER, "Move left."),
                        block("3", Role.ASSISTANT, "First response."),
                        block("4", Role.ASSISTANT, " Second response."),
                        block("5", Role.ASSISTANT, " Third response.")),
                List.of(),
                settings(2048, false, 1, 100),
                new PromptAuxiliaryInput(
                        List.of(), "", null, "Continue from last response.", 2));

        assertEquals(List.of(
                new ChatMessage("system", "Narrate."),
                new ChatMessage("assistant", "Earlier response."),
                new ChatMessage("user", "> Move left."),
                new ChatMessage("assistant",
                        "First response.\n\nAuthor's Note: Keep the danger immediate."),
                new ChatMessage("user", "Continue from last response."),
                new ChatMessage("assistant", " Second response. Third response.")),
                compilation.messages());
    }

    @Test
    void authorNoteUsesAContextTurnWhenNoEarlierAssistantCanCarryIt()
    {
        PromptCompilation compilation = new PromptCompiler().compile(
                story("Narrate.", "", "Keep the danger immediate."),
                List.of(block("1", Role.ASSISTANT, "Opening prefill.")),
                List.of(),
                settings(2048, false, 1, 100));

        assertEquals(List.of(
                new ChatMessage("system", "Narrate."),
                new ChatMessage("user", "Author's Note: Keep the danger immediate."),
                new ChatMessage("assistant", "Opening prefill.")),
                compilation.messages());
    }

    @Test
    void flattenedLayoutBuildsOneUserDocumentWithTheNewestBlockAfterAuthorNote()
    {
        StoryCard gretchen = card("gretchen", "", "Gretchen profile.", true);
        List<Block> blocks = List.of(
                block("1", Role.ASSISTANT, "Opening scene."),
                block("2", Role.USER, "Move left.\nStay low."),
                block("3", Role.ASSISTANT, "Earlier response."),
                block("4", Role.ASSISTANT, " Latest response."));

        PromptCompilation compilation = new PromptCompiler().compile(
                story("Narrate.", "Plot facts.", "Keep danger immediate."),
                blocks,
                List.of(gretchen),
                settings(2048, false, 1, 100,
                        StoryCardWrappingStyle.BRACES, ConversationLayout.FLATTENED));

        assertEquals(List.of("system", "user"),
                compilation.messages().stream().map(ChatMessage::role).toList());
        assertEquals("Narrate.", compilation.messages().getFirst().content());
        assertEquals("Plot facts.\n\n"
                        + "World Lore:\n{Gretchen profile.}\n\n"
                        + "Recent story:\nOpening scene.\n\n"
                        + "> Move left.\n> Stay low.\n\n"
                        + "Earlier response.\n\n"
                        + "[Author's note:\nKeep danger immediate.\n]\n\n"
                        + " Latest response.",
                compilation.messages().getLast().content());
    }

    @Test
    void flattenedWithPrefillMovesOnlyTheNewestAiBlockToAssistantOnContinue()
    {
        List<Block> blocks = List.of(
                block("1", Role.ASSISTANT, "Opening scene."),
                block("2", Role.USER, "Move left."),
                block("3", Role.ASSISTANT, "Earlier response."),
                block("4", Role.ASSISTANT, " Latest response."));

        PromptCompilation compilation = new PromptCompiler().compile(
                story("Narrate.", "Plot facts.", "Keep danger immediate."),
                blocks,
                List.of(),
                settings(2048, false, 1, 100,
                        StoryCardWrappingStyle.NONE, ConversationLayout.FLATTENED_WITH_PREFILL),
                new PromptAuxiliaryInput(
                        List.of(), "", null, "Continue from last response.", 2, true));

        assertEquals(List.of("system", "user", "assistant"),
                compilation.messages().stream().map(ChatMessage::role).toList());
        assertEquals("Plot facts.\n\n"
                        + "Recent story:\nOpening scene.\n\n"
                        + "> Move left.\n\n"
                        + "Earlier response.\n\n"
                        + "[Author's note:\nKeep danger immediate.\n]",
                compilation.messages().get(1).content());
        assertEquals(" Latest response.", compilation.messages().getLast().content());
        assertFalse(messageText(compilation).contains("Continue from last response."));
    }

    @Test
    void flattenedWithPrefillKeepsACurrentTakeTurnAfterAuthorNoteWithoutPrefill()
    {
        PromptCompilation compilation = new PromptCompiler().compile(
                story("Narrate.", "", "Keep danger immediate."),
                List.of(
                        block("1", Role.ASSISTANT, "Opening scene."),
                        block("2", Role.USER, "Describe me moving left.")),
                List.of(),
                settings(2048, false, 1, 100,
                        StoryCardWrappingStyle.NONE, ConversationLayout.FLATTENED_WITH_PREFILL));

        assertEquals(List.of("system", "user"),
                compilation.messages().stream().map(ChatMessage::role).toList());
        assertEquals("Recent story:\nOpening scene.\n\n"
                        + "[Author's note:\nKeep danger immediate.\n]\n\n"
                        + "> Describe me moving left.",
                compilation.messages().getLast().content());
    }

    @Test
    void flattenedAuxiliaryTaskSharesTheSingleUserDocument()
    {
        PromptCompilation compilation = new PromptCompiler().compile(
                story("Narrate.", "Plot facts.", ""),
                List.of(block("1", Role.ASSISTANT, "Latest story output.")),
                List.of(),
                settings(2048, false, 1, 100,
                        StoryCardWrappingStyle.NONE, ConversationLayout.FLATTENED),
                new PromptAuxiliaryInput(
                        List.of(new ChatMessage("user", "Perform the auxiliary task.")), "", null));

        assertEquals(List.of("system", "user"),
                compilation.messages().stream().map(ChatMessage::role).toList());
        assertEquals("Plot facts.\n\nRecent story:\n\nLatest story output.\n\n"
                        + "Perform the auxiliary task.",
                compilation.messages().getLast().content());
    }

    @Test
    void flattenedPrefillRemainsWithinBudgetWhileRetainingTheNewestAiOutput()
    {
        PromptCompiler compiler = exactCompiler();
        PromptCompilation compilation = compiler.compile(
                story("", "", ""),
                List.of(
                        block("1", Role.ASSISTANT, "OLD-CONTEXT-".repeat(20)),
                        block("2", Role.USER, "Move left."),
                        block("3", Role.ASSISTANT, "LATEST-PREFILL")),
                List.of(),
                settings(330, false, 1, 100,
                        StoryCardWrappingStyle.NONE, ConversationLayout.FLATTENED_WITH_PREFILL),
                new PromptAuxiliaryInput(List.of(), "", null, "", 0, true));

        assertWithinBudget(compilation);
        assertEquals("assistant", compilation.messages().getLast().role());
        assertEquals("LATEST-PREFILL", compilation.messages().getLast().content());
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

    private static int occurrences(String text, String target)
    {
        return (text.length() - text.replace(target, "").length()) / target.length();
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
        return settings(contextLimit, responseLengthEnabled, responseLength,
                minStoryWindow, StoryCardWrappingStyle.NONE);
    }

    private static GenerationSettings settings(int contextLimit, boolean responseLengthEnabled, int responseLength,
            int minStoryWindow, StoryCardWrappingStyle wrappingStyle)
    {
        return settings(contextLimit, responseLengthEnabled, responseLength,
                minStoryWindow, wrappingStyle, ConversationLayout.ROLE_AWARE);
    }

    private static GenerationSettings settings(int contextLimit, boolean responseLengthEnabled, int responseLength,
            int minStoryWindow, StoryCardWrappingStyle wrappingStyle, ConversationLayout conversationLayout)
    {
        return new GenerationSettings(
                GenerationSettings.DEFAULT_MODEL, GenerationSettings.DEFAULT_OLLAMA_HOST, contextLimit, 1.0,
                responseLengthEnabled, responseLength,
                false, 0.8,
                false, 200,
                false, 0.95,
                false, 0.025,
                false, 1.0,
                false, 0.25,
                false, 0.0,
                false, 64,
                false, 1.05,
                minStoryWindow,
                7,
                com.llamaquill.model.AppSettings.DEFAULT_OLLAMA_KEEP_ALIVE_MINUTES,
                wrappingStyle, conversationLayout);
    }
}
