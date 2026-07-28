package com.llamaquill.prompt;

import com.llamaquill.model.Block;
import com.llamaquill.model.ChatMessage;
import com.llamaquill.model.GenerationSettings;
import com.llamaquill.model.Role;
import com.llamaquill.model.Story;
import com.llamaquill.model.StoryCard;
import com.llamaquill.prompt.PromptContextReport.Component;
import com.llamaquill.prompt.PromptContextReport.Entry;
import com.llamaquill.prompt.PromptContextReport.Status;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;

public class PromptCompiler
{
    private static final int CHARS_PER_TOKEN = 4;
    private static final double TOKEN_ESTIMATE_MULTIPLIER = 1.0;
    private static final boolean PREFIX_USER_LINES = true;
    private static final int BOUNDARY_SEARCH_LIMIT = 256;

    private double operationTokenScale = 1.0;
    private ToIntFunction<String> tokenEstimatorOverride;

    public void setTokenEstimator(ToIntFunction<String> tokenEstimator)
    {
        tokenEstimatorOverride = tokenEstimator;
    }

    public synchronized PromptCompilation compile(Story story, List<Block> blocks, List<StoryCard> storyCards,
            GenerationSettings settings)
    {
        return compile(story, blocks, storyCards, settings, PromptAuxiliaryInput.none());
    }

