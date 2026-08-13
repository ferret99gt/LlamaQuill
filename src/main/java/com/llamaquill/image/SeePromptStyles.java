package com.llamaquill.image;

import com.llamaquill.model.SeePromptPreset;

import java.util.List;
import java.util.Locale;

public final class SeePromptStyles
{
    public static final String NONE_ID = "builtin:none";

    private static final List<PresetChoice> BUILT_INS = List.of(
            new PresetChoice(NONE_ID, "None", "", true),
            new PresetChoice("builtin:photo", "Photo", """
                    Describe the scene as a high-quality photograph. Favor believable camera framing, natural or motivated lighting, realistic materials and skin, and physically plausible depth of field. Avoid illustration, painting, anime, and CGI terminology.""", true),
            new PresetChoice("builtin:realistic", "Realistic", """
                    Render the scene with grounded visual realism. Emphasize plausible anatomy, natural expressions and poses, convincing materials, atmospheric depth, and coherent cinematic lighting while preserving the story's established details.""", true),
            new PresetChoice("builtin:anime", "Anime", """
                    Describe the scene as a polished anime illustration with expressive character design, clean linework, controlled cel shading, deliberate color harmony, and a detailed background. Avoid photographic camera language and photorealistic rendering.""", true),
            new PresetChoice("builtin:digital-illustration", "Digital Illustration", """
                    Describe the scene as a polished digital illustration or concept-art piece. Emphasize strong composition, readable silhouettes, cohesive color design, dramatic lighting, and crisp, intentional environmental and character detail.""", true),
            new PresetChoice("builtin:painterly", "Painterly", """
                    Describe the scene as a painterly illustration with visible brushwork, layered color, softened edges, atmospheric light, and expressive texture. Keep people and objects recognizable and consistent without pushing the result toward photography.""", true));

    private SeePromptStyles()
    {
    }

    public static List<PresetChoice> builtIns()
    {
        return BUILT_INS;
    }

    public static PresetChoice defaultPreset()
    {
        return BUILT_INS.getFirst();
    }

    public static boolean isBuiltInName(String name)
    {
        String normalized = normalizeName(name);
        return BUILT_INS.stream().anyMatch(preset -> normalizeName(preset.name()).equals(normalized));
    }

    public static boolean isBuiltInId(String id)
    {
        String value = id == null ? "" : id.trim();
        return BUILT_INS.stream().anyMatch(preset -> preset.id().equals(value));
    }

    public static String validatePrompt(String prompt)
    {
        String normalized = prompt == null ? "" : prompt.trim();
        if (normalized.isBlank())
        {
            throw new IllegalArgumentException("See style prompt cannot be empty.");
        }
        return normalized;
    }

    private static String normalizeName(String name)
    {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    public record PresetChoice(String id, String name, String prompt, boolean builtIn)
    {
        public PresetChoice
        {
            id = id == null ? "" : id;
            name = name == null ? "" : name;
            prompt = prompt == null ? "" : prompt;
        }

        public static PresetChoice user(SeePromptPreset preset)
        {
            return new PresetChoice(preset.id(), preset.name(), preset.prompt(), false);
        }

        @Override
        public String toString()
        {
            return name;
        }
    }
}
