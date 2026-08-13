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
    public static final int FIXED_ESTIMATION_SAFETY_RESERVE = 64;
    public static final double TOKEN_ESTIMATION_UNDERSHOOT_ALLOWANCE = 0.10;

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
        return from(settings, Integer.MAX_VALUE);
    }

    public static PromptBudget from(GenerationSettings settings, int maximumInputTokens)
    {
        Objects.requireNonNull(settings, "settings");

        int contextLimit = Math.max(0, settings.contextLimit());
        int requestedResponseReserve = settings.responseLengthEnabled()
                ? Math.max(0, settings.responseLength())
                : DEFAULT_RESPONSE_RESERVE;
        int responseReserve = Math.min(contextLimit, requestedResponseReserve);
        int remainingAfterResponse = contextLimit - responseReserve;
        int inputLimit = Math.min(
                safeEstimatedInputLimit(remainingAfterResponse),
                Math.max(0, maximumInputTokens));
        int safetyReserve = remainingAfterResponse - inputLimit;
        int requestedProtectedStoryTokens = Math.max(0, settings.minStoryWindow());
        int protectedStoryTokens = Math.min(requestedProtectedStoryTokens, inputLimit);

        return new PromptBudget(contextLimit, responseReserve, safetyReserve, inputLimit,
                requestedProtectedStoryTokens, protectedStoryTokens);
    }

    /**
     * Converts Ollama's measured prompt size into a tighter estimate-space
     * ceiling for one retry. The configured response reserve and a fixed chat
     * framing margin remain outside the retried prompt.
     */
    public int correctedInputLimit(int estimatedPromptTokens, int actualPromptTokens, int actualContextLimit)
    {
        if (estimatedPromptTokens <= 0 || actualPromptTokens <= 0 || actualContextLimit <= 0
                || actualPromptTokens <= actualContextLimit)
        {
            return inputLimit;
        }

        int effectiveContextLimit = Math.min(contextLimit, actualContextLimit);
        int safeActualPromptLimit = Math.max(0,
                effectiveContextLimit - responseReserve - FIXED_ESTIMATION_SAFETY_RESERVE);
        int correctedLimit = (int) Math.floor(
                estimatedPromptTokens * (safeActualPromptLimit / (double) actualPromptTokens));
        return Math.min(inputLimit, Math.max(0, correctedLimit));
    }

    private static int safeEstimatedInputLimit(int availableInputTokens)
    {
        if (availableInputTokens <= FIXED_ESTIMATION_SAFETY_RESERVE)
        {
            return 0;
        }

        /*
         * PromptCompiler estimates model tokens without access to Ollama's
         * tokenizer or rendered chat template. Protect against both a fixed
         * framing cost and an estimate that is proportionally optimistic. The
         * allowance is divided out rather than merely subtracted so an actual
         * token count up to 10% above the estimate still fits.
         */
        double usableAfterFixedReserve = availableInputTokens - FIXED_ESTIMATION_SAFETY_RESERVE;
        return (int) Math.floor(
                usableAfterFixedReserve / (1.0 + TOKEN_ESTIMATION_UNDERSHOOT_ALLOWANCE));
    }
}
