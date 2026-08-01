package com.llamaquill.stories;

import com.llamaquill.model.Story;

import java.util.Objects;

public record StoryCloneRequest(String newName, boolean includeStoryDetails, boolean includeStoryCards,
        boolean includeInitialBlock, boolean includeAllBlocks)
{
    public StoryCloneRequest
    {
        newName = newName == null ? "" : newName;
    }

    public static StoryCloneRequest defaultsFor(Story story)
    {
        Objects.requireNonNull(story, "story");
        return new StoryCloneRequest(story.title() + " - Clone", true, true, true, false);
    }
}
