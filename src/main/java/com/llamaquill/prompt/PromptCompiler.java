package com.llamaquill.prompt;

import com.llamaquill.model.Block;
import com.llamaquill.model.ChatMessage;
import com.llamaquill.model.GenerationSettings;
import com.llamaquill.model.Role;
import com.llamaquill.model.Story;
import com.llamaquill.model.StoryCard;

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
    private static final int RESPONSE_SAFETY_BAND_MULTIPLIER = 2;
    private static final boolean PREFIX_USER_LINES = true;
    private ToIntFunction<String> tokenEstimator = PromptCompiler::estimateTokensHeuristic;

    public void setTokenEstimator(ToIntFunction<String> tokenEstimator)
    {
        this.tokenEstimator = tokenEstimator == null ? PromptCompiler::estimateTokensHeuristic : tokenEstimator;
    }

    public PromptCompilation compile(Story story, List<Block> blocks, List<StoryCard> storyCards, GenerationSettings settings)
    {
        Objects.requireNonNull(story, "story");
        Objects.requireNonNull(blocks, "blocks");
        Objects.requireNonNull(storyCards, "storyCards");
        Objects.requireNonNull(settings, "settings");

        int safetyBand = Math.max(0, settings.responseLength() * RESPONSE_SAFETY_BAND_MULTIPLIER);
        int tokenBudget = Math.max(0, settings.contextLimit() - settings.responseLength() - safetyBand);
        int minWindowChars = Math.max(0, settings.minStoryWindow()) * CHARS_PER_TOKEN;

        List<Block> promptBlocks = filterPromptBlocks(blocks);
        List<Block> window = new ArrayList<>(promptBlocks);

        StoryCardSelection selection = selectStoryCards(storyCards, window, settings.storyCardLookback());
        String originalPlotEssentials = safeText(story.plotEssentials());
        String plotEssentials = originalPlotEssentials;
        boolean plotEssentialsTrimmed = false;
        List<DroppedStoryCard> droppedForSpace = new ArrayList<>();
        while (true)
        {
            List<Block> windowWithNote = insertAuthorNote(window, settings.anPlacement(), safeText(story.authorNote()));
            String systemText = safeText(story.systemPrompt());

            List<Message> messages = new ArrayList<>();
            if (!plotEssentials.isBlank())
            {
                messages.add(new Message(Role.USER, plotEssentials.trim()));
            }
            for (StoryCard card : selection.pinned)
            {
                messages.add(new Message(Role.USER, formatStoryCard(card)));
            }
            for (StoryCard card : selection.triggered)
            {
                messages.add(new Message(Role.USER, formatStoryCard(card)));
            }
            messages.addAll(groupMessages(windowWithNote));
            List<ChatMessage> promptMessages = buildPromptMessages(systemText, messages);
            int estimatedTokens = estimateTokens(promptMessages);

            if (estimatedTokens <= tokenBudget)
            {
                logCompilationReport(selection, droppedForSpace, plotEssentialsTrimmed, originalPlotEssentials,
                        plotEssentials, estimatedTokens, tokenBudget);
                return new PromptCompilation(promptMessages, estimatedTokens);
            }

            boolean trimmed = false;
            // Start from the full promptable story and trim down to fit, preserving
            // the most recent prose unless higher-priority context must be sacrificed.
            if (window.size() > 1 && windowSizeChars(window) > minWindowChars)
            {
                window = trimStoryWindow(window, minWindowChars);
                trimmed = true;
            }
            else if (!selection.triggered.isEmpty())
            {
                StoryCard removed = selection.triggered.removeLast();
                droppedForSpace.add(new DroppedStoryCard(removed, "triggered"));
                trimmed = true;
            }
            else if (!selection.pinned.isEmpty())
            {
                StoryCard removed = selection.pinned.removeLast();
                droppedForSpace.add(new DroppedStoryCard(removed, "pinned"));
                trimmed = true;
            }
            else if (!plotEssentials.isBlank())
            {
                int excessTokens = estimatedTokens - tokenBudget;
                plotEssentials = trimFromStart(plotEssentials, excessTokens * CHARS_PER_TOKEN);
                if (!plotEssentials.equals(originalPlotEssentials))
                {
                    plotEssentialsTrimmed = true;
                }
                trimmed = true;
            }

            if (!trimmed)
            {
                logCompilationReport(selection, droppedForSpace, plotEssentialsTrimmed, originalPlotEssentials,
                        plotEssentials, estimatedTokens, tokenBudget);
                return new PromptCompilation(promptMessages, estimatedTokens);
            }
        }
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

    private static int windowSizeChars(List<Block> window)
    {
        int total = 0;
        for (Block block : window)
        {
            total += block.text().length();
        }
        return total;
    }

    private static List<Block> trimStoryWindow(List<Block> window, int minWindowChars)
    {
        List<Block> trimmed = new ArrayList<>(window);
        if (trimmed.size() > 1 && windowSizeChars(trimmed) > minWindowChars)
        {
            trimmed.removeFirst();
        }
        return trimmed;
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
                String updatedText = "Author's Note: " + authorNote + "\n\n" + block.text();
                updated.add(
                        new Block(block.id(), block.storyId(), block.role(), updatedText, block.createdAt(), block.position()));
            }
            else
            {
                updated.add(block);
            }
        }
        return updated;
    }

    private static StoryCardSelection selectStoryCards(List<StoryCard> storyCards, List<Block> window, int lookback)
    {
        String matchText = buildLookbackText(window, lookback);
        List<StoryCard> pinned = new ArrayList<>();
        List<StoryCard> triggered = new ArrayList<>();
        Map<StoryCard, Integer> relevance = new HashMap<>();
        Map<StoryCard, List<String>> triggerMatches = new HashMap<>();

        for (StoryCard card : storyCards)
        {
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
            sb.append(window.get(i).text()).append('\n');
        }
        return sb.toString();
    }

    private static String formatStoryCard(StoryCard card)
    {
        if (card == null)
        {
            return "";
        }
        String content = safeText(card.content()).trim();
        if (!content.isBlank())
        {
            return content;
        }
        return safeText(card.title()).trim();
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

    private static String trimFromStart(String text, int charsToTrim)
    {
        if (text.isBlank())
        {
            return "";
        }
        if (charsToTrim <= 0)
        {
            return text;
        }
        if (charsToTrim >= text.length())
        {
            return "";
        }
        return text.substring(charsToTrim).stripLeading();
    }

    private int estimateTokens(String text)
    {
        if (text == null || text.isBlank())
        {
            return 0;
        }
        int estimated = -1;
        try
        {
            estimated = tokenEstimator.applyAsInt(text);
        }
        catch (Exception ignored)
        {
            // Fall back to heuristic below.
        }
        if (estimated > 0)
        {
            return estimated;
        }
        return estimateTokensHeuristic(text);
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

    private static String safeText(String text)
    {
        return text == null ? "" : text;
    }

    private static void logCompilationReport(StoryCardSelection selection, List<DroppedStoryCard> droppedForSpace,
            boolean plotEssentialsTrimmed, String originalPlotEssentials, String finalPlotEssentials,
            int estimatedTokens, int tokenBudget)
    {
        System.out.println("[PromptCompiler] Estimated prompt tokens: " + estimatedTokens + " / budget " + tokenBudget);
        System.out.println("[PromptCompiler] Included story cards:");
        if (selection.pinned.isEmpty() && selection.triggered.isEmpty())
        {
            System.out.println("[PromptCompiler]   - none");
        }
        for (StoryCard card : selection.pinned)
        {
            System.out.println("[PromptCompiler]   - " + safeTitle(card) + " (pinned)");
        }
        for (StoryCard card : selection.triggered)
        {
            List<String> matched = selection.triggerMatches.getOrDefault(card, List.of());
            if (matched.isEmpty())
            {
                System.out.println("[PromptCompiler]   - " + safeTitle(card) + " (triggered)");
            }
            else
            {
                System.out.println("[PromptCompiler]   - " + safeTitle(card) + " (triggered by: "
                        + String.join(", ", matched) + ")");
            }
        }

        System.out.println("[PromptCompiler] Story cards dropped for space:");
        if (droppedForSpace.isEmpty())
        {
            System.out.println("[PromptCompiler]   - none");
        }
        for (DroppedStoryCard dropped : droppedForSpace)
        {
            StoryCard card = dropped.card;
            if ("triggered".equals(dropped.reason))
            {
                List<String> matched = selection.triggerMatches.getOrDefault(card, List.of());
                if (matched.isEmpty())
                {
                    System.out.println("[PromptCompiler]   - " + safeTitle(card) + " (triggered)");
                }
                else
                {
                    System.out.println("[PromptCompiler]   - " + safeTitle(card) + " (triggered by: "
                            + String.join(", ", matched) + ")");
                }
            }
            else
            {
                System.out.println("[PromptCompiler]   - " + safeTitle(card) + " (pinned)");
            }
        }

        if (plotEssentialsTrimmed)
        {
            System.out.println("[PromptCompiler] Plot essentials trimmed for space: yes ("
                    + safeText(originalPlotEssentials).length() + " -> " + safeText(finalPlotEssentials).length()
                    + " chars)");
        }
        else
        {
            System.out.println("[PromptCompiler] Plot essentials trimmed for space: no");
        }
    }

    private static String safeTitle(StoryCard card)
    {
        if (card == null)
        {
            return "(untitled)";
        }
        String title = safeText(card.title()).trim();
        if (title.isEmpty())
        {
            return "(untitled)";
        }
        return title;
    }

    private record StoryCardSelection(List<StoryCard> pinned, List<StoryCard> triggered,
            Map<StoryCard, List<String>> triggerMatches)
    {
    }

    private record DroppedStoryCard(StoryCard card, String reason)
    {
    }

    private record Message(Role role, String content)
    {
    }

    private static List<ChatMessage> buildPromptMessages(String systemText, List<Message> messages)
    {
        List<ChatMessage> promptMessages = new ArrayList<>();
        if (systemText != null && !systemText.isBlank())
        {
            promptMessages.add(new ChatMessage("system", systemText.trim()));
        }
        for (Message message : messages)
        {
            promptMessages.add(new ChatMessage(message.role().wire(), message.content()));
        }
        return promptMessages;
    }

}
