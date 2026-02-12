package com.llamaquill.model;

public record ModelAutoCardsSettings(String modelName, String createPrompt, String updatePrompt,
        String summarizePrompt, int maxTokensCreate, int maxTokensUpdate, int maxTokensSummarize,
        Double temperatureOverride)
{
    public static ModelAutoCardsSettings defaults(String modelName)
    {
        return new ModelAutoCardsSettings(
                modelName,
                "Write a short, factual entry about %{title} in third person. 3–6 sentences. No dialogue.",
                "Update the existing entry with new facts only. Avoid repetition.",
                "Condense to 3–5 sentences, keep key facts and names.",
                256,
                192,
                192,
                null);
    }
}
