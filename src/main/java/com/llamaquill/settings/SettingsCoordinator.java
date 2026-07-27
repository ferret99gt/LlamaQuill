package com.llamaquill.settings;

import com.llamaquill.model.AppAutoCardsSettings;
import com.llamaquill.model.AppSettings;
import com.llamaquill.model.ModelAutoCardsSettings;
import com.llamaquill.model.ModelSettings;
import com.llamaquill.model.StoryAutoCardsSettings;

public final class SettingsCoordinator
{
    private SettingsCoordinator()
    {
    }

    public static AppSettings withSelectedModel(AppSettings current, String modelName)
    {
        return new AppSettings(current.ollamaUrl(), current.comfyUiUrl(), modelName, current.contextLimit(),
                current.responseLengthEnabled(), current.responseLength(), current.minStoryWindow(), current.storyCardLookback(),
                current.anPlacement(), current.comfyWorkflow(), current.comfyWidth(), current.comfyHeight(),
                current.comfyBatchSize());
    }

    public static AppSettings withOllamaUrl(AppSettings current, String url)
    {
        return new AppSettings(url.trim(), current.comfyUiUrl(), current.selectedModel(), current.contextLimit(),
                current.responseLengthEnabled(), current.responseLength(), current.minStoryWindow(), current.storyCardLookback(),
                current.anPlacement(), current.comfyWorkflow(), current.comfyWidth(), current.comfyHeight(),
                current.comfyBatchSize());
    }

    public static AppSettings withComfyUiUrl(AppSettings current, String url)
    {
        return new AppSettings(current.ollamaUrl(), url.trim(), current.selectedModel(), current.contextLimit(),
                current.responseLengthEnabled(), current.responseLength(), current.minStoryWindow(),
                current.storyCardLookback(), current.anPlacement(),
                current.comfyWorkflow(), current.comfyWidth(), current.comfyHeight(), current.comfyBatchSize());
    }

    public static AppSettings withContextLimit(AppSettings current, int value, int minStoryPercent)
    {
        int capped = Math.max(1024, Math.min(32768, value));
        int percent = Math.max(10, Math.min(100, minStoryPercent));
        int minWindow = (int) Math.round(capped * (percent / 100.0));
        return new AppSettings(current.ollamaUrl(), current.comfyUiUrl(), current.selectedModel(), capped,
                current.responseLengthEnabled(), current.responseLength(), minWindow,
                current.storyCardLookback(), current.anPlacement(),
                current.comfyWorkflow(), current.comfyWidth(), current.comfyHeight(), current.comfyBatchSize());
    }

    public static AppSettings withResponseLength(AppSettings current, int value)
    {
        int capped = Math.max(1, Math.min(250, value));
        return new AppSettings(current.ollamaUrl(), current.comfyUiUrl(), current.selectedModel(),
                current.contextLimit(), current.responseLengthEnabled(), capped, current.minStoryWindow(),
                current.storyCardLookback(), current.anPlacement(),
                current.comfyWorkflow(), current.comfyWidth(), current.comfyHeight(), current.comfyBatchSize());
    }

    public static AppSettings withResponseLengthEnabled(AppSettings current, boolean enabled)
    {
        return new AppSettings(current.ollamaUrl(), current.comfyUiUrl(), current.selectedModel(),
                current.contextLimit(), enabled, current.responseLength(), current.minStoryWindow(),
                current.storyCardLookback(), current.anPlacement(), current.comfyWorkflow(),
                current.comfyWidth(), current.comfyHeight(), current.comfyBatchSize());
    }

    public static AppSettings withMinStoryPercent(AppSettings current, int percent)
    {
        int capped = Math.max(10, Math.min(100, percent));
        int minWindow = (int) Math.round(current.contextLimit() * (capped / 100.0));
        return new AppSettings(current.ollamaUrl(), current.comfyUiUrl(), current.selectedModel(), current.contextLimit(),
                current.responseLengthEnabled(), current.responseLength(), minWindow,
                current.storyCardLookback(), current.anPlacement(),
                current.comfyWorkflow(), current.comfyWidth(), current.comfyHeight(), current.comfyBatchSize());
    }

