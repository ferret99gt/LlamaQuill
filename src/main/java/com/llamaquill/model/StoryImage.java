package com.llamaquill.model;

public record StoryImage(String id, String storyId, String prompt, String mimeType, int width, int height,
        int batchSize, String workflowJson, byte[] imageBytes, String createdAt)
{
    public StoryImage
    {
        batchSize = Math.max(1, batchSize);
    }

    public StoryImage(String id, String storyId, String prompt, String mimeType, int width, int height,
            String workflowJson, byte[] imageBytes, String createdAt)
    {
        this(id, storyId, prompt, mimeType, width, height, 1, workflowJson, imageBytes, createdAt);
    }
}
