package com.llamaquill.prompt;

import java.util.List;
import java.util.Objects;

/**
 * Structured context-selection details for diagnostics and a future context inspector.
 */
public record PromptContextReport(PromptBudget budget, int estimatedInputTokens, List<Entry> entries)
{
    public PromptContextReport
    {
        budget = Objects.requireNonNull(budget, "budget");
        estimatedInputTokens = Math.max(0, estimatedInputTokens);
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public List<Entry> entries(Component component)
    {
        Objects.requireNonNull(component, "component");
        return entries.stream().filter(entry -> entry.component() == component).toList();
    }

    public enum Component
    {
        SYSTEM,
        STORY,
        PLOT_ESSENTIALS,
        AUTHOR_NOTE,
        PINNED_STORY_CARD,
        TRIGGERED_STORY_CARD,
        FORCED_STORY_CARD,
        AUXILIARY_TASK
    }

    public enum Status
    {
        INCLUDED,
        TRIMMED,
        DROPPED
    }

    public record Entry(Component component, String id, String label, Status status,
            int originalEstimatedTokens, int includedEstimatedTokens, int originalItems, int includedItems,
            List<String> matchedTriggers)
    {
        public Entry
        {
            component = Objects.requireNonNull(component, "component");
            id = id == null ? "" : id;
            label = label == null ? "" : label;
            status = Objects.requireNonNull(status, "status");
            originalEstimatedTokens = Math.max(0, originalEstimatedTokens);
            includedEstimatedTokens = Math.max(0, includedEstimatedTokens);
            originalItems = Math.max(0, originalItems);
            includedItems = Math.max(0, includedItems);
            matchedTriggers = matchedTriggers == null ? List.of() : List.copyOf(matchedTriggers);
        }
    }
}
