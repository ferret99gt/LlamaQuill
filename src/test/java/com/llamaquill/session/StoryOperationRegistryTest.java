package com.llamaquill.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StoryOperationRegistryTest
{
    @Test
    void tracksOperationsPerStoryAndCancelsOutstandingWork()
    {
        StoryOperationRegistry registry = new StoryOperationRegistry();
        StoryOperationRegistry.Operation first = registry.begin(new StorySession("story-a", 1, null), "Continue");
        StoryOperationRegistry.Operation second = registry.begin(new StorySession("story-b", 2, null), "Retry");

        assertEquals(1, registry.activeCount("story-a"));
        assertEquals(1, registry.activeCount("story-b"));
        assertTrue(registry.complete(first));
        assertFalse(registry.hasActive("story-a"));

        registry.cancelAll();
        assertTrue(second.cancelled());
        assertFalse(registry.hasActive("story-b"));
    }
}
