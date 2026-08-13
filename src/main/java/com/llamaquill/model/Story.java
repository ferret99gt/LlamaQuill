package com.llamaquill.model;

public record Story(String id, String title, String systemPrompt, String plotEssentials, String authorNote,
        String storyCardGenerationContext, boolean forcePinAllStoryCards, String selectedSeePromptPresetId,
        String createdAt, String updatedAt)
{
    public Story
    {
        storyCardGenerationContext = storyCardGenerationContext == null ? "" : storyCardGenerationContext;
        selectedSeePromptPresetId = selectedSeePromptPresetId == null || selectedSeePromptPresetId.isBlank()
                ? "builtin:none"
                : selectedSeePromptPresetId;
    }

    public Story(String id, String title, String systemPrompt, String plotEssentials, String authorNote,
            String storyCardGenerationContext, boolean forcePinAllStoryCards, String createdAt, String updatedAt)
    {
        this(id, title, systemPrompt, plotEssentials, authorNote,
                storyCardGenerationContext, forcePinAllStoryCards, "builtin:none", createdAt, updatedAt);
    }

    public Story(String id, String title, String systemPrompt, String plotEssentials, String authorNote,
            String storyCardGenerationContext, String createdAt, String updatedAt)
    {
        this(id, title, systemPrompt, plotEssentials, authorNote,
                storyCardGenerationContext, false, "builtin:none", createdAt, updatedAt);
    }

    public Story(String id, String title, String systemPrompt, String plotEssentials, String authorNote,
            String createdAt, String updatedAt)
    {
        this(id, title, systemPrompt, plotEssentials, authorNote, "", false, "builtin:none", createdAt, updatedAt);
    }
}
