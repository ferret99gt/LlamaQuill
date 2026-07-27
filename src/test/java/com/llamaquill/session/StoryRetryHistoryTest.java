package com.llamaquill.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StoryRetryHistoryTest
{
    @Test
    void retryEntriesCannotLeakAcrossStoriesOrHeads()
    {
        StoryRetryHistory<String> history = new StoryRetryHistory<>();
        StorySession firstHead = new StorySession("story-a", 1, "head-a");
        StorySession sameHeadNewRevision = new StorySession("story-a", 2, "head-a");
        StorySession differentHead = new StorySession("story-a", 3, "head-b");
        StorySession differentStory = new StorySession("story-b", 4, "head-a");

        history.add(firstHead, "original");
        history.add(firstHead, "retry");
        assertEquals(2, history.size(sameHeadNewRevision));

        history.activate(differentHead);
        assertTrue(history.isEmpty(differentHead));
        history.add(differentHead, "new head");

        history.activate(differentStory);
        assertTrue(history.isEmpty(differentStory));
    }
}
