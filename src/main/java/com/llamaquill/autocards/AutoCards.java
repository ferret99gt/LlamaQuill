package com.llamaquill.autocards;

import com.llamaquill.model.ChatMessage;
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
    public static final String CONTEXT_MODE_WINDOWED_EXCERPT = "Windowed Excerpt";
    public static final String CONTEXT_MODE_FULL_STORY = "Full Story Context";

    private static final Pattern AUTO_CARD_PROMPT_TAG_PATTERN = Pattern.compile("(?i)<(system|user|assistant)>");
    private static final Set<String> AUTO_CARD_CONTRACTION_SUFFIXES = Set.of(
            "re", "ve", "ll", "d", "m", "t");
    private static final Set<String> AUTO_CARD_MINOR_WORDS = Set.of(
            "&", "the", "for", "of", "le", "la", "el");
    private static final Set<String> AUTO_CARD_HONORIFICS = Set.of(
            "mr.", "ms.", "mrs.", "dr.");
    private static final Set<String> AUTO_CARD_ABBREVIATIONS = Set.of(
            "sr.", "jr.", "etc.", "st.", "ex.", "inc.");
    private static final Set<String> AUTO_CARD_STOPWORDS = Set.of(
            "a", "an", "and", "as", "at", "before", "but", "did", "do", "does", "for", "from", "he",
            "her", "hers", "him", "his", "how", "i", "if", "in", "it", "its", "me", "my", "of", "on",
            "or", "our", "ours", "she", "so", "story", "that", "the", "their", "theirs", "them", "then",
            "there", "they", "this", "to", "user", "we", "what", "when", "where", "while", "who", "why",
            "you", "your", "yours", "here", "yes", "no", "maybe", "ready", "should", "could", "would",
            "will", "shall", "must", "can", "may", "might", "is", "are", "was", "were", "am");

    public record Candidate(String title, String triggers)
    {
    }

    public record GeneratedCard(String title, String triggers, String content)
    {
    }

    public record PromptParts(String system, String user, String assistant)
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

    public static String normalizeContextMode(String mode)
    {
        if (CONTEXT_MODE_FULL_STORY.equals(mode))
        {
            return CONTEXT_MODE_FULL_STORY;
        }
        return CONTEXT_MODE_WINDOWED_EXCERPT;
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
        Map<String, String> orderedTitles = new LinkedHashMap<>();
        for (String extractedTitle : extractProperNounTitles(excerpt))
        {
            String title = normalizeHeuristicCandidateTitle(extractedTitle);
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

    public static GeneratedCard parseGeneratedCardFromModelResponse(String response)
    {
        if (response == null || response.isBlank())
        {
            return null;
        }

        LinkedHashSet<String> candidateObjects = new LinkedHashSet<>();
        String fencedObject = extractJsonObjectFromFencedBlock(response);
        if (fencedObject != null)
        {
            candidateObjects.add(fencedObject);
        }
        String json = extractJsonObject(response);
        if (json != null)
        {
            candidateObjects.add(json);
        }

        for (String objectText : candidateObjects)
        {
            GeneratedCard strict = parseGeneratedCardFromJsonObject(objectText);
            if (strict != null)
            {
                return strict;
            }
            GeneratedCard loose = parseGeneratedCardLoosely(objectText);
            if (loose != null)
            {
                return loose;
            }
        }

        String jsonArray = extractJsonArray(response);
        if (jsonArray == null)
        {
            return null;
        }

        try
        {
            JSONArray arr = new JSONArray(jsonArray);
            if (arr.length() == 0 || !(arr.get(0) instanceof JSONObject obj))
            {
                return null;
            }
            return toGeneratedCard(obj);
        }
        catch (Exception e)
        {
            GeneratedCard loose = parseGeneratedCardLoosely(jsonArray);
            if (loose != null)
            {
                return loose;
            }
        }
        return parseGeneratedCardLoosely(response);
    }

    private static GeneratedCard parseGeneratedCardFromJsonObject(String json)
    {
        try
        {
            JSONObject obj = new JSONObject(json);
            return toGeneratedCard(obj);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private static GeneratedCard toGeneratedCard(JSONObject obj)
    {
        if (obj == null)
        {
            return null;
        }
        String title = obj.optString("title", "").trim();
        String triggers = obj.optString("triggers", "").trim();
        String content = obj.optString("content", "").trim();
        if (content.isBlank())
        {
            content = obj.optString("value", "").trim();
        }
        if (title.isBlank() || content.isBlank())
        {
            return null;
        }
        return new GeneratedCard(title, triggers, content);
    }

    private static GeneratedCard parseGeneratedCardLoosely(String text)
    {
        String title = extractLooseJsonStringField(text, "title");
        String content = extractLooseJsonStringField(text, "content");
        if (content.isBlank())
        {
            content = extractLooseJsonStringField(text, "value");
        }
        String triggers = extractLooseJsonStringField(text, "triggers");
        if (title.isBlank() || content.isBlank())
        {
            return null;
        }
        return new GeneratedCard(title.trim(), triggers.trim(), content.trim());
    }

    private static String extractLooseJsonStringField(String text, String field)
    {
        if (text == null || text.isBlank() || field == null || field.isBlank())
        {
            return "";
        }
        String key = "\"" + field + "\"";
        int keyIndex = text.toLowerCase(Locale.ROOT).indexOf(key.toLowerCase(Locale.ROOT));
        if (keyIndex < 0)
        {
            return "";
        }

        int colon = text.indexOf(':', keyIndex + key.length());
        if (colon < 0)
        {
            return "";
        }
        int valueStart = skipWhitespace(text, colon + 1);
        if (valueStart >= text.length())
        {
            return "";
        }
        if (text.charAt(valueStart) != '"')
        {
            return "";
        }
        int endQuote = findLooseJsonStringEnd(text, valueStart + 1);
        if (endQuote <= valueStart)
        {
            return "";
        }
        String raw = text.substring(valueStart + 1, endQuote);
        return unescapeLooseJsonString(raw).trim();
    }

    private static int findLooseJsonStringEnd(String text, int start)
    {
        for (int i = start; i < text.length(); i++)
        {
            if (text.charAt(i) != '"' || isEscaped(text, i))
            {
                continue;
            }
            int tail = skipWhitespace(text, i + 1);
            if (tail >= text.length())
            {
                return i;
            }
            char next = text.charAt(tail);
            if (next == '}')
            {
                return i;
            }
            if (next == ',')
            {
                int afterComma = skipWhitespace(text, tail + 1);
                if (afterComma < text.length() && text.charAt(afterComma) == '"')
                {
                    return i;
                }
                continue;
            }
            if (next == '\n' || next == '\r')
            {
                int afterLine = skipWhitespace(text, tail + 1);
                if (afterLine < text.length() && (text.charAt(afterLine) == '"' || text.charAt(afterLine) == '}'))
                {
                    return i;
                }
                continue;
            }
        }
        return -1;
    }

    private static boolean isEscaped(String text, int index)
    {
        int slashCount = 0;
        for (int i = index - 1; i >= 0 && text.charAt(i) == '\\'; i--)
        {
            slashCount++;
        }
        return (slashCount % 2) == 1;
    }

    private static int skipWhitespace(String text, int index)
    {
        int i = Math.max(0, index);
        while (i < text.length() && Character.isWhitespace(text.charAt(i)))
        {
            i++;
        }
        return i;
    }

    private static String unescapeLooseJsonString(String raw)
    {
        StringBuilder sb = new StringBuilder(raw.length());
        boolean escaping = false;
        for (int i = 0; i < raw.length(); i++)
        {
            char c = raw.charAt(i);
            if (escaping)
            {
                switch (c)
                {
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                default -> sb.append(c);
                }
                escaping = false;
                continue;
            }
            if (c == '\\')
            {
                escaping = true;
                continue;
            }
            sb.append(c);
        }
        if (escaping)
        {
            sb.append('\\');
        }
        return sb.toString();
    }

    private static String extractJsonObjectFromFencedBlock(String text)
    {
        if (text == null || text.isBlank())
        {
            return null;
        }
        Pattern fencePattern = Pattern.compile("(?is)```(?:json)?\\s*(.*?)```");
        Matcher matcher = fencePattern.matcher(text);
        while (matcher.find())
        {
            String body = matcher.group(1);
            String object = extractJsonObject(body);
            if (object != null)
            {
                return object;
            }
        }
        return null;
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

    public static List<ChatMessage> buildChatMessages(String system, String user)
    {
        return buildChatMessages(new PromptParts(system, user, ""));
    }

    public static List<ChatMessage> buildChatMessages(PromptParts parts)
    {
        if (parts == null)
        {
            return List.of();
        }
        List<ChatMessage> messages = new ArrayList<>(3);
        appendChatMessage(messages, "system", parts.system());
        appendChatMessage(messages, "user", parts.user());
        appendChatMessage(messages, "assistant", parts.assistant());
        return List.copyOf(messages);
    }

    private static void appendChatMessage(List<ChatMessage> messages, String role, String content)
    {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isBlank())
        {
            return;
        }
        messages.add(new ChatMessage(role, normalized));
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
        String contentValue = content == null ? "" : content;
        String excerptValue = excerpt == null ? "" : excerpt;
        return template.replace("%{title}", title == null ? "" : title)
                .replace("%{triggers}", triggers == null ? "" : triggers)
                .replace("%{content}", contentValue)
                .replace("%{memory}", contentValue)
                .replace("%{entry}", contentValue)
                .replace("%{excerpt}", excerptValue)
                .replace("%{verbosity}", "");
    }

    private static PromptParts splitPrompt(String prompt)
    {
        if (prompt == null)
        {
            return new PromptParts("", "", "");
        }
        String raw = prompt.trim();
        if (raw.isBlank())
        {
            return new PromptParts("", "", "");
        }
        Matcher matcher = AUTO_CARD_PROMPT_TAG_PATTERN.matcher(raw);
        List<String> tags = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        List<Integer> tagStarts = new ArrayList<>();
        while (matcher.find())
        {
            tags.add(matcher.group(1).toLowerCase(Locale.ROOT));
            starts.add(matcher.end());
            tagStarts.add(matcher.start());
        }
        if (tags.isEmpty())
        {
            return new PromptParts("", raw, "");
        }

        String system = "";
        String user = "";
        String assistant = "";
        for (int i = 0; i < tags.size(); i++)
        {
            int contentStart = starts.get(i);
            int contentEnd = i + 1 < tags.size() ? tagStarts.get(i + 1) : raw.length();
            String section = raw.substring(contentStart, contentEnd).trim();
            if (section.isBlank())
            {
                continue;
            }
            String tag = tags.get(i);
            if ("system".equals(tag))
            {
                system = appendSection(system, section);
            }
            else if ("user".equals(tag))
            {
                user = appendSection(user, section);
            }
            else if ("assistant".equals(tag))
            {
                assistant = appendSection(assistant, section);
            }
        }
        return new PromptParts(system, user, assistant);
    }

    private static String appendSection(String current, String section)
    {
        if (current == null || current.isBlank())
        {
            return section;
        }
        return current + "\n\n" + section;
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
        if (text == null || text.isBlank())
        {
            return null;
        }
        int start = -1;
        int depth = 0;
        boolean inString = false;
        boolean escaping = false;
        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);
            if (start < 0)
            {
                if (c == '{')
                {
                    start = i;
                    depth = 1;
                }
                continue;
            }

            if (inString)
            {
                if (escaping)
                {
                    escaping = false;
                    continue;
                }
                if (c == '\\')
                {
                    escaping = true;
                    continue;
                }
                if (c == '"')
                {
                    inString = false;
                }
                continue;
            }

            if (c == '"')
            {
                inString = true;
                continue;
            }
            if (c == '{')
            {
                depth++;
                continue;
            }
            if (c == '}')
            {
                depth--;
                if (depth == 0)
                {
                    return text.substring(start, i + 1);
                }
            }
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

    private static List<String> extractProperNounTitles(String text)
    {
        if (text == null || text.isBlank())
        {
            return List.of();
        }
        String normalized = normalizeForTitleDetection(text);
        if (normalized.isBlank())
        {
            return List.of();
        }

        List<String> titles = new ArrayList<>();
        List<String> incompleteTitle = new ArrayList<>();
        boolean previousWordTerminates = true;
        String[] words = normalized.split("\\s+");
        for (String rawWord : words)
        {
            String word = rawWord;
            while (!word.isEmpty() && startsWithTerminator(word))
            {
                pushIncompleteTitle(titles, incompleteTitle);
                previousWordTerminates = true;
                word = word.substring(1);
            }
            if (word.isEmpty())
            {
                continue;
            }

            if (previousWordTerminates)
            {
                if (endsWithTerminator(word))
                {
                    continue;
                }
                if (startsWithUppercase(word))
                {
                    if (isMinorWord(word))
                    {
                        previousWordTerminates = false;
                    }
                    continue;
                }
                if (!isMinorWord(word) && !isAndLikeWord(word))
                {
                    previousWordTerminates = false;
                }
                continue;
            }

            boolean terminated = false;
            while (!word.isEmpty() && endsWithTerminator(word))
            {
                previousWordTerminates = true;
                terminated = true;
                word = word.substring(0, word.length() - 1);
            }
            if (word.isEmpty())
            {
                pushIncompleteTitle(titles, incompleteTitle);
                continue;
            }

            if (isMinorWord(word))
            {
                if (!incompleteTitle.isEmpty())
                {
                    if (2 < incompleteTitle.size()
                            && !(isMinorWord(incompleteTitle.getLast())
                                    && isMinorWord(incompleteTitle.get(incompleteTitle.size() - 2))))
                    {
                        pushIncompleteTitle(titles, incompleteTitle);
                        continue;
                    }
                    incompleteTitle.add(word.toLowerCase(Locale.ROOT));
                }
            }
            else if (startsWithUppercase(word))
            {
                incompleteTitle.add(word);
            }
            else
            {
                pushIncompleteTitle(titles, incompleteTitle);
                continue;
            }

            if (terminated)
            {
                pushIncompleteTitle(titles, incompleteTitle);
            }
        }
        pushIncompleteTitle(titles, incompleteTitle);
        return titles;
    }

    private static String normalizeForTitleDetection(String text)
    {
        String normalized = text.replace('\u2014', ' ')
                .replace('\u2013', ' ')
                .replace('\u201c', '"')
                .replace('\u201d', '"')
                .replace('\u2018', '\'')
                .replace('\u2019', '\'')
                .replace('\u00b4', '`')
                .replace('\u3002', '.')
                .replace('\uff1f', '?')
                .replace('\uff01', '!');
        normalized = normalized.replaceAll("(^|\\s+)[\"'`]\\s*", ": ");
        normalized = normalized.replaceAll("\\s*[\\(\\[{]\\s*", ": ");
        normalized = normalized.replaceAll("\\s*,?\\s*[\"'`](?:\\s+|$)", ": ");
        normalized = normalized.replaceAll("[؟،«»¿¡„“…§，、*_~><\\)\\]}#\"`\\s]+", " ");
        normalized = normalized.replaceAll("\\s*[—;,/\\\\]\\s*", " %@% ");
        normalized = normalized.replaceAll("(?:^|\\s+|-)I(?:'(?:m|d|ll|ve))?(?:\\s+|-|$)", " %@% ");
        normalized = normalized.replaceAll("'s(?![a-zA-Z])", "");
        normalized = normalized.replaceAll("(?<=[a-zA-Z])s'(?![a-zA-Z])", "s");
        normalized = normalized.replaceAll("(?<![a-zA-Z])'(?![a-zA-Z])", "");
        normalized = normalized.replaceAll("^\\s*-+\\s*", "");
        normalized = replaceWordsWithPlaceholder(normalized, AUTO_CARD_HONORIFICS);
        normalized = removeWords(normalized, AUTO_CARD_ABBREVIATIONS);
        normalized = normalized.replaceAll("\\s+\\.(?![a-zA-Z])", ".").replaceAll("\\.{2,}", ".");
        normalized = normalized.replaceAll("\\s+\\?(?![a-zA-Z])", "?").replaceAll("\\?{2,}", "?");
        normalized = normalized.replaceAll("\\s+!(?![a-zA-Z])", "!").replaceAll("!{2,}", "!");
        normalized = normalized.replaceAll("\\s+:(?![a-zA-Z])", ":").replaceAll(":{2,}", ":");
        normalized = capitalizeFirstAfterColon(normalized);
        return normalized.trim().replaceAll("\\s+", " ");
    }

    private static String replaceWordsWithPlaceholder(String text, Set<String> words)
    {
        if (text.isBlank() || words.isEmpty())
        {
            return text;
        }
        String regex = buildWordSetPattern(words);
        return text.replaceAll(regex, " %@% ");
    }

    private static String removeWords(String text, Set<String> words)
    {
        if (text.isBlank() || words.isEmpty())
        {
            return text;
        }
        String regex = buildWordSetPattern(words);
        return text.replaceAll(regex, " ");
    }

    private static String buildWordSetPattern(Set<String> words)
    {
        String joined = words.stream()
                .map(word -> Pattern.quote(word.replace(".", "")) + "\\.?")
                .reduce((left, right) -> left + "|" + right)
                .orElse("");
        return "(?i)(?:^|\\s+|-)(?:" + joined + ")(?:\\s+|-|$)";
    }

    private static String capitalizeFirstAfterColon(String text)
    {
        Matcher matcher = Pattern.compile(":\\s+(\\S)").matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find())
        {
            matcher.appendReplacement(sb, ": " + Matcher.quoteReplacement(
                    matcher.group(1).toUpperCase(Locale.ROOT)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static void pushIncompleteTitle(List<String> titles, List<String> incompleteTitle)
    {
        while (1 < incompleteTitle.size() && isMinorWord(incompleteTitle.getLast()))
        {
            incompleteTitle.removeLast();
        }
        if (!incompleteTitle.isEmpty())
        {
            titles.add(String.join(" ", incompleteTitle));
            incompleteTitle.clear();
        }
    }

    private static boolean isMinorWord(String word)
    {
        return AUTO_CARD_MINOR_WORDS.contains(word.toLowerCase(Locale.ROOT));
    }

    private static boolean startsWithUppercase(String word)
    {
        return !word.isEmpty() && Character.isUpperCase(word.codePointAt(0));
    }

    private static boolean startsWithTerminator(String word)
    {
        return !word.isEmpty() && ".?!:".indexOf(word.charAt(0)) >= 0;
    }

    private static boolean endsWithTerminator(String word)
    {
        return !word.isEmpty() && ".?!:".indexOf(word.charAt(word.length() - 1)) >= 0;
    }

    private static boolean isAndLikeWord(String word)
    {
        return word.matches("(?i)^(?:and|&)(?:$|[.?!:]$)");
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
