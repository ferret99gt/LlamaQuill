package com.llamaquill.autocards;

import com.llamaquill.db.BlockRepository;
import com.llamaquill.db.StoryCardRepository;
import com.llamaquill.model.AppAutoCardsSettings;
import com.llamaquill.model.AppSettings;
import com.llamaquill.model.Block;
import com.llamaquill.model.ModelAutoCardsSettings;
import com.llamaquill.model.ModelSettings;
import com.llamaquill.model.Role;
import com.llamaquill.model.Story;
import com.llamaquill.model.StoryAutoCardsSettings;
import com.llamaquill.model.StoryCard;
import com.llamaquill.util.Ids;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AutoCardsCoordinator
{
    private final BlockRepository blockRepository;
    private final StoryCardRepository storyCardRepository;
    private final AutoCardsService autoCardsService;
    private final Map<String, AutoCardsRunState> runStateByStory = new HashMap<>();

    public AutoCardsCoordinator(BlockRepository blockRepository, StoryCardRepository storyCardRepository,
            AutoCardsService autoCardsService)
    {
        this.blockRepository = Objects.requireNonNull(blockRepository, "blockRepository");
        this.storyCardRepository = Objects.requireNonNull(storyCardRepository, "storyCardRepository");
        this.autoCardsService = Objects.requireNonNull(autoCardsService, "autoCardsService");
    }

    public RunResult runIfNeeded(Story story, List<Block> currentBlocks, List<StoryCard> currentCards, boolean manual,
            AppSettings appSettings, StoryAutoCardsSettings storySettings, AppAutoCardsSettings appAutoCardsSettings,
            ModelSettings modelSettings, ModelAutoCardsSettings modelAutoCardsSettings, PreviewCallbacks previewCallbacks)
            throws IOException, InterruptedException, SQLException
    {
        Objects.requireNonNull(story, "story");
        Objects.requireNonNull(appSettings, "appSettings");
        Objects.requireNonNull(storySettings, "storySettings");
        Objects.requireNonNull(appAutoCardsSettings, "appAutoCardsSettings");
        Objects.requireNonNull(modelSettings, "modelSettings");
        Objects.requireNonNull(modelAutoCardsSettings, "modelAutoCardsSettings");

        if (!manual && !storySettings.enabled())
        {
            return new RunResult(0, 0, false);
        }
        if (currentBlocks == null || currentBlocks.isEmpty())
        {
            return new RunResult(0, 0, false);
        }
        if (!storySettings.updateExisting() && !storySettings.createNew())
        {
            return new RunResult(0, 0, false);
        }
        if (!manual && !passesCooldown(story.id(), currentBlocks, appAutoCardsSettings.cooldownTurns()))
        {
            return new RunResult(0, 0, false);
        }

        String excerpt = buildExcerpt(currentBlocks, appAutoCardsSettings.candidateWindow());
        if (excerpt.isBlank())
        {
            updateRunState(story.id(), currentBlocks);
            return new RunResult(0, 0, true);
        }

        AutoCardsService.PromptContext fullStoryContext = AutoCardsService.PromptContext.empty();
        if (AutoCards.CONTEXT_MODE_FULL_STORY.equals(
                AutoCards.normalizeContextMode(appAutoCardsSettings.contextMode())))
        {
            fullStoryContext = autoCardsService.buildFullStoryContext(
                    story,
                    currentBlocks,
                    currentCards,
                    appSettings,
                    modelSettings,
                    modelAutoCardsSettings);
        }

        List<AutoCards.Candidate> candidates = extractCandidates(
                excerpt,
                currentCards,
                appAutoCardsSettings.maxCardsPerRun(),
                appAutoCardsSettings,
                appSettings,
                modelSettings,
                modelAutoCardsSettings);
        if (candidates.isEmpty())
        {
            updateRunState(story.id(), currentBlocks);
            return new RunResult(0, 0, true);
        }

        Map<String, StoryCard> byTitle = new HashMap<>();
        for (StoryCard card : currentCards)
        {
            if (card.title() != null)
            {
                byTitle.put(card.title().trim().toLowerCase(), card);
            }
        }

        int created = 0;
        int updated = 0;
        int limit = appAutoCardsSettings.cardLengthLimit();
        boolean summarize = appAutoCardsSettings.summarizeInsteadOfTrim();
        boolean preview = storySettings.previewFirst();

        for (AutoCards.Candidate candidate : candidates)
        {
            if (candidate.title().isBlank())
            {
                continue;
            }

            String key = candidate.title().trim().toLowerCase();
            StoryCard existing = byTitle.get(key);
            if (existing != null)
            {
                if (!storySettings.updateExisting())
                {
                    continue;
                }

                String updatedContent = autoCardsService.generateCardUpdate(
                        existing,
                        excerpt,
                        fullStoryContext,
                        appAutoCardsSettings.useBulletedLists(),
                        appSettings,
                        modelSettings,
                        modelAutoCardsSettings);
                if (updatedContent.isBlank())
                {
                    continue;
                }

                AutoCardsService.LengthEnforcementResult lengthResult = autoCardsService.enforceCardLengthDetailed(
                        updatedContent,
                        summarize,
                        limit,
                        existing.title(),
                        existing.triggers(),
                        excerpt,
                        fullStoryContext,
                        appAutoCardsSettings.useBulletedLists(),
                        appSettings,
                        modelSettings,
                        modelAutoCardsSettings);
                updatedContent = lengthResult.content();

                if (preview && previewCallbacks != null)
                {
                    String approved = previewCallbacks.previewUpdate().preview(existing, updatedContent,
                            lengthResult.summarized());
                    if (approved == null)
                    {
                        continue;
                    }
                    updatedContent = approved;
                }

                StoryCard updatedCard = new StoryCard(existing.id(), existing.storyId(), existing.title(),
                        existing.triggers(), updatedContent, existing.pinned());
                storyCardRepository.update(updatedCard);
                updated++;
                continue;
            }

            if (!storySettings.createNew())
            {
                continue;
            }

            String content = autoCardsService.generateCardCreate(
                    candidate,
                    excerpt,
                    fullStoryContext,
                    appAutoCardsSettings.useBulletedLists(),
                    appSettings,
                    modelSettings,
                    modelAutoCardsSettings);
            if (content.isBlank())
            {
                continue;
            }

            content = autoCardsService.enforceCardLength(
                    content,
                    summarize,
                    limit,
                    candidate.title(),
                    candidate.triggers(),
                    excerpt,
                    fullStoryContext,
                    appAutoCardsSettings.useBulletedLists(),
                    appSettings,
                    modelSettings,
                    modelAutoCardsSettings);

            StoryCard createdCard = new StoryCard(Ids.newId(), story.id(), candidate.title(), candidate.triggers(),
                    content, storySettings.pinNew());
            if (preview && previewCallbacks != null)
            {
                StoryCard approved = previewCallbacks.previewCreate().preview(createdCard);
                if (approved == null)
                {
                    continue;
                }
                createdCard = approved;
            }

            storyCardRepository.insert(createdCard);
            created++;
        }

        updateRunState(story.id(), currentBlocks);
        return new RunResult(created, updated, true);
    }

    public AutoCards.GeneratedCard generateCardDraftFromPrompt(Story story, String request, AppSettings appSettings,
            AppAutoCardsSettings appAutoCardsSettings, ModelSettings modelSettings,
            ModelAutoCardsSettings modelAutoCardsSettings) throws IOException, InterruptedException, SQLException
    {
        Objects.requireNonNull(story, "story");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(appSettings, "appSettings");
        Objects.requireNonNull(appAutoCardsSettings, "appAutoCardsSettings");
        Objects.requireNonNull(modelSettings, "modelSettings");
        Objects.requireNonNull(modelAutoCardsSettings, "modelAutoCardsSettings");

        List<Block> currentBlocks = blockRepository.listForStory(story.id());
        List<StoryCard> currentCards = storyCardRepository.listForStory(story.id());
        String contextMode = AutoCards.normalizeContextMode(appAutoCardsSettings.contextMode());

        String excerpt = "";
        if (!AutoCards.CONTEXT_MODE_FULL_STORY.equals(contextMode))
        {
            excerpt = buildExcerpt(currentBlocks, appAutoCardsSettings.candidateWindow());
        }

        AutoCardsService.PromptContext fullStoryContext = AutoCardsService.PromptContext.empty();
        if (AutoCards.CONTEXT_MODE_FULL_STORY.equals(contextMode))
        {
            fullStoryContext = autoCardsService.buildFullStoryContext(
                    story,
                    currentBlocks,
                    currentCards,
                    appSettings,
                    modelSettings,
                    modelAutoCardsSettings);
        }

        AutoCards.GeneratedCard generated = autoCardsService.generateCardFromUserPrompt(
                request,
                excerpt,
                fullStoryContext,
                appAutoCardsSettings.useBulletedLists(),
                appSettings,
                modelSettings,
                modelAutoCardsSettings);
        if (generated == null)
        {
            return null;
        }

        String enforced = autoCardsService.enforceCardLength(
                generated.content(),
                appAutoCardsSettings.summarizeInsteadOfTrim(),
                appAutoCardsSettings.cardLengthLimit(),
                generated.title(),
                generated.triggers(),
                excerpt,
                fullStoryContext,
                appAutoCardsSettings.useBulletedLists(),
                appSettings,
                modelSettings,
                modelAutoCardsSettings);
        return new AutoCards.GeneratedCard(generated.title(), generated.triggers(), enforced);
    }

    private List<AutoCards.Candidate> extractCandidates(String excerpt, List<StoryCard> currentCards, int maxCount,
            AppAutoCardsSettings appAutoCardsSettings, AppSettings appSettings, ModelSettings modelSettings,
            ModelAutoCardsSettings modelAutoCardsSettings) throws IOException, InterruptedException
    {
        String mode = AutoCards.normalizeCandidateSelectionMode(appAutoCardsSettings.candidateSelectionMode());
        if (AutoCards.CANDIDATE_SELECTION_MODE_ASK_MODEL.equals(mode))
        {
            return autoCardsService.extractCandidatesByModel(
                    excerpt,
                    currentCards,
                    maxCount,
                    appSettings,
                    modelSettings,
                    modelAutoCardsSettings);
        }
        return AutoCards.extractCandidatesByHeuristics(excerpt, currentCards, maxCount);
    }

    private boolean passesCooldown(String storyId, List<Block> currentBlocks, int cooldownTurns)
    {
        AutoCardsRunState state = runStateByStory.get(storyId);
        if (state == null)
        {
            return true;
        }
        return countAssistantBlocks(currentBlocks) - state.assistantCount >= cooldownTurns;
    }

    private void updateRunState(String storyId, List<Block> currentBlocks)
    {
        runStateByStory.put(storyId, new AutoCardsRunState(countAssistantBlocks(currentBlocks)));
    }

    private static int countAssistantBlocks(List<Block> currentBlocks)
    {
        int count = 0;
        for (Block block : currentBlocks)
        {
            if (block.role() == Role.ASSISTANT)
            {
                count++;
            }
        }
        return count;
    }

    private static String buildExcerpt(List<Block> currentBlocks, int window)
    {
        int start = Math.max(0, currentBlocks.size() - Math.max(1, window));
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < currentBlocks.size(); i++)
        {
            Block block = currentBlocks.get(i);
            if (block.role() != Role.USER && block.role() != Role.ASSISTANT)
            {
                continue;
            }
            sb.append(block.role() == Role.USER ? "User: " : "Story: ");
            sb.append(block.text().trim());
            sb.append("\n\n");
        }
        return sb.toString().trim();
    }

    @FunctionalInterface
    public interface CreatePreview
    {
        StoryCard preview(StoryCard draft);
    }

    @FunctionalInterface
    public interface UpdatePreview
    {
        String preview(StoryCard existing, String proposedContent, boolean summarized);
    }

    public record PreviewCallbacks(CreatePreview previewCreate, UpdatePreview previewUpdate)
    {
    }

    public record RunResult(int created, int updated, boolean ran)
    {
    }

    private record AutoCardsRunState(int assistantCount)
    {
    }
}
