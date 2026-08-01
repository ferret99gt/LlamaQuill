package com.llamaquill.model;

import java.util.Locale;

public enum StoryCardWrappingStyle
{
    NONE("None", "", ""),
    BRACES("{...}", "{", "}"),
    BRACKETS("[...]", "[", "]");

    private final String label;
    private final String opening;
    private final String closing;

    StoryCardWrappingStyle(String label, String opening, String closing)
    {
        this.label = label;
        this.opening = opening;
        this.closing = closing;
    }

    public String applyTo(String value)
    {
        String text = value == null ? "" : value;
        if (this == NONE || text.isBlank())
        {
            return text;
        }

        String unwrapped = stripRecognizedOuterWrapper(text);
        return opening + unwrapped + closing;
    }

    public static StoryCardWrappingStyle fromDatabase(String value)
    {
        if (value == null || value.isBlank())
        {
            return NONE;
        }
        try
        {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException ignored)
        {
            return NONE;
        }
    }

    private static String stripRecognizedOuterWrapper(String text)
    {
        if (text.length() >= 2
                && ((text.startsWith("{") && text.endsWith("}"))
                        || (text.startsWith("[") && text.endsWith("]"))))
        {
            return text.substring(1, text.length() - 1).strip();
        }
        return text;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
