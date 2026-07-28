package com.llamaquill.prompt;

import com.llamaquill.model.ChatMessage;
import com.llamaquill.model.StoryCard;

import java.util.List;

public record PromptAuxiliaryInput(List<ChatMessage> trailingMessages, String activationText,
        StoryCard forcedStoryCard)
{
    public PromptAuxiliaryInput
    {
        trailingMessages = trailingMessages == null ? List.of() : List.copyOf(trailingMessages);
        activationText = activationText == null ? "" : activationText;
    }

    public static PromptAuxiliaryInput none()
    {
        return new PromptAuxiliaryInput(List.of(), "", null);
    }
}
