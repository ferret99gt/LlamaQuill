package com.llamaquill.db;

import com.llamaquill.model.StoryCardCommandPreset;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class StoryCardCommandPresetRepository
{
    private final Database database;

    public StoryCardCommandPresetRepository(Database database)
    {
        this.database = database;
    }

    public void insert(StoryCardCommandPreset preset) throws SQLException
    {
        database.useConnection(connection ->
        {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO story_card_command_presets (
                        id, name, command, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?)
                    """))
            {
                bind(statement, preset);
                statement.executeUpdate();
            }
        });
    }

    public void update(StoryCardCommandPreset preset) throws SQLException
    {
        database.useConnection(connection ->
        {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE story_card_command_presets
                    SET name = ?, command = ?, updated_at = ?
                    WHERE id = ?
                    """))
            {
                statement.setString(1, preset.name());
                statement.setString(2, preset.command());
                statement.setString(3, preset.updatedAt());
                statement.setString(4, preset.id());
                statement.executeUpdate();
            }
        });
    }

    public void delete(String id) throws SQLException
    {
        database.useConnection(connection ->
        {
            try (PreparedStatement statement = connection.prepareStatement("""
                    DELETE FROM story_card_command_presets WHERE id = ?
                    """))
            {
                statement.setString(1, id);
                statement.executeUpdate();
            }
        });
    }

    public Optional<StoryCardCommandPreset> findById(String id) throws SQLException
    {
        return database.withConnection(connection ->
        {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT id, name, command, created_at, updated_at
                    FROM story_card_command_presets
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

    public Optional<StoryCardCommandPreset> findByName(String name) throws SQLException
    {
        return database.withConnection(connection ->
        {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT id, name, command, created_at, updated_at
                    FROM story_card_command_presets
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

    public List<StoryCardCommandPreset> listAll() throws SQLException
    {
        return database.withConnection(connection ->
        {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT id, name, command, created_at, updated_at
                    FROM story_card_command_presets
                    ORDER BY name COLLATE NOCASE
                    """);
                 ResultSet result = statement.executeQuery())
            {
                List<StoryCardCommandPreset> presets = new ArrayList<>();
                while (result.next())
                {
                    presets.add(map(result));
                }
                return presets;
            }
        });
    }

    private static void bind(PreparedStatement statement, StoryCardCommandPreset preset) throws SQLException
    {
        statement.setString(1, preset.id());
        statement.setString(2, preset.name());
        statement.setString(3, preset.command());
        statement.setString(4, preset.createdAt());
        statement.setString(5, preset.updatedAt());
    }

    private static StoryCardCommandPreset map(ResultSet result) throws SQLException
    {
        return new StoryCardCommandPreset(
                result.getString("id"),
                result.getString("name"),
                result.getString("command"),
                result.getString("created_at"),
                result.getString("updated_at"));
    }
}
