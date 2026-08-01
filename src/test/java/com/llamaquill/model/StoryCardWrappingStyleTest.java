package com.llamaquill.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StoryCardWrappingStyleTest
{
    @Test
    void appliesSelectedWrappersWithoutDoubleWrappingLegacyEntries()
    {
        assertEquals("entry", StoryCardWrappingStyle.NONE.applyTo("entry"));
        assertEquals("{entry}", StoryCardWrappingStyle.BRACES.applyTo("entry"));
        assertEquals("[entry]", StoryCardWrappingStyle.BRACKETS.applyTo("entry"));
        assertEquals("{entry}", StoryCardWrappingStyle.BRACES.applyTo("[entry]"));
        assertEquals("[entry]", StoryCardWrappingStyle.BRACKETS.applyTo("{entry}"));
    }
}