    public synchronized PromptCompilation compile(Story story, List<Block> blocks, List<StoryCard> storyCards,
            GenerationSettings settings, PromptAuxiliaryInput auxiliaryInput)
    {
        Objects.requireNonNull(story, "story");
        Objects.requireNonNull(blocks, "blocks");
        Objects.requireNonNull(storyCards, "storyCards");
        Objects.requireNonNull(settings, "settings");
        PromptAuxiliaryInput auxiliary = auxiliaryInput == null ? PromptAuxiliaryInput.none() : auxiliaryInput;
        operationTokenScale = settings.promptTokenScale();

        PromptBudget budget = PromptBudget.from(settings);
        List<Block> originalWindow = List.copyOf(filterPromptBlocks(blocks));
        List<Block> window = new ArrayList<>(originalWindow);
        List<ChatMessage> originalTaskMessages = normalizeTrailingMessages(auxiliary.trailingMessages());
        List<ChatMessage> taskMessages = new ArrayList<>(originalTaskMessages);
        StoryCard originalForcedCard = auxiliary.forcedStoryCard();
        StoryCard forcedCard = originalForcedCard;

        StoryCardSelection selectedCards = selectStoryCards(
                storyCards, originalWindow, settings.storyCardLookback(), auxiliary.activationText(),
                originalForcedCard == null ? "" : originalForcedCard.id());
        List<StoryCard> originalPinned = List.copyOf(selectedCards.pinned());
        List<StoryCard> originalTriggered = List.copyOf(selectedCards.triggered());
        List<StoryCard> pinned = new ArrayList<>(originalPinned);
        List<StoryCard> triggered = new ArrayList<>(originalTriggered);

        String originalSystem = normalizeContextText(story.systemPrompt());
        String originalPlotEssentials = normalizeContextText(story.plotEssentials());
        String originalAuthorNote = normalizeContextText(story.authorNote());
        String systemText = originalSystem;
        String plotEssentials = originalPlotEssentials;
        String authorNote = originalAuthorNote;

        int originalStoryTokens = estimateStoryTokens(originalWindow);
        int protectedStoryFloor = Math.min(budget.protectedStoryTokens(), originalStoryTokens);

        while (true)
        {
            if (window.isEmpty() && !authorNote.isBlank())
            {
                authorNote = "";
            }
            CompiledState compiled = compileState(systemText, plotEssentials, authorNote, window, pinned, triggered,
                    forcedCard, taskMessages, settings.anPlacement());
            if (compiled.estimatedTokens() <= budget.inputLimit())
            {
                return finishCompilation(compiled, budget, story, originalSystem, systemText,
                        originalPlotEssentials, plotEssentials, originalAuthorNote, authorNote,
                        originalWindow, window, originalPinned, pinned, originalTriggered, triggered,
                        selectedCards.triggerMatches(), originalForcedCard, forcedCard,
                        originalTaskMessages, taskMessages);
            }

            int excessTokens = compiled.estimatedTokens() - budget.inputLimit();
            int currentStoryTokens = estimateStoryTokens(window);

            // Older adventure text above the protected story floor is the first
            // context sacrificed. The newest story tail is retained.
            if (currentStoryTokens > protectedStoryFloor)
            {
                int targetStoryTokens = Math.max(protectedStoryFloor, currentStoryTokens - excessTokens);
                List<Block> shortened = retainNewestStoryAtLeast(window, targetStoryTokens);
                if (!shortened.equals(window))
                {
                    window = shortened;
                    continue;
                }
            }

            // Pinned means always active, not mandatory. Triggered cards are more
            // immediately relevant, so pinned cards leave the prompt first.
            if (!pinned.isEmpty())
            {
                pinned.removeLast();
                continue;
            }
            if (!triggered.isEmpty())
            {
                triggered.removeLast();
                continue;
            }
            if (forcedCard != null)
            {
                forcedCard = null;
                continue;
            }

            // Author's Note and Plot Essentials outrank story cards. Preserve the
            // beginning of each when shortening at a sensible prose boundary.
            if (!authorNote.isBlank())
            {
                authorNote = shortenFromEnd(authorNote, excessTokens);
                continue;
            }
            if (!plotEssentials.isBlank())
            {
                plotEssentials = shortenFromEnd(plotEssentials, excessTokens);
                continue;
            }

            // Pathological mandatory-context pressure: progressively reduce the
            // protected story tail, then truncate the system message last.
            if (currentStoryTokens > 0)
            {
                int targetStoryTokens = Math.max(0, currentStoryTokens - excessTokens);
                List<Block> shortened = retainNewestStoryAtLeast(window, targetStoryTokens);
                if (shortened.equals(window))
                {
                    shortened = forceTrimOldestStory(window);
                }
                window = shortened;
                continue;
            }
            if (!systemText.isBlank())
            {
                systemText = shortenFromEnd(systemText, excessTokens);
                continue;
            }
            if (taskMessages.stream().anyMatch(message -> !message.content().isBlank()))
            {
                taskMessages = shortenTaskMessages(taskMessages, excessTokens);
                continue;
            }

            // A zero-sized or otherwise invalid external setting cannot leave an
            // over-budget payload behind. Empty messages are the final safe state.
            CompiledState empty = new CompiledState(List.of(), 0);
            return finishCompilation(empty, budget, story, originalSystem, "",
                    originalPlotEssentials, "", originalAuthorNote, "",
                    originalWindow, List.of(), originalPinned, List.of(),
                    originalTriggered, List.of(), selectedCards.triggerMatches(),
                    originalForcedCard, null, originalTaskMessages, taskMessages);
        }
    }

    private PromptCompilation finishCompilation(CompiledState compiled, PromptBudget budget, Story story,
            String originalSystem, String systemText,
            String originalPlotEssentials, String plotEssentials,
            String originalAuthorNote, String authorNote,
            List<Block> originalWindow, List<Block> window,
            List<StoryCard> originalPinned, List<StoryCard> pinned,
            List<StoryCard> originalTriggered, List<StoryCard> triggered,
            Map<StoryCard, List<String>> triggerMatches,
            StoryCard originalForcedCard, StoryCard forcedCard,
            List<ChatMessage> originalTaskMessages, List<ChatMessage> taskMessages)
    {
        PromptContextReport report = buildContextReport(budget, compiled.estimatedTokens(), story,
                originalSystem, systemText, originalPlotEssentials, plotEssentials,
                originalAuthorNote, authorNote, originalWindow, window,
                originalPinned, pinned, originalTriggered, triggered, triggerMatches,
                originalForcedCard, forcedCard, originalTaskMessages, taskMessages);
        logCompilationReport(report);
        return new PromptCompilation(compiled.messages(), compiled.estimatedTokens(), report);
    }

