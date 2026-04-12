package com.llamaquill.generation;

import com.llamaquill.autocards.AutoCards;
import com.llamaquill.autocards.AutoCardsService;
import com.llamaquill.db.BlockRepository;
import com.llamaquill.db.StoryCardRepository;
import com.llamaquill.model.AppAutoCardsSettings;
import com.llamaquill.model.AppSettings;
import com.llamaquill.model.Block;
import com.llamaquill.model.ModelAutoCardsSettings;
import com.llamaquill.model.ModelSettings;
import com.llamaquill.model.Role;
import com.llamaquill.model.Story;
import com.llamaquill.model.StoryCard;

import java.util.List;
import java.util.Objects;

public final class StoryPromptCoordinator
{
    private final BlockRepository blockRepository;
    private final StoryCardRepository storyCardRepository;
    private final AutoCardsService autoCardsService;

    public StoryPromptCoordinator(BlockRepository blockRepository, StoryCardRepository storyCardRepository,
            AutoCardsService autoCardsService)
    {
        this.blockRepository = Objects.requireNonNull(blockRepository, "blockRepository");
        this.storyCardRepository = Objects.requireNonNull(storyCardRepository, "storyCardRepository");
        this.autoCardsService = Objects.requireNonNull(autoCardsService, "autoCardsService");
    }

    public String generateResponse(Story story, String systemPrompt, String userPrompt, AppSettings appSettings,
            AppAutoCardsSettings appAutoCardsSettings, ModelSettings modelSettings,
            ModelAutoCardsSettings modelAutoCardsSettings) throws Exception
    {
        Objects.requireNonNull(story, "story");
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

        return autoCardsService.generateOneShotResponse(
                systemPrompt,
                userPrompt,
                excerpt,
                fullStoryContext,
                appSettings,
                modelSettings);
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
}
