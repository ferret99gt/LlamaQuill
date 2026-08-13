package com.llamaquill.storycards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StoryCardTextDiffTest
{
    @Test
    void highlightsChangedWordsWhilePreservingBothOriginalEntries()
    {
        StoryCardTextDiff.Comparison comparison = StoryCardTextDiff.compare(
                "Mia is a quiet thief.",
                "Mia is a clever thief.");

        assertEquals("Mia is a quiet thief.", join(comparison.previous()));
        assertEquals("Mia is a clever thief.", join(comparison.current()));
        assertEquals(1, comparison.removedWords());
        assertEquals(1, comparison.addedWords());
        assertTrue(comparison.previous().stream().anyMatch(
                span -> span.kind() == StoryCardTextDiff.Kind.REMOVED && span.text().contains("quiet")));
        assertTrue(comparison.current().stream().anyMatch(
                span -> span.kind() == StoryCardTextDiff.Kind.ADDED && span.text().contains("clever")));
    }

    @Test
    void leavesIdenticalEntriesUnchanged()
    {
        StoryCardTextDiff.Comparison comparison = StoryCardTextDiff.compare("Same entry.", "Same entry.");

        assertEquals(0, comparison.removedWords());
        assertEquals(0, comparison.addedWords());
        assertTrue(comparison.previous().stream().allMatch(
                span -> span.kind() == StoryCardTextDiff.Kind.UNCHANGED));
        assertTrue(comparison.current().stream().allMatch(
                span -> span.kind() == StoryCardTextDiff.Kind.UNCHANGED));
    }

    private static String join(java.util.List<StoryCardTextDiff.Span> spans)
    {
        return spans.stream().map(StoryCardTextDiff.Span::text).reduce("", String::concat);
    }
}
