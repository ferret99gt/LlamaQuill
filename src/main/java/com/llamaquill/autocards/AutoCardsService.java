package com.llamaquill.autocards;

import com.llamaquill.model.AppSettings;
import com.llamaquill.model.Block;
import com.llamaquill.model.ChatMessage;
import com.llamaquill.model.GenerationSettings;
import com.llamaquill.model.ModelAutoCardsSettings;
import com.llamaquill.model.ModelSettings;
import com.llamaquill.model.Story;
import com.llamaquill.model.StoryCard;
import com.llamaquill.prompt.PromptCompilation;
import com.llamaquill.prompt.PromptCompiler;
import com.llamaquill.serviceClients.OllamaClient;

import java.io.IOException;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AutoCardsService
{
    public record LengthEnforcementResult(String content, boolean summarized)
    {
    }

    public record PromptContext(List<ChatMessage> messages)
    {
        public PromptContext
        {
            messages = messages == null ? List.of() : List.copyOf(messages);
        }

        public static PromptContext empty()
        {
            return new PromptContext(List.of());
        }
    }

    private record ModelRequest(List<ChatMessage> messages)
    {
        private ModelRequest
        {
            messages = messages == null ? List.of() : List.copyOf(messages);
        }
    }

    private final OllamaClient ollamaClient;
    private final PromptCompiler promptCompiler;

    public AutoCardsService(OllamaClient ollamaClient, PromptCompiler promptCompiler)
    {
        this.ollamaClient = ollamaClient;
        this.promptCompiler = promptCompiler;
    }

    public PromptContext buildFullStoryContext(Story story, List<Block> blocks, List<StoryCard> storyCards,
            AppSettings appSettings, ModelSettings modelSettings, ModelAutoCardsSettings modelAutoCardsSettings)
    {
        int tokenCap = Math.max(modelAutoCardsSettings.maxTokensCreate(),
                Math.max(modelAutoCardsSettings.maxTokensUpdate(), modelAutoCardsSettings.maxTokensSummarize()));
        GenerationSettings contextSettings = buildGenerationSettings(appSettings, modelSettings, tokenCap);
        PromptCompilation compiled = promptCompiler.compile(story, blocks, storyCards, contextSettings);
        return new PromptContext(compiled.messages());
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

        int tokenCap = Math.max(128, modelAutoCardsSettings.maxTokensCreate());
        GenerationSettings autoSettings = buildGenerationSettings(appSettings, modelSettings, tokenCap);
        ModelRequest request = new ModelRequest(AutoCards.buildChatMessages(system, user));
        String response = generateModelResponse(request, autoSettings);
        return AutoCards.parseCandidatesFromModelResponse(response, maxCount);
    }

    public String generateCardCreate(AutoCards.Candidate candidate, String excerpt, PromptContext fullStoryContext,
            boolean useBulletedLists,
            AppSettings appSettings, ModelSettings modelSettings, ModelAutoCardsSettings modelAutoCardsSettings)
            throws IOException, InterruptedException
    {
        AutoCards.PromptParts promptParts = AutoCards.buildPromptParts(
                modelAutoCardsSettings.createPrompt(),
                candidate.title(),
                candidate.triggers(),
                "",
                excerpt);
        ModelRequest request = buildAutoCardRequest(promptParts, fullStoryContext);
        GenerationSettings autoSettings = buildGenerationSettings(
                appSettings,
                modelSettings,
                modelAutoCardsSettings.maxTokensCreate());
        String generated = normalizeOutput(generateModelResponse(request, autoSettings));
        return formatAsBulletedList(generated, useBulletedLists);
    }

    public String generateCardUpdate(StoryCard existing, String excerpt, PromptContext fullStoryContext,
            boolean useBulletedLists,
            AppSettings appSettings, ModelSettings modelSettings, ModelAutoCardsSettings modelAutoCardsSettings)
            throws IOException, InterruptedException
    {
        AutoCards.PromptParts promptParts = AutoCards.buildPromptParts(
                modelAutoCardsSettings.updatePrompt(),
                existing.title(),
                existing.triggers(),
                existing.content(),
                excerpt);
        ModelRequest request = buildAutoCardRequest(promptParts, fullStoryContext);
        GenerationSettings autoSettings = buildGenerationSettings(
                appSettings,
                modelSettings,
                modelAutoCardsSettings.maxTokensUpdate());
        String continuation = normalizeOutput(generateModelResponse(request, autoSettings));
        continuation = formatAsBulletedList(continuation, useBulletedLists);
        if (continuation.isBlank())
        {
            return "";
        }
        String merged = appendCardContinuation(existing.content(), continuation, useBulletedLists);
        if (normalizeOutput(existing.content()).equals(merged))
        {
            return "";
        }
        return merged;
    }

    public String generateCardSummary(String title, String triggers, String content, String excerpt,
            PromptContext fullStoryContext, boolean useBulletedLists, AppSettings appSettings, ModelSettings modelSettings,
            ModelAutoCardsSettings modelAutoCardsSettings)
            throws IOException, InterruptedException
    {
        AutoCards.PromptParts promptParts = AutoCards.buildPromptParts(
                modelAutoCardsSettings.summarizePrompt(),
                title,
                triggers,
                content,
                excerpt);
        ModelRequest request = buildAutoCardRequest(promptParts, fullStoryContext);
        GenerationSettings autoSettings = buildGenerationSettings(
                appSettings,
                modelSettings,
                modelAutoCardsSettings.maxTokensSummarize());
        String generated = normalizeOutput(generateModelResponse(request, autoSettings));
        return formatAsBulletedList(generated, useBulletedLists);
    }

    public AutoCards.GeneratedCard generateCardFromUserPrompt(String request, String excerpt, PromptContext fullStoryContext,
            boolean useBulletedLists, AppSettings appSettings, ModelSettings modelSettings,
            ModelAutoCardsSettings modelAutoCardsSettings) throws IOException, InterruptedException
    {
        String trimmedRequest = request == null ? "" : request.trim();
        if (trimmedRequest.isBlank())
        {
            return null;
        }

        StringBuilder user = new StringBuilder();
        if (excerpt != null && !excerpt.isBlank())
        {
            user.append("# Story excerpt:\n")
                    .append(excerpt.trim())
                    .append("\n\n");
        }
        user.append("# Request:\n")
                .append(trimmedRequest)
                .append("\n\n")
                .append("# Output:\n")
                .append("Return one JSON object only with keys: title, triggers, content.")
                .append("The title should be the name of the character, location, etc. Triggers must be comma-separated keywords, and should start with the name (break first and last names apart for characters) followed by unique uncommon descriptors.");

        String system = "You create one grounded story card from the request and context. "
                + "Return strict JSON only.";
        ModelRequest requestModel = buildAutoCardRequest(new AutoCards.PromptParts(system, user.toString(), ""),
                fullStoryContext);
        GenerationSettings autoSettings = buildGenerationSettings(
                appSettings,
                modelSettings,
                modelAutoCardsSettings.maxTokensCreate());
        String response = generateModelResponse(requestModel, autoSettings);
        AutoCards.GeneratedCard parsed = AutoCards.parseGeneratedCardFromModelResponse(response);
        if (parsed == null)
        {
            return null;
        }
        String content = formatAsBulletedList(normalizeOutput(parsed.content()), useBulletedLists);
        if (content.isBlank())
        {
            return null;
        }
        return new AutoCards.GeneratedCard(parsed.title().trim(), parsed.triggers().trim(), content);
    }

    public String generateImagePromptFromUserPrompt(String request, String excerpt, PromptContext fullStoryContext,
            AppSettings appSettings, ModelSettings modelSettings, ModelAutoCardsSettings modelAutoCardsSettings)
            throws IOException, InterruptedException
    {
        String trimmedRequest = request == null ? "" : request.trim();
        /*
        if (trimmedRequest.isBlank())
        {
            return "";
        }
        */

        StringBuilder user = new StringBuilder();
        if (excerpt != null && !excerpt.isBlank())
        {
            user.append("# Story excerpt:\n")
                    .append(excerpt.trim())
                    .append("\n\n");
        }
        String userPrompt = """
                Your job is to generate a prompt for an image generator that describes the most recent scene in the story. The prompt must describe each of the important subjects (gender, age, hair, eyes, build, clothing, and other visible details), what they are doing (eating, walking, talking, holding an object, using a weapon, etc), and where they are doing it (describe the room and theme, such as "an opulent castle bedroom during the morning").
                """;
        user.append(userPrompt);
        if(!trimmedRequest.isBlank())
        {
            user.append("\n\n# User specific request:\n")
                .append(trimmedRequest);
        }
        String system = "You create image generation prompts for story scenes.";
        ModelRequest requestModel = buildAutoCardRequest(new AutoCards.PromptParts(system, user.toString(), ""),
                fullStoryContext);
        GenerationSettings autoSettings = buildGenerationSettings(
                appSettings,
                modelSettings,
                modelAutoCardsSettings.maxTokensCreate());
        return normalizeOutput(generateModelResponse(requestModel, autoSettings));
    }

    public String generateOneShotResponse(String systemPrompt, String userPrompt, String excerpt,
            PromptContext fullStoryContext, AppSettings appSettings, ModelSettings modelSettings)
            throws IOException, InterruptedException
    {
        ModelRequest request = buildOneShotRequest(systemPrompt, userPrompt, excerpt, fullStoryContext);
        GenerationSettings oneShotSettings = buildUnboundedGenerationSettings(appSettings, modelSettings);
        return normalizePromptResponse(generateModelResponse(request, oneShotSettings));
    }

    public String enforceCardLength(String content, boolean summarize, int limit, String title, String triggers,
            String excerpt, PromptContext fullStoryContext, boolean useBulletedLists, AppSettings appSettings,
            ModelSettings modelSettings,
            ModelAutoCardsSettings modelAutoCardsSettings)
            throws IOException, InterruptedException
    {
        return enforceCardLengthDetailed(content, summarize, limit, title, triggers, excerpt, fullStoryContext,
                useBulletedLists,
                appSettings, modelSettings, modelAutoCardsSettings).content();
    }

    public LengthEnforcementResult enforceCardLengthDetailed(String content, boolean summarize, int limit, String title,
            String triggers, String excerpt, PromptContext fullStoryContext, boolean useBulletedLists,
            AppSettings appSettings,
            ModelSettings modelSettings, ModelAutoCardsSettings modelAutoCardsSettings)
            throws IOException, InterruptedException
    {
        if (limit <= 0 || content.length() <= limit)
        {
            return new LengthEnforcementResult(content, false);
        }
        if (summarize)
        {
            String summarized = generateCardSummary(title, triggers, content, excerpt, fullStoryContext,
                    useBulletedLists,
                    appSettings, modelSettings, modelAutoCardsSettings);
            if (!summarized.isBlank())
            {
                if (summarized.length() <= limit)
                {
                    return new LengthEnforcementResult(summarized, true);
                }
                return new LengthEnforcementResult(AutoCards.truncateContent(summarized, limit), true);
            }
        }
        return new LengthEnforcementResult(AutoCards.truncateContent(content, limit), false);
    }

    private String generateModelResponse(ModelRequest request, GenerationSettings settings)
            throws IOException, InterruptedException
    {
        return ollamaClient.chat(request.messages(), settings).content();
    }

    private ModelRequest buildAutoCardRequest(AutoCards.PromptParts promptParts, PromptContext fullStoryContext)
    {
        List<ChatMessage> messages = new ArrayList<>(fullStoryContext == null ? List.of() : fullStoryContext.messages());
        messages.addAll(AutoCards.buildChatMessages(promptParts));
        return new ModelRequest(messages);
    }

    private static GenerationSettings buildGenerationSettings(AppSettings appSettings, ModelSettings modelSettings, int maxTokens)
    {
        int contextLimit = modelSettings.contextLimit();
        int protectedStoryTokens = (int) Math.round(contextLimit * (appSettings.minStoryPercent() / 100.0));
        return new GenerationSettings(modelSettings.modelName(), appSettings.ollamaUrl(),
                contextLimit, modelSettings.promptTokenScale(),
                true, maxTokens,
                modelSettings.temperatureEnabled(), modelSettings.temperature(),
                modelSettings.topKEnabled(), modelSettings.topK(),
                modelSettings.topPEnabled(), modelSettings.topP(),
                modelSettings.minPEnabled(), modelSettings.minP(),
                modelSettings.presencePenaltyEnabled(), modelSettings.presencePenalty(),
                modelSettings.frequencyPenaltyEnabled(), modelSettings.frequencyPenalty(),
                modelSettings.repetitionPenaltyEnabled(), modelSettings.repetitionPenalty(),
                protectedStoryTokens, appSettings.storyCardLookback(), appSettings.anPlacement());
    }

    private static GenerationSettings buildUnboundedGenerationSettings(AppSettings appSettings, ModelSettings modelSettings)
    {
        int contextLimit = modelSettings.contextLimit();
        int protectedStoryTokens = (int) Math.round(contextLimit * (appSettings.minStoryPercent() / 100.0));
        return new GenerationSettings(modelSettings.modelName(), appSettings.ollamaUrl(),
                contextLimit, modelSettings.promptTokenScale(),
                false, appSettings.responseLength(),
                modelSettings.temperatureEnabled(), modelSettings.temperature(),
                modelSettings.topKEnabled(), modelSettings.topK(),
                modelSettings.topPEnabled(), modelSettings.topP(),
                modelSettings.minPEnabled(), modelSettings.minP(),
                modelSettings.presencePenaltyEnabled(), modelSettings.presencePenalty(),
                modelSettings.frequencyPenaltyEnabled(), modelSettings.frequencyPenalty(),
                modelSettings.repetitionPenaltyEnabled(), modelSettings.repetitionPenalty(),
                protectedStoryTokens, appSettings.storyCardLookback(), appSettings.anPlacement());
    }

    private static ModelRequest buildOneShotRequest(String systemPrompt, String userPrompt, String excerpt,
            PromptContext fullStoryContext)
    {
        List<ChatMessage> messages = new ArrayList<>(fullStoryContext == null ? List.of() : fullStoryContext.messages());
        appendMessage(messages, "system", systemPrompt);
        appendMessage(messages, "user", excerpt == null || excerpt.isBlank() ? "" : "# Story excerpt:\n" + excerpt);
        appendMessage(messages, "user", userPrompt);
        return new ModelRequest(messages);
    }

    private static void appendMessage(List<ChatMessage> messages, String role, String content)
    {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isBlank())
        {
            return;
        }
        messages.add(new ChatMessage(role, normalized));
    }

    private static String normalizePromptResponse(String output)
    {
        if (output == null)
        {
            return "";
        }
        return output.replace("\r\n", "\n").trim();
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

    private static String appendCardContinuation(String existingContent, String continuation, boolean useBulletedLists)
    {
        String existing = existingContent == null ? "" : existingContent;
        String continuationText = continuation == null ? "" : continuation;
        if (continuationText.isBlank())
        {
            return normalizeOutput(existing);
        }
        if (existing.isBlank())
        {
            return continuationText;
        }
        if (useBulletedLists)
        {
            StringBuilder mergedBullets = new StringBuilder(existing);
            if (!existing.endsWith("\n"))
            {
                mergedBullets.append('\n');
            }
            mergedBullets.append(continuationText);
            return normalizeOutput(mergedBullets.toString());
        }

        StringBuilder merged = new StringBuilder(existing);
        char last = merged.charAt(merged.length() - 1);
        char first = continuationText.charAt(0);
        boolean needsSpace = !Character.isWhitespace(last)
                && !Character.isWhitespace(first)
                && ",.;:!?)]}\"'".indexOf(first) < 0;
        if (needsSpace)
        {
            merged.append(' ');
        }
        merged.append(continuationText);
        return normalizeOutput(merged.toString());
    }

    private static String formatAsBulletedList(String text, boolean useBulletedLists)
    {
        String normalized = normalizeOutput(text);
        if (!useBulletedLists || normalized.isBlank())
        {
            return normalized;
        }

        List<String> lines = new ArrayList<>();
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.US);
        iterator.setText(normalized);
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next())
        {
            String sentence = normalized.substring(start, end).trim();
            if (sentence.isBlank())
            {
                continue;
            }
            sentence = sentence.replaceFirst("^[\\-•*]\\s+", "").trim();
            if (!sentence.isBlank())
            {
                lines.add("- " + sentence);
            }
        }

        if (lines.isEmpty())
        {
            return "- " + normalized.replaceFirst("^[\\-•*]\\s+", "").trim();
        }
        return String.join("\n", lines);
    }
}
