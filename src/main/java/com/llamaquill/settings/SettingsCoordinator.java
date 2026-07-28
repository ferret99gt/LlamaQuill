package com.llamaquill.settings;

import com.llamaquill.model.AppSettings;
import com.llamaquill.model.ModelSettings;

public final class SettingsCoordinator
{
    private SettingsCoordinator()
    {
    }

    public static AppSettings withSelectedModel(AppSettings current, String modelName)
    {
        return new AppSettings(current.ollamaUrl(), current.comfyUiUrl(), modelName,
                current.responseLengthEnabled(), current.responseLength(), current.minStoryPercent(), current.storyCardLookback(),
                current.anPlacement(), current.comfyWorkflow(), current.comfyWidth(), current.comfyHeight(),
                current.comfyBatchSize());
    }

    public static AppSettings withOllamaUrl(AppSettings current, String url)
    {
        return new AppSettings(url, current.comfyUiUrl(), current.selectedModel(),
                current.responseLengthEnabled(), current.responseLength(), current.minStoryPercent(), current.storyCardLookback(),
                current.anPlacement(), current.comfyWorkflow(), current.comfyWidth(), current.comfyHeight(),
                current.comfyBatchSize());
    }

    public static AppSettings withComfyUiUrl(AppSettings current, String url)
    {
        return new AppSettings(current.ollamaUrl(), url, current.selectedModel(),
                current.responseLengthEnabled(), current.responseLength(), current.minStoryPercent(),
                current.storyCardLookback(), current.anPlacement(),
                current.comfyWorkflow(), current.comfyWidth(), current.comfyHeight(), current.comfyBatchSize());
    }

    public static ModelSettings withContextLimit(ModelSettings current, int value)
    {
        return new ModelSettings(current.modelName(), current.active(), value, current.promptTokenScale(),
                current.temperatureEnabled(), current.temperature(),
                current.topKEnabled(), current.topK(),
                current.topPEnabled(), current.topP(),
                current.minPEnabled(), current.minP(),
                current.typicalPEnabled(), current.typicalP(),
                current.presencePenaltyEnabled(), current.presencePenalty(),
                current.frequencyPenaltyEnabled(), current.frequencyPenalty(),
                current.repeatLastNEnabled(), current.repeatLastN(),
                current.repetitionPenaltyEnabled(), current.repetitionPenalty());
    }

    public static ModelSettings withPromptTokenScale(ModelSettings current, double value)
    {
        return new ModelSettings(current.modelName(), current.active(), current.contextLimit(), value,
                current.temperatureEnabled(), current.temperature(),
                current.topKEnabled(), current.topK(),
                current.topPEnabled(), current.topP(),
                current.minPEnabled(), current.minP(),
                current.typicalPEnabled(), current.typicalP(),
                current.presencePenaltyEnabled(), current.presencePenalty(),
                current.frequencyPenaltyEnabled(), current.frequencyPenalty(),
                current.repeatLastNEnabled(), current.repeatLastN(),
                current.repetitionPenaltyEnabled(), current.repetitionPenalty());
    }

    public static AppSettings withResponseLength(AppSettings current, int value)
    {
        return new AppSettings(current.ollamaUrl(), current.comfyUiUrl(), current.selectedModel(),
                current.responseLengthEnabled(), value, current.minStoryPercent(),
                current.storyCardLookback(), current.anPlacement(),
                current.comfyWorkflow(), current.comfyWidth(), current.comfyHeight(), current.comfyBatchSize());
    }

    public static AppSettings withResponseLengthEnabled(AppSettings current, boolean enabled)
    {
        return new AppSettings(current.ollamaUrl(), current.comfyUiUrl(), current.selectedModel(),
                enabled, current.responseLength(), current.minStoryPercent(),
                current.storyCardLookback(), current.anPlacement(), current.comfyWorkflow(),
                current.comfyWidth(), current.comfyHeight(), current.comfyBatchSize());
    }

    public static AppSettings withMinStoryPercent(AppSettings current, int percent)
    {
        return new AppSettings(current.ollamaUrl(), current.comfyUiUrl(), current.selectedModel(),
                current.responseLengthEnabled(), current.responseLength(), percent,
                current.storyCardLookback(), current.anPlacement(),
                current.comfyWorkflow(), current.comfyWidth(), current.comfyHeight(), current.comfyBatchSize());
    }

