package com.llamaquill.model;

import com.llamaquill.serviceClients.ComfyUiClient;
import com.llamaquill.serviceClients.OllamaClient;

public record AppSettings(String ollamaUrl, String comfyUiUrl, String selectedModel, int contextLimit, int responseLength, int minStoryWindow,
        int storyCardLookback, int anPlacement)
{
    public static AppSettings defaults()
    {
        GenerationSettings defaults = GenerationSettings.defaults();
        return new AppSettings(OllamaClient.DEFAULT_HOST, ComfyUiClient.DEFAULT_HOST, OllamaClient.DEFAULT_MODEL, defaults.contextLimit(),
                defaults.responseLength(), defaults.minStoryWindow(), defaults.storyCardLookback(), defaults.anPlacement());
    }
}
