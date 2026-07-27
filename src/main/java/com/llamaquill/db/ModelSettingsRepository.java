package com.llamaquill.db;

import com.llamaquill.model.ModelSettings;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ModelSettingsRepository
{
    private final Database database;

    public ModelSettingsRepository(Database database)
    {
        this.database = database;
    }

    public Optional<ModelSettings> load(String modelName) throws SQLException
    {
        return database.withConnection(connection ->
        {
            try (PreparedStatement stmt = connection.prepareStatement("""
                    SELECT model_name, active,
                           context_limit, prompt_token_scale,
                           temperature_enabled, temperature,
                           top_k_enabled, top_k,
                           top_p_enabled, top_p,
                           min_p_enabled, min_p,
                           typical_p_enabled, typical_p,
                           presence_penalty_enabled, presence_penalty,
                           frequency_penalty_enabled, frequency_penalty,
                           repeat_last_n_enabled, repeat_last_n,
                           repetition_penalty_enabled, repetition_penalty
                    FROM model_settings
                    WHERE model_name = ?
                    """))
            {
                stmt.setString(1, modelName);
                try (ResultSet rs = stmt.executeQuery())
                {
                    if (!rs.next())
                    {
                        return Optional.empty();
                    }
                    return Optional.of(fromRow(rs));
                }
            }
        });
    }

    public List<ModelSettings> listActive() throws SQLException
    {
        return database.withConnection(connection ->
        {
            try (PreparedStatement stmt = connection.prepareStatement("""
                    SELECT model_name, active,
                           context_limit, prompt_token_scale,
                           temperature_enabled, temperature,
                           top_k_enabled, top_k,
                           top_p_enabled, top_p,
                           min_p_enabled, min_p,
                           typical_p_enabled, typical_p,
                           presence_penalty_enabled, presence_penalty,
                           frequency_penalty_enabled, frequency_penalty,
                           repeat_last_n_enabled, repeat_last_n,
                           repetition_penalty_enabled, repetition_penalty
                    FROM model_settings
                    WHERE active = 1
                    ORDER BY model_name
                    """);
                 ResultSet rs = stmt.executeQuery())
            {
                List<ModelSettings> items = new ArrayList<>();
                while (rs.next())
                {
                    items.add(fromRow(rs));
                }
                return items;
            }
        });
    }

    public List<ModelSettings> listAll() throws SQLException
    {
        return database.withConnection(connection ->
        {
            try (PreparedStatement stmt = connection.prepareStatement("""
                    SELECT model_name, active,
                           context_limit, prompt_token_scale,
                           temperature_enabled, temperature,
                           top_k_enabled, top_k,
                           top_p_enabled, top_p,
                           min_p_enabled, min_p,
                           typical_p_enabled, typical_p,
                           presence_penalty_enabled, presence_penalty,
                           frequency_penalty_enabled, frequency_penalty,
                           repeat_last_n_enabled, repeat_last_n,
                           repetition_penalty_enabled, repetition_penalty
                    FROM model_settings
                    ORDER BY model_name
                    """);
                 ResultSet rs = stmt.executeQuery())
            {
                List<ModelSettings> items = new ArrayList<>();
                while (rs.next())
                {
                    items.add(fromRow(rs));
                }
                return items;
            }
        });
    }

    public void save(ModelSettings settings) throws SQLException
    {
        database.useConnection(connection ->
        {
            try (PreparedStatement stmt = connection.prepareStatement("""
                    INSERT INTO model_settings (
                        model_name, active, context_limit, prompt_token_scale,
                        temperature_enabled, temperature,
                        top_k_enabled, top_k,
                        top_p_enabled, top_p,
                        min_p_enabled, min_p,
                        typical_p_enabled, typical_p,
                        presence_penalty_enabled, presence_penalty,
                        frequency_penalty_enabled, frequency_penalty,
                        repeat_last_n_enabled, repeat_last_n,
                        repetition_penalty_enabled, repetition_penalty
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(model_name) DO UPDATE SET
                        active = excluded.active,
                        context_limit = excluded.context_limit,
                        prompt_token_scale = excluded.prompt_token_scale,
                        temperature_enabled = excluded.temperature_enabled,
                        temperature = excluded.temperature,
                        top_k_enabled = excluded.top_k_enabled,
                        top_k = excluded.top_k,
                        top_p_enabled = excluded.top_p_enabled,
                        top_p = excluded.top_p,
                        min_p_enabled = excluded.min_p_enabled,
                        min_p = excluded.min_p,
                        typical_p_enabled = excluded.typical_p_enabled,
                        typical_p = excluded.typical_p,
                        presence_penalty_enabled = excluded.presence_penalty_enabled,
                        presence_penalty = excluded.presence_penalty,
                        frequency_penalty_enabled = excluded.frequency_penalty_enabled,
                        frequency_penalty = excluded.frequency_penalty,
                        repeat_last_n_enabled = excluded.repeat_last_n_enabled,
                        repeat_last_n = excluded.repeat_last_n,
                        repetition_penalty_enabled = excluded.repetition_penalty_enabled,
                        repetition_penalty = excluded.repetition_penalty
                    """))
            {
                stmt.setString(1, settings.modelName());
                stmt.setInt(2, settings.active() ? 1 : 0);
                stmt.setInt(3, settings.contextLimit());
                stmt.setDouble(4, settings.promptTokenScale());
                stmt.setInt(5, settings.temperatureEnabled() ? 1 : 0);
                stmt.setDouble(6, settings.temperature());
                stmt.setInt(7, settings.topKEnabled() ? 1 : 0);
                stmt.setInt(8, settings.topK());
                stmt.setInt(9, settings.topPEnabled() ? 1 : 0);
                stmt.setDouble(10, settings.topP());
                stmt.setInt(11, settings.minPEnabled() ? 1 : 0);
                stmt.setDouble(12, settings.minP());
                stmt.setInt(13, settings.typicalPEnabled() ? 1 : 0);
                stmt.setDouble(14, settings.typicalP());
                stmt.setInt(15, settings.presencePenaltyEnabled() ? 1 : 0);
                stmt.setDouble(16, settings.presencePenalty());
                stmt.setInt(17, settings.frequencyPenaltyEnabled() ? 1 : 0);
                stmt.setDouble(18, settings.frequencyPenalty());
                stmt.setInt(19, settings.repeatLastNEnabled() ? 1 : 0);
                stmt.setInt(20, settings.repeatLastN());
                stmt.setInt(21, settings.repetitionPenaltyEnabled() ? 1 : 0);
                stmt.setDouble(22, settings.repetitionPenalty());
                stmt.executeUpdate();
            }
        });
    }

    public void syncWithModels(List<String> models, ModelSettings defaults) throws SQLException
    {
        database.inTransaction(connection ->
        {
            Set<String> available = new HashSet<>(models);
            Set<String> existing = new HashSet<>();
            for (ModelSettings settings : listAll())
            {
                existing.add(settings.modelName());
            }

            for (String model : available)
            {
                Optional<ModelSettings> existingRow = load(model);
                if (existingRow.isPresent())
                {
                    ModelSettings current = existingRow.get();
                    if (!current.active())
                    {
                        save(new ModelSettings(model, true, current.contextLimit(), current.promptTokenScale(),
                                current.temperatureEnabled(), current.temperature(),
                                current.topKEnabled(), current.topK(),
                                current.topPEnabled(), current.topP(),
                                current.minPEnabled(), current.minP(),
                                current.typicalPEnabled(), current.typicalP(),
                                current.presencePenaltyEnabled(), current.presencePenalty(),
                                current.frequencyPenaltyEnabled(), current.frequencyPenalty(),
                                current.repeatLastNEnabled(), current.repeatLastN(),
                                current.repetitionPenaltyEnabled(), current.repetitionPenalty()));
                    }
                }
                else
                {
                    save(new ModelSettings(model, true, defaults.contextLimit(), defaults.promptTokenScale(),
                            defaults.temperatureEnabled(), defaults.temperature(),
                            defaults.topKEnabled(), defaults.topK(),
                            defaults.topPEnabled(), defaults.topP(),
                            defaults.minPEnabled(), defaults.minP(),
                            defaults.typicalPEnabled(), defaults.typicalP(),
                            defaults.presencePenaltyEnabled(), defaults.presencePenalty(),
                            defaults.frequencyPenaltyEnabled(), defaults.frequencyPenalty(),
                            defaults.repeatLastNEnabled(), defaults.repeatLastN(),
                            defaults.repetitionPenaltyEnabled(), defaults.repetitionPenalty()));
                }
            }

            for (String model : existing)
            {
                if (!available.contains(model))
                {
                    Optional<ModelSettings> existingRow = load(model);
                    if (existingRow.isPresent())
                    {
                        ModelSettings current = existingRow.get();
                        if (current.active())
                        {
                            save(new ModelSettings(model, false, current.contextLimit(), current.promptTokenScale(),
                                    current.temperatureEnabled(), current.temperature(),
                                    current.topKEnabled(), current.topK(),
                                    current.topPEnabled(), current.topP(),
                                    current.minPEnabled(), current.minP(),
                                    current.typicalPEnabled(), current.typicalP(),
                                    current.presencePenaltyEnabled(), current.presencePenalty(),
                                    current.frequencyPenaltyEnabled(), current.frequencyPenalty(),
                                    current.repeatLastNEnabled(), current.repeatLastN(),
                                    current.repetitionPenaltyEnabled(), current.repetitionPenalty()));
                        }
                    }
                }
            }
        });
    }

    private static ModelSettings fromRow(ResultSet rs) throws SQLException
    {
        return new ModelSettings(
                rs.getString("model_name"),
                rs.getInt("active") == 1,
                rs.getInt("context_limit"),
                rs.getDouble("prompt_token_scale"),
                rs.getInt("temperature_enabled") == 1,
                rs.getDouble("temperature"),
                rs.getInt("top_k_enabled") == 1,
                rs.getInt("top_k"),
                rs.getInt("top_p_enabled") == 1,
                rs.getDouble("top_p"),
                rs.getInt("min_p_enabled") == 1,
                rs.getDouble("min_p"),
                rs.getInt("typical_p_enabled") == 1,
                rs.getDouble("typical_p"),
                rs.getInt("presence_penalty_enabled") == 1,
                rs.getDouble("presence_penalty"),
                rs.getInt("frequency_penalty_enabled") == 1,
                rs.getDouble("frequency_penalty"),
                rs.getInt("repeat_last_n_enabled") == 1,
                rs.getInt("repeat_last_n"),
                rs.getInt("repetition_penalty_enabled") == 1,
                rs.getDouble("repetition_penalty"));
    }
}
