package com.llamaquill.model;

import com.llamaquill.serviceClients.ComfyUiClient;
import com.llamaquill.serviceClients.OllamaClient;
import com.llamaquill.serviceClients.OllamaEndpoint;

public record AppSettings(String ollamaUrl, String comfyUiUrl, String selectedModel,
        boolean responseLengthEnabled, int responseLength, int minStoryPercent, int storyCardLookback, int anPlacement,
        String comfyWorkflow, int comfyWidth, int comfyHeight, int comfyBatchSize)
{
    public static final String DEFAULT_COMFY_WORKFLOW = "ChromaHD";
    public static final int DEFAULT_COMFY_WIDTH = 720;
    public static final int DEFAULT_COMFY_HEIGHT = 720;
    public static final int DEFAULT_COMFY_BATCH_SIZE = 4;
    public static final int MIN_STORY_PERCENT = 10;
    public static final int MAX_STORY_PERCENT = 100;

    public AppSettings
    {
        ollamaUrl = OllamaEndpoint.normalizeOrDefault(ollamaUrl);
        comfyUiUrl = comfyUiUrl == null || comfyUiUrl.isBlank() ? ComfyUiClient.DEFAULT_HOST : comfyUiUrl.trim();
        selectedModel = selectedModel == null || selectedModel.isBlank()
                ? OllamaClient.DEFAULT_MODEL
                : selectedModel.trim();
        responseLength = Math.max(1, Math.min(32768, responseLength));
        minStoryPercent = Math.max(MIN_STORY_PERCENT, Math.min(MAX_STORY_PERCENT, minStoryPercent));
        storyCardLookback = Math.max(0, Math.min(100, storyCardLookback));
        anPlacement = Math.max(1, Math.min(100, anPlacement));
        comfyWorkflow = comfyWorkflow == null || comfyWorkflow.isBlank()
                ? DEFAULT_COMFY_WORKFLOW
                : comfyWorkflow.trim();
        comfyWidth = Math.max(64, Math.min(4096, comfyWidth));
        comfyHeight = Math.max(64, Math.min(4096, comfyHeight));
        comfyBatchSize = Math.max(1, Math.min(32, comfyBatchSize));
    }

    public static AppSettings defaults()
    {
        GenerationSettings defaults = GenerationSettings.defaults();
        return new AppSettings(OllamaClient.DEFAULT_HOST, ComfyUiClient.DEFAULT_HOST, OllamaClient.DEFAULT_MODEL,
                false, defaults.responseLength(), 60,
                defaults.storyCardLookback(), defaults.anPlacement(), DEFAULT_COMFY_WORKFLOW,
                DEFAULT_COMFY_WIDTH, DEFAULT_COMFY_HEIGHT, DEFAULT_COMFY_BATCH_SIZE);
    }
}
