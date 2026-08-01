package com.llamaquill.storycards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.llamaquill.db.AppPaths;
import com.llamaquill.db.Database;
import com.llamaquill.db.StoryCardCommandPresetRepository;
import com.llamaquill.model.StoryCardCommandPreset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

class StoryCardCommandsTest
{
    @TempDir
    Path temporaryDirectory;

    @Test
    void exposesTheThreeImmutableBuiltInCommands()
    {
        List<StoryCardCommands.PresetChoice> builtIns = StoryCardCommands.builtIns();

        assertEquals(List.of("Basic List Prompt", "Basic Prose Prompt", "Condensed"),
                builtIns.stream().map(StoryCardCommands.PresetChoice::name).toList());
        assertTrue(builtIns.stream().allMatch(StoryCardCommands.PresetChoice::builtIn));
        assertEquals(StoryCardCommands.CONDENSED_PROMPT, StoryCardCommands.defaultPreset().command());
    }

    @Test
    void requiresTitleTokenAndSubstitutesOnlySupportedTokens()
    {
        assertThrows(IllegalArgumentException.class,
                () -> StoryCardCommands.validateCommand("Describe Mia."));

        String rendered = StoryCardCommands.renderCommand(
                "Describe {{title}} using {{triggers}}. Saved: {{entry}}",
                "Mia", "Mia, thief", "Old entry");

        assertEquals("Describe Mia using Mia, thief. Saved: Old entry", rendered);
    }

    @Test
    void buildsDatedRevisionNotes()
    {
        assertEquals("Player notes\n\n---\n\n[Entry replaced 2026-07-27 20:15:30]\nOld entry",
                StoryCardCommands.appendGenerationHistory(
                        "Player notes", "Old entry", LocalDateTime.of(2026, 7, 27, 20, 15, 30)));
    }

    @Test
    void userPresetsCannotOverwriteBuiltInsOrCaseInsensitivePeers() throws Exception
    {
        AppPaths paths = AppPaths.forDirectories(
                temporaryDirectory.resolve("data"), temporaryDirectory.resolve("legacy"));
        try (Database database = Database.open(paths))
        {
            StoryCardPresetService service = new StoryCardPresetService(
                    new StoryCardCommandPresetRepository(database));
            StoryCardCommandPreset custom = service.create("My Command", "Write {{title}}.");

            assertEquals(4, service.listChoices().size());
            assertThrows(IllegalArgumentException.class,
                    () -> service.create("condensed", "Write {{title}}."));
            assertThrows(IllegalArgumentException.class,
                    () -> service.create("MY COMMAND", "Rewrite {{title}}."));

            StoryCardCommandPreset updated = service.update(
                    custom.id(), "Renamed", "Rewrite {{title}} using {{entry}}.");
            assertEquals("Renamed", updated.name());
            service.delete(updated.id());
            assertEquals(3, service.listChoices().size());
        }
    }
}
