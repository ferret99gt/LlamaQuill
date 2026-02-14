package com.llamaquill.autocards;

import com.llamaquill.model.StoryCard;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AutoCards
{
    public static final String CANDIDATE_SELECTION_MODE_HEURISTICS = "Proper Noun Heuristics";
    public static final String CANDIDATE_SELECTION_MODE_ASK_MODEL = "Ask Model";

    private static final String AUTO_CARD_PROMPT_SYSTEM_TAG = "<system>";
    private static final String AUTO_CARD_PROMPT_USER_TAG = "<user>";
    private static final Pattern AUTO_CARD_PROPER_NOUN_PATTERN = Pattern.compile(
            "\\b([A-Z][\\p{L}\\p{N}'-]*(?:\\s+[A-Z][\\p{L}\\p{N}'-]*){0,3})\\b");
    private static final Set<String> AUTO_CARD_CONTRACTION_SUFFIXES = Set.of(
            "re", "ve", "ll", "d", "m", "t");
    private static final Set<String> AUTO_CARD_STOPWORDS = Set.of(
            "a", "an", "and", "as", "at", "before", "but", "did", "do", "does", "for", "from", "he",
            "her", "hers", "him", "his", "how", "i", "if", "in", "it", "its", "me", "my", "of", "on",
            "or", "our", "ours", "she", "so", "story", "that", "the", "their", "theirs", "them", "then",
            "there", "they", "this", "to", "user", "we", "what", "when", "where", "while", "who", "why",
            "you", "your", "yours", "here");

    public record Candidate(String title, String triggers)
    {
    }

    public record PromptParts(String system, String user)
    {
    }

    private AutoCards()
    {
    }

    public static String normalizeCandidateSelectionMode(String mode)
    {
        if (mode == null || mode.isBlank())
        {
            return CANDIDATE_SELECTION_MODE_HEURISTICS;
        }
        if (CANDIDATE_SELECTION_MODE_ASK_MODEL.equals(mode))
        {
            return CANDIDATE_SELECTION_MODE_ASK_MODEL;
        }
        return CANDIDATE_SELECTION_MODE_HEURISTICS;
    }

    public static List<Candidate> extractCandidatesByHeuristics(String excerpt, List<StoryCard> currentCards, int maxCount)
    {
        if (excerpt == null || excerpt.isBlank() || maxCount <= 0)
        {
            return List.of();
        }

        Map<String, StoryCard> existingByTitle = new HashMap<>();
        for (StoryCard card : currentCards)
        {
            if (card.title() == null || card.title().isBlank())
            {
                continue;
            }
            String titleKey = canonicalizeHeuristicCandidateKey(card.title());
            if (!titleKey.isBlank())
            {
                existingByTitle.putIfAbsent(titleKey, card);
            }
            for (String titleToken : card.title().split("\\s+"))
            {
                String titleTokenKey = canonicalizeHeuristicCandidateKey(titleToken);
                if (titleTokenKey.length() >= 3 && !AUTO_CARD_STOPWORDS.contains(titleTokenKey))
                {
                    existingByTitle.putIfAbsent(titleTokenKey, card);
                }
            }
            for (String trigger : splitTriggers(card.triggers()))
            {
                String triggerKey = canonicalizeHeuristicCandidateKey(trigger);
                if (!triggerKey.isBlank())
                {
                    existingByTitle.putIfAbsent(triggerKey, card);
                }
            }
        }

        Map<String, Integer> hitCounts = new HashMap<>();
        Map<String, Integer> sentenceStartHitCounts = new HashMap<>();
        Map<String, Integer> questionOrExclaimHits = new HashMap<>();
        Map<String, String> orderedTitles = new LinkedHashMap<>();
        Matcher matcher = AUTO_CARD_PROPER_NOUN_PATTERN.matcher(excerpt);
        while (matcher.find())
        {
            String title = normalizeHeuristicCandidateTitle(matcher.group(1));
            if (!isValidHeuristicCandidateTitle(title))
            {
                continue;
            }
            String key = canonicalizeHeuristicCandidateKey(title);
            if (key.isBlank())
            {
                continue;
            }
            orderedTitles.putIfAbsent(key, title);
            hitCounts.merge(key, 1, Integer::sum);
            if (isHeuristicSentenceStart(excerpt, matcher.start()))
            {
                sentenceStartHitCounts.merge(key, 1, Integer::sum);
            }
            if (isHeuristicQuestionOrExclamation(excerpt, matcher.end()))
            {
                questionOrExclaimHits.merge(key, 1, Integer::sum);
            }
        }

        Map<String, Candidate> candidates = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : orderedTitles.entrySet())
        {
            if (candidates.size() >= maxCount)
            {
                break;
            }
            String key = entry.getKey();
            String title = entry.getValue();
            StoryCard existing = existingByTitle.get(key);
            boolean singleWord = title.indexOf(' ') < 0;
            if (existing == null && singleWord && hitCounts.getOrDefault(key, 0) < 2)
            {
                continue;
            }
            if (existing == null
                    && sentenceStartHitCounts.getOrDefault(key, 0) == hitCounts.getOrDefault(key, 0)
                    && hitCounts.getOrDefault(key, 0) < 3)
            {
                continue;
            }
            if (existing == null
                    && questionOrExclaimHits.getOrDefault(key, 0) == hitCounts.getOrDefault(key, 0)
                    && hitCounts.getOrDefault(key, 0) < 3)
            {
                continue;
            }
            String resolvedTitle = existing == null ? title : existing.title();
            String triggers = existing == null ? buildHeuristicCandidateTriggers(resolvedTitle) : existing.triggers();
            candidates.put(key, new Candidate(resolvedTitle, triggers));
        }
        return new ArrayList<>(candidates.values());
    }

    public static List<Candidate> parseCandidatesFromModelResponse(String response, int maxCount)
    {
        if (response == null || response.isBlank())
        {
            return List.of();
        }
        String json = extractJsonArray(response);
        if (json == null)
        {
            json = extractJsonObject(response);
            if (json == null)
            {
                return List.of();
            }
            json = "[" + json + "]";
        }

        try
        {
            JSONArray array = new JSONArray(json);
            List<Candidate> results = new ArrayList<>();
            for (int i = 0; i < array.length() && results.size() < maxCount; i++)
            {
                Object entry = array.get(i);
                if (!(entry instanceof JSONObject obj))
                {
                    continue;
                }
                String title = obj.optString("title", "").trim();
                if (title.isBlank())
                {
                    continue;
                }
                String triggers = obj.optString("triggers", "").trim();
                results.add(new Candidate(title, triggers));
            }
            return results;
        }
        catch (Exception e)
        {
            return List.of();
        }
    }

    public static String truncateContent(String content, int limit)
    {
        if (content.length() <= limit)
        {
            return content;
        }
        if (limit <= 3)
        {
            return content.substring(0, limit);
        }
        return content.substring(0, limit - 3) + "...";
    }

    public static String buildChatPrompt(String system, String user)
    {
        String systemText = system == null ? "" : system.trim();
        String userText = user == null ? "" : user.trim();
        StringBuilder prompt = new StringBuilder();
        if (!systemText.isBlank())
        {
            prompt.append("<|im_start|>system\n")
                    .append(systemText)
                    .append("<|im_end|>\n");
        }
        prompt.append("<|im_start|>user\n")
                .append(userText)
                .append("<|im_end|>\n")
                .append("<|im_start|>assistant\n");
        return prompt.toString();
    }

    public static PromptParts buildPromptParts(String template, String title, String triggers, String content,
            String excerpt)
    {
        String rendered = applyPromptTemplate(template, title, triggers, content, excerpt);
        return splitPrompt(rendered);
    }

    public static String applyPromptTemplate(String template, String title, String triggers, String content, String excerpt)
    {
        if (template == null)
        {
            return "";
        }
        return template.replace("%{title}", title == null ? "" : title)
                .replace("%{triggers}", triggers == null ? "" : triggers)
                .replace("%{content}", content == null ? "" : content)
                .replace("%{excerpt}", excerpt == null ? "" : excerpt)
                .replace("%{verbosity}", "");
    }

    private static PromptParts splitPrompt(String prompt)
    {
        if (prompt == null)
        {
            return new PromptParts("", "");
        }
        String raw = prompt.trim();
        if (raw.isBlank())
        {
            return new PromptParts("", "");
        }

        String lowered = raw.toLowerCase(Locale.ROOT);
        int systemIndex = lowered.indexOf(AUTO_CARD_PROMPT_SYSTEM_TAG);
        int userIndex = lowered.indexOf(AUTO_CARD_PROMPT_USER_TAG);

        if (systemIndex < 0 && userIndex < 0)
        {
            return new PromptParts("", raw);
        }
        if (systemIndex >= 0 && userIndex >= 0)
        {
            if (systemIndex < userIndex)
            {
                String system = raw.substring(systemIndex + AUTO_CARD_PROMPT_SYSTEM_TAG.length(), userIndex).trim();
                String user = raw.substring(userIndex + AUTO_CARD_PROMPT_USER_TAG.length()).trim();
                return new PromptParts(system, user);
            }
            String user = raw.substring(userIndex + AUTO_CARD_PROMPT_USER_TAG.length(), systemIndex).trim();
            String system = raw.substring(systemIndex + AUTO_CARD_PROMPT_SYSTEM_TAG.length()).trim();
            return new PromptParts(system, user);
        }
        if (systemIndex >= 0)
        {
            String system = raw.substring(systemIndex + AUTO_CARD_PROMPT_SYSTEM_TAG.length()).trim();
            return new PromptParts(system, "");
        }
        String user = raw.substring(userIndex + AUTO_CARD_PROMPT_USER_TAG.length()).trim();
        return new PromptParts("", user);
    }

    private static String extractJsonArray(String text)
    {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start)
        {
            return text.substring(start, end + 1);
        }
        return null;
    }

    private static String extractJsonObject(String text)
    {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start)
        {
            return text.substring(start, end + 1);
        }
        return null;
    }

    private static String normalizeHeuristicCandidateTitle(String value)
    {
        if (value == null)
        {
            return "";
        }
        String cleaned = value.trim().replaceAll("^[\\p{Punct}\\s]+", "").replaceAll("[\\p{Punct}\\s]+$", "");
        if (cleaned.isBlank())
        {
            return "";
        }
        String[] words = cleaned.split("\\s+");
        List<String> normalizedWords = new ArrayList<>(words.length);
        for (String word : words)
        {
            String normalizedWord = normalizeHeuristicCandidateWord(word);
            if (normalizedWord.isBlank())
            {
                return "";
            }
            normalizedWords.add(normalizedWord);
        }
        return String.join(" ", normalizedWords);
    }

    private static String normalizeHeuristicCandidateWord(String value)
    {
        String cleaned = value.replaceAll("^[^\\p{L}\\p{N}]+", "").replaceAll("[^\\p{L}\\p{N}'-]+$", "");
        if (cleaned.isBlank())
        {
            return "";
        }
        String lower = cleaned.toLowerCase(Locale.ROOT);
        if (isContractionWord(lower))
        {
            return "";
        }
        cleaned = cleaned.replaceAll("(?i)'s$", "");
        return cleaned.replaceAll("\\s+", " ").trim();
    }

    private static boolean isContractionWord(String value)
    {
        int apostropheIndex = value.lastIndexOf('\'');
        if (apostropheIndex <= 0 || apostropheIndex >= value.length() - 1)
        {
            return false;
        }
        String suffix = value.substring(apostropheIndex + 1);
        return AUTO_CARD_CONTRACTION_SUFFIXES.contains(suffix);
    }

    private static String canonicalizeHeuristicCandidateKey(String value)
    {
        String normalized = normalizeHeuristicCandidateTitle(value);
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static boolean isHeuristicSentenceStart(String text, int index)
    {
        int i = index - 1;
        while (i >= 0 && Character.isWhitespace(text.charAt(i)))
        {
            i--;
        }
        if (i < 0)
        {
            return true;
        }
        char previous = text.charAt(i);
        return previous == '.' || previous == '!' || previous == '?' || previous == ':' || previous == ';'
                || previous == '\n';
    }

    private static boolean isHeuristicQuestionOrExclamation(String text, int endIndex)
    {
        int i = endIndex;
        while (i < text.length() && Character.isWhitespace(text.charAt(i)))
        {
            i++;
        }
        if (i >= text.length())
        {
            return false;
        }
        char c = text.charAt(i);
        return c == '?' || c == '!';
    }

    private static List<String> splitTriggers(String triggers)
    {
        if (triggers == null || triggers.isBlank())
        {
            return List.of();
        }
        String[] parts = triggers.split("[,\\n;|]");
        List<String> out = new ArrayList<>(parts.length);
        for (String part : parts)
        {
            String trimmed = part.trim();
            if (!trimmed.isBlank())
            {
                out.add(trimmed);
            }
        }
        return out;
    }

    private static boolean isValidHeuristicCandidateTitle(String title)
    {
        if (title == null || title.isBlank() || title.length() < 2)
        {
            return false;
        }
        String lower = title.toLowerCase(Locale.ROOT);
        if (AUTO_CARD_STOPWORDS.contains(lower))
        {
            return false;
        }
        String[] words = title.split("\\s+");
        if (words.length == 1)
        {
            return !AUTO_CARD_STOPWORDS.contains(words[0].toLowerCase(Locale.ROOT));
        }
        int nonStopWords = 0;
        for (String word : words)
        {
            if (!AUTO_CARD_STOPWORDS.contains(word.toLowerCase(Locale.ROOT)))
            {
                nonStopWords++;
            }
        }
        return nonStopWords > 0;
    }

    private static String buildHeuristicCandidateTriggers(String title)
    {
        if (title == null || title.isBlank())
        {
            return "";
        }
        LinkedHashSet<String> triggers = new LinkedHashSet<>();
        String key = canonicalizeHeuristicCandidateKey(title);
        if (!key.isBlank())
        {
            triggers.add(key);
        }
        for (String token : title.split("\\s+"))
        {
            String normalizedToken = normalizeHeuristicCandidateWord(token).toLowerCase(Locale.ROOT);
            if (normalizedToken.length() >= 3 && !AUTO_CARD_STOPWORDS.contains(normalizedToken))
            {
                triggers.add(normalizedToken);
            }
        }
        return String.join(", ", triggers);
    }
}
