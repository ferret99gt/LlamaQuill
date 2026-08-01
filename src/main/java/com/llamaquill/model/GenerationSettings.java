package com.llamaquill.model;

public record GenerationSettings(String modelName, String ollamaHost, int contextLimit, double promptTokenScale,
        boolean responseLengthEnabled, int responseLength,
        boolean temperatureEnabled, double temperature,
        boolean topKEnabled, int topK,
        boolean topPEnabled, double topP,
        boolean minPEnabled, double minP,
        boolean typicalPEnabled, double typicalP,
        boolean presencePenaltyEnabled, double presencePenalty,
        boolean frequencyPenaltyEnabled, double frequencyPenalty,
        boolean repeatLastNEnabled, int repeatLastN,
        boolean repetitionPenaltyEnabled, double repetitionPenalty,
        int minStoryWindow, int storyCardLookback,
        int ollamaKeepAliveMinutes,
        StoryCardWrappingStyle storyCardWrappingStyle,
        ConversationLayout conversationLayout)
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
        typicalP = Math.max(0.0, Math.min(1.0, typicalP));
        presencePenalty = Math.max(-2.0, Math.min(2.0, presencePenalty));
        frequencyPenalty = Math.max(-2.0, Math.min(2.0, frequencyPenalty));
        repeatLastN = Math.max(-1, Math.min(contextLimit, repeatLastN));
        repetitionPenalty = Math.max(0.0, Math.min(5.0, repetitionPenalty));
        minStoryWindow = Math.max(0, Math.min(contextLimit, minStoryWindow));
        storyCardLookback = Math.max(0, Math.min(100, storyCardLookback));
        ollamaKeepAliveMinutes = Math.max(AppSettings.MIN_OLLAMA_KEEP_ALIVE_MINUTES,
                Math.min(AppSettings.MAX_OLLAMA_KEEP_ALIVE_MINUTES, ollamaKeepAliveMinutes));
        storyCardWrappingStyle = storyCardWrappingStyle == null
                ? StoryCardWrappingStyle.NONE
                : storyCardWrappingStyle;
        conversationLayout = conversationLayout == null
                ? ConversationLayout.ROLE_AWARE
                : conversationLayout;
    }

    public GenerationSettings(String modelName, String ollamaHost, int contextLimit, double promptTokenScale,
            boolean responseLengthEnabled, int responseLength,
            boolean temperatureEnabled, double temperature,
            boolean topKEnabled, int topK,
            boolean topPEnabled, double topP,
            boolean minPEnabled, double minP,
            boolean typicalPEnabled, double typicalP,
            boolean presencePenaltyEnabled, double presencePenalty,
            boolean frequencyPenaltyEnabled, double frequencyPenalty,
            boolean repeatLastNEnabled, int repeatLastN,
            boolean repetitionPenaltyEnabled, double repetitionPenalty,
            int minStoryWindow, int storyCardLookback,
            int ollamaKeepAliveMinutes)
    {
        this(modelName, ollamaHost, contextLimit, promptTokenScale,
                responseLengthEnabled, responseLength,
                temperatureEnabled, temperature,
                topKEnabled, topK,
                topPEnabled, topP,
                minPEnabled, minP,
                typicalPEnabled, typicalP,
                presencePenaltyEnabled, presencePenalty,
                frequencyPenaltyEnabled, frequencyPenalty,
                repeatLastNEnabled, repeatLastN,
                repetitionPenaltyEnabled, repetitionPenalty,
                minStoryWindow, storyCardLookback, ollamaKeepAliveMinutes,
                StoryCardWrappingStyle.NONE, ConversationLayout.ROLE_AWARE);
    }

    public GenerationSettings(String modelName, String ollamaHost, int contextLimit, double promptTokenScale,
            boolean responseLengthEnabled, int responseLength,
            boolean temperatureEnabled, double temperature,
            boolean topKEnabled, int topK,
            boolean topPEnabled, double topP,
            boolean minPEnabled, double minP,
            boolean typicalPEnabled, double typicalP,
            boolean presencePenaltyEnabled, double presencePenalty,
            boolean frequencyPenaltyEnabled, double frequencyPenalty,
            boolean repeatLastNEnabled, int repeatLastN,
            boolean repetitionPenaltyEnabled, double repetitionPenalty,
            int minStoryWindow, int storyCardLookback)
    {
        this(modelName, ollamaHost, contextLimit, promptTokenScale,
                responseLengthEnabled, responseLength,
                temperatureEnabled, temperature,
                topKEnabled, topK,
                topPEnabled, topP,
                minPEnabled, minP,
                typicalPEnabled, typicalP,
                presencePenaltyEnabled, presencePenalty,
                frequencyPenaltyEnabled, frequencyPenalty,
                repeatLastNEnabled, repeatLastN,
                repetitionPenaltyEnabled, repetitionPenalty,
                minStoryWindow, storyCardLookback,
                AppSettings.DEFAULT_OLLAMA_KEEP_ALIVE_MINUTES);
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
                false, 1.0,
                false, 0.25,
                false, 0.0,
                false, 64,
                false, 1.05,
                minStoryWindow, 7,
                AppSettings.DEFAULT_OLLAMA_KEEP_ALIVE_MINUTES);
    }

    public GenerationSettings withoutNumPredict()
    {
        if (!responseLengthEnabled)
        {
            return this;
        }
        return new GenerationSettings(
                modelName, ollamaHost, contextLimit, promptTokenScale,
                false, responseLength,
                temperatureEnabled, temperature,
                topKEnabled, topK,
                topPEnabled, topP,
                minPEnabled, minP,
                typicalPEnabled, typicalP,
                presencePenaltyEnabled, presencePenalty,
                frequencyPenaltyEnabled, frequencyPenalty,
                repeatLastNEnabled, repeatLastN,
                repetitionPenaltyEnabled, repetitionPenalty,
                minStoryWindow, storyCardLookback,
                ollamaKeepAliveMinutes,
                storyCardWrappingStyle, conversationLayout);
    }
}
