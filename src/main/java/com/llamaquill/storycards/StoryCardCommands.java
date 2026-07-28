package com.llamaquill.storycards;

import com.llamaquill.model.StoryCardCommandPreset;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class StoryCardCommands
{
    public static final String TITLE_TOKEN = "{{title}}";
    public static final String TRIGGERS_TOKEN = "{{triggers}}";
    public static final String ENTRY_TOKEN = "{{entry}}";

    public static final String BASIC_LIST_PROMPT = """
            Generate an information card for {{title}} using clearly labeled fields which are each on their own line, beginning with a field that identifies the name of {{title}}. Each field should represent characteristics of {{title}}. Limit the response to 750 characters and do not use markdown or leave empty lines.""";
    public static final String BASIC_PROSE_PROMPT = """
            Generate a concise information card for {{title}} that captures the most important identity, role, appearance, personality, and motivations. It must clearly identify {{title}} in third person. Limit the response to 750 characters and do not use markdown or leave empty lines.""";
    public static final String CONDENSED_PROMPT = """
            Write a high-density factual summary for {{title}}. Start with {{title}} name. Use short, punchy, declarative sentences. Prioritize permanent attributes and core identity. Omit unnecessary filler words (the, a, is). Avoid repeating facts, avoid meta-commentary, avoid transient details. Limit to 750 characters. No markdown. No empty lines.""";

    private static final DateTimeFormatter HISTORY_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<PresetChoice> BUILT_INS = List.of(
            new PresetChoice("builtin:basic-list", "Basic List Prompt", BASIC_LIST_PROMPT, true),
            new PresetChoice("builtin:basic-prose", "Basic Prose Prompt", BASIC_PROSE_PROMPT, true),
            new PresetChoice("builtin:condensed", "Condensed", CONDENSED_PROMPT, true));

    private StoryCardCommands()
    {
    }

    public static List<PresetChoice> builtIns()
    {
        return BUILT_INS;
    }

    public static PresetChoice defaultPreset()
    {
        return BUILT_INS.getLast();
    }

    public static boolean isBuiltInName(String name)
    {
        String normalized = normalizeName(name);
        return BUILT_INS.stream().anyMatch(preset -> normalizeName(preset.name()).equals(normalized));
    }

    public static String validateCommand(String command)
    {
        String normalized = command == null ? "" : command.trim();
        if (normalized.isBlank())
        {
            throw new IllegalArgumentException("Story Card command cannot be empty.");
        }
        if (!normalized.contains(TITLE_TOKEN))
        {
            throw new IllegalArgumentException("Story Card command must contain " + TITLE_TOKEN + ".");
        }
        return normalized;
    }

    public static String renderCommand(String command, String title, String triggers, String savedEntry)
    {
        String rendered = validateCommand(command);
        String normalizedTitle = title == null ? "" : title.trim();
        if (normalizedTitle.isBlank())
        {
            throw new IllegalArgumentException("Story Card title cannot be empty.");
        }
        return rendered
                .replace(TITLE_TOKEN, normalizedTitle)
                .replace(TRIGGERS_TOKEN, triggers == null ? "" : triggers.trim())
                .replace(ENTRY_TOKEN, savedEntry == null ? "" : savedEntry);
    }

    public static String applyFormatting(String generated, EntryFormatting formatting)
    {
        String normalized = generated == null ? "" : generated.trim();
        if (normalized.isBlank())
        {
            return "";
        }
        EntryFormatting active = formatting == null ? EntryFormatting.NONE : formatting;
        return switch (active)
        {
        case NONE -> normalized;
        case BRACES -> "{" + normalized + "}";
        case BRACKETS -> "[" + normalized + "]";
        };
    }

    public static String appendGenerationHistory(String notes, String priorEntry, LocalDateTime localTimestamp)
    {
        String previous = priorEntry == null ? "" : priorEntry.trim();
        if (previous.isBlank())
        {
            return notes == null ? "" : notes;
        }
        LocalDateTime timestamp = localTimestamp == null ? LocalDateTime.now() : localTimestamp;
        String history = "[Entry replaced " + HISTORY_TIMESTAMP.format(timestamp) + "]\n" + previous;
        String existing = notes == null ? "" : notes.stripTrailing();
        return existing.isBlank() ? history : existing + "\n\n---\n\n" + history;
    }

    public static String normalizeName(String name)
    {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    public enum EntryFormatting
    {
        NONE("None"),
        BRACES("{...}"),
        BRACKETS("[...]");

        private final String label;

        EntryFormatting(String label)
        {
            this.label = label;
        }

        @Override
        public String toString()
        {
            return label;
        }
    }

    public record PresetChoice(String id, String name, String command, boolean builtIn)
    {
        public PresetChoice
        {
            id = id == null ? "" : id;
            name = name == null ? "" : name;
            command = command == null ? "" : command;
        }

        public static PresetChoice user(StoryCardCommandPreset preset)
        {
            return new PresetChoice(preset.id(), preset.name(), preset.command(), false);
        }

        @Override
        public String toString()
        {
            return name;
        }
    }
}
