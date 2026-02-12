package com.llamaquill.db;

import com.llamaquill.model.AppSettings;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class AppSettingsRepository
{
    private static final int SETTINGS_ID = 1;

    private final Connection connection;

    public AppSettingsRepository(Connection connection)
    {
        this.connection = connection;
    }

    public Optional<AppSettings> load() throws SQLException
    {
        try (PreparedStatement stmt = connection.prepareStatement("""
                SELECT ollama_url, selected_model, context_limit, response_length,
                       min_story_window, story_card_lookback, an_placement
                FROM app_settings
                WHERE id = ?
                """))
        {
            stmt.setInt(1, SETTINGS_ID);
            try (ResultSet rs = stmt.executeQuery())
            {
                if (!rs.next())
                {
                    return Optional.empty();
                }
                return Optional.of(new AppSettings(
                        rs.getString("ollama_url"),
                        rs.getString("selected_model"),
                        rs.getInt("context_limit"),
                        rs.getInt("response_length"),
                        rs.getInt("min_story_window"),
                        rs.getInt("story_card_lookback"),
                        rs.getInt("an_placement")));
            }
        }
    }

    public void save(AppSettings settings) throws SQLException
    {
        try (PreparedStatement stmt = connection.prepareStatement("""
                INSERT INTO app_settings (
                    id, ollama_url, selected_model, context_limit, response_length,
                    min_story_window, story_card_lookback, an_placement
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    ollama_url = excluded.ollama_url,
                    selected_model = excluded.selected_model,
                    context_limit = excluded.context_limit,
                    response_length = excluded.response_length,
                    min_story_window = excluded.min_story_window,
                    story_card_lookback = excluded.story_card_lookback,
                    an_placement = excluded.an_placement
                """))
        {
            stmt.setInt(1, SETTINGS_ID);
            stmt.setString(2, settings.ollamaUrl());
            stmt.setString(3, settings.selectedModel());
            stmt.setInt(4, settings.contextLimit());
            stmt.setInt(5, settings.responseLength());
            stmt.setInt(6, settings.minStoryWindow());
            stmt.setInt(7, settings.storyCardLookback());
            stmt.setInt(8, settings.anPlacement());
            stmt.executeUpdate();
        }
    }

    public AppSettings defaults()
    {
        return AppSettings.defaults();
    }
}
