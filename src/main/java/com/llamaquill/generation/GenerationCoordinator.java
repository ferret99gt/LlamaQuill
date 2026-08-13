package com.llamaquill.generation;

import com.llamaquill.db.BlockRepository;
import com.llamaquill.db.Database;
import com.llamaquill.db.StoryCardRepository;
import com.llamaquill.db.StoryRepository;
import com.llamaquill.model.Block;
import com.llamaquill.model.ChatMessage;
import com.llamaquill.model.ConversationLayout;
import com.llamaquill.model.GenerationSettings;
import com.llamaquill.model.Role;
import com.llamaquill.model.Story;
import com.llamaquill.model.StoryCard;
import com.llamaquill.prompt.PromptAuxiliaryInput;
import com.llamaquill.prompt.PromptBudget;
import com.llamaquill.prompt.PromptCompilation;
import com.llamaquill.prompt.PromptCompiler;
import com.llamaquill.serviceClients.OllamaChatResult;
import com.llamaquill.serviceClients.OllamaClient;
import com.llamaquill.serviceClients.OllamaContextLimitException;
import com.llamaquill.serviceClients.OllamaException;
import com.llamaquill.util.Ids;
import com.llamaquill.util.Timestamps;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class GenerationCoordinator
{
    static final String CONTINUE_PROMPT = "Continue from last response.";

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

    public ContinueResult continueStory(Story story, GenerationSettings settings)
            throws Exception
    {
        return continueStory(story, settings, GenerationObserver.NOOP);
    }

    public ContinueResult continueStory(Story story, GenerationSettings settings, GenerationObserver observer)
            throws Exception
    {
        Objects.requireNonNull(story, "story");
        Objects.requireNonNull(settings, "settings");
        GenerationObserver activeObserver = observer == null ? GenerationObserver.NOOP : observer;

        List<Block> currentBlocks = blockRepository.listForStory(story.id());
        String expectedHeadId = headId(currentBlocks);
        List<StoryCard> currentCards = storyCardRepository.listForStory(story.id());

        PromptCompilation compilation = compileContinuation(
                story, currentBlocks, currentCards, settings);
        String adjacentAssistantText = flattenedAdjacentAssistantText(currentBlocks, settings);
        GeneratedText generated;
        try
        {
            generated = generateContinuationWithFallback(
                    compilation, settings, activeObserver, adjacentAssistantText);
        }
        catch (OllamaException error)
        {
            OllamaContextLimitException contextError = requireContextLimitError(error);
            compilation = compileContinuationWithinInputLimit(
                    story, currentBlocks, currentCards, settings,
                    correctedInputLimit(compilation, contextError));
            generated = generateContinuationWithFallback(
                    compilation, settings, activeObserver, adjacentAssistantText);
        }
        if (generated.text().isBlank())
        {
            return new ContinueResult(story, null, compilation.estimatedTokens(), ResultStatus.EMPTY,
                    generated.response());
        }

        PromptCompilation completedCompilation = compilation;
        GeneratedText completedGeneration = generated;
        return database.transaction(connection ->
        {
            if (!blockRepository.isCurrentHead(story.id(), expectedHeadId))
            {
                return new ContinueResult(story, null, completedCompilation.estimatedTokens(), ResultStatus.STALE,
                        completedGeneration.response());
            }
            int position = blockRepository.nextPosition(story.id());
            Block block = new Block(Ids.newId(), story.id(), Role.ASSISTANT,
                    completedGeneration.text(), Timestamps.now(), position);
            blockRepository.insert(block);
            Story updatedStory = touchStory(story);
            return new ContinueResult(updatedStory, block, completedCompilation.estimatedTokens(), ResultStatus.APPLIED,
                    completedGeneration.response());
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

        PromptCompilation compilation = compileContinuation(
                story, promptBlocks, currentCards, settings);
        String adjacentAssistantText = flattenedAdjacentAssistantText(promptBlocks, settings);
        GeneratedText generated;
        try
        {
            generated = generateContinuationWithFallback(
                    compilation, settings, activeObserver, adjacentAssistantText);
        }
        catch (OllamaException error)
        {
            OllamaContextLimitException contextError = requireContextLimitError(error);
            compilation = compileContinuationWithinInputLimit(
                    story, promptBlocks, currentCards, settings,
                    correctedInputLimit(compilation, contextError));
            generated = generateContinuationWithFallback(
                    compilation, settings, activeObserver, adjacentAssistantText);
        }
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

    public TurnResult takeTurn(Story story, String userText, GenerationSettings settings)
            throws Exception
    {
        return takeTurn(story, userText, settings, GenerationObserver.NOOP);
    }

    public TurnResult takeTurn(Story story, String userText, GenerationSettings settings,
            GenerationObserver observer)
            throws Exception
    {
        Objects.requireNonNull(story, "story");
        Objects.requireNonNull(userText, "userText");
        Objects.requireNonNull(settings, "settings");
        GenerationObserver activeObserver = observer == null ? GenerationObserver.NOOP : observer;

        List<Block> currentBlocks = blockRepository.listForStory(story.id());
        String expectedHeadId = headId(currentBlocks);
        List<StoryCard> currentCards = storyCardRepository.listForStory(story.id());

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
        String adjacentAssistantText = settings.conversationLayout() == ConversationLayout.FLATTENED
                && seedRole == Role.ASSISTANT
                        ? seedResult.seedBlock().text()
                        : "";
        activeObserver.onAttemptStarted("");
        OllamaChatResult response;
        try
        {
            response = generateResponse(
                    compilation, settings, activeObserver, adjacentAssistantText);
        }
        catch (OllamaException error)
        {
            OllamaContextLimitException contextError = requireContextLimitError(error);
            compilation = promptCompiler.compileWithinInputLimit(
                    story, currentBlocks, currentCards, settings, PromptAuxiliaryInput.none(),
                    correctedInputLimit(compilation, contextError));
            activeObserver.onAttemptStarted("");
            response = generateResponse(
                    compilation, settings, activeObserver, adjacentAssistantText);
        }
        String cleaned = ContinuationJoiner.join(
                adjacentAssistantText, normalizeOutput(response.content()));
        if (cleaned.isBlank())
        {
            return new TurnResult(seedResult.updatedStory(), false, compilation.estimatedTokens(),
                    ResultStatus.EMPTY, response);
        }

        PromptCompilation completedCompilation = compilation;
        OllamaChatResult completedResponse = response;
        return database.transaction(connection ->
        {
            if (!blockRepository.isCurrentHead(story.id(), seedResult.seedBlock().id()))
            {
                return new TurnResult(seedResult.updatedStory(), false, completedCompilation.estimatedTokens(),
                        ResultStatus.STALE, completedResponse);
            }
            int assistantPosition = blockRepository.nextPosition(story.id());
            Block assistantBlock = new Block(Ids.newId(), story.id(), Role.ASSISTANT, cleaned, Timestamps.now(),
                    assistantPosition);
            blockRepository.insert(assistantBlock);
            Story updatedStory = touchStory(seedResult.updatedStory());
            return new TurnResult(updatedStory, true, completedCompilation.estimatedTokens(),
                    ResultStatus.APPLIED, completedResponse);
        });
    }

    private Story touchStory(Story story) throws SQLException
    {
        return storyRepository.touch(story.id(), Timestamps.now());
    }

    private PromptCompilation compileContinuation(Story story, List<Block> blocks,
            List<StoryCard> storyCards, GenerationSettings settings)
    {
        return compileContinuationWithinInputLimit(
                story, blocks, storyCards, settings, Integer.MAX_VALUE);
    }

    private PromptCompilation compileContinuationWithinInputLimit(Story story, List<Block> blocks,
            List<StoryCard> storyCards, GenerationSettings settings, int maximumInputTokens)
    {
        int assistantTailBlockCount = assistantTailBlockCountAfterContinuationCue(blocks);
        PromptAuxiliaryInput auxiliaryInput = new PromptAuxiliaryInput(
                List.of(), "", null,
                assistantTailBlockCount > 0 ? CONTINUE_PROMPT : "",
                assistantTailBlockCount,
                true);
        return promptCompiler.compileWithinInputLimit(
                story, blocks, storyCards, settings, auxiliaryInput, maximumInputTokens);
    }

    private static int correctedInputLimit(PromptCompilation compilation, OllamaContextLimitException error)
            throws OllamaContextLimitException
    {
        PromptBudget budget = compilation.contextReport().budget();
        int correctedInputLimit = budget.correctedInputLimit(
                compilation.estimatedTokens(), error.promptTokens(), error.contextLimit());
        if (correctedInputLimit >= budget.inputLimit())
        {
            throw error;
        }
        return correctedInputLimit;
    }

    private static OllamaContextLimitException requireContextLimitError(OllamaException error)
            throws OllamaException
    {
        OllamaContextLimitException contextError = OllamaContextLimitException.from(error);
        if (contextError == null)
        {
            throw error;
        }
        return contextError;
    }

    private static int assistantTailBlockCountAfterContinuationCue(List<Block> blocks)
    {
        int promptBlockCount = 0;
        for (int index = blocks.size() - 1; index >= 0; index--)
        {
            Block block = blocks.get(index);
            if (block == null || block.role() == Role.IMAGE)
            {
                continue;
            }
            if (block.role() == Role.USER)
            {
                return 0;
            }
            promptBlockCount++;
            if (promptBlockCount > 2)
            {
                return 2;
            }
        }
        return Math.min(2, promptBlockCount);
    }

    private static String flattenedAdjacentAssistantText(List<Block> blocks, GenerationSettings settings)
    {
        if (settings.conversationLayout() != ConversationLayout.FLATTENED || blocks.isEmpty())
        {
            return "";
        }
        Block head = blocks.getLast();
        return head != null && head.role() == Role.ASSISTANT ? head.text() : "";
    }

    private GeneratedText generateContinuationWithFallback(PromptCompilation compilation, GenerationSettings settings,
            GenerationObserver observer, String adjacentAssistantText)
            throws IOException, InterruptedException
    {
        observer.onAttemptStarted("");
        OllamaChatResult response = generateResponse(
                compilation, settings, observer, adjacentAssistantText);
        String cleaned = ContinuationJoiner.join(
                adjacentAssistantText, normalizeOutput(response.content()));
        if (!cleaned.isBlank())
        {
            return new GeneratedText(cleaned, response);
        }

        observer.onAttemptStarted(" ");
        response = generateResponseWithAssistantSuffix(
                compilation, settings, " ", observer);
        cleaned = normalizeOutput(response.content());
        if (!cleaned.isBlank())
        {
            return new GeneratedText(" " + cleaned, response);
        }

        observer.onAttemptStarted("\n");
        response = generateResponseWithAssistantSuffix(
                compilation, settings, "\n", observer);
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
        return generateResponse(compilation, settings, observer, "");
    }

    private OllamaChatResult generateResponse(PromptCompilation compilation, GenerationSettings settings,
            GenerationObserver observer, String adjacentAssistantText)
            throws IOException, InterruptedException
    {
        StreamingOutputNormalizer normalizer = new StreamingOutputNormalizer(observer, adjacentAssistantText);
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
            String suffix, GenerationObserver observer)
            throws IOException, InterruptedException
    {
        StreamingOutputNormalizer normalizer = new StreamingOutputNormalizer(observer, "");
        try
        {
            return ollamaClient.chat(
                    withAssistantSuffix(compilation.messages(), suffix),
                    settings, normalizer::accept);
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
        private final String adjacentAssistantText;
        private boolean pendingCarriageReturn;
        private boolean boundaryDecided;

        private StreamingOutputNormalizer(GenerationObserver observer, String adjacentAssistantText)
        {
            this.observer = observer;
            this.adjacentAssistantText = adjacentAssistantText == null ? "" : adjacentAssistantText;
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
                if (!boundaryDecided)
                {
                    boundaryDecided = true;
                    String separator = ContinuationJoiner.separator(adjacentAssistantText, normalized);
                    if (!separator.isEmpty())
                    {
                        observer.onGeneratedText(separator);
                    }
                }
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