    public static AppSettings withStoryCardLookback(AppSettings current, int value)
    {
        return new AppSettings(current.ollamaUrl(), current.comfyUiUrl(), current.selectedModel(), current.contextLimit(),
                current.responseLengthEnabled(), current.responseLength(), current.minStoryWindow(),
                value, current.anPlacement(),
                current.comfyWorkflow(), current.comfyWidth(), current.comfyHeight(), current.comfyBatchSize());
    }

    public static AppSettings withAnPlacement(AppSettings current, int value)
    {
        return new AppSettings(current.ollamaUrl(), current.comfyUiUrl(), current.selectedModel(), current.contextLimit(),
                current.responseLengthEnabled(), current.responseLength(), current.minStoryWindow(),
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
        return new AppSettings(current.ollamaUrl(), current.comfyUiUrl(), current.selectedModel(), current.contextLimit(),
                current.responseLengthEnabled(), current.responseLength(), current.minStoryWindow(),
                current.storyCardLookback(), current.anPlacement(),
                trimmed, current.comfyWidth(), current.comfyHeight(), current.comfyBatchSize());
    }

    public static AppSettings withComfyWidth(AppSettings current, int width)
    {
        int capped = Math.max(64, Math.min(4096, width));
        return new AppSettings(current.ollamaUrl(), current.comfyUiUrl(), current.selectedModel(), current.contextLimit(),
                current.responseLengthEnabled(), current.responseLength(), current.minStoryWindow(),
                current.storyCardLookback(), current.anPlacement(),
                current.comfyWorkflow(), capped, current.comfyHeight(), current.comfyBatchSize());
    }

    public static AppSettings withComfyHeight(AppSettings current, int height)
    {
        int capped = Math.max(64, Math.min(4096, height));
        return new AppSettings(current.ollamaUrl(), current.comfyUiUrl(), current.selectedModel(), current.contextLimit(),
                current.responseLengthEnabled(), current.responseLength(), current.minStoryWindow(),
                current.storyCardLookback(), current.anPlacement(),
                current.comfyWorkflow(), current.comfyWidth(), capped, current.comfyBatchSize());
    }

    public static AppSettings withComfyBatchSize(AppSettings current, int batchSize)
    {
        int capped = Math.max(1, Math.min(32, batchSize));
        return new AppSettings(current.ollamaUrl(), current.comfyUiUrl(), current.selectedModel(), current.contextLimit(),
                current.responseLengthEnabled(), current.responseLength(), current.minStoryWindow(),
                current.storyCardLookback(), current.anPlacement(),
                current.comfyWorkflow(), current.comfyWidth(), current.comfyHeight(), capped);
    }

    public static ModelSettings withTemperature(ModelSettings current, double value)
    {
        return new ModelSettings(current.modelName(), current.active(),
                current.temperatureEnabled(), value,
                current.topKEnabled(), current.topK(),
                current.topPEnabled(), current.topP(),
                current.minPEnabled(), current.minP(),
                current.presencePenaltyEnabled(), current.presencePenalty(),
                current.frequencyPenaltyEnabled(), current.frequencyPenalty(),
                current.repetitionPenaltyEnabled(), current.repetitionPenalty());
    }

    public static ModelSettings withTemperatureEnabled(ModelSettings current, boolean enabled)
    {
        return new ModelSettings(current.modelName(), current.active(),
                enabled, current.temperature(),
                current.topKEnabled(), current.topK(),
                current.topPEnabled(), current.topP(),
                current.minPEnabled(), current.minP(),
                current.presencePenaltyEnabled(), current.presencePenalty(),
                current.frequencyPenaltyEnabled(), current.frequencyPenalty(),
                current.repetitionPenaltyEnabled(), current.repetitionPenalty());
    }

    public static ModelSettings withTopK(ModelSettings current, int value)
    {
        return new ModelSettings(current.modelName(), current.active(),
                current.temperatureEnabled(), current.temperature(),
                current.topKEnabled(), value,
                current.topPEnabled(), current.topP(),
                current.minPEnabled(), current.minP(),
                current.presencePenaltyEnabled(), current.presencePenalty(),
                current.frequencyPenaltyEnabled(), current.frequencyPenalty(),
                current.repetitionPenaltyEnabled(), current.repetitionPenalty());
    }

    public static ModelSettings withTopKEnabled(ModelSettings current, boolean enabled)
    {
        return new ModelSettings(current.modelName(), current.active(),
                current.temperatureEnabled(), current.temperature(),
                enabled, current.topK(),
                current.topPEnabled(), current.topP(),
                current.minPEnabled(), current.minP(),
                current.presencePenaltyEnabled(), current.presencePenalty(),
                current.frequencyPenaltyEnabled(), current.frequencyPenalty(),
                current.repetitionPenaltyEnabled(), current.repetitionPenalty());
    }

    public static ModelSettings withTopP(ModelSettings current, double value)
    {
        return new ModelSettings(current.modelName(), current.active(),
                current.temperatureEnabled(), current.temperature(),
                current.topKEnabled(), current.topK(),
                current.topPEnabled(), value,
                current.minPEnabled(), current.minP(),
                current.presencePenaltyEnabled(), current.presencePenalty(),
                current.frequencyPenaltyEnabled(), current.frequencyPenalty(),
                current.repetitionPenaltyEnabled(), current.repetitionPenalty());
    }

    public static ModelSettings withTopPEnabled(ModelSettings current, boolean enabled)
    {
        return new ModelSettings(current.modelName(), current.active(),
                current.temperatureEnabled(), current.temperature(),
                current.topKEnabled(), current.topK(),
                enabled, current.topP(),
                current.minPEnabled(), current.minP(),
                current.presencePenaltyEnabled(), current.presencePenalty(),
                current.frequencyPenaltyEnabled(), current.frequencyPenalty(),
                current.repetitionPenaltyEnabled(), current.repetitionPenalty());
    }

    public static ModelSettings withMinP(ModelSettings current, double value)
    {
        return new ModelSettings(current.modelName(), current.active(),
                current.temperatureEnabled(), current.temperature(),
                current.topKEnabled(), current.topK(),
                current.topPEnabled(), current.topP(),
                current.minPEnabled(), value,
                current.presencePenaltyEnabled(), current.presencePenalty(),
                current.frequencyPenaltyEnabled(), current.frequencyPenalty(),
                current.repetitionPenaltyEnabled(), current.repetitionPenalty());
    }

    public static ModelSettings withMinPEnabled(ModelSettings current, boolean enabled)
    {
        return new ModelSettings(current.modelName(), current.active(),
                current.temperatureEnabled(), current.temperature(),
                current.topKEnabled(), current.topK(),
                current.topPEnabled(), current.topP(),
                enabled, current.minP(),
                current.presencePenaltyEnabled(), current.presencePenalty(),
                current.frequencyPenaltyEnabled(), current.frequencyPenalty(),
                current.repetitionPenaltyEnabled(), current.repetitionPenalty());
    }

    public static ModelSettings withPresencePenalty(ModelSettings current, double value)
    {
        return new ModelSettings(current.modelName(), current.active(),
                current.temperatureEnabled(), current.temperature(),
                current.topKEnabled(), current.topK(),
                current.topPEnabled(), current.topP(),
                current.minPEnabled(), current.minP(),
                current.presencePenaltyEnabled(), value,
                current.frequencyPenaltyEnabled(), current.frequencyPenalty(),
                current.repetitionPenaltyEnabled(), current.repetitionPenalty());
    }

    public static ModelSettings withPresencePenaltyEnabled(ModelSettings current, boolean enabled)
    {
        return new ModelSettings(current.modelName(), current.active(),
                current.temperatureEnabled(), current.temperature(),
                current.topKEnabled(), current.topK(),
                current.topPEnabled(), current.topP(),
                current.minPEnabled(), current.minP(),
                enabled, current.presencePenalty(),
                current.frequencyPenaltyEnabled(), current.frequencyPenalty(),
                current.repetitionPenaltyEnabled(), current.repetitionPenalty());
    }

    public static ModelSettings withFrequencyPenalty(ModelSettings current, double value)
    {
        return new ModelSettings(current.modelName(), current.active(),
                current.temperatureEnabled(), current.temperature(),
                current.topKEnabled(), current.topK(),
                current.topPEnabled(), current.topP(),
                current.minPEnabled(), current.minP(),
                current.presencePenaltyEnabled(), current.presencePenalty(),
                current.frequencyPenaltyEnabled(), value,
                current.repetitionPenaltyEnabled(), current.repetitionPenalty());
    }

    public static ModelSettings withFrequencyPenaltyEnabled(ModelSettings current, boolean enabled)
    {
        return new ModelSettings(current.modelName(), current.active(),
                current.temperatureEnabled(), current.temperature(),
                current.topKEnabled(), current.topK(),
                current.topPEnabled(), current.topP(),
                current.minPEnabled(), current.minP(),
                current.presencePenaltyEnabled(), current.presencePenalty(),
                enabled, current.frequencyPenalty(),
                current.repetitionPenaltyEnabled(), current.repetitionPenalty());
    }

    public static ModelSettings withRepetitionPenalty(ModelSettings current, double value)
    {
        return new ModelSettings(current.modelName(), current.active(),
                current.temperatureEnabled(), current.temperature(),
                current.topKEnabled(), current.topK(),
                current.topPEnabled(), current.topP(),
                current.minPEnabled(), current.minP(),
                current.presencePenaltyEnabled(), current.presencePenalty(),
                current.frequencyPenaltyEnabled(), current.frequencyPenalty(),
                current.repetitionPenaltyEnabled(), value);
    }

    public static ModelSettings withRepetitionPenaltyEnabled(ModelSettings current, boolean enabled)
    {
        return new ModelSettings(current.modelName(), current.active(),
                current.temperatureEnabled(), current.temperature(),
                current.topKEnabled(), current.topK(),
                current.topPEnabled(), current.topP(),
                current.minPEnabled(), current.minP(),
                current.presencePenaltyEnabled(), current.presencePenalty(),
                current.frequencyPenaltyEnabled(), current.frequencyPenalty(),
                enabled, current.repetitionPenalty());
    }

    public static AppAutoCardsSettings withCandidateSelectionMode(AppAutoCardsSettings current, String mode)
    {
        return new AppAutoCardsSettings(current.cooldownTurns(), current.maxCardsPerRun(),
                current.candidateWindow(), current.cardLengthLimit(), current.summarizeInsteadOfTrim(),
                current.useBulletedLists(), mode, current.contextMode());
    }

    public static AppAutoCardsSettings withContextMode(AppAutoCardsSettings current, String mode)
    {
        return new AppAutoCardsSettings(current.cooldownTurns(), current.maxCardsPerRun(),
                current.candidateWindow(), current.cardLengthLimit(), current.summarizeInsteadOfTrim(),
                current.useBulletedLists(), current.candidateSelectionMode(), mode);
    }

    public static AppAutoCardsSettings withUseBulletedLists(AppAutoCardsSettings current, boolean value)
    {
        return new AppAutoCardsSettings(current.cooldownTurns(), current.maxCardsPerRun(),
                current.candidateWindow(), current.cardLengthLimit(), current.summarizeInsteadOfTrim(),
                value, current.candidateSelectionMode(), current.contextMode());
    }

    public static AppAutoCardsSettings withCooldownTurns(AppAutoCardsSettings current, int value)
    {
        int capped = Math.max(0, value);
        return new AppAutoCardsSettings(capped, current.maxCardsPerRun(),
                current.candidateWindow(), current.cardLengthLimit(), current.summarizeInsteadOfTrim(),
                current.useBulletedLists(), current.candidateSelectionMode(), current.contextMode());
    }

    public static AppAutoCardsSettings withMaxCardsPerRun(AppAutoCardsSettings current, int value)
    {
        int capped = Math.max(1, value);
        return new AppAutoCardsSettings(current.cooldownTurns(), capped,
                current.candidateWindow(), current.cardLengthLimit(), current.summarizeInsteadOfTrim(),
                current.useBulletedLists(), current.candidateSelectionMode(), current.contextMode());
    }

    public static AppAutoCardsSettings withCandidateWindow(AppAutoCardsSettings current, int value)
    {
        int capped = Math.max(1, value);
        return new AppAutoCardsSettings(current.cooldownTurns(), current.maxCardsPerRun(),
                capped, current.cardLengthLimit(), current.summarizeInsteadOfTrim(),
                current.useBulletedLists(), current.candidateSelectionMode(), current.contextMode());
    }

    public static AppAutoCardsSettings withCardLengthLimit(AppAutoCardsSettings current, int value)
    {
        int capped = Math.max(1, value);
        return new AppAutoCardsSettings(current.cooldownTurns(), current.maxCardsPerRun(),
                current.candidateWindow(), capped, current.summarizeInsteadOfTrim(),
                current.useBulletedLists(), current.candidateSelectionMode(), current.contextMode());
    }

    public static AppAutoCardsSettings withSummarizeInsteadOfTrim(AppAutoCardsSettings current, boolean value)
    {
        return new AppAutoCardsSettings(current.cooldownTurns(), current.maxCardsPerRun(),
                current.candidateWindow(), current.cardLengthLimit(), value,
                current.useBulletedLists(), current.candidateSelectionMode(), current.contextMode());
    }

    public static StoryAutoCardsSettings withEnabled(StoryAutoCardsSettings current, boolean value)
    {
        return new StoryAutoCardsSettings(current.storyId(), value, current.updateExisting(),
                current.createNew(), current.pinNew(), current.previewFirst());
    }

    public static StoryAutoCardsSettings withUpdateExisting(StoryAutoCardsSettings current, boolean value)
    {
        return new StoryAutoCardsSettings(current.storyId(), current.enabled(), value,
                current.createNew(), current.pinNew(), current.previewFirst());
    }

    public static StoryAutoCardsSettings withCreateNew(StoryAutoCardsSettings current, boolean value)
    {
        return new StoryAutoCardsSettings(current.storyId(), current.enabled(), current.updateExisting(),
                value, current.pinNew(), current.previewFirst());
    }

    public static StoryAutoCardsSettings withPinNew(StoryAutoCardsSettings current, boolean value)
    {
        return new StoryAutoCardsSettings(current.storyId(), current.enabled(), current.updateExisting(),
                current.createNew(), value, current.previewFirst());
    }

    public static StoryAutoCardsSettings withPreviewFirst(StoryAutoCardsSettings current, boolean value)
    {
        return new StoryAutoCardsSettings(current.storyId(), current.enabled(), current.updateExisting(),
                current.createNew(), current.pinNew(), value);
    }

    public static ModelAutoCardsSettings withPrompts(ModelAutoCardsSettings current, String createPrompt, String updatePrompt,
            String summarizePrompt)
    {
        return new ModelAutoCardsSettings(current.modelName(), createPrompt, updatePrompt, summarizePrompt,
                current.maxTokensCreate(), current.maxTokensUpdate(), current.maxTokensSummarize());
    }

    public static ModelAutoCardsSettings withTokenCaps(ModelAutoCardsSettings current, int createTokens, int updateTokens,
            int summarizeTokens)
    {
        return new ModelAutoCardsSettings(current.modelName(), current.createPrompt(), current.updatePrompt(),
                current.summarizePrompt(), createTokens, updateTokens, summarizeTokens);
    }
}
