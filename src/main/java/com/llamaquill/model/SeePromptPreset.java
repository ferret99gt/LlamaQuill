package com.llamaquill.model;

public record SeePromptPreset(String id, String name, String prompt, String createdAt, String updatedAt)
{
    public SeePromptPreset
    {
        id = id == null ? "" : id;
        name = name == null ? "" : name.trim();
        prompt = prompt == null ? "" : prompt;
        createdAt = createdAt == null ? "" : createdAt;
        updatedAt = updatedAt == null ? "" : updatedAt;
    }
}