    public static AppSettings withStoryCardLookback(AppSettings current, int value)
    {
        return new AppSettings(current.ollamaUrl(), current.comfyUiUrl(), current.selectedModel(),
                current.responseLengthEnabled(), current.responseLength(), current.minStoryPercent(),
                value, current.anPlacement(),
                current.comfyWorkflow(), current.comfyWidth(), current.comfyHeight(), current.comfyBatchSize());
    }

    public static AppSettings withAnPlacement(AppSettings current, int value)
    {
        return new AppSettings(current.ollamaUrl(), current.comfyUiUrl(), current.selectedModel(),
                current.responseLengthEnabled(), current.responseLength(), current.minStoryPercent(),
                current.storyCardLookback(), value,
                current.comfyWorkflow(), current.comfyWidth(), current.comfyHeight(), current.comfyBatchSize());
    }

    public static AppSettings withComfyWorkflow(AppSettings current, String workflow)
    {
        String trimmed = workflow == null ? "" : workflow.trim();
        if (trimmed.isBlank())
        {
            trimmed = current.comfyWorkflow();
        }
        return new AppSettings(current.ollamaUrl(), current.comfyUiUrl(), current.selectedModel(),
                current.responseLengthEnabled(), current.responseLength(), current.minStoryPercent(),
                current.storyCardLookback(), current.anPlacement(),
                trimmed, current.comfyWidth(), current.comfyHeight(), current.comfyBatchSize());
    }

    public static AppSettings withComfyWidth(AppSettings current, int width)
    {
        int capped = Math.max(64, Math.min(4096, width));
        return new AppSettings(current.ollamaUrl(), current.comfyUiUrl(), current.selectedModel(),
                current.responseLengthEnabled(), current.responseLength(), current.minStoryPercent(),
                current.storyCardLookback(), current.anPlacement(),
                current.comfyWorkflow(), capped, current.comfyHeight(), current.comfyBatchSize());
    }

    public static AppSettings withComfyHeight(AppSettings current, int height)
    {
        int capped = Math.max(64, Math.min(4096, height));
        return new AppSettings(current.ollamaUrl(), current.comfyUiUrl(), current.selectedModel(),
                current.responseLengthEnabled(), current.responseLength(), current.minStoryPercent(),
                current.storyCardLookback(), current.anPlacement(),
                current.comfyWorkflow(), current.comfyWidth(), capped, current.comfyBatchSize());
    }

    public static AppSettings withComfyBatchSize(AppSettings current, int batchSize)
    {
        int capped = Math.max(1, Math.min(32, batchSize));
        return new AppSettings(current.ollamaUrl(), current.comfyUiUrl(), current.selectedModel(),
                current.responseLengthEnabled(), current.responseLength(), current.minStoryPercent(),
                current.storyCardLookback(), current.anPlacement(),
                current.comfyWorkflow(), current.comfyWidth(), current.comfyHeight(), capped);
    }

    public static ModelSettings withTemperature(ModelSettings current, double value)
    {
        return withModelOption(current, ModelOption.TEMPERATURE, null, value, null);
    }

    public static ModelSettings withTemperatureEnabled(ModelSettings current, boolean enabled)
    {
        return withModelOption(current, ModelOption.TEMPERATURE, enabled, null, null);
    }

    public static ModelSettings withTopK(ModelSettings current, int value)
    {
        return withModelOption(current, ModelOption.TOP_K, null, null, value);
    }

    public static ModelSettings withTopKEnabled(ModelSettings current, boolean enabled)
    {
        return withModelOption(current, ModelOption.TOP_K, enabled, null, null);
    }

    public static ModelSettings withTopP(ModelSettings current, double value)
    {
        return withModelOption(current, ModelOption.TOP_P, null, value, null);
    }

    public static ModelSettings withTopPEnabled(ModelSettings current, boolean enabled)
    {
        return withModelOption(current, ModelOption.TOP_P, enabled, null, null);
    }

    public static ModelSettings withMinP(ModelSettings current, double value)
    {
        return withModelOption(current, ModelOption.MIN_P, null, value, null);
    }

    public static ModelSettings withMinPEnabled(ModelSettings current, boolean enabled)
    {
        return withModelOption(current, ModelOption.MIN_P, enabled, null, null);
    }

    public static ModelSettings withTypicalP(ModelSettings current, double value)
    {
        return withModelOption(current, ModelOption.TYPICAL_P, null, value, null);
    }

    public static ModelSettings withTypicalPEnabled(ModelSettings current, boolean enabled)
    {
        return withModelOption(current, ModelOption.TYPICAL_P, enabled, null, null);
    }

