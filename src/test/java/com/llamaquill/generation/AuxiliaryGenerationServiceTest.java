package com.llamaquill.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.llamaquill.model.Block;
import com.llamaquill.model.ChatMessage;
import com.llamaquill.model.GenerationSettings;
import com.llamaquill.model.Role;
import com.llamaquill.model.Story;
import com.llamaquill.prompt.PromptAuxiliaryInput;
import com.llamaquill.prompt.PromptCompilation;
import com.llamaquill.prompt.PromptCompiler;
import com.llamaquill.serviceClients.OllamaChatResult;
import com.llamaquill.serviceClients.OllamaClient;
import com.llamaquill.serviceClients.OllamaException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class AuxiliaryGenerationServiceTest
{
    @Test
    void recompilesAndRetriesOnceUsingOllamasMeasuredPromptSize() throws Exception
    {
        PromptCompiler compiler = new PromptCompiler();
        Story story = new Story("story", "Story", "Write prose.", "", "", "now", "now");
        List<Block> blocks = List.of(new Block(
                "block", story.id(), Role.ASSISTANT, "Long imported story. ".repeat(2_000), "now", 1));
        PromptAuxiliaryInput input = new PromptAuxiliaryInput(
                List.of(new ChatMessage("user", "List every character.")), "", null);
        GenerationSettings settings = GenerationSettings.defaults();
        PromptCompilation initial = compiler.compile(story, blocks, List.of(), settings, input);
        ContextRejectingOllamaClient ollama = new ContextRejectingOllamaClient();
        AuxiliaryGenerationService service = new AuxiliaryGenerationService(compiler, ollama);

        AuxiliaryGenerationService.Result result = service.generate(
                story, blocks, List.of(), settings, input);

        assertEquals("Character list", result.content());
        assertEquals(2, ollama.requests.size());
        assertTrue(totalCharacters(ollama.requests.get(1)) < totalCharacters(ollama.requests.get(0)));
        assertTrue(result.compilation().contextReport().budget().inputLimit()
                < initial.contextReport().budget().inputLimit());
        assertTrue(result.compilation().estimatedTokens()
                <= result.compilation().contextReport().budget().inputLimit());
    }

    private static int totalCharacters(List<ChatMessage> messages)
    {
        return messages.stream().mapToInt(message -> message.content().length()).sum();
    }

    private static final class ContextRejectingOllamaClient extends OllamaClient
    {
        private final List<List<ChatMessage>> requests = new ArrayList<>();

        @Override
        public OllamaChatResult chatNonStreaming(List<ChatMessage> messages, GenerationSettings settings)
                throws OllamaException
        {
            requests.add(List.copyOf(messages));
            if (requests.size() == 1)
            {
                String detail = """
                        {"error":{"code":400,"message":"request (8300 tokens) exceeds the available context size (8192 tokens), try increasing it","type":"exceed_context_size_error","n_prompt_tokens":8300,"n_ctx":8192}}
                        """;
                throw new OllamaException(
                        "Ollama returned status 400 for /api/chat: " + detail,
                        "http://localhost:11434/api/chat", 400, detail);
            }
            return new OllamaChatResult(
                    settings.modelName(), " Character list ", 7_900, 12, "stop", 1, 1, 1, 1, 0);
        }
    }
}
