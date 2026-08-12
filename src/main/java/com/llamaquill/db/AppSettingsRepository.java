package com.llamaquill.db;

import com.llamaquill.model.AppSettings;
import com.llamaquill.model.ImageRatio;

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
                           min_story_percent, story_card_lookback, comfyui_url,
                           comfy_workflow, comfy_dimension, comfy_ratio, comfy_batch_size,
                           comfy_save_images, ollama_keep_alive_minutes,
                           selected_story_card_command_preset_id
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
                            rs.getString("comfy_workflow"),
                            rs.getInt("comfy_dimension"),
                            ImageRatio.fromPersisted(rs.getString("comfy_ratio")),
                            rs.getInt("comfy_batch_size"),
                            rs.getInt("comfy_save_images") != 0,
                            rs.getInt("ollama_keep_alive_minutes"),
                            rs.getString("selected_story_card_command_preset_id")));
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
                        min_story_percent, story_card_lookback, comfyui_url,
                        comfy_workflow, comfy_dimension, comfy_ratio, comfy_batch_size,
                        comfy_save_images, ollama_keep_alive_minutes,
                        selected_story_card_command_preset_id
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET
                        ollama_url = excluded.ollama_url,
                        comfyui_url = excluded.comfyui_url,
                        selected_model = excluded.selected_model,
                        response_length_enabled = excluded.response_length_enabled,
                        response_length = excluded.response_length,
                        min_story_percent = excluded.min_story_percent,
                        story_card_lookback = excluded.story_card_lookback,
                        comfy_workflow = excluded.comfy_workflow,
                        comfy_dimension = excluded.comfy_dimension,
                        comfy_ratio = excluded.comfy_ratio,
                        comfy_batch_size = excluded.comfy_batch_size,
                        comfy_save_images = excluded.comfy_save_images,
                        ollama_keep_alive_minutes = excluded.ollama_keep_alive_minutes,
                        selected_story_card_command_preset_id = excluded.selected_story_card_command_preset_id
                    """))
            {
                stmt.setInt(1, SETTINGS_ID);
                stmt.setString(2, settings.ollamaUrl());
                stmt.setString(3, settings.selectedModel());
                stmt.setInt(4, settings.responseLengthEnabled() ? 1 : 0);
                stmt.setInt(5, settings.responseLength());
                stmt.setInt(6, settings.minStoryPercent());
                stmt.setInt(7, settings.storyCardLookback());
                stmt.setString(8, settings.comfyUiUrl());
                stmt.setString(9, settings.comfyWorkflow());
                stmt.setInt(10, settings.comfyDimension());
                stmt.setString(11, settings.comfyRatio().persistedValue());
                stmt.setInt(12, settings.comfyBatchSize());
                stmt.setInt(13, settings.comfySaveImages() ? 1 : 0);
                stmt.setInt(14, settings.ollamaKeepAliveMinutes());
                stmt.setString(15, settings.selectedStoryCardCommandPresetId());
                stmt.executeUpdate();
            }
        });
    }

    public AppSettings defaults()
    {
        return AppSettings.defaults();
    }
}
