package com.llamaquill.db;

import com.llamaquill.model.AppSettings;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class AppSettingsRepository
{
    private static final int SETTINGS_ID = 1;

    private final Database database;

    public AppSettingsRepository(Database database)
    {
        this.database = database;
    }

    public Optional<AppSettings> load() throws SQLException
    {
        return database.withConnection(connection ->
        {
            try (PreparedStatement stmt = connection.prepareStatement("""
                    SELECT ollama_url, selected_model, response_length_enabled, response_length,
                           min_story_percent, story_card_lookback, an_placement, comfyui_url,
                           comfy_workflow, comfy_width, comfy_height, comfy_batch_size
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
                            rs.getString("comfyui_url"),
                            rs.getString("selected_model"),
                            rs.getInt("response_length_enabled") != 0,
                            rs.getInt("response_length"),
                            rs.getInt("min_story_percent"),
                            rs.getInt("story_card_lookback"),
                            rs.getInt("an_placement"),
                            rs.getString("comfy_workflow"),
                            rs.getInt("comfy_width"),
                            rs.getInt("comfy_height"),
                            rs.getInt("comfy_batch_size")));
                }
            }
        });
    }

    public void save(AppSettings settings) throws SQLException
    {
        database.useConnection(connection ->
        {
            try (PreparedStatement stmt = connection.prepareStatement("""
                    INSERT INTO app_settings (
                        id, ollama_url, selected_model, response_length_enabled, response_length,
                        min_story_percent, story_card_lookback, an_placement, comfyui_url,
                        comfy_workflow, comfy_width, comfy_height, comfy_batch_size
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET
                        ollama_url = excluded.ollama_url,
                        comfyui_url = excluded.comfyui_url,
                        selected_model = excluded.selected_model,
                        response_length_enabled = excluded.response_length_enabled,
                        response_length = excluded.response_length,
                        min_story_percent = excluded.min_story_percent,
                        story_card_lookback = excluded.story_card_lookback,
                        an_placement = excluded.an_placement,
                        comfy_workflow = excluded.comfy_workflow,
                        comfy_width = excluded.comfy_width,
                        comfy_height = excluded.comfy_height,
                        comfy_batch_size = excluded.comfy_batch_size
                    """))
            {
                stmt.setInt(1, SETTINGS_ID);
                stmt.setString(2, settings.ollamaUrl());
                stmt.setString(3, settings.selectedModel());
                stmt.setInt(4, settings.responseLengthEnabled() ? 1 : 0);
                stmt.setInt(5, settings.responseLength());
                stmt.setInt(6, settings.minStoryPercent());
                stmt.setInt(7, settings.storyCardLookback());
                stmt.setInt(8, settings.anPlacement());
                stmt.setString(9, settings.comfyUiUrl());
                stmt.setString(10, settings.comfyWorkflow());
                stmt.setInt(11, settings.comfyWidth());
                stmt.setInt(12, settings.comfyHeight());
                stmt.setInt(13, settings.comfyBatchSize());
                stmt.executeUpdate();
            }
        });
    }

    public AppSettings defaults()
    {
        return AppSettings.defaults();
    }
}
