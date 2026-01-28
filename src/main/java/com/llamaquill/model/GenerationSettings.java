package com.llamaquill.model;

public record GenerationSettings(int contextLimit, int responseLength, double temperature, int topK,
        double topP, double presencePenalty, double frequencyPenalty, int minStoryWindow,
        int storyCardLookback, int anPlacement)
{
    public static GenerationSettings defaults()
    {
        return new GenerationSettings(8192, 256, 0.8, 40, 0.95, 0.0, 0.0, 1024, 8, 2);
    }
}
