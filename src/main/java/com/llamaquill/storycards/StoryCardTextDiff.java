package com.llamaquill.storycards;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class StoryCardTextDiff
{
    private static final Pattern TOKEN = Pattern.compile("\\s+|\\S+\\s*");
    private static final long MAX_LCS_CELLS = 2_000_000;

    private StoryCardTextDiff()
    {
    }

    static Comparison compare(String previous, String current)
    {
        String before = previous == null ? "" : previous;
        String after = current == null ? "" : current;
        List<String> beforeTokens = tokens(before);
        List<String> afterTokens = tokens(after);
        if ((long) (beforeTokens.size() + 1) * (afterTokens.size() + 1) > MAX_LCS_CELLS)
        {
            return new Comparison(
                    changedText(before, Kind.REMOVED),
                    changedText(after, Kind.ADDED),
                    countWords(beforeTokens), countWords(afterTokens));
        }

        int[][] lengths = new int[beforeTokens.size() + 1][afterTokens.size() + 1];
        for (int beforeIndex = beforeTokens.size() - 1; beforeIndex >= 0; beforeIndex--)
        {
            for (int afterIndex = afterTokens.size() - 1; afterIndex >= 0; afterIndex--)
            {
                lengths[beforeIndex][afterIndex] = beforeTokens.get(beforeIndex).equals(afterTokens.get(afterIndex))
                        ? lengths[beforeIndex + 1][afterIndex + 1] + 1
                        : Math.max(lengths[beforeIndex + 1][afterIndex], lengths[beforeIndex][afterIndex + 1]);
            }
        }

        List<Span> beforeSpans = new ArrayList<>();
        List<Span> afterSpans = new ArrayList<>();
        int removedWords = 0;
        int addedWords = 0;
        int beforeIndex = 0;
        int afterIndex = 0;
        while (beforeIndex < beforeTokens.size() || afterIndex < afterTokens.size())
        {
            if (beforeIndex < beforeTokens.size() && afterIndex < afterTokens.size()
                    && beforeTokens.get(beforeIndex).equals(afterTokens.get(afterIndex)))
            {
                append(beforeSpans, beforeTokens.get(beforeIndex), Kind.UNCHANGED);
                append(afterSpans, afterTokens.get(afterIndex), Kind.UNCHANGED);
                beforeIndex++;
                afterIndex++;
            }
            else if (afterIndex >= afterTokens.size()
                    || (beforeIndex < beforeTokens.size()
                    && lengths[beforeIndex + 1][afterIndex] >= lengths[beforeIndex][afterIndex + 1]))
            {
                String removed = beforeTokens.get(beforeIndex++);
                append(beforeSpans, removed, Kind.REMOVED);
                if (!removed.isBlank())
                {
                    removedWords++;
                }
            }
            else
            {
                String added = afterTokens.get(afterIndex++);
                append(afterSpans, added, Kind.ADDED);
                if (!added.isBlank())
                {
                    addedWords++;
                }
            }
        }
        return new Comparison(beforeSpans, afterSpans, removedWords, addedWords);
    }

    private static List<String> tokens(String text)
    {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(text);
        while (matcher.find())
        {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private static List<Span> changedText(String text, Kind kind)
    {
        return text.isEmpty() ? List.of() : List.of(new Span(text, kind));
    }

    private static int countWords(List<String> tokens)
    {
        return (int) tokens.stream().filter(token -> !token.isBlank()).count();
    }

    private static void append(List<Span> spans, String text, Kind kind)
    {
        if (text.isEmpty())
        {
            return;
        }
        if (!spans.isEmpty() && spans.getLast().kind() == kind)
        {
            Span previous = spans.removeLast();
            spans.add(new Span(previous.text() + text, kind));
            return;
        }
        spans.add(new Span(text, kind));
    }

    enum Kind
    {
        UNCHANGED,
        REMOVED,
        ADDED
    }

    record Span(String text, Kind kind)
    {
    }

    record Comparison(List<Span> previous, List<Span> current, int removedWords, int addedWords)
    {
        Comparison
        {
            previous = List.copyOf(previous);
            current = List.copyOf(current);
        }
    }
}
