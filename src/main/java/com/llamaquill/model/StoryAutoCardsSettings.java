package com.llamaquill.model;

public record StoryAutoCardsSettings(String storyId, boolean enabled, boolean updateExisting, boolean createNew,
        boolean pinNew, boolean previewFirst)
{
    public static StoryAutoCardsSettings defaults(String storyId, boolean defaultEnabled)
    {
        return new StoryAutoCardsSettings(storyId, defaultEnabled, true, true, false, false);
    }
}
