package com.llamaquill.prompt;

import com.llamaquill.model.Block;
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
import java.util.regex.Pattern;

public class PromptCompiler
{
    private static final int CHARS_PER_TOKEN = 4;
    private static final boolean PREFIX_USER_LINES = true;

    public PromptCompilation compile(Story story, List<Block> blocks, List<StoryCard> storyCards,
            GenerationSettings settings)
    {
        Objects.requireNonNull(story, "story");
        Objects.requireNonNull(blocks, "blocks");
        Objects.requireNonNull(storyCards, "storyCards");
        Objects.requireNonNull(settings, "settings");

        int tokenBudget = Math.max(0, settings.contextLimit() - settings.responseLength());
        int minWindowChars = Math.max(0, settings.minStoryWindow()) * CHARS_PER_TOKEN;

        List<Block> window = buildStoryWindow(blocks, minWindowChars);

        StoryCardSelection selection = selectStoryCards(storyCards, window,
                settings.storyCardLookback());
        String plotEssentials = safeText(story.plotEssentials());

        while (true)
        {
            List<Block> windowWithNote = insertAuthorNote(window, settings.anPlacement(),
                    safeText(story.authorNote()));
            String systemText = buildSystemText(safeText(story.systemPrompt()), plotEssentials,
                    selection.pinned, selection.triggered);

            List<Message> messages = groupMessages(windowWithNote);
            String prompt = ChatMl.format(systemText, messages);
            int estimatedTokens = estimateTokens(prompt);

            if (estimatedTokens <= tokenBudget)
            {
                return new PromptCompilation(prompt, estimatedTokens);
            }

            boolean trimmed = false;
            if (window.size() > 1 && windowSizeChars(window) > minWindowChars)
            {
                window = trimStoryWindow(window, minWindowChars);
                trimmed = true;
            }
            else if (!selection.triggered.isEmpty())
            {
                selection.triggered.remove(selection.triggered.size() - 1);
                trimmed = true;
            }
            else if (!selection.pinned.isEmpty())
            {
                selection.pinned.remove(selection.pinned.size() - 1);
                trimmed = true;
            }
            else if (!plotEssentials.isBlank())
            {
                int excessTokens = estimatedTokens - tokenBudget;
                plotEssentials = trimFromStart(plotEssentials, excessTokens * CHARS_PER_TOKEN);
                trimmed = true;
            }

            if (!trimmed)
            {
                return new PromptCompilation(prompt, estimatedTokens);
            }
        }
    }

    private static List<Block> buildStoryWindow(List<Block> blocks, int minWindowChars)
    {
        List<Block> window = new ArrayList<>();
        int totalChars = 0;
        for (int i = blocks.size() - 1; i >= 0; i--)
        {
            Block block = blocks.get(i);
            window.add(0, block);
            totalChars += block.text().length();
            if (totalChars >= minWindowChars)
            {
                break;
            }
        }
        return window;
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
        while (trimmed.size() > 1 && windowSizeChars(trimmed) > minWindowChars)
        {
            trimmed.remove(0);
        }
        return trimmed;
    }

    private static List<Block> insertAuthorNote(List<Block> window, int anPlacement,
            String authorNote)
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

    private static StoryCardSelection selectStoryCards(List<StoryCard> storyCards,
            List<Block> window, int lookback)
    {
        String matchText = buildLookbackText(window, lookback);
        List<StoryCard> pinned = new ArrayList<>();
        List<StoryCard> triggered = new ArrayList<>();
        Map<StoryCard, Integer> relevance = new HashMap<>();

        for (StoryCard card : storyCards)
        {
            if (card.pinned())
            {
                pinned.add(card);
                continue;
            }
            int hits = countTriggerHits(card.triggers(), matchText);
            if (hits > 0)
            {
                triggered.add(card);
                relevance.put(card, hits);
            }
        }

        triggered.sort(Comparator.comparingInt(relevance::get).reversed());
        return new StoryCardSelection(pinned, triggered);
    }

    private static int countTriggerHits(String triggers, String text)
    {
        if (triggers == null || triggers.isBlank() || text.isBlank())
        {
            return 0;
        }
        int hits = 0;
        for (String trigger : triggers.split(","))
        {
            String trimmed = trigger.trim();
            if (trimmed.isEmpty())
            {
                continue;
            }
            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(trimmed) + "\\b",
                    Pattern.CASE_INSENSITIVE);
            if (pattern.matcher(text).find())
            {
                hits++;
            }
        }
        return hits;
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

    private static String buildSystemText(String systemPrompt, String plotEssentials,
            List<StoryCard> pinned, List<StoryCard> triggered)
    {
        StringBuilder sb = new StringBuilder();
        if (!systemPrompt.isBlank())
        {
            sb.append(systemPrompt.trim());
        }
        if (!plotEssentials.isBlank())
        {
            sb.append("\n\nPlot Essentials:\n").append(plotEssentials.trim());
        }
        if (!pinned.isEmpty())
        {
            sb.append("\n\nStory Cards (Pinned):\n");
            appendCards(sb, pinned);
        }
        if (!triggered.isEmpty())
        {
            sb.append("\n\nStory Cards (Triggered):\n");
            appendCards(sb, triggered);
        }
        return sb.toString().trim();
    }

    private static void appendCards(StringBuilder sb, List<StoryCard> cards)
    {
        for (StoryCard card : cards)
        {
            sb.append("- ").append(card.title()).append(": ").append(card.content()).append('\n');
        }
    }

    private static List<Message> groupMessages(List<Block> window)
    {
        List<Message> messages = new ArrayList<>();
        if (window.isEmpty())
        {
            return messages;
        }

        Role currentRole = null;
        StringBuilder currentText = new StringBuilder();

        for (Block block : window)
        {
            Role role = block.role();
            String text = normalizeBlockText(block);

            if (currentRole == null)
            {
                currentRole = role;
                currentText.append(text);
                continue;
            }

            if (currentRole == role)
            {
                currentText.append("\n\n").append(text);
            }
            else
            {
                messages.add(new Message(currentRole, currentText.toString()));
                currentRole = role;
                currentText = new StringBuilder(text);
            }
        }

        if (currentRole != null)
        {
            messages.add(new Message(currentRole, currentText.toString()));
        }
        return messages;
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

    private static int estimateTokens(String text)
    {
        if (text == null || text.isBlank())
        {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.length() / (double) CHARS_PER_TOKEN));
    }

    private static String safeText(String text)
    {
        return text == null ? "" : text;
    }

    private record StoryCardSelection(List<StoryCard> pinned, List<StoryCard> triggered)
    {
    }

    private record Message(Role role, String content)
    {
    }

    private static final class ChatMl
    {
        private ChatMl()
        {
        }

        static String format(String systemText, List<Message> messages)
        {
            StringBuilder sb = new StringBuilder();
            if (systemText != null && !systemText.isBlank())
            {
                appendMessage(sb, "system", systemText.trim());
            }
            for (Message message : messages)
            {
                appendMessage(sb, message.role().wire(), message.content());
            }
            return sb.toString().trim();
        }

        private static void appendMessage(StringBuilder sb, String role, String content)
        {
            if (sb.length() > 0)
            {
                sb.append('\n');
            }
            sb.append("<|im_start|>").append(role).append('\n').append(content).append('\n')
                    .append("<|im_end|>");
        }
    }
}
