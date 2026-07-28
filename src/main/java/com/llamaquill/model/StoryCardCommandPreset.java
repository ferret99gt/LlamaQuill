package com.llamaquill.model;

public record StoryCardCommandPreset(String id, String name, String command, String createdAt, String updatedAt)
{
    public StoryCardCommandPreset
    {
        id = id == null ? "" : id;
        name = name == null ? "" : name.trim();
        command = command == null ? "" : command;
        createdAt = createdAt == null ? "" : createdAt;
        updatedAt = updatedAt == null ? "" : updatedAt;
    }
}
