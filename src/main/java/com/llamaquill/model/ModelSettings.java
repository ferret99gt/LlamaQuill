package com.llamaquill.model;

public record ModelSettings(String modelName, boolean active, int contextLimit, double promptTokenScale,
        boolean temperatureEnabled, double temperature,
        boolean topKEnabled, int topK,
        boolean topPEnabled, double topP,
        boolean minPEnabled, double minP,
        boolean presencePenaltyEnabled, double presencePenalty,
        boolean frequencyPenaltyEnabled, double frequencyPenalty,
        boolean repetitionPenaltyEnabled, double repetitionPenalty)
{
    public static final int MIN_CONTEXT_LIMIT = 1024;
    public static final int DEFAULT_CONTEXT_LIMIT = 8192;
    public static final int MAX_CONTEXT_LIMIT = 1_048_576;
    public static final double MIN_PROMPT_TOKEN_SCALE = 0.5;
    public static final double MAX_PROMPT_TOKEN_SCALE = 4.0;

    public ModelSettings
    {
        if (modelName == null || modelName.isBlank())
        {
            throw new IllegalArgumentException("Model name is required.");
        }
        modelName = modelName.trim();
        contextLimit = clamp(contextLimit, MIN_CONTEXT_LIMIT, MAX_CONTEXT_LIMIT);
        promptTokenScale = clampFinite(promptTokenScale, 1.0, MIN_PROMPT_TOKEN_SCALE, MAX_PROMPT_TOKEN_SCALE);
        temperature = clampFinite(temperature, 0.8, 0.0, 5.0);
        topK = clamp(topK, 0, 10000);
        topP = clampFinite(topP, 0.95, 0.0, 1.0);
        minP = clampFinite(minP, 0.025, 0.0, 1.0);
        presencePenalty = clampFinite(presencePenalty, 0.25, -2.0, 2.0);
        frequencyPenalty = clampFinite(frequencyPenalty, 0.0, -2.0, 2.0);
        repetitionPenalty = clampFinite(repetitionPenalty, 1.05, 0.0, 5.0);
    }

    public static ModelSettings defaults(String modelName)
    {
        GenerationSettings defaults = GenerationSettings.defaults();
        return new ModelSettings(modelName, true, DEFAULT_CONTEXT_LIMIT, 1.0,
                false, defaults.temperature(),
                false, defaults.topK(),
                false, defaults.topP(),
                false, defaults.minP(),
                false, defaults.presencePenalty(),
                false, defaults.frequencyPenalty(),
                false, defaults.repetitionPenalty());
    }

    private static int clamp(int value, int minimum, int maximum)
    {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clampFinite(double value, double fallback, double minimum, double maximum)
    {
        double finite = Double.isFinite(value) ? value : fallback;
        return Math.max(minimum, Math.min(maximum, finite));
    }
}
