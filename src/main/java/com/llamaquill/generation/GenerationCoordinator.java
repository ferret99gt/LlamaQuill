package com.llamaquill.generation;

import com.llamaquill.db.BlockRepository;
import com.llamaquill.db.StoryCardRepository;
import com.llamaquill.db.StoryRepository;
import com.llamaquill.model.Block;
import com.llamaquill.model.GenerationSettings;
import com.llamaquill.model.Role;
import com.llamaquill.model.Story;
import com.llamaquill.model.StoryCard;
import com.llamaquill.prompt.PromptCompilation;
import com.llamaquill.prompt.PromptCompiler;
import com.llamaquill.serviceClients.OllamaClient;
import com.llamaquill.util.Ids;
import com.llamaquill.util.Timestamps;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class GenerationCoordinator
{
    private final BlockRepository blockRepository;
    private final StoryRepository storyRepository;
    private final StoryCardRepository storyCardRepository;
    private final PromptCompiler promptCompiler;
    private final OllamaClient ollamaClient;

    public GenerationCoordinator(BlockRepository blockRepository, StoryRepository storyRepository,
            StoryCardRepository storyCardRepository, PromptCompiler promptCompiler, OllamaClient ollamaClient)
    {
        this.blockRepository = Objects.requireNonNull(blockRepository, "blockRepository");
        this.storyRepository = Objects.requireNonNull(storyRepository, "storyRepository");
        this.storyCardRepository = Objects.requireNonNull(storyCardRepository, "storyCardRepository");
        this.promptCompiler = Objects.requireNonNull(promptCompiler, "promptCompiler");
        this.ollamaClient = Objects.requireNonNull(ollamaClient, "ollamaClient");
    }

    public ContinueResult continueStory(Story story, GenerationSettings settings, AutoCardsRunner autoCardsRunner)
            throws Exception
    {
        Objects.requireNonNull(story, "story");
        Objects.requireNonNull(settings, "settings");

        List<Block> currentBlocks = blockRepository.listForStory(story.id());
        List<StoryCard> currentCards = storyCardRepository.listForStory(story.id());
        if (autoCardsRunner != null && autoCardsRunner.run(currentBlocks, currentCards))
        {
            currentCards = storyCardRepository.listForStory(story.id());
        }

        PromptCompilation compilation = promptCompiler.compile(story, currentBlocks, currentCards, settings);
        String cleaned = generateContinuationWithFallback(compilation.prompt(), settings);
        if (cleaned.isBlank())
        {
            return new ContinueResult(story, null, compilation.estimatedTokens());
        }

        int position = blockRepository.nextPosition(story.id());
        Block block = new Block(Ids.newId(), story.id(), Role.ASSISTANT, cleaned, Timestamps.now(), position);
        blockRepository.insert(block);

        Story updatedStory = touchStory(story);
        return new ContinueResult(updatedStory, block, compilation.estimatedTokens());
    }

    public RetryResult retryAssistantHead(Story story, List<Block> blocks, Block head, GenerationSettings settings)
            throws IOException, InterruptedException, SQLException
    {
        Objects.requireNonNull(story, "story");
        Objects.requireNonNull(blocks, "blocks");
        Objects.requireNonNull(head, "head");
        Objects.requireNonNull(settings, "settings");

        List<Block> promptBlocks = new ArrayList<>(blocks);
        promptBlocks.removeLast();
        List<StoryCard> currentCards = storyCardRepository.listForStory(story.id());

        PromptCompilation compilation = promptCompiler.compile(story, promptBlocks, currentCards, settings);
        String cleaned = generateContinuationWithFallback(compilation.prompt(), settings);
        if (cleaned.isBlank())
        {
            return new RetryResult(null, compilation.estimatedTokens());
        }

        Block updated = new Block(head.id(), head.storyId(), Role.ASSISTANT, cleaned, Timestamps.now(), head.position());
        blockRepository.replaceHead(updated);
        return new RetryResult(updated, compilation.estimatedTokens());
    }

    public TurnResult takeTurn(Story story, String userText, GenerationSettings settings, AutoCardsRunner autoCardsRunner)
            throws Exception
    {
        Objects.requireNonNull(story, "story");
        Objects.requireNonNull(userText, "userText");
        Objects.requireNonNull(settings, "settings");

        List<Block> currentBlocks = blockRepository.listForStory(story.id());
        List<StoryCard> currentCards = storyCardRepository.listForStory(story.id());
        if (autoCardsRunner != null && autoCardsRunner.run(currentBlocks, currentCards))
        {
            currentCards = storyCardRepository.listForStory(story.id());
        }

        boolean isFirstTurn = currentBlocks.isEmpty();
        int position = blockRepository.nextPosition(story.id());
        Role seedRole = isFirstTurn ? Role.ASSISTANT : Role.USER;
        Block seedBlock = new Block(Ids.newId(), story.id(), seedRole, userText, Timestamps.now(), position);
        blockRepository.insert(seedBlock);

        currentBlocks = blockRepository.listForStory(story.id());
        currentCards = storyCardRepository.listForStory(story.id());

        PromptCompilation compilation = promptCompiler.compile(story, currentBlocks, currentCards, settings);
        String response = ollamaClient.generate(compilation.prompt(), settings);
        String cleaned = normalizeOutput(response);
        if (cleaned.isBlank())
        {
            return new TurnResult(story, false, compilation.estimatedTokens());
        }

        int assistantPosition = blockRepository.nextPosition(story.id());
        Block assistantBlock = new Block(Ids.newId(), story.id(), Role.ASSISTANT, cleaned, Timestamps.now(), assistantPosition);
        blockRepository.insert(assistantBlock);

        Story updatedStory = touchStory(story);
        return new TurnResult(updatedStory, true, compilation.estimatedTokens());
    }

    private Story touchStory(Story story) throws SQLException
    {
        String now = Timestamps.now();
        Story updatedStory = new Story(story.id(), story.title(), story.systemPrompt(), story.plotEssentials(),
                story.authorNote(), story.createdAt(), now);
        storyRepository.update(updatedStory);
        return updatedStory;
    }

    private String generateContinuationWithFallback(String prompt, GenerationSettings settings)
            throws IOException, InterruptedException
    {
        String cleaned = normalizeOutput(ollamaClient.generate(prompt, settings));
        if (!cleaned.isBlank())
        {
            return cleaned;
        }

        String withSpace = prompt + " ";
        cleaned = normalizeOutput(ollamaClient.generate(withSpace, settings));
        if (!cleaned.isBlank())
        {
            System.out.println("Continuation fallback succeeded with trailing space.");
            return " " + cleaned;
        }

        String withNewline = prompt + "\n";
        cleaned = normalizeOutput(ollamaClient.generate(withNewline, settings));
        if (!cleaned.isBlank())
        {
            System.out.println("Continuation fallback succeeded with trailing newline.");
            return "\n" + cleaned;
        }
        return cleaned;
    }

    private static String normalizeOutput(String output)
    {
        if (output == null)
        {
            return "";
        }
        return output.replace("\r\n", "\n").replace("\r", "\n");
    }

    @FunctionalInterface
    public interface AutoCardsRunner
    {
        boolean run(List<Block> currentBlocks, List<StoryCard> currentCards) throws Exception;
    }

    public record ContinueResult(Story updatedStory, Block block, int estimatedPromptTokens)
    {
    }

    public record RetryResult(Block updatedBlock, int estimatedPromptTokens)
    {
    }

    public record TurnResult(Story updatedStory, boolean generated, int estimatedPromptTokens)
    {
    }
}
