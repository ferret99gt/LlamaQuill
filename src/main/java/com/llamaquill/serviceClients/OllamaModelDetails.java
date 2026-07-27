package com.llamaquill.serviceClients;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public record OllamaModelDetails(String model, int maxContextLength, List<String> capabilities,
        String family, String parameterSize, String quantization)
{
    public OllamaModelDetails
    {
        model = model == null ? "" : model.trim();
        maxContextLength = Math.max(-1, maxContextLength);
        capabilities = capabilities == null
                ? List.of()
                : List.copyOf(new LinkedHashSet<>(capabilities.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(value -> value.trim().toLowerCase(Locale.ROOT))
                        .toList()));
        family = family == null ? "" : family.trim();
        parameterSize = parameterSize == null ? "" : parameterSize.trim();
        quantization = quantization == null ? "" : quantization.trim();
    }

    public boolean hasCapability(String capability)
    {
        return capability != null && capabilities.contains(capability.trim().toLowerCase(Locale.ROOT));
    }

    public String displaySummary()
    {
        List<String> parts = new java.util.ArrayList<>();
        if (maxContextLength > 0)
        {
            parts.add("maximum context " + maxContextLength + " tokens");
        }
        if (!parameterSize.isBlank())
        {
            parts.add(parameterSize);
        }
        if (!quantization.isBlank())
        {
            parts.add(quantization);
        }
        if (!capabilities.isEmpty())
        {
            parts.add(String.join(", ", capabilities));
        }
        return parts.isEmpty() ? "Model metadata unavailable" : String.join(" · ", parts);
    }
}