    public static ModelSettings withPresencePenalty(ModelSettings current, double value)
    {
        return withModelOption(current, ModelOption.PRESENCE_PENALTY, null, value, null);
    }

    public static ModelSettings withPresencePenaltyEnabled(ModelSettings current, boolean enabled)
    {
        return withModelOption(current, ModelOption.PRESENCE_PENALTY, enabled, null, null);
    }

    public static ModelSettings withFrequencyPenalty(ModelSettings current, double value)
    {
        return withModelOption(current, ModelOption.FREQUENCY_PENALTY, null, value, null);
    }

    public static ModelSettings withFrequencyPenaltyEnabled(ModelSettings current, boolean enabled)
    {
        return withModelOption(current, ModelOption.FREQUENCY_PENALTY, enabled, null, null);
    }

    public static ModelSettings withRepeatLastN(ModelSettings current, int value)
    {
        return withModelOption(current, ModelOption.REPEAT_LAST_N, null, null, value);
    }

    public static ModelSettings withRepeatLastNEnabled(ModelSettings current, boolean enabled)
    {
        return withModelOption(current, ModelOption.REPEAT_LAST_N, enabled, null, null);
    }

    public static ModelSettings withRepetitionPenalty(ModelSettings current, double value)
    {
        return withModelOption(current, ModelOption.REPETITION_PENALTY, null, value, null);
    }

    public static ModelSettings withRepetitionPenaltyEnabled(ModelSettings current, boolean enabled)
    {
        return withModelOption(current, ModelOption.REPETITION_PENALTY, enabled, null, null);
    }

    private static ModelSettings withModelOption(ModelSettings current, ModelOption option, Boolean enabled,
            Double doubleValue, Integer integerValue)
    {
        return new ModelSettings(current.modelName(), current.active(), current.contextLimit(), current.promptTokenScale(),
                enabled(option, ModelOption.TEMPERATURE, enabled, current.temperatureEnabled()),
                doubleValue(option, ModelOption.TEMPERATURE, doubleValue, current.temperature()),
                enabled(option, ModelOption.TOP_K, enabled, current.topKEnabled()),
                integerValue(option, ModelOption.TOP_K, integerValue, current.topK()),
                enabled(option, ModelOption.TOP_P, enabled, current.topPEnabled()),
                doubleValue(option, ModelOption.TOP_P, doubleValue, current.topP()),
                enabled(option, ModelOption.MIN_P, enabled, current.minPEnabled()),
                doubleValue(option, ModelOption.MIN_P, doubleValue, current.minP()),
                enabled(option, ModelOption.TYPICAL_P, enabled, current.typicalPEnabled()),
                doubleValue(option, ModelOption.TYPICAL_P, doubleValue, current.typicalP()),
                enabled(option, ModelOption.PRESENCE_PENALTY, enabled, current.presencePenaltyEnabled()),
                doubleValue(option, ModelOption.PRESENCE_PENALTY, doubleValue, current.presencePenalty()),
                enabled(option, ModelOption.FREQUENCY_PENALTY, enabled, current.frequencyPenaltyEnabled()),
                doubleValue(option, ModelOption.FREQUENCY_PENALTY, doubleValue, current.frequencyPenalty()),
                enabled(option, ModelOption.REPEAT_LAST_N, enabled, current.repeatLastNEnabled()),
                integerValue(option, ModelOption.REPEAT_LAST_N, integerValue, current.repeatLastN()),
                enabled(option, ModelOption.REPETITION_PENALTY, enabled, current.repetitionPenaltyEnabled()),
                doubleValue(option, ModelOption.REPETITION_PENALTY, doubleValue, current.repetitionPenalty()));
    }

    private static boolean enabled(ModelOption option, ModelOption target, Boolean value, boolean current)
    {
        return option == target && value != null ? value : current;
    }

    private static double doubleValue(ModelOption option, ModelOption target, Double value, double current)
    {
        return option == target && value != null ? value : current;
    }

    private static int integerValue(ModelOption option, ModelOption target, Integer value, int current)
    {
        return option == target && value != null ? value : current;
    }

    private enum ModelOption
    {
        TEMPERATURE,
        TOP_K,
        TOP_P,
        MIN_P,
        TYPICAL_P,
        PRESENCE_PENALTY,
        FREQUENCY_PENALTY,
        REPEAT_LAST_N,
        REPETITION_PENALTY
    }

}
