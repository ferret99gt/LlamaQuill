package com.llamaquill.model;

public record ModelAutoCardsSettings(String modelName, String createPrompt, String updatePrompt,
        String summarizePrompt, int maxTokensCreate, int maxTokensUpdate, int maxTokensSummarize)
{
    public static ModelAutoCardsSettings defaults(String modelName)
    {
        return new ModelAutoCardsSettings(
                modelName,
                """
                <system>
                # Write a brief and coherent informational entry for %{title} following these instructions:
                - Write only third-person pure prose information about %{title} using complete sentences with correct punctuation
                - Avoid short-term temporary details or actions, instead focus on plot-significant information
                - Introduce %{title} by stating who/what, followed by a detailed description of permanent physical traits, followed by story-relevant details.
                - Prioritize story-relevant details about %{title} first to ensure seamless integration with the previous plot
                - Create new information based on the context and story direction
                - Mention %{title} in every sentence
                - Use semicolons if needed
                - Be concise and grounded
                - Imitate the story's writing style and infer the reader's preferences
                <user>
                # Card details:
                Title: %{title}
                Triggers: %{triggers}

                # Story excerpt:
                %{excerpt}

                # Instruction:
                Create the entry for %{title} while avoiding repetition. Return only the card content.
                """,
                """
                <system>
                # Update the entry for %{title} following these instructions:
                - Ensure the passage retains the core meaning and most essential details
                - Use the third-person perspective
                - Prioritize information-density, accuracy, and completeness
                - Remain brief and concise
                - Introduce %{title} by stating who/what, followed by a detailed description of permanent physical traits, followed by story-relevant details.
                - Prioritize story-relevant details about %{title} first to ensure seamless integration with the previous plot
                - Add new information based on the context and story direction
                - Mention %{title} in every sentence
                - Use semicolons if needed
                - Be concise and grounded
                - Imitate the story's writing style and infer the reader's preferences
                <user>
                # Card details:
                Title: %{title}
                Triggers: %{triggers}

                # Story excerpt:
                %{excerpt}

                # Instruction:
                Update the entry for %{title} while avoiding repetition. Continue the card as provided:
                <assistant>
                %{content}
                """,
                """
                <system>
                # Summarize the entry for %{title} following these instructions:
                - Ensure the passage retains the core meaning and most essential details
                - Use the third-person perspective
                - Prioritize information-density, accuracy, and completeness
                - Remain brief and concise
                - Introduce %{title} by stating who/what, followed by a detailed description of permanent physical traits, followed by story-relevant details.
                - Prioritize story-relevant details about %{title} first to ensure seamless integration with the previous plot
                - Add new information based on the context and story direction
                - Mention %{title} in every sentence
                - Use semicolons if needed
                - Be concise and grounded
                - Imitate the story's writing style and infer the reader's preferences
                <user>
                # Card details:
                Title: %{title}
                Triggers: %{triggers}

                # Existing card:
                %{content}

                # Story excerpt:
                %{excerpt}

                # Instruction:
                Summarize the entry for %{title} while avoiding repetition. Return only the summarized card content.
                """,
                512,
                512,
                512);
    }
}