    private CompiledState compileState(String systemText, String plotEssentials, String authorNote,
            List<Block> window, List<StoryCard> pinned, List<StoryCard> triggered,
            StoryCard forcedCard, List<ChatMessage> taskMessages, int authorNotePlacement)
    {
        List<Block> windowWithNote = insertAuthorNote(window, authorNotePlacement, authorNote);
        List<Message> messages = new ArrayList<>();
        if (!plotEssentials.isBlank())
        {
            messages.add(new Message(Role.USER, plotEssentials));
        }
        if (forcedCard != null)
        {
            String text = formatStoryCard(forcedCard);
            if (!text.isBlank())
            {
                messages.add(new Message(Role.USER, text));
            }
        }
        for (StoryCard card : pinned)
        {
            String text = formatStoryCard(card);
            if (!text.isBlank())
            {
                messages.add(new Message(Role.USER, text));
            }
        }
        for (StoryCard card : triggered)
        {
            String text = formatStoryCard(card);
            if (!text.isBlank())
            {
                messages.add(new Message(Role.USER, text));
            }
        }
        messages.addAll(groupMessages(windowWithNote));

        String mergedSystemText = systemText;
        for (ChatMessage taskMessage : taskMessages)
        {
            if ("system".equalsIgnoreCase(taskMessage.role()))
            {
                mergedSystemText = mergeSystemInstructions(mergedSystemText, taskMessage.content());
            }
        }
        List<ChatMessage> promptMessages = buildPromptMessages(mergedSystemText, messages);
        for (ChatMessage taskMessage : taskMessages)
        {
            if (!"system".equalsIgnoreCase(taskMessage.role())
                    && !taskMessage.role().isBlank() && !taskMessage.content().isBlank())
            {
                promptMessages.add(taskMessage);
            }
        }
        return new CompiledState(promptMessages, estimateTokens(promptMessages));
    }

    private PromptContextReport buildContextReport(PromptBudget budget, int estimatedInputTokens, Story story,
            String originalSystem, String systemText,
            String originalPlotEssentials, String plotEssentials,
            String originalAuthorNote, String authorNote,
            List<Block> originalWindow, List<Block> window,
            List<StoryCard> originalPinned, List<StoryCard> pinned,
            List<StoryCard> originalTriggered, List<StoryCard> triggered,
            Map<StoryCard, List<String>> triggerMatches,
            StoryCard originalForcedCard, StoryCard forcedCard,
            List<ChatMessage> originalTaskMessages, List<ChatMessage> taskMessages)
    {
        List<Entry> entries = new ArrayList<>();
        addTextEntry(entries, Component.SYSTEM, story.id(), "System",
                originalSystem, systemText, "system");

        if (!originalWindow.isEmpty())
        {
            int originalTokens = estimateStoryTokens(originalWindow);
            int includedTokens = estimateStoryTokens(window);
            entries.add(new Entry(Component.STORY, story.id(), "Adventure",
                    statusFor(originalWindow, window), originalTokens, includedTokens,
                    originalWindow.size(), window.size(), List.of()));
        }

        addTextEntry(entries, Component.PLOT_ESSENTIALS, story.id(), "Plot Essentials",
                originalPlotEssentials, plotEssentials, "user");

        String includedAuthorNote = window.isEmpty() ? "" : authorNote;
        addTextEntry(entries, Component.AUTHOR_NOTE, story.id(), "Author's Note",
                originalAuthorNote, includedAuthorNote, null);

        for (StoryCard card : originalTriggered)
        {
            boolean included = triggered.contains(card);
            entries.add(cardEntry(Component.TRIGGERED_STORY_CARD, card, included,
                    triggerMatches.getOrDefault(card, List.of())));
        }
        for (StoryCard card : originalPinned)
        {
            entries.add(cardEntry(Component.PINNED_STORY_CARD, card, pinned.contains(card), List.of()));
        }
        if (originalForcedCard != null)
        {
            entries.add(cardEntry(Component.FORCED_STORY_CARD, originalForcedCard,
                    forcedCard != null, List.of()));
        }
        for (int index = 0; index < originalTaskMessages.size(); index++)
        {
            ChatMessage original = originalTaskMessages.get(index);
            ChatMessage included = index < taskMessages.size()
                    ? taskMessages.get(index)
                    : new ChatMessage(original.role(), "");
            addTextEntry(entries, Component.AUXILIARY_TASK, "task-" + index,
                    "Auxiliary " + original.role() + " message",
                    original.content(), included.content(), original.role());
        }

        return new PromptContextReport(budget, estimatedInputTokens, entries);
    }

