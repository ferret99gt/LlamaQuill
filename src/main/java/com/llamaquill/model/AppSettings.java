package com.llamaquill.model;

import com.llamaquill.serviceClients.ComfyUiClient;
import com.llamaquill.serviceClients.OllamaClient;
import com.llamaquill.serviceClients.OllamaEndpoint;

public record AppSettings(String ollamaUrl, String comfyUiUrl, String selectedModel,
        boolean responseLengthEnabled, int responseLength, int minStoryPercent, int storyCardLookback,
        String comfyWorkflow, int comfyDimension, ImageRatio comfyRatio, int comfyBatchSize,
        boolean comfySaveImages, int ollamaKeepAliveMinutes, String selectedStoryCardCommandPresetId)
{
    public static final String DEFAULT_COMFY_WORKFLOW = "ChromaHD";
    public static final int MIN_RESPONSE_LENGTH = 1;
    public static final int MAX_RESPONSE_LENGTH = 1000;
    public static final int DEFAULT_COMFY_DIMENSION = 720;
    public static final ImageRatio DEFAULT_COMFY_RATIO = ImageRatio.SQUARE;
    public static final int DEFAULT_COMFY_BATCH_SIZE = 4;
    public static final int MIN_STORY_PERCENT = 10;
    public static final int MAX_STORY_PERCENT = 100;
    public static final int DEFAULT_OLLAMA_KEEP_ALIVE_MINUTES = 5;
    public static final int MIN_OLLAMA_KEEP_ALIVE_MINUTES = 5;
    public static final int MAX_OLLAMA_KEEP_ALIVE_MINUTES = 30;
    public static final String DEFAULT_STORY_CARD_COMMAND_PRESET_ID = "builtin:condensed";

    public AppSettings
    {
        ollamaUrl = OllamaEndpoint.normalizeOrDefault(ollamaUrl);
        comfyUiUrl = comfyUiUrl == null || comfyUiUrl.isBlank() ? ComfyUiClient.DEFAULT_HOST : comfyUiUrl.trim();
        selectedModel = selectedModel == null || selectedModel.isBlank()
                ? OllamaClient.DEFAULT_MODEL
                : selectedModel.trim();
        responseLength = Math.max(MIN_RESPONSE_LENGTH, Math.min(MAX_RESPONSE_LENGTH, responseLength));
        minStoryPercent = Math.max(MIN_STORY_PERCENT, Math.min(MAX_STORY_PERCENT, minStoryPercent));
        storyCardLookback = Math.max(0, Math.min(100, storyCardLookback));
        comfyWorkflow = comfyWorkflow == null || comfyWorkflow.isBlank()
                ? DEFAULT_COMFY_WORKFLOW
                : comfyWorkflow.trim();
        comfyDimension = ImageRatio.normalizeDimension(comfyDimension);
        comfyRatio = comfyRatio == null ? DEFAULT_COMFY_RATIO : comfyRatio;
        comfyBatchSize = Math.max(1, Math.min(32, comfyBatchSize));
        ollamaKeepAliveMinutes = Math.max(MIN_OLLAMA_KEEP_ALIVE_MINUTES,
                Math.min(MAX_OLLAMA_KEEP_ALIVE_MINUTES, ollamaKeepAliveMinutes));
        selectedStoryCardCommandPresetId = selectedStoryCardCommandPresetId == null
                || selectedStoryCardCommandPresetId.isBlank()
                        ? DEFAULT_STORY_CARD_COMMAND_PRESET_ID
                        : selectedStoryCardCommandPresetId.trim();
    }

    public AppSettings(String ollamaUrl, String comfyUiUrl, String selectedModel,
            boolean responseLengthEnabled, int responseLength, int minStoryPercent,
            int storyCardLookback, String comfyWorkflow,
            int comfyDimension, ImageRatio comfyRatio, int comfyBatchSize)
    {
        this(ollamaUrl, comfyUiUrl, selectedModel,
                responseLengthEnabled, responseLength, minStoryPercent,
                storyCardLookback, comfyWorkflow,
                comfyDimension, comfyRatio, comfyBatchSize, false,
                DEFAULT_OLLAMA_KEEP_ALIVE_MINUTES, DEFAULT_STORY_CARD_COMMAND_PRESET_ID);
    }

    public AppSettings(String ollamaUrl, String comfyUiUrl, String selectedModel,
            boolean responseLengthEnabled, int responseLength, int minStoryPercent,
            int storyCardLookback, String comfyWorkflow,
            int comfyDimension, ImageRatio comfyRatio, int comfyBatchSize,
            int ollamaKeepAliveMinutes)
    {
        this(ollamaUrl, comfyUiUrl, selectedModel,
                responseLengthEnabled, responseLength, minStoryPercent,
                storyCardLookback, comfyWorkflow,
                comfyDimension, comfyRatio, comfyBatchSize, false,
                ollamaKeepAliveMinutes, DEFAULT_STORY_CARD_COMMAND_PRESET_ID);
    }

    public AppSettings(String ollamaUrl, String comfyUiUrl, String selectedModel,
            boolean responseLengthEnabled, int responseLength, int minStoryPercent,
            int storyCardLookback, String comfyWorkflow,
            int comfyDimension, ImageRatio comfyRatio, int comfyBatchSize,
            int ollamaKeepAliveMinutes, String selectedStoryCardCommandPresetId)
    {
        this(ollamaUrl, comfyUiUrl, selectedModel,
                responseLengthEnabled, responseLength, minStoryPercent,
                storyCardLookback, comfyWorkflow,
                comfyDimension, comfyRatio, comfyBatchSize, false,
                ollamaKeepAliveMinutes, selectedStoryCardCommandPresetId);
    }

    public static AppSettings defaults()
    {
        GenerationSettings defaults = GenerationSettings.defaults();
        return new AppSettings(OllamaClient.DEFAULT_HOST, ComfyUiClient.DEFAULT_HOST, OllamaClient.DEFAULT_MODEL,
                false, defaults.responseLength(), 60,
                defaults.storyCardLookback(), DEFAULT_COMFY_WORKFLOW,
                DEFAULT_COMFY_DIMENSION, DEFAULT_COMFY_RATIO, DEFAULT_COMFY_BATCH_SIZE, false,
                DEFAULT_OLLAMA_KEEP_ALIVE_MINUTES, DEFAULT_STORY_CARD_COMMAND_PRESET_ID);
    }

    public Builder toBuilder()
    {
        return new Builder(this);
    }

    public static final class Builder
    {
        private String ollamaUrl;
        private String comfyUiUrl;
        private String selectedModel;
        private boolean responseLengthEnabled;
        private int responseLength;
        private int minStoryPercent;
        private int storyCardLookback;
        private String comfyWorkflow;
        private int comfyDimension;
        private ImageRatio comfyRatio;
        private int comfyBatchSize;
        private boolean comfySaveImages;
        private int ollamaKeepAliveMinutes;
        private String selectedStoryCardCommandPresetId;

        private Builder(AppSettings settings)
        {
            ollamaUrl = settings.ollamaUrl();
            comfyUiUrl = settings.comfyUiUrl();
            selectedModel = settings.selectedModel();
            responseLengthEnabled = settings.responseLengthEnabled();
            responseLength = settings.responseLength();
            minStoryPercent = settings.minStoryPercent();
            storyCardLookback = settings.storyCardLookback();
            comfyWorkflow = settings.comfyWorkflow();
            comfyDimension = settings.comfyDimension();
            comfyRatio = settings.comfyRatio();
            comfyBatchSize = settings.comfyBatchSize();
            comfySaveImages = settings.comfySaveImages();
            ollamaKeepAliveMinutes = settings.ollamaKeepAliveMinutes();
            selectedStoryCardCommandPresetId = settings.selectedStoryCardCommandPresetId();
        }

        public Builder ollamaUrl(String value)
        {
            ollamaUrl = value;
            return this;
        }

        public Builder comfyUiUrl(String value)
        {
            comfyUiUrl = value;
            return this;
        }

        public Builder selectedModel(String value)
        {
            selectedModel = value;
            return this;
        }

        public Builder responseLengthEnabled(boolean value)
        {
            responseLengthEnabled = value;
            return this;
        }

        public Builder responseLength(int value)
        {
            responseLength = value;
            return this;
        }

        public Builder minStoryPercent(int value)
        {
            minStoryPercent = value;
            return this;
        }

        public Builder storyCardLookback(int value)
        {
            storyCardLookback = value;
            return this;
        }

        public Builder comfyWorkflow(String value)
        {
            comfyWorkflow = value;
            return this;
        }

        public Builder comfyDimension(int value)
        {
            comfyDimension = value;
            return this;
        }

        public Builder comfyRatio(ImageRatio value)
        {
            comfyRatio = value;
            return this;
        }

        public Builder comfyBatchSize(int value)
        {
            comfyBatchSize = value;
            return this;
        }

        public Builder comfySaveImages(boolean value)
        {
            comfySaveImages = value;
            return this;
        }

        public Builder ollamaKeepAliveMinutes(int value)
        {
            ollamaKeepAliveMinutes = value;
            return this;
        }

        public Builder selectedStoryCardCommandPresetId(String value)
        {
            selectedStoryCardCommandPresetId = value;
            return this;
        }

        public AppSettings build()
        {
            return new AppSettings(ollamaUrl, comfyUiUrl, selectedModel,
                    responseLengthEnabled, responseLength, minStoryPercent,
                    storyCardLookback, comfyWorkflow,
                    comfyDimension, comfyRatio, comfyBatchSize, comfySaveImages, ollamaKeepAliveMinutes,
                    selectedStoryCardCommandPresetId);
        }
    }
}
