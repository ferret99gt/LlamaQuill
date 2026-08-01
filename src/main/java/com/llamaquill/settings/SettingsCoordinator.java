package com.llamaquill.settings;

import com.llamaquill.model.AppSettings;
import com.llamaquill.model.ModelSettings;
import com.llamaquill.model.ConversationLayout;
import com.llamaquill.model.StoryCardWrappingStyle;

public final class SettingsCoordinator
{
    static final double PROMPT_TOKEN_SCALE_HYSTERESIS = 0.01;
    static final double MIN_PROMPT_CALIBRATION_CONTEXT_FRACTION = 0.5;

    private SettingsCoordinator()
    {
    }

    public static AppSettings withSelectedModel(AppSettings current, String modelName)
    {
        return current.toBuilder().selectedModel(modelName).build();
    }

    public static AppSettings withOllamaUrl(AppSettings current, String url)
    {
        return current.toBuilder().ollamaUrl(url).build();
    }

    public static AppSettings withComfyUiUrl(AppSettings current, String url)
    {
        return current.toBuilder().comfyUiUrl(url).build();
    }

    public static ModelSettings withContextLimit(ModelSettings current, int value)
    {
        return current.toBuilder().contextLimit(value).build();
    }

    public static ModelSettings withPromptTokenScale(ModelSettings current, double value)
    {
        return current.toBuilder().promptTokenScale(value).build();
    }

    public static ModelSettings withStoryCardWrappingStyle(ModelSettings current, StoryCardWrappingStyle value)
    {
        return current.toBuilder().storyCardWrappingStyle(value).build();
    }

    public static ModelSettings withConversationLayout(ModelSettings current, ConversationLayout value)
    {
        return current.toBuilder().conversationLayout(value).build();
    }

    public static ModelSettings calibratePromptTokenScale(ModelSettings current,
            int estimatedPromptTokens, int actualPromptTokens)
    {
        if (current == null || estimatedPromptTokens <= 0 || actualPromptTokens <= 0)
        {
            return current;
        }

        int observedPromptTokens = Math.max(estimatedPromptTokens, actualPromptTokens);
        int minimumSampleTokens = (int) Math.ceil(
                current.contextLimit() * MIN_PROMPT_CALIBRATION_CONTEXT_FRACTION);
        if (observedPromptTokens < minimumSampleTokens)
        {
            return current;
        }

        double sampleRatio = actualPromptTokens / (double) estimatedPromptTokens;
        // Exact prompt prefixes are required for Ollama's context-cache reuse.
        // Do not let harmless measurement noise move a trimming boundary on
        // every otherwise-identical generation.
        if (!Double.isFinite(sampleRatio)
                || Math.abs(sampleRatio - 1.0) <= PROMPT_TOKEN_SCALE_HYSTERESIS)
        {
            return current;
        }

        double oldScale = current.promptTokenScale();
        double updatedScale = clampPromptTokenScale(oldScale * sampleRatio);
        if (Double.compare(updatedScale, oldScale) == 0)
        {
            return current;
        }
        return withPromptTokenScale(current, updatedScale);
    }

    private static double clampPromptTokenScale(double value)
    {
        return Math.max(ModelSettings.MIN_PROMPT_TOKEN_SCALE,
                Math.min(ModelSettings.MAX_PROMPT_TOKEN_SCALE, value));
    }

    public static AppSettings withResponseLength(AppSettings current, int value)
    {
        return current.toBuilder().responseLength(value).build();
    }

    public static AppSettings withResponseLengthEnabled(AppSettings current, boolean enabled)
    {
        return current.toBuilder().responseLengthEnabled(enabled).build();
    }

    public static AppSettings withMinStoryPercent(AppSettings current, int percent)
    {
        return current.toBuilder().minStoryPercent(percent).build();
    }

    public static AppSettings withStoryCardLookback(AppSettings current, int value)
    {
        return current.toBuilder().storyCardLookback(value).build();
    }

