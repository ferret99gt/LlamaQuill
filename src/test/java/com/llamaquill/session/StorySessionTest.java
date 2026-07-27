package com.llamaquill.session;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.llamaquill.model.Block;
import com.llamaquill.model.Role;
import org.junit.jupiter.api.Test;

import java.util.List;

class StorySessionTest
{
    @Test
    void onlyAppliesResultsToTheSameStoryWithACompatibleHead()
    {
        StorySession source = StorySession.open("story-a", 1, List.of(block("head-a", "story-a")));
        StorySession switchedStory = StorySession.open("story-b", 2, List.of(block("head-b", "story-b")));
        StorySession returnedToSameHead = StorySession.open("story-a", 3, List.of(block("head-a", "story-a")));
        StorySession sameStoryChangedHead = StorySession.open("story-a", 4, List.of(block("new-head", "story-a")));

        assertTrue(source.canApplyResultFrom(source));
        assertTrue(returnedToSameHead.canApplyResultFrom(source));
        assertFalse(switchedStory.canApplyResultFrom(source));
        assertFalse(sameStoryChangedHead.canApplyResultFrom(source));
    }

    private static Block block(String id, String storyId)
    {
        return new Block(id, storyId, Role.ASSISTANT, "text", "2026-01-01T00:00:00Z", 1);
    }
}
