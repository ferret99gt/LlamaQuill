package com.llamaquill.model;

public record ModelSettings(String modelName, boolean active, int contextLimit, double promptTokenScale,
        boolean temperatureEnabled, double temperature,
        boolean topKEnabled, int topK,
        boolean topPEnabled, double topP,
        boolean minPEnabled, double minP,
        boolean typicalPEnabled, double typicalP,
        boolean presencePenaltyEnabled, double presencePenalty,
        boolean frequencyPenaltyEnabled, double frequencyPenalty,
        boolean repeatLastNEnabled, int repeatLastN,
        boolean repetitionPenaltyEnabled, double repetitionPenalty,
        StoryCardWrappingStyle storyCardWrappingStyle,
        ConversationLayout conversationLayout)
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
        typicalP = clampFinite(typicalP, 1.0, 0.0, 1.0);
        presencePenalty = clampFinite(presencePenalty, 0.25, -2.0, 2.0);
        frequencyPenalty = clampFinite(frequencyPenalty, 0.0, -2.0, 2.0);
        repeatLastN = clamp(repeatLastN, -1, contextLimit);
        repetitionPenalty = clampFinite(repetitionPenalty, 1.05, 0.0, 5.0);
        storyCardWrappingStyle = storyCardWrappingStyle == null
                ? StoryCardWrappingStyle.NONE
                : storyCardWrappingStyle;
        conversationLayout = conversationLayout == null
                ? ConversationLayout.ROLE_AWARE
                : conversationLayout;
    }

    public ModelSettings(String modelName, boolean active, int contextLimit, double promptTokenScale,
            boolean temperatureEnabled, double temperature,
            boolean topKEnabled, int topK,
            boolean topPEnabled, double topP,
            boolean minPEnabled, double minP,
            boolean typicalPEnabled, double typicalP,
            boolean presencePenaltyEnabled, double presencePenalty,
            boolean frequencyPenaltyEnabled, double frequencyPenalty,
            boolean repeatLastNEnabled, int repeatLastN,
            boolean repetitionPenaltyEnabled, double repetitionPenalty)
    {
        this(modelName, active, contextLimit, promptTokenScale,
                temperatureEnabled, temperature,
                topKEnabled, topK,
                topPEnabled, topP,
                minPEnabled, minP,
                typicalPEnabled, typicalP,
                presencePenaltyEnabled, presencePenalty,
                frequencyPenaltyEnabled, frequencyPenalty,
                repeatLastNEnabled, repeatLastN,
                repetitionPenaltyEnabled, repetitionPenalty,
                StoryCardWrappingStyle.NONE, ConversationLayout.ROLE_AWARE);
    }

    public static ModelSettings defaults(String modelName)
    {
        GenerationSettings defaults = GenerationSettings.defaults();
        return new ModelSettings(modelName, true, DEFAULT_CONTEXT_LIMIT, 1.0,
                false, defaults.temperature(),
                false, defaults.topK(),
                false, defaults.topP(),
                false, defaults.minP(),
                false, defaults.typicalP(),
                false, defaults.presencePenalty(),
                false, defaults.frequencyPenalty(),
                false, defaults.repeatLastN(),
                false, defaults.repetitionPenalty(),
                StoryCardWrappingStyle.NONE, ConversationLayout.ROLE_AWARE);
    }

    public Builder toBuilder()
    {
        return new Builder(this);
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

    public static final class Builder
    {
        private String modelName;
        private boolean active;
        private int contextLimit;
        private double promptTokenScale;
        private boolean temperatureEnabled;
        private double temperature;
        private boolean topKEnabled;
        private int topK;
        private boolean topPEnabled;
        private double topP;
        private boolean minPEnabled;
        private double minP;
        private boolean typicalPEnabled;
        private double typicalP;
        private boolean presencePenaltyEnabled;
        private double presencePenalty;
        private boolean frequencyPenaltyEnabled;
        private double frequencyPenalty;
        private boolean repeatLastNEnabled;
        private int repeatLastN;
        private boolean repetitionPenaltyEnabled;
        private double repetitionPenalty;
        private StoryCardWrappingStyle storyCardWrappingStyle;
        private ConversationLayout conversationLayout;

        private Builder(ModelSettings settings)
        {
            modelName = settings.modelName();
            active = settings.active();
            contextLimit = settings.contextLimit();
            promptTokenScale = settings.promptTokenScale();
            temperatureEnabled = settings.temperatureEnabled();
            temperature = settings.temperature();
            topKEnabled = settings.topKEnabled();
            topK = settings.topK();
            topPEnabled = settings.topPEnabled();
            topP = settings.topP();
            minPEnabled = settings.minPEnabled();
            minP = settings.minP();
            typicalPEnabled = settings.typicalPEnabled();
            typicalP = settings.typicalP();
            presencePenaltyEnabled = settings.presencePenaltyEnabled();
            presencePenalty = settings.presencePenalty();
            frequencyPenaltyEnabled = settings.frequencyPenaltyEnabled();
            frequencyPenalty = settings.frequencyPenalty();
            repeatLastNEnabled = settings.repeatLastNEnabled();
            repeatLastN = settings.repeatLastN();
            repetitionPenaltyEnabled = settings.repetitionPenaltyEnabled();
            repetitionPenalty = settings.repetitionPenalty();
            storyCardWrappingStyle = settings.storyCardWrappingStyle();
            conversationLayout = settings.conversationLayout();
        }

        public Builder contextLimit(int value)
        {
            contextLimit = value;
            return this;
        }

        public Builder promptTokenScale(double value)
        {
            promptTokenScale = value;
            return this;
        }

        public Builder temperatureEnabled(boolean value)
        {
            temperatureEnabled = value;
            return this;
        }

        public Builder temperature(double value)
        {
            temperature = value;
            return this;
        }

        public Builder topKEnabled(boolean value)
        {
            topKEnabled = value;
            return this;
        }

        public Builder topK(int value)
        {
            topK = value;
            return this;
        }

        public Builder topPEnabled(boolean value)
        {
            topPEnabled = value;
            return this;
        }

        public Builder topP(double value)
        {
            topP = value;
            return this;
        }

        public Builder minPEnabled(boolean value)
        {
            minPEnabled = value;
            return this;
        }

        public Builder minP(double value)
        {
            minP = value;
            return this;
        }

        public Builder typicalPEnabled(boolean value)
        {
            typicalPEnabled = value;
            return this;
        }

        public Builder typicalP(double value)
        {
            typicalP = value;
            return this;
        }

        public Builder presencePenaltyEnabled(boolean value)
        {
            presencePenaltyEnabled = value;
            return this;
        }

        public Builder presencePenalty(double value)
        {
            presencePenalty = value;
            return this;
        }

        public Builder frequencyPenaltyEnabled(boolean value)
        {
            frequencyPenaltyEnabled = value;
            return this;
        }

        public Builder frequencyPenalty(double value)
        {
            frequencyPenalty = value;
            return this;
        }

        public Builder repeatLastNEnabled(boolean value)
        {
            repeatLastNEnabled = value;
            return this;
        }

        public Builder repeatLastN(int value)
        {
            repeatLastN = value;
            return this;
        }

        public Builder repetitionPenaltyEnabled(boolean value)
        {
            repetitionPenaltyEnabled = value;
            return this;
        }

        public Builder repetitionPenalty(double value)
        {
            repetitionPenalty = value;
            return this;
        }

        public Builder storyCardWrappingStyle(StoryCardWrappingStyle value)
        {
            storyCardWrappingStyle = value;
            return this;
        }

        public Builder conversationLayout(ConversationLayout value)
        {
            conversationLayout = value;
            return this;
        }

        public ModelSettings build()
        {
            return new ModelSettings(modelName, active, contextLimit, promptTokenScale,
                    temperatureEnabled, temperature,
                    topKEnabled, topK,
                    topPEnabled, topP,
                    minPEnabled, minP,
                    typicalPEnabled, typicalP,
                    presencePenaltyEnabled, presencePenalty,
                    frequencyPenaltyEnabled, frequencyPenalty,
                    repeatLastNEnabled, repeatLastN,
                    repetitionPenaltyEnabled, repetitionPenalty,
                    storyCardWrappingStyle, conversationLayout);
        }
    }
}
