package com.llamaquill.serviceClients;

import com.llamaquill.model.ChatMessage;

import java.util.List;

public record OllamaChatRequestSnapshot(String endpoint, String model,
        boolean streaming, List<ChatMessage> messages)
{
    public OllamaChatRequestSnapshot
    {
        endpoint = endpoint == null ? "" : endpoint;
        model = model == null ? "" : model;
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
