package com.llamaquill.model;

public record StoryImage(String id, String storyId, String prompt, String mimeType, int width, int height,
        String workflowJson, byte[] imageBytes, String createdAt)
{
}
