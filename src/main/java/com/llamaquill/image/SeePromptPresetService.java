package com.llamaquill.image;

import com.llamaquill.db.SeePromptPresetRepository;
import com.llamaquill.model.SeePromptPreset;
import com.llamaquill.util.Ids;
import com.llamaquill.util.Timestamps;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SeePromptPresetService
{
    private final SeePromptPresetRepository repository;

    public SeePromptPresetService(SeePromptPresetRepository repository)
    {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public List<SeePromptStyles.PresetChoice> listChoices() throws SQLException
    {
        List<SeePromptStyles.PresetChoice> choices = new ArrayList<>(SeePromptStyles.builtIns());
        repository.listAll().stream().map(SeePromptStyles.PresetChoice::user).forEach(choices::add);
        return choices;
    }

    public SeePromptPreset create(String name, String prompt) throws SQLException
    {
        String normalizedName = validateName(name);
        String normalizedPrompt = SeePromptStyles.validatePrompt(prompt);
        rejectNameCollision(normalizedName, null);
        String now = Timestamps.now();
        SeePromptPreset preset = new SeePromptPreset(
                Ids.newId(), normalizedName, normalizedPrompt, now, now);
        repository.insert(preset);
        return preset;
    }

    public SeePromptPreset update(String id, String name, String prompt) throws SQLException
    {
        SeePromptPreset existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("See style preset no longer exists."));
        String normalizedName = validateName(name);
        String normalizedPrompt = SeePromptStyles.validatePrompt(prompt);
        rejectNameCollision(normalizedName, existing.id());
        SeePromptPreset updated = new SeePromptPreset(
                existing.id(), normalizedName, normalizedPrompt, existing.createdAt(), Timestamps.now());
        repository.update(updated);
        return updated;
    }

    public void delete(String id) throws SQLException
    {
        if (id == null || id.isBlank())
        {
            throw new IllegalArgumentException("A custom See style preset must be selected.");
        }
        if (SeePromptStyles.isBuiltInId(id))
        {
            throw new IllegalArgumentException("Built-in See style presets cannot be deleted.");
        }
        repository.delete(id);
    }

    private void rejectNameCollision(String name, String allowedId) throws SQLException
    {
        if (SeePromptStyles.isBuiltInName(name))
        {
            throw new IllegalArgumentException("Built-in See style names cannot be overwritten.");
        }
        repository.findByName(name).ifPresent(existing ->
        {
            if (allowedId == null || !existing.id().equals(allowedId))
            {
                throw new IllegalArgumentException("A See style preset with that name already exists.");
            }
        });
    }

    private static String validateName(String name)
    {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isBlank())
        {
            throw new IllegalArgumentException("See style preset name cannot be empty.");
        }
        if (normalized.length() > 64)
        {
            throw new IllegalArgumentException("See style preset names are limited to 64 characters.");
        }
        return normalized;
    }
}
