package com.llamaquill.model;

public record GenerationSettings(String modelName, String ollamaHost, int contextLimit, double promptTokenScale,
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
    public static final String DEFAULT_MODEL = "hf.co/LatitudeGames/Muse-12B-GGUF:BF16";
    public static final String DEFAULT_OLLAMA_HOST = "http://localhost:11434";

    public GenerationSettings
    {
        modelName = modelName == null || modelName.isBlank() ? DEFAULT_MODEL : modelName.trim();
        ollamaHost = ollamaHost == null || ollamaHost.isBlank() ? DEFAULT_OLLAMA_HOST : ollamaHost.trim();
        // ModelSettings enforces the user-facing minimum. GenerationSettings also
        // permits tiny synthetic budgets used by prompt-budget tests and diagnostics.
        contextLimit = Math.max(1, Math.min(ModelSettings.MAX_CONTEXT_LIMIT, contextLimit));
        promptTokenScale = Double.isFinite(promptTokenScale)
                ? Math.max(ModelSettings.MIN_PROMPT_TOKEN_SCALE,
                        Math.min(ModelSettings.MAX_PROMPT_TOKEN_SCALE, promptTokenScale))
                : 1.0;
        responseLength = Math.max(1, Math.min(32768, responseLength));
        temperature = Math.max(0.0, Math.min(5.0, temperature));
        topK = Math.max(0, Math.min(10000, topK));
        topP = Math.max(0.0, Math.min(1.0, topP));
        minP = Math.max(0.0, Math.min(1.0, minP));
        presencePenalty = Math.max(-2.0, Math.min(2.0, presencePenalty));
        frequencyPenalty = Math.max(-2.0, Math.min(2.0, frequencyPenalty));
        repetitionPenalty = Math.max(0.0, Math.min(5.0, repetitionPenalty));
        minStoryWindow = Math.max(0, Math.min(contextLimit, minStoryWindow));
        storyCardLookback = Math.max(0, Math.min(100, storyCardLookback));
        anPlacement = Math.max(1, Math.min(100, anPlacement));
    }

    public static GenerationSettings defaults()
    {
        int contextLimit = 8192;
        int minStoryWindow = (int) Math.round(contextLimit * 0.6);
        return new GenerationSettings(DEFAULT_MODEL, DEFAULT_OLLAMA_HOST, contextLimit, 1.0,
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