    private void addTextEntry(List<Entry> entries, Component component, String id, String label,
            String originalText, String includedText, String standaloneRole)
    {
        if (originalText.isBlank())
        {
            return;
        }
        int originalTokens = standaloneRole == null
                ? estimateTokens(originalText)
                : estimateStandaloneMessage(standaloneRole, originalText);
        int includedTokens = includedText.isBlank()
                ? 0
                : standaloneRole == null
                        ? estimateTokens(includedText)
                        : estimateStandaloneMessage(standaloneRole, includedText);
        entries.add(new Entry(component, id, label, statusFor(originalText, includedText),
                originalTokens, includedTokens, 1, includedText.isBlank() ? 0 : 1, List.of()));
    }

    private Entry cardEntry(Component component, StoryCard card, boolean included, List<String> matchedTriggers)
    {
        String text = formatStoryCard(card);
        int originalTokens = text.isBlank() ? 0 : estimateStandaloneMessage("user", text);
        return new Entry(component, safeText(card.id()), safeTitle(card),
                included ? Status.INCLUDED : Status.DROPPED,
                originalTokens, included ? originalTokens : 0, 1, included ? 1 : 0, matchedTriggers);
    }

    private static Status statusFor(String original, String included)
    {
        if (included.isBlank())
        {
            return Status.DROPPED;
        }
        return original.equals(included) ? Status.INCLUDED : Status.TRIMMED;
    }

    private static Status statusFor(List<Block> original, List<Block> included)
    {
        if (included.isEmpty())
        {
            return Status.DROPPED;
        }
        return original.equals(included) ? Status.INCLUDED : Status.TRIMMED;
    }

    private static List<Block> filterPromptBlocks(List<Block> blocks)
    {
        List<Block> filtered = new ArrayList<>(blocks.size());
        for (Block block : blocks)
        {
            if (block == null)
            {
                continue;
            }
            if (block.role() == Role.USER || block.role() == Role.ASSISTANT)
            {
                filtered.add(block);
            }
        }
        return filtered;
    }

    private List<Block> retainNewestStoryAtLeast(List<Block> source, int targetTokens)
    {
        if (source.isEmpty() || targetTokens <= 0)
        {
            return List.of();
        }
        if (estimateStoryTokens(source) <= targetTokens)
        {
            return List.copyOf(source);
        }

        List<Block> retained = new ArrayList<>();
        for (int index = source.size() - 1; index >= 0; index--)
        {
            retained.addFirst(source.get(index));
            if (estimateStoryTokens(retained) >= targetTokens)
            {
                return minimizeEarliestBlock(retained, targetTokens);
            }
        }
        return List.copyOf(source);
    }

    private List<Block> minimizeEarliestBlock(List<Block> retained, int targetTokens)
    {
        if (retained.isEmpty())
        {
            return List.of();
        }

        Block first = retained.getFirst();
        String text = safeText(first.text());
        if (text.length() <= 1)
        {
            return List.copyOf(retained);
        }

        int low = 0;
        int high = text.length() - 1;
        int latestValidStart = 0;
        while (low <= high)
        {
            int rawMiddle = (low + high) >>> 1;
            int middle = safeStartIndex(text, rawMiddle);
            List<Block> candidate = replaceFirstBlock(retained, text.substring(middle));
            if (estimateStoryTokens(candidate) >= targetTokens)
            {
                latestValidStart = middle;
                low = rawMiddle + 1;
            }
            else
            {
                high = rawMiddle - 1;
            }
        }

        int preferredStart = preferredStartBoundary(text, latestValidStart);
        List<Block> preferred = replaceFirstBlock(retained, text.substring(preferredStart));
        if (preferredStart > 0 && estimateStoryTokens(preferred) >= targetTokens)
        {
            return preferred;
        }
        return replaceFirstBlock(retained, text.substring(latestValidStart));
    }

