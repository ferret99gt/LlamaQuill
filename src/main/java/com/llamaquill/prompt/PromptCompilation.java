package com.llamaquill.prompt;

import com.llamaquill.model.ChatMessage;

import java.util.List;

public record PromptCompilation(String prompt, List<ChatMessage> messages, int estimatedTokens)
{
    public PromptCompilation
    {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
