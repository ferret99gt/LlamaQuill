package com.llamaquill.prompt;

import com.llamaquill.model.ChatMessage;
import com.llamaquill.model.StoryCard;

import java.util.List;

public record PromptAuxiliaryInput(List<ChatMessage> trailingMessages, String activationText,
        StoryCard forcedStoryCard, String storyTailUserMessage, int storyTailBlockCount,
        boolean continuation)
{
    public PromptAuxiliaryInput
    {
        trailingMessages = trailingMessages == null ? List.of() : List.copyOf(trailingMessages);
        activationText = activationText == null ? "" : activationText;
        storyTailUserMessage = storyTailUserMessage == null ? "" : storyTailUserMessage.trim();
        storyTailBlockCount = storyTailUserMessage.isBlank() ? 0 : Math.max(0, storyTailBlockCount);
    }

    public PromptAuxiliaryInput(List<ChatMessage> trailingMessages, String activationText,
            StoryCard forcedStoryCard, String storyTailUserMessage, int storyTailBlockCount)
    {
        this(trailingMessages, activationText, forcedStoryCard,
                storyTailUserMessage, storyTailBlockCount,
                storyTailUserMessage != null && !storyTailUserMessage.isBlank());
    }

    public PromptAuxiliaryInput(List<ChatMessage> trailingMessages, String activationText,
            StoryCard forcedStoryCard)
    {
        this(trailingMessages, activationText, forcedStoryCard, "", 0, false);
    }

    public static PromptAuxiliaryInput none()
    {
        return new PromptAuxiliaryInput(List.of(), "", null, "", 0, false);
    }
}
