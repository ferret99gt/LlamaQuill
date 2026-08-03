package com.llamaquill.storycards;

public record StoryCardGenerationRequest(String existingCardId, String title, String triggers,
        String command, String additionalContext, boolean ignoreResponseLength)
{
    public StoryCardGenerationRequest
    {
        existingCardId = existingCardId == null ? "" : existingCardId.trim();
        title = title == null ? "" : title.trim();
        triggers = triggers == null ? "" : triggers.trim();
        command = command == null ? "" : command;
        additionalContext = additionalContext == null ? "" : additionalContext.trim();
    }

    public StoryCardGenerationRequest(String existingCardId, String title, String triggers,
            String command, String additionalContext)
    {
        this(existingCardId, title, triggers, command, additionalContext, true);
    }
}
