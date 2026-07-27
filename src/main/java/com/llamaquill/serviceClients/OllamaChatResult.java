package com.llamaquill.serviceClients;

import java.time.Duration;
import java.util.Locale;

public record OllamaChatResult(String model, String content,
        int promptEvalCount, int evalCount, String doneReason,
        long totalDurationNanos, long loadDurationNanos,
        long promptEvalDurationNanos, long evalDurationNanos)
{
    public OllamaChatResult
    {
        model = model == null ? "" : model.trim();
        content = content == null ? "" : content;
        promptEvalCount = Math.max(-1, promptEvalCount);
        evalCount = Math.max(-1, evalCount);
        doneReason = doneReason == null ? "" : doneReason.trim();
        totalDurationNanos = Math.max(-1L, totalDurationNanos);
        loadDurationNanos = Math.max(-1L, loadDurationNanos);
        promptEvalDurationNanos = Math.max(-1L, promptEvalDurationNanos);
        evalDurationNanos = Math.max(-1L, evalDurationNanos);
    }

    public String diagnosticSummary()
    {
        StringBuilder summary = new StringBuilder();
        if (!model.isBlank())
        {
            summary.append("Model: ").append(model);
        }
        appendCount(summary, "Prompt tokens", promptEvalCount);
        appendCount(summary, "Output tokens", evalCount);
        if (!doneReason.isBlank())
        {
            append(summary, "Done reason", doneReason);
        }
        if (totalDurationNanos >= 0)
        {
            append(summary, "Total duration", formatDuration(totalDurationNanos));
        }
        if (loadDurationNanos >= 0)
        {
            append(summary, "Load duration", formatDuration(loadDurationNanos));
        }
        if (promptEvalDurationNanos >= 0)
        {
            append(summary, "Prompt evaluation", formatDuration(promptEvalDurationNanos));
        }
        if (evalDurationNanos >= 0)
        {
            append(summary, "Generation", formatDuration(evalDurationNanos));
        }
        return summary.toString();
    }

    private static void appendCount(StringBuilder target, String label, int value)
    {
        if (value >= 0)
        {
            append(target, label, Integer.toString(value));
        }
    }

    private static void append(StringBuilder target, String label, String value)
    {
        if (!target.isEmpty())
        {
            target.append('\n');
        }
        target.append(label).append(": ").append(value);
    }

    private static String formatDuration(long nanos)
    {
        Duration duration = Duration.ofNanos(nanos);
        if (duration.toSeconds() >= 1)
        {
            return String.format(Locale.ROOT, "%.2f s", nanos / 1_000_000_000.0);
        }
        return duration.toMillis() + " ms";
    }
}
