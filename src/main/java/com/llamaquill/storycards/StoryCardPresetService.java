package com.llamaquill.storycards;

import com.llamaquill.db.StoryCardCommandPresetRepository;
import com.llamaquill.model.StoryCardCommandPreset;
import com.llamaquill.util.Ids;
import com.llamaquill.util.Timestamps;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class StoryCardPresetService
{
    private final StoryCardCommandPresetRepository repository;

    public StoryCardPresetService(StoryCardCommandPresetRepository repository)
    {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public List<StoryCardCommands.PresetChoice> listChoices() throws SQLException
    {
        List<StoryCardCommands.PresetChoice> choices = new ArrayList<>(StoryCardCommands.builtIns());
        repository.listAll().stream().map(StoryCardCommands.PresetChoice::user).forEach(choices::add);
        return choices;
    }

    public StoryCardCommandPreset create(String name, String command) throws SQLException
    {
        String normalizedName = validateName(name);
        String normalizedCommand = StoryCardCommands.validateCommand(command);
        rejectNameCollision(normalizedName, null);
        String now = Timestamps.now();
        StoryCardCommandPreset preset = new StoryCardCommandPreset(
                Ids.newId(), normalizedName, normalizedCommand, now, now);
        repository.insert(preset);
        return preset;
    }

    public StoryCardCommandPreset update(String id, String name, String command) throws SQLException
    {
        StoryCardCommandPreset existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Story Card command preset no longer exists."));
        String normalizedName = validateName(name);
        String normalizedCommand = StoryCardCommands.validateCommand(command);
        rejectNameCollision(normalizedName, existing.id());
        StoryCardCommandPreset updated = new StoryCardCommandPreset(
                existing.id(), normalizedName, normalizedCommand, existing.createdAt(), Timestamps.now());
        repository.update(updated);
        return updated;
    }

    public void delete(String id) throws SQLException
    {
        if (id == null || id.isBlank())
        {
            throw new IllegalArgumentException("A user Story Card command preset must be selected.");
        }
        repository.delete(id);
    }

    private void rejectNameCollision(String name, String allowedId) throws SQLException
    {
        if (StoryCardCommands.isBuiltInName(name))
        {
            throw new IllegalArgumentException("Built-in Story Card command names cannot be overwritten.");
        }
        repository.findByName(name).ifPresent(existing ->
        {
            if (allowedId == null || !existing.id().equals(allowedId))
            {
                throw new IllegalArgumentException("A Story Card command preset with that name already exists.");
            }
        });
    }

    private static String validateName(String name)
    {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isBlank())
        {
            throw new IllegalArgumentException("Story Card command preset name cannot be empty.");
        }
        if (normalized.length() > 64)
        {
            throw new IllegalArgumentException("Story Card command preset names are limited to 64 characters.");
        }
        return normalized;
    }
}
