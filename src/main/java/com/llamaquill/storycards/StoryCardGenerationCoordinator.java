package com.llamaquill.storycards;

import com.llamaquill.db.BlockRepository;
import com.llamaquill.db.StoryCardRepository;
import com.llamaquill.generation.AuxiliaryGenerationService;
import com.llamaquill.model.Block;
import com.llamaquill.model.ChatMessage;
import com.llamaquill.model.ConversationLayout;
import com.llamaquill.model.GenerationSettings;
import com.llamaquill.model.Story;
import com.llamaquill.model.StoryCard;
import com.llamaquill.prompt.PromptAuxiliaryInput;
import com.llamaquill.prompt.PromptCompilation;
import com.llamaquill.serviceClients.OllamaChatResult;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

public final class StoryCardGenerationCoordinator
{
    private final BlockRepository blockRepository;
    private final StoryCardRepository cardRepository;
    private final AuxiliaryGenerationService auxiliaryGenerationService;

    public StoryCardGenerationCoordinator(BlockRepository blockRepository, StoryCardRepository cardRepository,
            AuxiliaryGenerationService auxiliaryGenerationService)
    {
        this.blockRepository = Objects.requireNonNull(blockRepository, "blockRepository");
        this.cardRepository = Objects.requireNonNull(cardRepository, "cardRepository");
        this.auxiliaryGenerationService = Objects.requireNonNull(
                auxiliaryGenerationService, "auxiliaryGenerationService");
    }

    public Result generate(Story story, StoryCardGenerationRequest request, GenerationSettings settings)
            throws Exception
    {
        Objects.requireNonNull(story, "story");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(settings, "settings");

        StoryCard savedCard = loadSavedCard(story.id(), request.existingCardId());
        String resolvedTriggers = request.triggers().isBlank() ? request.title() : request.triggers();
        String renderedCommand = StoryCardCommands.renderCommand(
                request.command(),
                request.title(),
                resolvedTriggers,
                savedCard == null ? "" : savedCard.content());
        String task = buildTask(renderedCommand, request.additionalContext());

        List<Block> blocks = blockRepository.listForStory(story.id());
        List<StoryCard> cards = cardRepository.listForStory(story.id());
        PromptAuxiliaryInput auxiliaryInput = new PromptAuxiliaryInput(
                List.of(new ChatMessage("user", task)),
                request.additionalContext(),
                savedCard);
        GenerationSettings storyCardSettings = settings.withConversationLayout(ConversationLayout.ROLE_AWARE);
        AuxiliaryGenerationService.Result generated = auxiliaryGenerationService.generate(
                story, blocks, cards, storyCardSettings, auxiliaryInput);
        String entry = generated.content();
        if (entry.isBlank())
        {
            throw new IOException("Ollama returned an empty Story Card entry.");
        }
        return new Result(entry, resolvedTriggers, generated.compilation(), generated.response());
    }

    private StoryCard loadSavedCard(String storyId, String cardId) throws Exception
    {
        if (cardId == null || cardId.isBlank())
        {
            return null;
        }
        StoryCard saved = cardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalStateException("The Story Card no longer exists."));
        if (!storyId.equals(saved.storyId()))
        {
            throw new IllegalStateException("The Story Card belongs to a different story.");
        }
        return saved;
    }

    private static String buildTask(String renderedCommand, String additionalContext)
    {
        StringBuilder task = new StringBuilder("# Story Card Command\n").append(renderedCommand);
        if (additionalContext != null && !additionalContext.isBlank())
        {
            task.append("\n\n# Additional Generation Context\n").append(additionalContext.trim());
        }
        return task.toString();
    }

    public record Result(String entry, String resolvedTriggers, PromptCompilation compilation,
            OllamaChatResult response)
    {
        public Result
        {
            entry = entry == null ? "" : entry;
            resolvedTriggers = resolvedTriggers == null ? "" : resolvedTriggers;
            compilation = Objects.requireNonNull(compilation, "compilation");
            response = Objects.requireNonNull(response, "response");
        }
    }
}
