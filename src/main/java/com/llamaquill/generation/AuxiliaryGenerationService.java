package com.llamaquill.generation;

import com.llamaquill.model.Block;
import com.llamaquill.model.GenerationSettings;
import com.llamaquill.model.Story;
import com.llamaquill.model.StoryCard;
import com.llamaquill.prompt.PromptAuxiliaryInput;
import com.llamaquill.prompt.PromptCompilation;
import com.llamaquill.prompt.PromptCompiler;
import com.llamaquill.serviceClients.OllamaChatResult;
import com.llamaquill.serviceClients.OllamaClient;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

public final class AuxiliaryGenerationService
{
    private final PromptCompiler promptCompiler;
    private final OllamaClient ollamaClient;

    public AuxiliaryGenerationService(PromptCompiler promptCompiler, OllamaClient ollamaClient)
    {
        this.promptCompiler = Objects.requireNonNull(promptCompiler, "promptCompiler");
        this.ollamaClient = Objects.requireNonNull(ollamaClient, "ollamaClient");
    }

    public Result generate(Story story, List<Block> blocks, List<StoryCard> storyCards,
            GenerationSettings settings, PromptAuxiliaryInput auxiliaryInput)
            throws IOException, InterruptedException
    {
        PromptCompilation compilation = promptCompiler.compile(
                story, blocks, storyCards, settings, auxiliaryInput);
        OllamaChatResult response = ollamaClient.chatNonStreaming(compilation.messages(), settings);
        String content = response.content() == null ? "" : response.content().strip();
        return new Result(content, compilation, response);
    }

    public record Result(String content, PromptCompilation compilation, OllamaChatResult response)
    {
        public Result
        {
            content = content == null ? "" : content;
            compilation = Objects.requireNonNull(compilation, "compilation");
            response = Objects.requireNonNull(response, "response");
        }
    }
}
