package com.llamaquill.generation;

import com.llamaquill.db.BlockRepository;
import com.llamaquill.db.StoryCardRepository;
import com.llamaquill.model.Block;
import com.llamaquill.model.ChatMessage;
import com.llamaquill.model.ConversationLayout;
import com.llamaquill.model.GenerationSettings;
import com.llamaquill.model.Story;
import com.llamaquill.model.StoryCard;
import com.llamaquill.prompt.PromptAuxiliaryInput;

import java.util.List;
import java.util.Objects;

public final class StoryPromptCoordinator
{
    private final BlockRepository blockRepository;
    private final StoryCardRepository storyCardRepository;
    private final AuxiliaryGenerationService auxiliaryGenerationService;

    public StoryPromptCoordinator(BlockRepository blockRepository, StoryCardRepository storyCardRepository,
            AuxiliaryGenerationService auxiliaryGenerationService)
    {
        this.blockRepository = Objects.requireNonNull(blockRepository, "blockRepository");
        this.storyCardRepository = Objects.requireNonNull(storyCardRepository, "storyCardRepository");
        this.auxiliaryGenerationService = Objects.requireNonNull(
                auxiliaryGenerationService, "auxiliaryGenerationService");
    }

    public AuxiliaryGenerationService.Result generateResponse(
            Story story, String systemPrompt, String userPrompt, GenerationSettings settings) throws Exception
    {
        return generateResponse(story, systemPrompt, userPrompt, settings, false, false);
    }

    public AuxiliaryGenerationService.Result generateResponse(
            Story story, String systemPrompt, String userPrompt, GenerationSettings settings,
            boolean overrideNumPredict, boolean forceRoleAwareTurns) throws Exception
    {
        Objects.requireNonNull(story, "story");
        Objects.requireNonNull(settings, "settings");

        List<Block> currentBlocks = blockRepository.listForStory(story.id());
        List<StoryCard> currentCards = storyCardRepository.listForStory(story.id());
        PromptAuxiliaryInput input = new PromptAuxiliaryInput(
                List.of(
                        new ChatMessage("system", systemPrompt),
                        new ChatMessage("user", userPrompt)),
                "",
                null);
        GenerationSettings promptSettings = overrideNumPredict ? settings.withoutNumPredict() : settings;
        if (forceRoleAwareTurns)
        {
            promptSettings = promptSettings.withConversationLayout(ConversationLayout.ROLE_AWARE);
        }
        return auxiliaryGenerationService.generate(story, currentBlocks, currentCards, promptSettings, input);
    }
}
