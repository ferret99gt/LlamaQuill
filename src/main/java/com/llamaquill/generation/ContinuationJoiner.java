package com.llamaquill.generation;

/**
 * Applies the smallest deterministic separator needed when a newly generated
 * assistant block is displayed directly after existing assistant prose.
 */
final class ContinuationJoiner
{
    private ContinuationJoiner()
    {
    }

    static String join(String previousText, String continuation)
    {
        String safeContinuation = continuation == null ? "" : continuation;
        return separator(previousText, safeContinuation) + safeContinuation;
    }

    static String separator(String previousText, String continuation)
    {
        if (previousText == null || previousText.isEmpty()
                || continuation == null || continuation.isEmpty())
        {
            return "";
        }

        int previousCodePoint = previousText.codePointBefore(previousText.length());
        int continuationCodePoint = continuation.codePointAt(0);
        if (isWhitespace(previousCodePoint) || isWhitespace(continuationCodePoint)
                || isNoSpaceBefore(continuationCodePoint)
                || isNoSpaceAfter(previousText, previousCodePoint))
        {
            return "";
        }
        return " ";
    }

    private static boolean isWhitespace(int codePoint)
    {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    private static boolean isNoSpaceBefore(int codePoint)
    {
        return switch (codePoint)
        {
            case ',', '.', ';', ':', '!', '?', '%',
                    ')', ']', '}', '>',
                    '/', '\\', '-', '\'', '"',
                    '\u2019', '\u201D', '\u2026' -> true;
            default -> isDash(codePoint);
        };
    }

    private static boolean isNoSpaceAfter(String previousText, int codePoint)
    {
        if (isDash(codePoint))
        {
            return true;
        }
        return switch (codePoint)
        {
            case '(', '[', '{', '<',
                    '/', '\\', '-', '$', '#', '@',
                    '\'', '\u2018', '\u2019', '\u201C' -> true;
            case '"', '\u201D' -> isLikelyOpeningQuote(previousText, codePoint);
            default -> false;
        };
    }

    private static boolean isLikelyOpeningQuote(String text, int quoteCodePoint)
    {
        int quoteStart = text.length() - Character.charCount(quoteCodePoint);
        if (quoteStart <= 0)
        {
            return true;
        }
        int preceding = text.codePointBefore(quoteStart);
        return isWhitespace(preceding)
                || preceding == '(' || preceding == '[' || preceding == '{' || preceding == '<';
    }

    private static boolean isDash(int codePoint)
    {
        return codePoint >= 0x2010 && codePoint <= 0x2015;
    }
}
