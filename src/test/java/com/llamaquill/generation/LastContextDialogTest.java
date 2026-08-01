package com.llamaquill.generation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.llamaquill.model.ChatMessage;
import com.llamaquill.serviceClients.OllamaChatRequestSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

class LastContextDialogTest
{
    @Test
    void formatsTheExactMessageListWithoutAddingViewerHeadingsToContent()
    {
        OllamaChatRequestSnapshot snapshot = new OllamaChatRequestSnapshot(
                "http://localhost:11434/api/chat", "equinox:Q6_K", true,
                List.of(
                        new ChatMessage("system", "Write vivid prose."),
                        new ChatMessage("user", "World Lore:\n[Mia is watchful.]"),
                        new ChatMessage("assistant", "Mia waits.")));

        String formatted = LastContextDialog.format(snapshot);

        assertTrue(formatted.contains("Model: equinox:Q6_K"));
        assertTrue(formatted.contains("Endpoint: http://localhost:11434/api/chat"));
        assertTrue(formatted.contains("Streaming: true"));
        assertTrue(formatted.contains("viewer-only"));
        assertTrue(formatted.contains("MESSAGE 1: SYSTEM"));
        assertTrue(formatted.contains("Write vivid prose."));
        assertTrue(formatted.contains("MESSAGE 2: USER"));
        assertTrue(formatted.contains("World Lore:\n[Mia is watchful.]"));
        assertTrue(formatted.contains("MESSAGE 3: ASSISTANT"));
        assertTrue(formatted.contains("Mia waits."));
    }
}
