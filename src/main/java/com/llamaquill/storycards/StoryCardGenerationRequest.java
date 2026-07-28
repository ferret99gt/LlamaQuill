package com.llamaquill.storycards;

public record StoryCardGenerationRequest(String existingCardId, String title, String triggers,
        String command, StoryCardCommands.EntryFormatting formatting, String additionalContext)
{
    public StoryCardGenerationRequest
    {
        existingCardId = existingCardId == null ? "" : existingCardId.trim();
        title = title == null ? "" : title.trim();
        triggers = triggers == null ? "" : triggers.trim();
        command = command == null ? "" : command;
        formatting = formatting == null ? StoryCardCommands.EntryFormatting.NONE : formatting;
        additionalContext = additionalContext == null ? "" : additionalContext.trim();
    }
}