    private static List<Block> replaceFirstBlock(List<Block> blocks, String text)
    {
        List<Block> updated = new ArrayList<>(blocks);
        Block first = updated.getFirst();
        updated.set(0, new Block(first.id(), first.storyId(), first.role(), text, first.createdAt(), first.position()));
        return updated;
    }

    private static List<Block> forceTrimOldestStory(List<Block> window)
    {
        if (window.isEmpty())
        {
            return List.of();
        }

        List<Block> shortened = new ArrayList<>(window);
        Block first = shortened.getFirst();
        String text = safeText(first.text());
        if (text.length() <= 1)
        {
            shortened.removeFirst();
            return shortened;
        }

        int start = Character.charCount(text.codePointAt(0));
        String remaining = text.substring(start).stripLeading();
        if (remaining.isEmpty())
        {
            shortened.removeFirst();
        }
        else
        {
            shortened.set(0, new Block(first.id(), first.storyId(), first.role(), remaining,
                    first.createdAt(), first.position()));
        }
        return shortened;
    }

    private String shortenFromEnd(String text, int tokensToRemove)
    {
        if (text.isBlank())
        {
            return "";
        }

        int currentTokens = estimateTokens(text);
        int targetTokens = Math.max(0, currentTokens - Math.max(1, tokensToRemove));
        String shortened = retainPrefixAtMost(text, targetTokens);
        if (!shortened.equals(text))
        {
            return shortened;
        }

        int end = text.offsetByCodePoints(text.length(), -1);
        return text.substring(0, end).stripTrailing();
    }

    private String retainPrefixAtMost(String text, int targetTokens)
    {
        if (targetTokens <= 0 || text.isBlank())
        {
            return "";
        }
        if (estimateTokens(text) <= targetTokens)
        {
            return text;
        }

        int low = 0;
        int high = text.length();
        int longestValidEnd = 0;
        while (low <= high)
        {
            int rawMiddle = (low + high) >>> 1;
            int middle = safeEndIndex(text, rawMiddle);
            String candidate = text.substring(0, middle).stripTrailing();
            if (estimateTokens(candidate) <= targetTokens)
            {
                longestValidEnd = middle;
                low = rawMiddle + 1;
            }
            else
            {
                high = rawMiddle - 1;
            }
        }

        int preferredEnd = preferredEndBoundary(text, longestValidEnd);
        return text.substring(0, preferredEnd).stripTrailing();
    }

    private static int preferredStartBoundary(String text, int latestStart)
    {
        int safeLatest = safeStartIndex(text, Math.max(0, Math.min(latestStart, text.length() - 1)));
        int minimum = Math.max(1, safeLatest - BOUNDARY_SEARCH_LIMIT);

        for (int index = safeLatest; index >= minimum; index--)
        {
            if (index >= 2 && text.charAt(index - 1) == '\n' && text.charAt(index - 2) == '\n')
            {
                return index;
            }
        }
        for (int index = safeLatest; index >= minimum; index--)
        {
            if (index >= 1 && text.charAt(index - 1) == '\n')
            {
                return index;
            }
        }
        for (int index = safeLatest; index >= minimum; index--)
        {
            if (isSentenceBoundary(text, index))
            {
                return index;
            }
        }
        for (int index = safeLatest; index >= minimum; index--)
        {
            if (index > 0 && Character.isWhitespace(text.charAt(index - 1))
                    && !Character.isWhitespace(text.charAt(index)))
            {
                return index;
            }
        }
        return safeLatest;
    }

