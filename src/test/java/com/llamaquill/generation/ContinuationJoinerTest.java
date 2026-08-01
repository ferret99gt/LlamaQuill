package com.llamaquill.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ContinuationJoinerTest
{
    @Test
    void insertsSpaceAtOrdinaryProseBoundaries()
    {
        assertEquals(" his eyes", ContinuationJoiner.join("The heat of", "his eyes"));
        assertEquals(" The room changed.", ContinuationJoiner.join("It was quiet.", "The room changed."));
    }

    @Test
    void preservesExistingWhitespace()
    {
        assertEquals(" his eyes", ContinuationJoiner.join("The heat of", " his eyes"));
        assertEquals("his eyes", ContinuationJoiner.join("The heat of ", "his eyes"));
        assertEquals("\n\nA new paragraph.", ContinuationJoiner.join(
                "The room fell silent.", "\n\nA new paragraph."));
    }

    @Test
    void doesNotSeparateObviousPunctuationOrJoinedForms()
    {
        assertEquals(", suddenly", ContinuationJoiner.join("The door moved", ", suddenly"));
        assertEquals("known", ContinuationJoiner.join("well-", "known"));
        assertEquals("forgotten", ContinuationJoiner.join("secrets of\u2014", "forgotten"));
        assertEquals("t move", ContinuationJoiner.join("don\u2019", "t move"));
        assertEquals("Run!", ContinuationJoiner.join("He whispered, \u201C", "Run!"));
    }

    @Test
    void deliberatelyPrefersAWordBoundaryOverGuessingCompoundWords()
    {
        assertEquals(" way", ContinuationJoiner.join("door", "way"));
    }
}
