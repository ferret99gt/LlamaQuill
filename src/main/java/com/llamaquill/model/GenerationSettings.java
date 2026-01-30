package com.llamaquill.model;

public record GenerationSettings(int contextLimit, int responseLength, double temperature, int topK,
        double topP, double minP, double presencePenalty, double frequencyPenalty,
        double repetitionPenalty, int minStoryWindow, int storyCardLookback, int anPlacement)
{
    public static GenerationSettings defaults()
    {
        int contextLimit = 8192;
        int minStoryWindow = (int) Math.round(contextLimit * 0.9);
        return new GenerationSettings(contextLimit, 200, 0.8, 200, 0.95, 0.025, 0.25, 0.0, 1.05,
                minStoryWindow, 5, 2);
    }
}
