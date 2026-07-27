package com.llamaquill.prompt;

import com.llamaquill.model.ChatMessage;

import java.util.List;
import java.util.Objects;

public record PromptCompilation(List<ChatMessage> messages, int estimatedTokens, PromptContextReport contextReport)
{
    public PromptCompilation
    {
        messages = messages == null ? List.of() : List.copyOf(messages);
        estimatedTokens = Math.max(0, estimatedTokens);
        contextReport = Objects.requireNonNull(contextReport, "contextReport");
    }
}
