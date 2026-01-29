package com.llamaquill.db;

import com.llamaquill.model.GenerationSettings;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class SettingsRepository {
    private static final int SETTINGS_ID = 1;

    private final Connection connection;

    public SettingsRepository(Connection connection) {
        this.connection = connection;
    }

    public Optional<GenerationSettings> load() throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("""
                SELECT context_limit, response_length, temperature, top_k, top_p, presence_penalty,
                       frequency_penalty, min_story_window, story_card_lookback, an_placement
                FROM generation_settings
                WHERE id = ?
                """)) {
            stmt.setInt(1, SETTINGS_ID);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new GenerationSettings(
                        rs.getInt("context_limit"),
                        rs.getInt("response_length"),
                        rs.getDouble("temperature"),
                        rs.getInt("top_k"),
                        rs.getDouble("top_p"),
                        rs.getDouble("presence_penalty"),
                        rs.getDouble("frequency_penalty"),
                        rs.getInt("min_story_window"),
                        rs.getInt("story_card_lookback"),
                        rs.getInt("an_placement")
                ));
            }
        }
    }

    public void save(GenerationSettings settings) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("""
                INSERT INTO generation_settings (
                    id, context_limit, response_length, temperature, top_k, top_p,
                    presence_penalty, frequency_penalty, min_story_window, story_card_lookback, an_placement
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    context_limit = excluded.context_limit,
                    response_length = excluded.response_length,
                    temperature = excluded.temperature,
                    top_k = excluded.top_k,
                    top_p = excluded.top_p,
                    presence_penalty = excluded.presence_penalty,
                    frequency_penalty = excluded.frequency_penalty,
                    min_story_window = excluded.min_story_window,
                    story_card_lookback = excluded.story_card_lookback,
                    an_placement = excluded.an_placement
                """)) {
            stmt.setInt(1, SETTINGS_ID);
            stmt.setInt(2, settings.contextLimit());
            stmt.setInt(3, settings.responseLength());
            stmt.setDouble(4, settings.temperature());
            stmt.setInt(5, settings.topK());
            stmt.setDouble(6, settings.topP());
            stmt.setDouble(7, settings.presencePenalty());
            stmt.setDouble(8, settings.frequencyPenalty());
            stmt.setInt(9, settings.minStoryWindow());
            stmt.setInt(10, settings.storyCardLookback());
            stmt.setInt(11, settings.anPlacement());
            stmt.executeUpdate();
        }
    }
}
