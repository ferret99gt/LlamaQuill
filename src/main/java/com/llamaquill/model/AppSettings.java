package com.llamaquill.model;

import com.llamaquill.serviceClients.ComfyUiClient;
import com.llamaquill.serviceClients.OllamaClient;
import com.llamaquill.serviceClients.OllamaEndpoint;

public record AppSettings(String ollamaUrl, String comfyUiUrl, String selectedModel,
        boolean responseLengthEnabled, int responseLength, int minStoryPercent, int storyCardLookback, int anPlacement,
        String comfyWorkflow, int comfyWidth, int comfyHeight, int comfyBatchSize,
        int ollamaKeepAliveMinutes)
{
    public static final String DEFAULT_COMFY_WORKFLOW = "ChromaHD";
    public static final int DEFAULT_COMFY_WIDTH = 720;
    public static final int DEFAULT_COMFY_HEIGHT = 720;
    public static final int DEFAULT_COMFY_BATCH_SIZE = 4;
    public static final int MIN_STORY_PERCENT = 10;
    public static final int MAX_STORY_PERCENT = 100;
    public static final int DEFAULT_OLLAMA_KEEP_ALIVE_MINUTES = 5;
    public static final int MIN_OLLAMA_KEEP_ALIVE_MINUTES = 5;
    public static final int MAX_OLLAMA_KEEP_ALIVE_MINUTES = 30;

    public AppSettings
    {
        ollamaUrl = OllamaEndpoint.normalizeOrDefault(ollamaUrl);
        comfyUiUrl = comfyUiUrl == null || comfyUiUrl.isBlank() ? ComfyUiClient.DEFAULT_HOST : comfyUiUrl.trim();
        selectedModel = selectedModel == null || selectedModel.isBlank()
                ? OllamaClient.DEFAULT_MODEL
                : selectedModel.trim();
        responseLength = Math.max(1, Math.min(32768, responseLength));
        minStoryPercent = Math.max(MIN_STORY_PERCENT, Math.min(MAX_STORY_PERCENT, minStoryPercent));
        storyCardLookback = Math.max(0, Math.min(100, storyCardLookback));
        anPlacement = Math.max(1, Math.min(100, anPlacement));
        comfyWorkflow = comfyWorkflow == null || comfyWorkflow.isBlank()
                ? DEFAULT_COMFY_WORKFLOW
                : comfyWorkflow.trim();
        comfyWidth = Math.max(64, Math.min(4096, comfyWidth));
        comfyHeight = Math.max(64, Math.min(4096, comfyHeight));
        comfyBatchSize = Math.max(1, Math.min(32, comfyBatchSize));
        ollamaKeepAliveMinutes = Math.max(MIN_OLLAMA_KEEP_ALIVE_MINUTES,
                Math.min(MAX_OLLAMA_KEEP_ALIVE_MINUTES, ollamaKeepAliveMinutes));
    }

    public AppSettings(String ollamaUrl, String comfyUiUrl, String selectedModel,
            boolean responseLengthEnabled, int responseLength, int minStoryPercent,
            int storyCardLookback, int anPlacement, String comfyWorkflow,
            int comfyWidth, int comfyHeight, int comfyBatchSize)
    {
        this(ollamaUrl, comfyUiUrl, selectedModel,
                responseLengthEnabled, responseLength, minStoryPercent,
                storyCardLookback, anPlacement, comfyWorkflow,
                comfyWidth, comfyHeight, comfyBatchSize,
                DEFAULT_OLLAMA_KEEP_ALIVE_MINUTES);
    }

    public static AppSettings defaults()
    {
        GenerationSettings defaults = GenerationSettings.defaults();
        return new AppSettings(OllamaClient.DEFAULT_HOST, ComfyUiClient.DEFAULT_HOST, OllamaClient.DEFAULT_MODEL,
                false, defaults.responseLength(), 60,
                defaults.storyCardLookback(), defaults.anPlacement(), DEFAULT_COMFY_WORKFLOW,
                DEFAULT_COMFY_WIDTH, DEFAULT_COMFY_HEIGHT, DEFAULT_COMFY_BATCH_SIZE,
                DEFAULT_OLLAMA_KEEP_ALIVE_MINUTES);
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
        private int anPlacement;
        private String comfyWorkflow;
        private int comfyWidth;
        private int comfyHeight;
        private int comfyBatchSize;
        private int ollamaKeepAliveMinutes;

        private Builder(AppSettings settings)
        {
            ollamaUrl = settings.ollamaUrl();
            comfyUiUrl = settings.comfyUiUrl();
            selectedModel = settings.selectedModel();
            responseLengthEnabled = settings.responseLengthEnabled();
            responseLength = settings.responseLength();
            minStoryPercent = settings.minStoryPercent();
            storyCardLookback = settings.storyCardLookback();
            anPlacement = settings.anPlacement();
            comfyWorkflow = settings.comfyWorkflow();
            comfyWidth = settings.comfyWidth();
            comfyHeight = settings.comfyHeight();
            comfyBatchSize = settings.comfyBatchSize();
            ollamaKeepAliveMinutes = settings.ollamaKeepAliveMinutes();
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

        public Builder anPlacement(int value)
        {
            anPlacement = value;
            return this;
        }

        public Builder comfyWorkflow(String value)
        {
            comfyWorkflow = value;
            return this;
        }

        public Builder comfyWidth(int value)
        {
            comfyWidth = value;
            return this;
        }

        public Builder comfyHeight(int value)
        {
            comfyHeight = value;
            return this;
        }

        public Builder comfyBatchSize(int value)
        {
            comfyBatchSize = value;
            return this;
        }

        public Builder ollamaKeepAliveMinutes(int value)
        {
            ollamaKeepAliveMinutes = value;
            return this;
        }

        public AppSettings build()
        {
            return new AppSettings(ollamaUrl, comfyUiUrl, selectedModel,
                    responseLengthEnabled, responseLength, minStoryPercent,
                    storyCardLookback, anPlacement, comfyWorkflow,
                    comfyWidth, comfyHeight, comfyBatchSize, ollamaKeepAliveMinutes);
        }
    }
}
