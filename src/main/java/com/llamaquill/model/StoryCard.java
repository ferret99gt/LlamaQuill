package com.llamaquill.model;

public record StoryCard(String id, String storyId, String title, String triggers, String content,
        String type, String notes, boolean pinned)
{
    public StoryCard
    {
        id = id == null ? "" : id;
        storyId = storyId == null ? "" : storyId;
        title = title == null ? "" : title;
        triggers = triggers == null ? "" : triggers;
        content = content == null ? "" : content;
        type = type == null ? "" : type;
        notes = notes == null ? "" : notes;
    }

    public StoryCard(String id, String storyId, String title, String triggers, String content, boolean pinned)
    {
        this(id, storyId, title, triggers, content, "", "", pinned);
    }

    public String displayType()
    {
        return type.isBlank() ? "Untyped" : type.trim();
    }
}
