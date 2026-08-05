package com.llamaquill.model;

public record Story(String id, String title, String systemPrompt, String plotEssentials, String authorNote,
        String storyCardGenerationContext, boolean forcePinAllStoryCards, String createdAt, String updatedAt)
{
    public Story
    {
        storyCardGenerationContext = storyCardGenerationContext == null ? "" : storyCardGenerationContext;
    }

    public Story(String id, String title, String systemPrompt, String plotEssentials, String authorNote,
            String storyCardGenerationContext, String createdAt, String updatedAt)
    {
        this(id, title, systemPrompt, plotEssentials, authorNote,
                storyCardGenerationContext, false, createdAt, updatedAt);
    }

    public Story(String id, String title, String systemPrompt, String plotEssentials, String authorNote,
            String createdAt, String updatedAt)
    {
        this(id, title, systemPrompt, plotEssentials, authorNote, "", false, createdAt, updatedAt);
    }
}
