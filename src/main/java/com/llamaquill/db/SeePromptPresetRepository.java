package com.llamaquill.db;

import com.llamaquill.model.SeePromptPreset;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SeePromptPresetRepository
{
    private static final String NONE_PRESET_ID = "builtin:none";

    private final Database database;

    public SeePromptPresetRepository(Database database)
    {
        this.database = database;
    }

    public void insert(SeePromptPreset preset) throws SQLException
    {
        database.useConnection(connection ->
        {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO see_prompt_presets (
                        id, name, prompt, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?)
                    """))
            {
                bind(statement, preset);
                statement.executeUpdate();
            }
        });
    }

    public void update(SeePromptPreset preset) throws SQLException
    {
        database.useConnection(connection ->
        {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE see_prompt_presets
                    SET name = ?, prompt = ?, updated_at = ?
                    WHERE id = ?
                    """))
            {
                statement.setString(1, preset.name());
                statement.setString(2, preset.prompt());
                statement.setString(3, preset.updatedAt());
                statement.setString(4, preset.id());
                statement.executeUpdate();
            }
        });
    }

    public void delete(String id) throws SQLException
    {
        database.inTransaction(connection ->
        {
            try (PreparedStatement resetStories = connection.prepareStatement("""
                    UPDATE stories
                    SET selected_see_prompt_preset_id = ?
                    WHERE selected_see_prompt_preset_id = ?
                    """))
            {
                resetStories.setString(1, NONE_PRESET_ID);
                resetStories.setString(2, id);
                resetStories.executeUpdate();
            }
            try (PreparedStatement deletePreset = connection.prepareStatement("""
                    DELETE FROM see_prompt_presets WHERE id = ?
                    """))
            {
                deletePreset.setString(1, id);
                deletePreset.executeUpdate();
            }
        });
    }

    public Optional<SeePromptPreset> findById(String id) throws SQLException
    {
        return database.withConnection(connection ->
        {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT id, name, prompt, created_at, updated_at
                    FROM see_prompt_presets
                    WHERE id = ?
                    """))
            {
                statement.setString(1, id);
                try (ResultSet result = statement.executeQuery())
                {
                    return result.next() ? Optional.of(map(result)) : Optional.empty();
                }
            }
        });
    }

    public Optional<SeePromptPreset> findByName(String name) throws SQLException
    {
        return database.withConnection(connection ->
        {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT id, name, prompt, created_at, updated_at
                    FROM see_prompt_presets
                    WHERE name = ? COLLATE NOCASE
                    """))
            {
                statement.setString(1, name);
                try (ResultSet result = statement.executeQuery())
                {
                    return result.next() ? Optional.of(map(result)) : Optional.empty();
                }
            }
        });
    }

    public List<SeePromptPreset> listAll() throws SQLException
    {
        return database.withConnection(connection ->
        {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT id, name, prompt, created_at, updated_at
                    FROM see_prompt_presets
                    ORDER BY name COLLATE NOCASE
                    """);
                 ResultSet result = statement.executeQuery())
            {
                List<SeePromptPreset> presets = new ArrayList<>();
                while (result.next())
                {
                    presets.add(map(result));
                }
                return presets;
            }
        });
    }

    private static void bind(PreparedStatement statement, SeePromptPreset preset) throws SQLException
    {
        statement.setString(1, preset.id());
        statement.setString(2, preset.name());
        statement.setString(3, preset.prompt());
        statement.setString(4, preset.createdAt());
        statement.setString(5, preset.updatedAt());
    }

    private static SeePromptPreset map(ResultSet result) throws SQLException
    {
        return new SeePromptPreset(
                result.getString("id"),
                result.getString("name"),
                result.getString("prompt"),
                result.getString("created_at"),
                result.getString("updated_at"));
    }
}
