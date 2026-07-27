package com.llamaquill.model;

public record ModelSettings(String modelName, boolean active,
        boolean temperatureEnabled, double temperature,
        boolean topKEnabled, int topK,
        boolean topPEnabled, double topP,
        boolean minPEnabled, double minP,
        boolean presencePenaltyEnabled, double presencePenalty,
        boolean frequencyPenaltyEnabled, double frequencyPenalty,
        boolean repetitionPenaltyEnabled, double repetitionPenalty)
{
    public static ModelSettings defaults(String modelName)
    {
        GenerationSettings defaults = GenerationSettings.defaults();
        return new ModelSettings(modelName, true,
                false, defaults.temperature(),
                false, defaults.topK(),
                false, defaults.topP(),
                false, defaults.minP(),
                false, defaults.presencePenalty(),
                false, defaults.frequencyPenalty(),
                false, defaults.repetitionPenalty());
    }
}
