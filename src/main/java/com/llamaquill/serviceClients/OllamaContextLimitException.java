package com.llamaquill.serviceClients;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ollama rejected a chat request after measuring its rendered prompt against
 * the active model context.
 */
public final class OllamaContextLimitException extends OllamaException
{
    private static final Pattern ERROR_TYPE_PATTERN = Pattern.compile(
            "\\\"type\\\"\\s*:\\s*\\\"exceed_context_size_error\\\"");
    private static final Pattern PROMPT_TOKEN_COUNT_PATTERN = Pattern.compile(
            "\\\"n_prompt_tokens\\\"\\s*:\\s*(\\d+)");
    private static final Pattern CONTEXT_TOKEN_COUNT_PATTERN = Pattern.compile(
            "\\\"n_ctx\\\"\\s*:\\s*(\\d+)");

    private final int promptTokens;
    private final int contextLimit;

    public OllamaContextLimitException(String message, String endpoint, int statusCode, String detail,
            int promptTokens, int contextLimit)
    {
        super(message, endpoint, statusCode, detail);
        if (promptTokens <= 0 || contextLimit <= 0)
        {
            throw new IllegalArgumentException("Ollama context measurements must be positive");
        }
        this.promptTokens = promptTokens;
        this.contextLimit = contextLimit;
    }

    public int promptTokens()
    {
        return promptTokens;
    }

    public int contextLimit()
    {
        return contextLimit;
    }

    /**
     * Recovers the structured context measurements if an upstream transport
     * left the response classified as a generic Ollama error.
     */
    public static OllamaContextLimitException from(OllamaException error)
    {
        if (error instanceof OllamaContextLimitException contextError)
        {
            return contextError;
        }
        if (error == null || error.statusCode() != 400 || !error.endpoint().endsWith("/api/chat"))
        {
            return null;
        }

        String diagnostic = error.detail() + "\n" + error.getMessage();
        if (!ERROR_TYPE_PATTERN.matcher(diagnostic).find())
        {
            return null;
        }
        int promptTokens = matchedPositiveInt(PROMPT_TOKEN_COUNT_PATTERN, diagnostic);
        int contextLimit = matchedPositiveInt(CONTEXT_TOKEN_COUNT_PATTERN, diagnostic);
        if (promptTokens <= contextLimit || contextLimit <= 0)
        {
            return null;
        }
        return new OllamaContextLimitException(
                error.getMessage(), error.endpoint(), error.statusCode(), error.detail(),
                promptTokens, contextLimit);
    }

    private static int matchedPositiveInt(Pattern pattern, String text)
    {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find())
        {
            return -1;
        }
        try
        {
            long value = Long.parseLong(matcher.group(1));
            return value > 0 && value <= Integer.MAX_VALUE ? (int) value : -1;
        }
        catch (NumberFormatException ignored)
        {
            return -1;
        }
    }
}
