package com.llamaquill.model;

public record ModelSettings(String modelName, boolean active, double temperature, int topK, double topP,
        double minP, double presencePenalty, double frequencyPenalty, double repetitionPenalty)
{
    public static ModelSettings defaults(String modelName)
    {
        GenerationSettings defaults = GenerationSettings.defaults();
        return new ModelSettings(
                modelName,
                true,
                defaults.temperature(),
                defaults.topK(),
                defaults.topP(),
                defaults.minP(),
                defaults.presencePenalty(),
                defaults.frequencyPenalty(),
                defaults.repetitionPenalty());
    }
}
