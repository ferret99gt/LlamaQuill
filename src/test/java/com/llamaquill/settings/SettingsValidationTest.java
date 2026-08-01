package com.llamaquill.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.llamaquill.model.AppSettings;
import com.llamaquill.model.ModelSettings;
import org.junit.jupiter.api.Test;

class SettingsValidationTest
{
    @Test
    void clampsPersistedModelValuesWhileRetainingValidZeros()
    {
        ModelSettings settings = new ModelSettings(
                "  model-name  ", true,
                Integer.MAX_VALUE, Double.NaN,
                true, -1.0,
                true, -20,
                true, -0.5,
                true, 0.0,
                true, Double.POSITIVE_INFINITY,
                true, -20.0,
                true, 20.0,
                true, Integer.MAX_VALUE,
                true, -3.0);

        assertEquals("model-name", settings.modelName());
        assertEquals(ModelSettings.MAX_CONTEXT_LIMIT, settings.contextLimit());
        assertEquals(1.0, settings.promptTokenScale());
        assertEquals(0.0, settings.temperature());
        assertEquals(0, settings.topK());
        assertEquals(0.0, settings.topP());
        assertEquals(0.0, settings.minP());
        assertEquals(1.0, settings.typicalP());
        assertEquals(-2.0, settings.presencePenalty());
        assertEquals(2.0, settings.frequencyPenalty());
        assertEquals(ModelSettings.MAX_CONTEXT_LIMIT, settings.repeatLastN());
        assertEquals(0.0, settings.repetitionPenalty());
    }

    @Test
    void modelCopiesPreserveContextCalibrationAndOptionEnablement()
    {
        ModelSettings original = ModelSettings.defaults("model-name");
        original = SettingsCoordinator.withContextLimit(original, 131072);
        original = SettingsCoordinator.withPromptTokenScale(original, 1.4);
        original = SettingsCoordinator.withTemperatureEnabled(original, true);
        original = SettingsCoordinator.withTypicalPEnabled(original, true);
        original = SettingsCoordinator.withTypicalP(original, 0.73);
        original = SettingsCoordinator.withRepeatLastNEnabled(original, true);
        original = SettingsCoordinator.withRepeatLastN(original, -1);

        assertEquals(original, original.toBuilder().build());
        ModelSettings updated = SettingsCoordinator.withTemperature(original, 0.0);

        assertEquals(131072, updated.contextLimit());
        assertEquals(1.4, updated.promptTokenScale());
        assertTrue(updated.temperatureEnabled());
        assertEquals(0.0, updated.temperature());
        assertTrue(updated.typicalPEnabled());
        assertEquals(0.73, updated.typicalP());
        assertTrue(updated.repeatLastNEnabled());
        assertEquals(-1, updated.repeatLastN());
        assertFalse(updated.topKEnabled());
    }

    @Test
    void promptCalibrationIgnoresSubPercentMeasurementJitter()
    {
        ModelSettings original = SettingsCoordinator.withPromptTokenScale(
                ModelSettings.defaults("model-name"), 1.25);

        ModelSettings calibrated = SettingsCoordinator.calibratePromptTokenScale(
                original, 44_443, 44_465);

        assertTrue(calibrated == original);
        assertEquals(1.25, calibrated.promptTokenScale());
    }

    @Test
    void promptCalibrationAppliesMeaningfulEstimateErrorInOneStep()
    {
        ModelSettings original = ModelSettings.defaults("model-name");

        ModelSettings calibrated = SettingsCoordinator.calibratePromptTokenScale(
                original, 5_000, 6_000);

        assertEquals(1.2, calibrated.promptTokenScale(), 0.000_001);
    }

    @Test
    void promptCalibrationIgnoresSamplesSmallerThanHalfTheContextWindow()
    {
        ModelSettings original = ModelSettings.defaults("model-name");

        ModelSettings calibrated = SettingsCoordinator.calibratePromptTokenScale(
                original, 3_000, 3_600);

        assertTrue(calibrated == original);
        assertEquals(1.0, calibrated.promptTokenScale());
    }

    @Test
    void appSettingsClampGlobalRangesAndNormalizeTheOllamaEndpoint()
    {
        AppSettings settings = new AppSettings(
                "http://localhost:11434///",
                "",
                "",
                false,
                Integer.MAX_VALUE,
                2,
                -10,
                500,
                "",
                1,
                99999,
                0,
                100);

        assertEquals("http://localhost:11434", settings.ollamaUrl());
        assertEquals(32768, settings.responseLength());
        assertEquals(AppSettings.MIN_STORY_PERCENT, settings.minStoryPercent());
        assertEquals(0, settings.storyCardLookback());
        assertEquals(100, settings.anPlacement());
        assertEquals(64, settings.comfyWidth());
        assertEquals(4096, settings.comfyHeight());
        assertEquals(1, settings.comfyBatchSize());
        assertEquals(AppSettings.MAX_OLLAMA_KEEP_ALIVE_MINUTES, settings.ollamaKeepAliveMinutes());
        assertEquals(settings, settings.toBuilder().build());
        assertEquals(AppSettings.MAX_OLLAMA_KEEP_ALIVE_MINUTES,
                SettingsCoordinator.withComfyWidth(settings, 800).ollamaKeepAliveMinutes());
    }
}
