package com.llamaquill.prompt;

import com.llamaquill.model.GenerationSettings;

import java.util.Objects;

/**
 * Token allocations used before a prompt is sent to Ollama.
 */
public record PromptBudget(int contextLimit, int responseReserve, int estimationSafetyReserve, int inputLimit,
        int requestedProtectedStoryTokens, int protectedStoryTokens)
{
    public static final int DEFAULT_RESPONSE_RESERVE = 200;
    public static final int ESTIMATION_SAFETY_RESERVE = 64;

    public PromptBudget
    {
        if (contextLimit < 0 || responseReserve < 0 || estimationSafetyReserve < 0 || inputLimit < 0
                || requestedProtectedStoryTokens < 0 || protectedStoryTokens < 0)
        {
            throw new IllegalArgumentException("Prompt budget values cannot be negative");
        }
        if (responseReserve + estimationSafetyReserve + inputLimit != contextLimit)
        {
            throw new IllegalArgumentException("Prompt budget allocations must equal the context limit");
        }
        if (protectedStoryTokens > inputLimit)
        {
            throw new IllegalArgumentException("Protected story tokens cannot exceed the input limit");
        }
    }

    public static PromptBudget from(GenerationSettings settings)
    {
        Objects.requireNonNull(settings, "settings");

        int contextLimit = Math.max(0, settings.contextLimit());
        int requestedResponseReserve = settings.responseLengthEnabled()
                ? Math.max(0, settings.responseLength())
                : DEFAULT_RESPONSE_RESERVE;
        int responseReserve = Math.min(contextLimit, requestedResponseReserve);
        int remainingAfterResponse = contextLimit - responseReserve;
        int safetyReserve = Math.min(ESTIMATION_SAFETY_RESERVE, remainingAfterResponse);
        int inputLimit = remainingAfterResponse - safetyReserve;
        int requestedProtectedStoryTokens = Math.max(0, settings.minStoryWindow());
        int protectedStoryTokens = Math.min(requestedProtectedStoryTokens, inputLimit);

        return new PromptBudget(contextLimit, responseReserve, safetyReserve, inputLimit,
                requestedProtectedStoryTokens, protectedStoryTokens);
    }
}
