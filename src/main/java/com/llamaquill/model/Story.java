package com.llamaquill.model;

public record Story(String id, String title, String systemPrompt, String plotEssentials,
                String authorNote, String createdAt, String updatedAt)
{
}
