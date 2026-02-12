package com.llamaquill.model;

public record AppAutoCardsSettings(String runMode, int minGapSeconds, boolean defaultEnabled, int cooldownTurns,
        int maxCardsPerRun, int candidateWindow, int cardLengthLimit, boolean summarizeInsteadOfTrim,
        String verbosity, String loggingLevel)
{
    public static AppAutoCardsSettings defaults()
    {
        return new AppAutoCardsSettings("Before Generation", 5, false, 8, 3, 8, 1500, true, "concise", "errors");
    }
}
