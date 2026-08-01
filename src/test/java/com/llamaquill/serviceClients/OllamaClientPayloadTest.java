package com.llamaquill.serviceClients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.llamaquill.model.ChatMessage;
import com.llamaquill.model.GenerationSettings;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

class OllamaClientPayloadTest
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
        assertEquals("5m", payload.getString("keep_alive"));
        assertEquals(Set.of("num_ctx"), payload.getJSONObject("options").keySet());
    }

    @Test
    void serializesTheConfiguredGlobalKeepAliveAsAnOllamaDuration()
    {
        OllamaClient client = new OllamaClient("http://localhost:11434", "test-model");
        JSONObject payload = new JSONObject(client.buildChatPayload(
                List.of(new ChatMessage("user", "Hello")),
                settings(false, "test-model", 30)));

        assertEquals("30m", payload.getString("keep_alive"));
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
        assertEquals(0.0, options.getDouble("typical_p"));
        assertEquals(0.0, options.getDouble("presence_penalty"));
        assertEquals(0.0, options.getDouble("frequency_penalty"));
        assertEquals(0, options.getInt("repeat_last_n"));
        assertEquals(0.0, options.getDouble("repeat_penalty"));
        assertEquals(150, options.getInt("num_predict"));
    }

    @Test
    void oneShotPromptCanOmitNumPredictWithoutChangingOtherModelOptions()
    {
        OllamaClient client = new OllamaClient("http://localhost:11434", "test-model");
        GenerationSettings promptSettings = settings(true).withoutNumPredict();
        JSONObject options = new JSONObject(client.buildChatPayload(
                List.of(new ChatMessage("user", "Rewrite the story.")),
                promptSettings)).getJSONObject("options");

        assertFalse(options.has("num_predict"));
        assertEquals(150, promptSettings.responseLength());
        assertEquals(0.0, options.getDouble("temperature"));
        assertEquals(0, options.getInt("top_k"));
    }

    @Test
    void coalescesConsecutiveAssistantMessagesBeforeSerialization()
    {
        OllamaClient client = new OllamaClient("http://localhost:11434", "test-model");
        JSONArray messages = new JSONObject(client.buildChatPayload(
                List.of(
                        new ChatMessage("system", "Write prose."),
                        new ChatMessage("assistant", "First"),
                        new ChatMessage("assistant", " continuation."),
                        new ChatMessage("user", "Go on."),
                        new ChatMessage("assistant", "Second"),
                        new ChatMessage("assistant", " continuation.")),
                settings(false))).getJSONArray("messages");

        assertEquals(4, messages.length());
        assertEquals("assistant", messages.getJSONObject(1).getString("role"));
        assertEquals("First continuation.", messages.getJSONObject(1).getString("content"));
        assertEquals("user", messages.getJSONObject(2).getString("role"));
        assertEquals("assistant", messages.getJSONObject(3).getString("role"));
        assertEquals("Second continuation.", messages.getJSONObject(3).getString("content"));
    }

    @Test
    void serializesMessageContentWithoutHandWrittenEscaping()
    {
        String content = "Quotes: \"hello\"; slash: \\\\; lines:\r\nnext\t\u2603\u0001";
        OllamaClient client = new OllamaClient("http://localhost:11434", "model/with:\"quotes\"");

        JSONObject payload = new JSONObject(client.buildChatPayload(
                List.of(new ChatMessage("user", content)),
                settings(false, "model/with:\"quotes\"")));

        assertEquals("model/with:\"quotes\"", payload.getString("model"));
        assertEquals(content, payload.getJSONArray("messages").getJSONObject(0).getString("content"));
        assertFalse(payload.getBoolean("think"));
        assertTrue(payload.getBoolean("stream"));
    }

    private static GenerationSettings settings(boolean enabled)
    {
        return settings(enabled, "test-model");
    }

    private static GenerationSettings settings(boolean enabled, String modelName)
    {
        return settings(enabled, modelName, 5);
    }

    private static GenerationSettings settings(boolean enabled, String modelName, int keepAliveMinutes)
    {
        return new GenerationSettings(
                modelName, GenerationSettings.DEFAULT_OLLAMA_HOST, 8192, 1.0,
                enabled, 150,
                enabled, 0.0,
                enabled, 0,
                enabled, 0.0,
                enabled, 0.0,
                enabled, 0.0,
                enabled, 0.0,
                enabled, 0.0,
                enabled, 0,
                enabled, 0.0,
                4096,
                7,
                keepAliveMinutes);
    }
}
