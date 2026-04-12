package com.llamaquill.model;

public record ChatMessage(String role, String content)
{
    public ChatMessage
    {
        role = role == null ? "" : role.trim();
        content = content == null ? "" : content;
    }
}
