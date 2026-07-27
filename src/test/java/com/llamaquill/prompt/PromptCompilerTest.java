package com.llamaquill.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.llamaquill.model.Block;
import com.llamaquill.model.ChatMessage;
import com.llamaquill.model.GenerationSettings;
import com.llamaquill.model.Role;
import com.llamaquill.model.Story;
import org.junit.jupiter.api.Test;

import java.util.List;

class PromptCompilerTest
{
    @Test
    void compilesEachConsecutiveAssistantRunAsOneChatTurn()
    {
        Story story = new Story("story", "Test", "Write prose.", "", "", "now", "now");
        List<Block> blocks = List.of(
                block("1", Role.ASSISTANT, "The first"),
                block("2", Role.ASSISTANT, " passage."),
                block("3", Role.ASSISTANT, " The second"),
                block("4", Role.ASSISTANT, " passage."),
                block("5", Role.USER, "Continue."),
                block("6", Role.ASSISTANT, "A new"),
                block("7", Role.ASSISTANT, " response."));

        List<ChatMessage> messages = new PromptCompiler()
                .compile(story, blocks, List.of(), GenerationSettings.defaults())
                .messages();

        assertEquals(List.of(
                new ChatMessage("system", "Write prose."),
                new ChatMessage("assistant", "The first passage. The second passage."),
                new ChatMessage("user", "> Continue."),
                new ChatMessage("assistant", "A new response.")),
                messages);
    }

    private static Block block(String id, Role role, String text)
    {
        return new Block(id, "story", role, text, "now", Integer.parseInt(id));
    }
}
