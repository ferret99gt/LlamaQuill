package com.llamaquill.image;

import com.llamaquill.db.AppPaths;
import com.llamaquill.db.Database;
import com.llamaquill.db.SeePromptPresetRepository;
import com.llamaquill.db.StoryRepository;
import com.llamaquill.model.SeePromptPreset;
import com.llamaquill.model.Story;
import com.llamaquill.util.Timestamps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeePromptStylesTest
{
    @TempDir
    Path temporaryDirectory;

    @Test
    void providesNoneAndFiveProtectedBuiltInStyles()
    {
        List<SeePromptStyles.PresetChoice> builtIns = SeePromptStyles.builtIns();

        assertEquals(List.of(
                        "None", "Photo", "Realistic", "Anime", "Digital Illustration", "Painterly"),
                builtIns.stream().map(SeePromptStyles.PresetChoice::name).toList());
        assertEquals(SeePromptStyles.NONE_ID, SeePromptStyles.defaultPreset().id());
        assertEquals("", SeePromptStyles.defaultPreset().prompt());
        assertTrue(builtIns.stream().allMatch(SeePromptStyles.PresetChoice::builtIn));
        assertTrue(builtIns.stream().skip(1).allMatch(choice -> !choice.prompt().isBlank()));
    }

    @Test
    void managesCustomStylesAndClearsStorySelectionsWhenOneIsDeleted() throws Exception
    {
        AppPaths paths = AppPaths.forDirectories(
                temporaryDirectory.resolve("data"), temporaryDirectory.resolve("legacy"));
        try (Database database = Database.open(paths))
        {
            SeePromptPresetRepository repository = new SeePromptPresetRepository(database);
            SeePromptPresetService service = new SeePromptPresetService(repository);
            StoryRepository stories = new StoryRepository(database);

            SeePromptPreset created = service.create("  Graphic Novel  ", "  Use inked comic panels.  ");
            assertEquals("Graphic Novel", created.name());
            assertEquals("Use inked comic panels.", created.prompt());
            assertEquals(7, service.listChoices().size());

            SeePromptPreset updated = service.update(
                    created.id(), "Graphic Novel", "Use expressive inks and restrained color.");
            assertEquals(created.id(), updated.id());
            assertEquals("Use expressive inks and restrained color.", updated.prompt());

            assertThrows(IllegalArgumentException.class,
                    () -> service.create("Anime", "Try to replace a built-in."));
            assertThrows(IllegalArgumentException.class,
                    () -> service.create("graphic novel", "Duplicate name."));
            assertThrows(IllegalArgumentException.class,
                    () -> service.delete("builtin:anime"));

            String now = Timestamps.now();
            Story story = new Story(
                    "story", "Story", "", "", "", "", false, created.id(), now, now);
            stories.insert(story);
            service.delete(created.id());

            assertFalse(repository.findById(created.id()).isPresent());
            assertEquals(SeePromptStyles.NONE_ID,
                    stories.findById(story.id()).orElseThrow().selectedSeePromptPresetId());
        }
    }
}