    private static int preferredEndBoundary(String text, int latestEnd)
    {
        int safeLatest = safeEndIndex(text, Math.max(0, Math.min(latestEnd, text.length())));
        int minimum = Math.max(0, safeLatest - BOUNDARY_SEARCH_LIMIT);

        for (int index = safeLatest; index > minimum; index--)
        {
            if (index >= 2 && text.charAt(index - 1) == '\n' && text.charAt(index - 2) == '\n')
            {
                return index;
            }
        }
        for (int index = safeLatest; index > minimum; index--)
        {
            if (text.charAt(index - 1) == '\n')
            {
                return index;
            }
        }
        for (int index = safeLatest; index > minimum; index--)
        {
            char previous = text.charAt(index - 1);
            if (previous == '.' || previous == '!' || previous == '?')
            {
                return index;
            }
        }
        for (int index = safeLatest; index > minimum; index--)
        {
            if (!Character.isWhitespace(text.charAt(index - 1))
                    && (index == text.length() || Character.isWhitespace(text.charAt(index))))
            {
                return index;
            }
        }
        return safeLatest;
    }

    private static boolean isSentenceBoundary(String text, int index)
    {
        if (index <= 0 || index >= text.length())
        {
            return false;
        }
        char previous = text.charAt(index - 1);
        return (previous == '.' || previous == '!' || previous == '?')
                && Character.isWhitespace(text.charAt(index));
    }

    private static int safeStartIndex(String text, int index)
    {
        int safe = Math.max(0, Math.min(index, Math.max(0, text.length() - 1)));
        if (safe > 0 && safe < text.length()
                && Character.isLowSurrogate(text.charAt(safe))
                && Character.isHighSurrogate(text.charAt(safe - 1)))
        {
            safe--;
        }
        return safe;
    }

    private static int safeEndIndex(String text, int index)
    {
        int safe = Math.max(0, Math.min(index, text.length()));
        if (safe > 0 && safe < text.length()
                && Character.isHighSurrogate(text.charAt(safe - 1))
                && Character.isLowSurrogate(text.charAt(safe)))
        {
            safe--;
        }
        return safe;
    }

    private static List<Block> insertAuthorNote(List<Block> window, int anPlacement, String authorNote)
    {
        if (authorNote.isBlank() || window.isEmpty())
        {
            return window;
        }

        int offset = Math.max(1, anPlacement);
        int index = Math.max(0, window.size() - offset);
        List<Block> updated = new ArrayList<>(window.size());

        for (int i = 0; i < window.size(); i++)
        {
            Block block = window.get(i);
            if (i == index)
            {
                String updatedText = "Author's Note: " + authorNote + "\n\n" + safeText(block.text());
                updated.add(new Block(block.id(), block.storyId(), block.role(), updatedText,
                        block.createdAt(), block.position()));
            }
            else
            {
                updated.add(block);
            }
        }
        return updated;
    }

    private List<ChatMessage> shortenTaskMessages(List<ChatMessage> messages, int excessTokens)
    {
        List<ChatMessage> shortened = new ArrayList<>(messages);
        for (int index = 0; index < shortened.size(); index++)
        {
            ChatMessage message = shortened.get(index);
            if (message.content().isBlank())
            {
                continue;
            }
            String content = shortenFromEnd(message.content(), excessTokens);
            if (content.equals(message.content()))
            {
                int end = message.content().offsetByCodePoints(message.content().length(), -1);
                content = message.content().substring(0, end).stripTrailing();
            }
            shortened.set(index, new ChatMessage(message.role(), content));
            return shortened;
        }
        return shortened;
    }

    private static List<ChatMessage> normalizeTrailingMessages(List<ChatMessage> messages)
    {
        List<ChatMessage> normalized = new ArrayList<>();
        for (ChatMessage message : messages == null ? List.<ChatMessage>of() : messages)
        {
            if (message == null || message.role().isBlank() || message.content().isBlank())
            {
                continue;
            }
            normalized.add(new ChatMessage(message.role().trim(), message.content().trim()));
        }
        return List.copyOf(normalized);
    }

    private static String mergeSystemInstructions(String existing, String addition)
    {
        String first = normalizeContextText(existing);
        String second = normalizeContextText(addition);
        if (first.isBlank())
        {
            return second;
        }
        if (second.isBlank())
        {
            return first;
        }
        return first + "\n\n" + second;
    }