    public static AppSettings withSelectedStoryCardCommandPreset(AppSettings current, String presetId)
    {
        return current.toBuilder().selectedStoryCardCommandPresetId(presetId).build();
    }

    public static AppSettings withComfyWorkflow(AppSettings current, String workflow)
    {
        String trimmed = workflow == null ? "" : workflow.trim();
        if (trimmed.isBlank())
        {
            trimmed = current.comfyWorkflow();
        }
        return current.toBuilder().comfyWorkflow(trimmed).build();
    }

    public static AppSettings withComfyWidth(AppSettings current, int width)
    {
        return current.toBuilder().comfyWidth(width).build();
    }

    public static AppSettings withComfyHeight(AppSettings current, int height)
    {
        return current.toBuilder().comfyHeight(height).build();
    }

    public static AppSettings withComfyBatchSize(AppSettings current, int batchSize)
    {
        return current.toBuilder().comfyBatchSize(batchSize).build();
    }

    public static AppSettings withOllamaKeepAliveMinutes(AppSettings current, int minutes)
    {
        return current.toBuilder().ollamaKeepAliveMinutes(minutes).build();
    }

    public static ModelSettings withTemperature(ModelSettings current, double value)
    {
        return current.toBuilder().temperature(value).build();
    }

    public static ModelSettings withTemperatureEnabled(ModelSettings current, boolean enabled)
    {
        return current.toBuilder().temperatureEnabled(enabled).build();
    }

    public static ModelSettings withTopK(ModelSettings current, int value)
    {
        return current.toBuilder().topK(value).build();
    }

    public static ModelSettings withTopKEnabled(ModelSettings current, boolean enabled)
    {
        return current.toBuilder().topKEnabled(enabled).build();
    }

    public static ModelSettings withTopP(ModelSettings current, double value)
    {
        return current.toBuilder().topP(value).build();
    }

    public static ModelSettings withTopPEnabled(ModelSettings current, boolean enabled)
    {
        return current.toBuilder().topPEnabled(enabled).build();
    }

    public static ModelSettings withMinP(ModelSettings current, double value)
    {
        return current.toBuilder().minP(value).build();
    }

    public static ModelSettings withMinPEnabled(ModelSettings current, boolean enabled)
    {
        return current.toBuilder().minPEnabled(enabled).build();
    }

    public static ModelSettings withTypicalP(ModelSettings current, double value)
    {
        return current.toBuilder().typicalP(value).build();
    }

    public static ModelSettings withTypicalPEnabled(ModelSettings current, boolean enabled)
    {
        return current.toBuilder().typicalPEnabled(enabled).build();
    }

    public static ModelSettings withPresencePenalty(ModelSettings current, double value)
    {
        return current.toBuilder().presencePenalty(value).build();
    }

    public static ModelSettings withPresencePenaltyEnabled(ModelSettings current, boolean enabled)
    {
        return current.toBuilder().presencePenaltyEnabled(enabled).build();
    }

    public static ModelSettings withFrequencyPenalty(ModelSettings current, double value)
    {
        return current.toBuilder().frequencyPenalty(value).build();
    }

    public static ModelSettings withFrequencyPenaltyEnabled(ModelSettings current, boolean enabled)
    {
        return current.toBuilder().frequencyPenaltyEnabled(enabled).build();
    }

    public static ModelSettings withRepeatLastN(ModelSettings current, int value)
    {
        return current.toBuilder().repeatLastN(value).build();
    }

    public static ModelSettings withRepeatLastNEnabled(ModelSettings current, boolean enabled)
    {
        return current.toBuilder().repeatLastNEnabled(enabled).build();
    }

    public static ModelSettings withRepetitionPenalty(ModelSettings current, double value)
    {
        return current.toBuilder().repetitionPenalty(value).build();
    }

    public static ModelSettings withRepetitionPenaltyEnabled(ModelSettings current, boolean enabled)
    {
        return current.toBuilder().repetitionPenaltyEnabled(enabled).build();
    }
}
