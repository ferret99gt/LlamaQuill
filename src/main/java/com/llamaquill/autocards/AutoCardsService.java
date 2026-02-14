package com.llamaquill.autocards;

import com.llamaquill.model.AppSettings;
import com.llamaquill.model.Block;
import com.llamaquill.model.GenerationSettings;
import com.llamaquill.model.ModelAutoCardsSettings;
import com.llamaquill.model.ModelSettings;
import com.llamaquill.model.Story;
import com.llamaquill.model.StoryCard;
import com.llamaquill.ollama.OllamaClient;
import com.llamaquill.prompt.PromptCompilation;
import com.llamaquill.prompt.PromptCompiler;

import java.io.IOException;
import java.util.List;

public class AutoCardsService
{
    private final OllamaClient ollamaClient;
    private final PromptCompiler promptCompiler;

    public AutoCardsService(OllamaClient ollamaClient, PromptCompiler promptCompiler)
    {
        this.ollamaClient = ollamaClient;
        this.promptCompiler = promptCompiler;
    }

    public String buildFullStoryPrompt(Story story, List<Block> blocks, List<StoryCard> storyCards,
            AppSettings appSettings, ModelSettings modelSettings, ModelAutoCardsSettings modelAutoCardsSettings)
    {
        int tokenCap = Math.max(modelAutoCardsSettings.maxTokensCreate(),
                Math.max(modelAutoCardsSettings.maxTokensUpdate(), modelAutoCardsSettings.maxTokensSummarize()));
        GenerationSettings contextSettings = buildGenerationSettings(appSettings, modelSettings, tokenCap);
        PromptCompilation compiled = promptCompiler.compile(story, blocks, storyCards, contextSettings);
        return compiled.prompt();
    }

    public List<AutoCards.Candidate> extractCandidatesByModel(String excerpt, List<StoryCard> currentCards, int maxCount,
            AppSettings appSettings, ModelSettings modelSettings, ModelAutoCardsSettings modelAutoCardsSettings)
            throws IOException, InterruptedException
    {
        StringBuilder existing = new StringBuilder();
        for (StoryCard card : currentCards)
        {
            if (card.title() == null || card.title().isBlank())
            {
                continue;
            }
            existing.append("- ").append(card.title().trim());
            if (!card.triggers().isBlank())
            {
                existing.append(" (").append(card.triggers()).append(")");
            }
            existing.append("\n");
        }

        String system = "You are a JSON generator. Respond with a JSON array only.";
        String user = "# Story excerpt:\n" + excerpt + "\n\n# Existing cards:\n" + existing
                + "\n\n# Task:\nIdentify up to " + maxCount
                + " story card candidates that should either be added as new story cards to track, or are existing story cards that need updates for new details. Story cards are meant to detail one singular character, location or critical object that are important to the story. They should not attempt act as a summarization of the story, combine topics (Character + Event), or as 'memories'. Do not suggest a story card candidate for the main character/player. If no candidates need added or removed, return empty. Return JSON array of objects with "
                + "\"title\" and \"triggers\" (comma separated keywords). No extra text.";

        String prompt = AutoCards.buildChatPrompt(system, user);
        int tokenCap = Math.max(128, modelAutoCardsSettings.maxTokensCreate());
        GenerationSettings autoSettings = buildGenerationSettings(appSettings, modelSettings, tokenCap);
        String response = ollamaClient.generate(prompt, autoSettings);
        return AutoCards.parseCandidatesFromModelResponse(response, maxCount);
    }

    public String generateCardCreate(AutoCards.Candidate candidate, String excerpt, String fullStoryPromptPrefix,
            AppSettings appSettings, ModelSettings modelSettings, ModelAutoCardsSettings modelAutoCardsSettings)
            throws IOException, InterruptedException
    {
        AutoCards.PromptParts promptParts = AutoCards.buildPromptParts(
                modelAutoCardsSettings.createPrompt(),
                candidate.title(),
                candidate.triggers(),
                "",
                excerpt);
        String prompt = buildAutoCardPrompt(promptParts, fullStoryPromptPrefix);
        GenerationSettings autoSettings = buildGenerationSettings(
                appSettings,
                modelSettings,
                modelAutoCardsSettings.maxTokensCreate());
        return normalizeOutput(ollamaClient.generate(prompt, autoSettings));
    }

