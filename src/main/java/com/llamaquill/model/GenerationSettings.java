package com.llamaquill.model;

public record GenerationSettings(int contextLimit,
        boolean responseLengthEnabled, int responseLength,
        boolean temperatureEnabled, double temperature,
        boolean topKEnabled, int topK,
        boolean topPEnabled, double topP,
        boolean minPEnabled, double minP,
        boolean presencePenaltyEnabled, double presencePenalty,
        boolean frequencyPenaltyEnabled, double frequencyPenalty,
        boolean repetitionPenaltyEnabled, double repetitionPenalty,
        int minStoryWindow, int storyCardLookback, int anPlacement)
{
    public static GenerationSettings defaults()
    {
        int contextLimit = 8192;
        int minStoryWindow = (int) Math.round(contextLimit * 0.6);
        return new GenerationSettings(contextLimit,
                false, 150,
                false, 0.8,
                false, 200,
                false, 0.95,
                false, 0.025,
                false, 0.25,
                false, 0.0,
                false, 1.05,
                minStoryWindow, 7, 3);
    }
}
