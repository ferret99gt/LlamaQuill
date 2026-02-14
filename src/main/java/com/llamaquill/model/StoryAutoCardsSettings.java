package com.llamaquill.model;

public record StoryAutoCardsSettings(String storyId, boolean enabled, boolean updateExisting, boolean createNew,
        boolean pinNew, boolean previewFirst)
{
    public static StoryAutoCardsSettings defaults(String storyId)
    {
        return new StoryAutoCardsSettings(storyId, false, true, true, false, false);
    }
}