    public String generateCardUpdate(StoryCard existing, String excerpt, String fullStoryPromptPrefix,
            AppSettings appSettings, ModelSettings modelSettings, ModelAutoCardsSettings modelAutoCardsSettings)
            throws IOException, InterruptedException
    {
        AutoCards.PromptParts promptParts = AutoCards.buildPromptParts(
                modelAutoCardsSettings.updatePrompt(),
                existing.title(),
                existing.triggers(),
                existing.content(),
                excerpt);
        String prompt = buildAutoCardPrompt(promptParts, fullStoryPromptPrefix);
        GenerationSettings autoSettings = buildGenerationSettings(
                appSettings,
                modelSettings,
                modelAutoCardsSettings.maxTokensUpdate());
        return normalizeOutput(ollamaClient.generate(prompt, autoSettings));
    }

    public String generateCardSummary(String title, String triggers, String content, String excerpt,
            String fullStoryPromptPrefix, AppSettings appSettings, ModelSettings modelSettings,
            ModelAutoCardsSettings modelAutoCardsSettings)
            throws IOException, InterruptedException
    {
        AutoCards.PromptParts promptParts = AutoCards.buildPromptParts(
                modelAutoCardsSettings.summarizePrompt(),
                title,
                triggers,
                content,
                excerpt);
        String prompt = buildAutoCardPrompt(promptParts, fullStoryPromptPrefix);
        GenerationSettings autoSettings = buildGenerationSettings(
                appSettings,
                modelSettings,
                modelAutoCardsSettings.maxTokensSummarize());
        return normalizeOutput(ollamaClient.generate(prompt, autoSettings));
    }

    public String enforceCardLength(String content, boolean summarize, int limit, String title, String triggers,
            String excerpt, String fullStoryPromptPrefix, AppSettings appSettings, ModelSettings modelSettings,
            ModelAutoCardsSettings modelAutoCardsSettings)
            throws IOException, InterruptedException
    {
        if (limit <= 0 || content.length() <= limit)
        {
            return content;
        }
        if (summarize)
        {
            String summarized = generateCardSummary(title, triggers, content, excerpt, fullStoryPromptPrefix,
                    appSettings, modelSettings, modelAutoCardsSettings);
            if (!summarized.isBlank())
            {
                if (summarized.length() <= limit)
                {
                    return summarized;
                }
                return AutoCards.truncateContent(summarized, limit);
            }
        }
        return AutoCards.truncateContent(content, limit);
    }

    private String buildAutoCardPrompt(AutoCards.PromptParts promptParts, String fullStoryPromptPrefix)
    {
        String autoCardPrompt = AutoCards.buildChatPrompt(promptParts);
        if (fullStoryPromptPrefix == null || fullStoryPromptPrefix.isBlank())
        {
            return autoCardPrompt;
        }
        String trimmedPrefix = fullStoryPromptPrefix.trim();
        StringBuilder combined = new StringBuilder(trimmedPrefix);
        if (!trimmedPrefix.endsWith("<|im_end|>"))
        {
            combined.append("<|im_end|>");
        }
        combined.append('\n').append(autoCardPrompt);
        return combined.toString();
    }

    private static GenerationSettings buildGenerationSettings(AppSettings appSettings, ModelSettings modelSettings, int maxTokens)
    {
        return new GenerationSettings(appSettings.contextLimit(), maxTokens, modelSettings.temperature(),
                modelSettings.topK(), modelSettings.topP(), modelSettings.minP(), modelSettings.presencePenalty(),
                modelSettings.frequencyPenalty(), modelSettings.repetitionPenalty(), appSettings.minStoryWindow(),
                appSettings.storyCardLookback(), appSettings.anPlacement());
    }

    private static String normalizeOutput(String output)
    {
        if (output == null)
        {
            return "";
        }
        String normalized = output.replace("\r\n", "\n").trim();
        while (normalized.contains("\n\n\n"))
        {
            normalized = normalized.replace("\n\n\n", "\n\n");
        }
        return normalized;
    }
}
