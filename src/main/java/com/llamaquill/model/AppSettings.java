package com.llamaquill.model;

import com.llamaquill.serviceClients.ComfyUiClient;
import com.llamaquill.serviceClients.OllamaClient;

public record AppSettings(String ollamaUrl, String comfyUiUrl, String selectedModel, int contextLimit, int responseLength, int minStoryWindow,
        int storyCardLookback, int anPlacement, String comfyWorkflow, int comfyWidth, int comfyHeight, int comfyBatchSize)
{
    public static final String DEFAULT_COMFY_WORKFLOW = "ChromaHD";
    public static final int DEFAULT_COMFY_WIDTH = 720;
    public static final int DEFAULT_COMFY_HEIGHT = 720;
    public static final int DEFAULT_COMFY_BATCH_SIZE = 4;

    public static AppSettings defaults()
    {
        GenerationSettings defaults = GenerationSettings.defaults();
        return new AppSettings(OllamaClient.DEFAULT_HOST, ComfyUiClient.DEFAULT_HOST, OllamaClient.DEFAULT_MODEL, defaults.contextLimit(),
                defaults.responseLength(), defaults.minStoryWindow(), defaults.storyCardLookback(), defaults.anPlacement(),
                DEFAULT_COMFY_WORKFLOW, DEFAULT_COMFY_WIDTH, DEFAULT_COMFY_HEIGHT, DEFAULT_COMFY_BATCH_SIZE);
    }
}
