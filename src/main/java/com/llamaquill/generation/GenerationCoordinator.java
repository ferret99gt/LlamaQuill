package com.llamaquill.generation;

import com.llamaquill.db.BlockRepository;
import com.llamaquill.db.Database;
import com.llamaquill.db.StoryCardRepository;
import com.llamaquill.db.StoryRepository;
import com.llamaquill.model.Block;
import com.llamaquill.model.ChatMessage;
import com.llamaquill.model.GenerationSettings;
import com.llamaquill.model.Role;
import com.llamaquill.model.Story;
import com.llamaquill.model.StoryCard;
import com.llamaquill.prompt.PromptCompilation;
import com.llamaquill.prompt.PromptCompiler;
import com.llamaquill.serviceClients.OllamaChatResult;
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
    private final Database database;
    private final BlockRepository blockRepository;
    private final StoryRepository storyRepository;
    private final StoryCardRepository storyCardRepository;
    private final PromptCompiler promptCompiler;
    private final OllamaClient ollamaClient;

    public GenerationCoordinator(Database database, BlockRepository blockRepository, StoryRepository storyRepository,
            StoryCardRepository storyCardRepository, PromptCompiler promptCompiler, OllamaClient ollamaClient)
    {
        this.database = Objects.requireNonNull(database, "database");
        this.blockRepository = Objects.requireNonNull(blockRepository, "blockRepository");
        this.storyRepository = Objects.requireNonNull(storyRepository, "storyRepository");
        this.storyCardRepository = Objects.requireNonNull(storyCardRepository, "storyCardRepository");
        this.promptCompiler = Objects.requireNonNull(promptCompiler, "promptCompiler");
        this.ollamaClient = Objects.requireNonNull(ollamaClient, "ollamaClient");
    }

    public ContinueResult continueStory(Story story, GenerationSettings settings, AutoCardsRunner autoCardsRunner)
            throws Exception
    {
        return continueStory(story, settings, autoCardsRunner, GenerationObserver.NOOP);
    }

    public ContinueResult continueStory(Story story, GenerationSettings settings, AutoCardsRunner autoCardsRunner,
            GenerationObserver observer)
            throws Exception
    {
        Objects.requireNonNull(story, "story");
        Objects.requireNonNull(settings, "settings");
        GenerationObserver activeObserver = observer == null ? GenerationObserver.NOOP : observer;

        List<Block> currentBlocks = blockRepository.listForStory(story.id());
        String expectedHeadId = headId(currentBlocks);
        List<StoryCard> currentCards = storyCardRepository.listForStory(story.id());
        if (autoCardsRunner != null && autoCardsRunner.run(currentBlocks, currentCards))
        {
            currentCards = storyCardRepository.listForStory(story.id());
        }

        PromptCompilation compilation = promptCompiler.compile(story, currentBlocks, currentCards, settings);
        GeneratedText generated = generateContinuationWithFallback(compilation, settings, activeObserver);
        if (generated.text().isBlank())
        {
            return new ContinueResult(story, null, compilation.estimatedTokens(), ResultStatus.EMPTY,
                    generated.response());
        }

        return database.transaction(connection ->
        {
            if (!blockRepository.isCurrentHead(story.id(), expectedHeadId))
            {
                return new ContinueResult(story, null, compilation.estimatedTokens(), ResultStatus.STALE,
                        generated.response());
            }
            int position = blockRepository.nextPosition(story.id());
            Block block = new Block(Ids.newId(), story.id(), Role.ASSISTANT,
                    generated.text(), Timestamps.now(), position);
            blockRepository.insert(block);
            Story updatedStory = touchStory(story);
            return new ContinueResult(updatedStory, block, compilation.estimatedTokens(), ResultStatus.APPLIED,
                    generated.response());
        });
    }

    public RetryResult retryAssistantHead(Story story, List<Block> blocks, Block head, GenerationSettings settings)
            throws IOException, InterruptedException, SQLException
    {
        return retryAssistantHead(story, blocks, head, settings, GenerationObserver.NOOP);
    }

    public RetryResult retryAssistantHead(Story story, List<Block> blocks, Block head, GenerationSettings settings,
            GenerationObserver observer)
            throws IOException, InterruptedException, SQLException
    {
        Objects.requireNonNull(story, "story");
        Objects.requireNonNull(blocks, "blocks");
        Objects.requireNonNull(head, "head");
        Objects.requireNonNull(settings, "settings");
        GenerationObserver activeObserver = observer == null ? GenerationObserver.NOOP : observer;

        List<Block> promptBlocks = new ArrayList<>(blocks);
        promptBlocks.removeLast();
        List<StoryCard> currentCards = storyCardRepository.listForStory(story.id());

        PromptCompilation compilation = promptCompiler.compile(story, promptBlocks, currentCards, settings);
        GeneratedText generated = generateContinuationWithFallback(compilation, settings, activeObserver);
        if (generated.text().isBlank())
        {
            return new RetryResult(null, compilation.estimatedTokens(), ResultStatus.EMPTY, generated.response());
        }

        Block updated = new Block(head.id(), head.storyId(), Role.ASSISTANT,
                generated.text(), Timestamps.now(), head.position());
        boolean applied = database.transaction(connection -> blockRepository.replaceHeadIfCurrent(updated));
        return new RetryResult(applied ? updated : null, compilation.estimatedTokens(),
                applied ? ResultStatus.APPLIED : ResultStatus.STALE, generated.response());
    }

    public TurnResult takeTurn(Story story, String userText, GenerationSettings settings,
            AutoCardsRunner autoCardsRunner)
            throws Exception
    {
        return takeTurn(story, userText, settings, autoCardsRunner, GenerationObserver.NOOP);
    }

    public TurnResult takeTurn(Story story, String userText, GenerationSettings settings,
            AutoCardsRunner autoCardsRunner, GenerationObserver observer)
            throws Exception
    {
        Objects.requireNonNull(story, "story");
        Objects.requireNonNull(userText, "userText");
        Objects.requireNonNull(settings, "settings");
        GenerationObserver activeObserver = observer == null ? GenerationObserver.NOOP : observer;

        List<Block> currentBlocks = blockRepository.listForStory(story.id());
        String expectedHeadId = headId(currentBlocks);
        List<StoryCard> currentCards = storyCardRepository.listForStory(story.id());
        if (autoCardsRunner != null && autoCardsRunner.run(currentBlocks, currentCards))
        {
            currentCards = storyCardRepository.listForStory(story.id());
        }

        boolean isFirstTurn = currentBlocks.isEmpty();
        Role seedRole = isFirstTurn ? Role.ASSISTANT : Role.USER;
        SeedResult seedResult = database.transaction(connection ->
        {
            if (!blockRepository.isCurrentHead(story.id(), expectedHeadId))
            {
                return null;
            }
            int position = blockRepository.nextPosition(story.id());
            Block seedBlock = new Block(Ids.newId(), story.id(), seedRole, userText, Timestamps.now(), position);
            blockRepository.insert(seedBlock);
            return new SeedResult(seedBlock, touchStory(story));
        });
        if (seedResult == null)
        {
            return new TurnResult(story, false, 0, ResultStatus.STALE, null);
        }
        activeObserver.onSeedCommitted(seedResult.seedBlock());

        currentBlocks = blockRepository.listForStory(story.id());
        currentCards = storyCardRepository.listForStory(story.id());

        PromptCompilation compilation = promptCompiler.compile(story, currentBlocks, currentCards, settings);
        activeObserver.onAttemptStarted("");
        OllamaChatResult response = generateResponse(compilation, settings, activeObserver);
        String cleaned = normalizeOutput(response.content());
        if (cleaned.isBlank())
        {
            return new TurnResult(seedResult.updatedStory(), false, compilation.estimatedTokens(),
                    ResultStatus.EMPTY, response);
        }

        return database.transaction(connection ->
        {
            if (!blockRepository.isCurrentHead(story.id(), seedResult.seedBlock().id()))
            {
                return new TurnResult(seedResult.updatedStory(), false, compilation.estimatedTokens(),
                        ResultStatus.STALE, response);
            }
            int assistantPosition = blockRepository.nextPosition(story.id());
            Block assistantBlock = new Block(Ids.newId(), story.id(), Role.ASSISTANT, cleaned, Timestamps.now(),
                    assistantPosition);
            blockRepository.insert(assistantBlock);
            Story updatedStory = touchStory(seedResult.updatedStory());
            return new TurnResult(updatedStory, true, compilation.estimatedTokens(), ResultStatus.APPLIED, response);
        });
    }

    private Story touchStory(Story story) throws SQLException
    {
        return storyRepository.touch(story.id(), Timestamps.now());
    }

    private GeneratedText generateContinuationWithFallback(PromptCompilation compilation, GenerationSettings settings,
            GenerationObserver observer)
            throws IOException, InterruptedException
    {
        observer.onAttemptStarted("");
        OllamaChatResult response = generateResponse(compilation, settings, observer);
        String cleaned = normalizeOutput(response.content());
        if (!cleaned.isBlank())
        {
            return new GeneratedText(cleaned, response);
        }

        observer.onAttemptStarted(" ");
        response = generateResponseWithAssistantSuffix(compilation, settings, " ", observer);
        cleaned = normalizeOutput(response.content());
        if (!cleaned.isBlank())
        {
            return new GeneratedText(" " + cleaned, response);
        }

        observer.onAttemptStarted("\n");
        response = generateResponseWithAssistantSuffix(compilation, settings, "\n", observer);
        cleaned = normalizeOutput(response.content());
        if (!cleaned.isBlank())
        {
            return new GeneratedText("\n" + cleaned, response);
        }
        return new GeneratedText("", response);
    }

    private OllamaChatResult generateResponse(PromptCompilation compilation, GenerationSettings settings,
            GenerationObserver observer)
            throws IOException, InterruptedException
    {
        StreamingOutputNormalizer normalizer = new StreamingOutputNormalizer(observer);
        try
        {
            return ollamaClient.chat(compilation.messages(), settings, normalizer::accept);
        }
        finally
        {
            normalizer.finish();
        }
    }

    private OllamaChatResult generateResponseWithAssistantSuffix(PromptCompilation compilation,
            GenerationSettings settings,
            String suffix, GenerationObserver observer) throws IOException, InterruptedException
    {
        StreamingOutputNormalizer normalizer = new StreamingOutputNormalizer(observer);
        try
        {
            return ollamaClient.chat(withAssistantSuffix(compilation.messages(), suffix), settings, normalizer::accept);
        }
        finally
        {
            normalizer.finish();
        }
    }

    private static List<ChatMessage> withAssistantSuffix(List<ChatMessage> messages, String suffix)
    {
        List<ChatMessage> updated = new ArrayList<>(messages);
        if (updated.isEmpty())
        {
            updated.add(new ChatMessage("assistant", suffix));
            return updated;
        }

        ChatMessage last = updated.getLast();
        if ("assistant".equals(last.role()))
        {
            updated.set(updated.size() - 1, new ChatMessage(last.role(), last.content() + suffix));
            return updated;
        }

        updated.add(new ChatMessage("assistant", suffix));
        return updated;
    }

    private static String normalizeOutput(String output)
    {
        if (output == null)
        {
            return "";
        }
        return output.replace("\r\n", "\n").replace("\r", "\n");
    }

    private static String headId(List<Block> blocks)
    {
        return blocks.isEmpty() ? null : blocks.getLast().id();
    }

    @FunctionalInterface
    public interface AutoCardsRunner
    {
        boolean run(List<Block> currentBlocks, List<StoryCard> currentCards) throws Exception;
    }

    public interface GenerationObserver
    {
        GenerationObserver NOOP = new GenerationObserver() { };

        default void onSeedCommitted(Block seedBlock)
        {
        }

        default void onAttemptStarted(String generatedPrefix)
        {
        }

        default void onGeneratedText(String chunk)
        {
        }
    }

    public enum ResultStatus
    {
        APPLIED,
        EMPTY,
        STALE
    }

    private record SeedResult(Block seedBlock, Story updatedStory)
    {
    }

    private record GeneratedText(String text, OllamaChatResult response)
    {
    }

    private static final class StreamingOutputNormalizer
    {
        private final GenerationObserver observer;
        private boolean pendingCarriageReturn;

        private StreamingOutputNormalizer(GenerationObserver observer)
        {
            this.observer = observer;
        }

        private void accept(String chunk)
        {
            if (chunk == null || chunk.isEmpty())
            {
                return;
            }
            String combined = pendingCarriageReturn ? "\r" + chunk : chunk;
            pendingCarriageReturn = combined.endsWith("\r");
            if (pendingCarriageReturn)
            {
                combined = combined.substring(0, combined.length() - 1);
            }
            String normalized = normalizeOutput(combined);
            if (!normalized.isEmpty())
            {
                observer.onGeneratedText(normalized);
            }
        }

        private void finish()
        {
            if (pendingCarriageReturn)
            {
                pendingCarriageReturn = false;
                observer.onGeneratedText("\n");
            }
        }
    }

    public record ContinueResult(Story updatedStory, Block block, int estimatedPromptTokens, ResultStatus status,
            OllamaChatResult ollamaResponse)
    {
    }

    public record RetryResult(Block updatedBlock, int estimatedPromptTokens, ResultStatus status,
            OllamaChatResult ollamaResponse)
    {
    }

    public record TurnResult(Story updatedStory, boolean generated, int estimatedPromptTokens, ResultStatus status,
            OllamaChatResult ollamaResponse)
    {
    }
}
