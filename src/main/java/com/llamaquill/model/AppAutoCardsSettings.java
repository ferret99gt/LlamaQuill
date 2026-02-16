package com.llamaquill.model;

public record AppAutoCardsSettings(int cooldownTurns, int maxCardsPerRun,
        int candidateWindow, int cardLengthLimit, boolean summarizeInsteadOfTrim,
        boolean useBulletedLists, String candidateSelectionMode, String contextMode)
{
    public static AppAutoCardsSettings defaults()
    {
        return new AppAutoCardsSettings(8, 3, 12, 2000, true, false, "Proper Noun Heuristics",
                "Full Story Context");
    }
}