    private static StoryCardSelection selectStoryCards(List<StoryCard> storyCards, List<Block> window, int lookback,
            String activationText, String forcedCardId)
    {
        String matchText = buildLookbackText(window, lookback);
        if (activationText != null && !activationText.isBlank())
        {
            matchText = matchText + "\n" + activationText;
        }
        List<StoryCard> pinned = new ArrayList<>();
        List<StoryCard> triggered = new ArrayList<>();
        Map<StoryCard, Integer> relevance = new HashMap<>();
        Map<StoryCard, List<String>> triggerMatches = new HashMap<>();

        for (StoryCard card : storyCards)
        {
            if (card == null)
            {
                continue;
            }
            if (forcedCardId != null && !forcedCardId.isBlank() && forcedCardId.equals(card.id()))
            {
                continue;
            }
            if (card.pinned())
            {
                pinned.add(card);
                continue;
            }
            List<String> matchedTriggers = findMatchedTriggers(card.triggers(), matchText);
            int hits = matchedTriggers.size();
            if (hits > 0)
            {
                triggered.add(card);
                relevance.put(card, hits);
                triggerMatches.put(card, matchedTriggers);
            }
        }

        triggered.sort(Comparator.comparingInt(relevance::get).reversed());
        return new StoryCardSelection(pinned, triggered, triggerMatches);
    }

