package com.llamaquill.serviceClients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.llamaquill.model.ChatMessage;
import com.llamaquill.model.GenerationSettings;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

class OllamaClientOptionsTest
{
    @Test
    void omitsDisabledModelOptionsSoOllamaDefaultsCanApply()
    {
        OllamaClient client = new OllamaClient("http://localhost:11434", "test-model");
        JSONObject payload = new JSONObject(client.buildChatPayload(
                List.of(new ChatMessage("user", "Hello")),
                settings(false)));

        assertFalse(payload.has("prompt"));
        assertFalse(payload.has("raw"));
        assertTrue(payload.has("messages"));
        assertEquals(Set.of("num_ctx"), payload.getJSONObject("options").keySet());
    }

    @Test
    void includesEnabledOptionsEvenWhenTheirValueIsZero()
    {
        OllamaClient client = new OllamaClient("http://localhost:11434", "test-model");
        JSONObject options = new JSONObject(client.buildChatPayload(
                List.of(new ChatMessage("user", "Hello")),
                settings(true))).getJSONObject("options");

        assertEquals(0.0, options.getDouble("temperature"));
        assertEquals(0, options.getInt("top_k"));
        assertEquals(0.0, options.getDouble("top_p"));
        assertEquals(0.0, options.getDouble("min_p"));
        assertEquals(0.0, options.getDouble("presence_penalty"));
        assertEquals(0.0, options.getDouble("frequency_penalty"));
        assertEquals(0.0, options.getDouble("repeat_penalty"));
        assertEquals(150, options.getInt("num_predict"));
    }

    private static GenerationSettings settings(boolean enabled)
    {
        return new GenerationSettings(
                8192,
                enabled, 150,
                enabled, 0.0,
                enabled, 0,
                enabled, 0.0,
                enabled, 0.0,
                enabled, 0.0,
                enabled, 0.0,
                enabled, 0.0,
                4096,
                7,
                3);
    }
}
