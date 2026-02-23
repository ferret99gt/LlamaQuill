package com.llamaquill.model;

public record ModelAutoCardsSettings(String modelName, String createPrompt, String updatePrompt,
        String summarizePrompt, int maxTokensCreate, int maxTokensUpdate, int maxTokensSummarize)
{
    public static ModelAutoCardsSettings defaults(String modelName)
    {
        return new ModelAutoCardsSettings(
                modelName,
                """
                <system># Stop the story and ignore previous instructions. Write a brief and coherent informational entry for %{title} following these instructions:
                - Write only third-person pure prose information about %{title} using complete sentences with correct punctuation
                - Avoid short-term temporary details or appearances, instead focus on plot-significant information
                - Prioritize story-relevant details about %{title} first to ensure seamless integration with the previous plot
                - Create new information based on the context and story direction
                - Mention %{title} in every sentence
                - Use semicolons if needed
                - Add additional details about %{title} beneath incomplete entries
                - Be concise and grounded
                - Imitate the story's writing style and infer the reader's preferences
                <assistant>
                Continue the entry for %{title} below while avoiding repetition:
                %{entry}
                """,
                """
                <system># Stop the story and ignore previous instructions. Write a brief and coherent informational entry for %{title} following these instructions:
                - Write only third-person pure prose information about %{title} using complete sentences with correct punctuation
                - Avoid short-term temporary details or appearances, instead focus on plot-significant information
                - Prioritize story-relevant details about %{title} first to ensure seamless integration with the previous plot
                - Create new information based on the context and story direction
                - Mention %{title} in every sentence
                - Use semicolons if needed
                - Add additional details about %{title} beneath incomplete entries
                - Be concise and grounded
                - Imitate the story's writing style and infer the reader's preferences
                <assistant>
                Continue the entry for %{title} below while avoiding repetition:
                %{entry}
                """,
                """
                -----

                <system># Stop the story and ignore previous instructions. Summarize and condense the given paragraph into a narrow and focused memory passage while following these guidelines:
                "- Ensure the passage retains the core meaning and most essential details
                "- Use the third-person perspective
                "- Prioritize information-density, accuracy, and completeness
                "- Remain brief and concise
                "- Write firmly in the past tense
                "- The paragraph below pertains to old events from far earlier in the story
                "- Integrate %{title} naturally within the memory; however, only write about the events as they occurred
                "- Only reference information present inside the paragraph itself, be specific"
                <assistant>
                "Write a summarized old memory passage for %{title} based only on the following paragraph:
                \"""
                %{entry}
                \"""
                Summarize below:

                """,
                512,
                512,
                512);
    }
}