    private static List<String> findMatchedTriggers(String triggers, String text)
    {
        if (triggers == null || triggers.isBlank() || text.isBlank())
        {
            return List.of();
        }
        List<String> matched = new ArrayList<>();
        for (String trigger : triggers.split(","))
        {
            String trimmed = trigger.trim();
            if (trimmed.isEmpty())
            {
                continue;
            }
            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(trimmed) + "\\b", Pattern.CASE_INSENSITIVE);
            if (pattern.matcher(text).find())
            {
                matched.add(trimmed);
            }
        }
        return matched;
    }

    private static String buildLookbackText(List<Block> window, int lookback)
    {
        if (window.isEmpty())
        {
            return "";
        }
        int start = 0;
        if (lookback > 0)
        {
            start = Math.max(0, window.size() - lookback);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < window.size(); i++)
        {
            sb.append(safeText(window.get(i).text())).append('\n');
        }
        return sb.toString();
    }

    private static String formatStoryCard(StoryCard card)
    {
        if (card == null)
        {
            return "";
        }
        String content = normalizeContextText(card.content());
        if (!content.isBlank())
        {
            return content;
        }
        return normalizeContextText(card.title());
    }

    private static List<Message> groupMessages(List<Block> window)
    {
        List<Message> messages = new ArrayList<>();
        if (window.isEmpty())
        {
            return messages;
        }

        int index = 0;
        while (index < window.size())
        {
            Block block = window.get(index);
            Role role = block.role();
            if (role == Role.USER)
            {
                StringBuilder currentText = new StringBuilder(normalizeBlockText(block));
                index++;
                while (index < window.size() && window.get(index).role() == Role.USER)
                {
                    currentText.append("\n\n").append(normalizeBlockText(window.get(index)));
                    index++;
                }
                messages.add(new Message(Role.USER, currentText.toString()));
                continue;
            }

            StringBuilder assistantText = new StringBuilder();
            while (index < window.size() && window.get(index).role() == Role.ASSISTANT)
            {
                appendContinuous(assistantText, normalizeBlockText(window.get(index)));
                index++;
            }

            if (assistantText.length() > 0)
            {
                messages.add(new Message(Role.ASSISTANT, assistantText.toString()));
            }
        }
        return messages;
    }

    private static void appendContinuous(StringBuilder currentText, String nextText)
    {
        if (nextText == null || nextText.isEmpty())
        {
            return;
        }
        if (currentText.length() == 0)
        {
            currentText.append(nextText);
            return;
        }
        currentText.append(nextText);
    }

    private static String normalizeBlockText(Block block)
    {
        String text = safeText(block.text());
        if (block.role() == Role.USER && PREFIX_USER_LINES)
        {
            return prefixUserText(text);
        }
        return text;
    }

    private static String prefixUserText(String text)
    {
        String[] lines = text.split("\\r?\\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++)
        {
            if (i > 0)
            {
                sb.append('\n');
            }
            sb.append("> ").append(lines[i]);
        }
        return sb.toString();
    }

    private int estimateStoryTokens(List<Block> window)
    {
        List<Message> messages = groupMessages(window);
        List<ChatMessage> chatMessages = buildPromptMessages("", messages);
        return estimateTokens(chatMessages);
    }

    private int estimateStandaloneMessage(String role, String text)
    {
        return estimateTokens(List.of(new ChatMessage(role, text)));
    }

    private int estimateTokens(String text)
    {
        if (text == null || text.isBlank())
        {
            return 0;
        }
        int baseEstimate = estimateTokensHeuristic(text);
        if (tokenEstimatorOverride != null)
        {
            try
            {
                int overridden = tokenEstimatorOverride.applyAsInt(text);
                if (overridden > 0)
                {
                    baseEstimate = overridden;
                }
            }
            catch (RuntimeException ignored)
            {
                // Keep the stable heuristic when a custom estimator fails.
            }
        }
        return Math.max(1, (int) Math.ceil(baseEstimate * operationTokenScale));
    }

    private int estimateTokens(List<ChatMessage> messages)
    {
        int total = 0;
        for (ChatMessage message : messages)
        {
            total += 4;
            total += estimateTokens(message.role());
            total += estimateTokens(message.content());
        }
        return total;
    }

    private static int estimateTokensHeuristic(String text)
    {
        if (text == null || text.isBlank())
        {
            return 0;
        }
        double raw = text.length() / (double) CHARS_PER_TOKEN;
        return Math.max(1, (int) Math.ceil(raw * TOKEN_ESTIMATE_MULTIPLIER));
    }

    private static String normalizeContextText(String text)
    {
        return safeText(text).trim();
    }

    private static String safeText(String text)
    {
        return text == null ? "" : text;
    }

    private static String safeTitle(StoryCard card)
    {
        if (card == null)
        {
            return "(untitled)";
        }
        String title = normalizeContextText(card.title());
        return title.isEmpty() ? "(untitled)" : title;
    }

    private static void logCompilationReport(PromptContextReport report)
    {
        PromptBudget budget = report.budget();
        System.out.println("[PromptCompiler] Estimated prompt tokens: " + report.estimatedInputTokens()
                + " / input budget " + budget.inputLimit()
                + " (context " + budget.contextLimit()
                + ", response reserve " + budget.responseReserve()
                + ", estimation reserve " + budget.estimationSafetyReserve() + ")");
        for (Entry entry : report.entries())
        {
            if (entry.status() != Status.INCLUDED)
            {
                System.out.println("[PromptCompiler] " + entry.label() + ": "
                        + entry.status().name().toLowerCase() + " ("
                        + entry.includedEstimatedTokens() + " / "
                        + entry.originalEstimatedTokens() + " estimated tokens)");
            }
        }
    }

    private record StoryCardSelection(List<StoryCard> pinned, List<StoryCard> triggered,
            Map<StoryCard, List<String>> triggerMatches)
    {
    }

    private record Message(Role role, String content)
    {
    }

    private record CompiledState(List<ChatMessage> messages, int estimatedTokens)
    {
        private CompiledState
        {
            messages = messages == null ? List.of() : List.copyOf(messages);
            estimatedTokens = Math.max(0, estimatedTokens);
        }
    }

    private static List<ChatMessage> buildPromptMessages(String systemText, List<Message> messages)
    {
        List<ChatMessage> promptMessages = new ArrayList<>();
        if (systemText != null && !systemText.isBlank())
        {
            promptMessages.add(new ChatMessage("system", systemText));
        }
        for (Message message : messages)
        {
            promptMessages.add(new ChatMessage(message.role().wire(), message.content()));
        }
        return promptMessages;
    }
}
