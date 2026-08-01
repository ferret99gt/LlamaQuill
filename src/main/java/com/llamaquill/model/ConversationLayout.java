package com.llamaquill.model;

import java.util.Locale;

public enum ConversationLayout
{
    ROLE_AWARE("Role-aware Turns"),
    FLATTENED("Flattened"),
    FLATTENED_WITH_PREFILL("Flattened with Prefill");

    private final String label;

    ConversationLayout(String label)
    {
        this.label = label;
    }

    public static ConversationLayout fromDatabase(String value)
    {
        if (value == null || value.isBlank())
        {
            return ROLE_AWARE;
        }
        try
        {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException ignored)
        {
            return ROLE_AWARE;
        }
    }

    @Override
    public String toString()
    {
        return label;
    }
}
