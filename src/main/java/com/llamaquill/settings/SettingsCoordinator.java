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
        return new AppSettings(current.ollamaUrl(), modelName, current.contextLimit(),
                current.responseLength(), current.minStoryWindow(), current.storyCardLookback(),
                current.anPlacement());
    }

    public static AppSettings withOllamaUrl(AppSettings current, String url)
    {
        return new AppSettings(url.trim(), current.selectedModel(), current.contextLimit(),
                current.responseLength(), current.minStoryWindow(), current.storyCardLookback(),
                current.anPlacement());
    }

    public static AppSettings withContextLimit(AppSettings current, int value, int minStoryPercent)
    {
        int capped = Math.max(1024, Math.min(32768, value));
        int percent = Math.max(10, Math.min(100, minStoryPercent));
        int minWindow = (int) Math.round(capped * (percent / 100.0));
        return new AppSettings(current.ollamaUrl(), current.selectedModel(), capped,
                current.responseLength(), minWindow, current.storyCardLookback(), current.anPlacement());
    }

    public static AppSettings withResponseLength(AppSettings current, int value)
    {
        int capped = Math.max(1, Math.min(250, value));
        return new AppSettings(current.ollamaUrl(), current.selectedModel(), current.contextLimit(), capped,
                current.minStoryWindow(), current.storyCardLookback(), current.anPlacement());
    }

    public static AppSettings withMinStoryPercent(AppSettings current, int percent)
    {
        int capped = Math.max(10, Math.min(100, percent));
        int minWindow = (int) Math.round(current.contextLimit() * (capped / 100.0));
        return new AppSettings(current.ollamaUrl(), current.selectedModel(), current.contextLimit(),
                current.responseLength(), minWindow, current.storyCardLookback(), current.anPlacement());
    }

    public static AppSettings withStoryCardLookback(AppSettings current, int value)
    {
        return new AppSettings(current.ollamaUrl(), current.selectedModel(), current.contextLimit(),
                current.responseLength(), current.minStoryWindow(), value, current.anPlacement());
    }

    public static AppSettings withAnPlacement(AppSettings current, int value)
    {
        return new AppSettings(current.ollamaUrl(), current.selectedModel(), current.contextLimit(),
                current.responseLength(), current.minStoryWindow(), current.storyCardLookback(), value);
    }

    public static ModelSettings withTemperature(ModelSettings current, double value)
    {
        return new ModelSettings(current.modelName(), current.active(), value,
                current.topK(), current.topP(), current.minP(), current.presencePenalty(),
                current.frequencyPenalty(), current.repetitionPenalty());
    }

    public static ModelSettings withTopK(ModelSettings current, int value)
    {
        return new ModelSettings(current.modelName(), current.active(), current.temperature(),
                value, current.topP(), current.minP(), current.presencePenalty(),
                current.frequencyPenalty(), current.repetitionPenalty());
    }

    public static ModelSettings withTopP(ModelSettings current, double value)
    {
        return new ModelSettings(current.modelName(), current.active(), current.temperature(),
                current.topK(), value, current.minP(), current.presencePenalty(),
                current.frequencyPenalty(), current.repetitionPenalty());
    }

    public static ModelSettings withMinP(ModelSettings current, double value)
    {
        return new ModelSettings(current.modelName(), current.active(), current.temperature(),
                current.topK(), current.topP(), value, current.presencePenalty(),
                current.frequencyPenalty(), current.repetitionPenalty());
    }

    public static ModelSettings withPresencePenalty(ModelSettings current, double value)
    {
        return new ModelSettings(current.modelName(), current.active(), current.temperature(),
                current.topK(), current.topP(), current.minP(), value,
                current.frequencyPenalty(), current.repetitionPenalty());
    }

    public static ModelSettings withFrequencyPenalty(ModelSettings current, double value)
    {
        return new ModelSettings(current.modelName(), current.active(), current.temperature(),
                current.topK(), current.topP(), current.minP(), current.presencePenalty(),
                value, current.repetitionPenalty());
    }

    public static ModelSettings withRepetitionPenalty(ModelSettings current, double value)
    {
        return new ModelSettings(current.modelName(), current.active(), current.temperature(),
                current.topK(), current.topP(), current.minP(), current.presencePenalty(),
                current.frequencyPenalty(), value);
    }

    public static AppAutoCardsSettings withCandidateSelectionMode(AppAutoCardsSettings current, String mode)
    {
        return new AppAutoCardsSettings(current.cooldownTurns(), current.maxCardsPerRun(),
                current.candidateWindow(), current.cardLengthLimit(), current.summarizeInsteadOfTrim(),
                mode);
    }

    public static AppAutoCardsSettings withCooldownTurns(AppAutoCardsSettings current, int value)
    {
        int capped = Math.max(0, value);
        return new AppAutoCardsSettings(capped, current.maxCardsPerRun(),
                current.candidateWindow(), current.cardLengthLimit(), current.summarizeInsteadOfTrim(),
                current.candidateSelectionMode());
    }

    public static AppAutoCardsSettings withMaxCardsPerRun(AppAutoCardsSettings current, int value)
    {
        int capped = Math.max(1, value);
        return new AppAutoCardsSettings(current.cooldownTurns(), capped,
                current.candidateWindow(), current.cardLengthLimit(), current.summarizeInsteadOfTrim(),
                current.candidateSelectionMode());
    }

    public static AppAutoCardsSettings withCandidateWindow(AppAutoCardsSettings current, int value)
    {
        int capped = Math.max(1, value);
        return new AppAutoCardsSettings(current.cooldownTurns(), current.maxCardsPerRun(),
                capped, current.cardLengthLimit(), current.summarizeInsteadOfTrim(),
                current.candidateSelectionMode());
    }

    public static AppAutoCardsSettings withCardLengthLimit(AppAutoCardsSettings current, int value)
    {
        int capped = Math.max(1, value);
        return new AppAutoCardsSettings(current.cooldownTurns(), current.maxCardsPerRun(),
                current.candidateWindow(), capped, current.summarizeInsteadOfTrim(),
                current.candidateSelectionMode());
    }

    public static AppAutoCardsSettings withSummarizeInsteadOfTrim(AppAutoCardsSettings current, boolean value)
    {
        return new AppAutoCardsSettings(current.cooldownTurns(), current.maxCardsPerRun(),
                current.candidateWindow(), current.cardLengthLimit(), value,
                current.candidateSelectionMode());
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
