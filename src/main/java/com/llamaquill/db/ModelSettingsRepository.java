package com.llamaquill.db;

import com.llamaquill.model.ModelSettings;

import java.sql.Connection;
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
    private final Connection connection;

    public ModelSettingsRepository(Connection connection)
    {
        this.connection = connection;
    }

    public Optional<ModelSettings> load(String modelName) throws SQLException
    {
        try (PreparedStatement stmt = connection.prepareStatement("""
                SELECT model_name, active, temperature, top_k, top_p, min_p, presence_penalty,
                       frequency_penalty, repetition_penalty
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
    }

    public List<ModelSettings> listActive() throws SQLException
    {
        try (PreparedStatement stmt = connection.prepareStatement("""
                SELECT model_name, active, temperature, top_k, top_p, min_p, presence_penalty,
                       frequency_penalty, repetition_penalty
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
    }

    public List<ModelSettings> listAll() throws SQLException
    {
        try (PreparedStatement stmt = connection.prepareStatement("""
                SELECT model_name, active, temperature, top_k, top_p, min_p, presence_penalty,
                       frequency_penalty, repetition_penalty
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
    }

    public void save(ModelSettings settings) throws SQLException
    {
        try (PreparedStatement stmt = connection.prepareStatement("""
                INSERT INTO model_settings (
                    model_name, active, temperature, top_k, top_p, min_p,
                    presence_penalty, frequency_penalty, repetition_penalty
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(model_name) DO UPDATE SET
                    active = excluded.active,
                    temperature = excluded.temperature,
                    top_k = excluded.top_k,
                    top_p = excluded.top_p,
                    min_p = excluded.min_p,
                    presence_penalty = excluded.presence_penalty,
                    frequency_penalty = excluded.frequency_penalty,
                    repetition_penalty = excluded.repetition_penalty
                """))
        {
            stmt.setString(1, settings.modelName());
            stmt.setInt(2, settings.active() ? 1 : 0);
            stmt.setDouble(3, settings.temperature());
            stmt.setInt(4, settings.topK());
            stmt.setDouble(5, settings.topP());
            stmt.setDouble(6, settings.minP());
            stmt.setDouble(7, settings.presencePenalty());
            stmt.setDouble(8, settings.frequencyPenalty());
            stmt.setDouble(9, settings.repetitionPenalty());
            stmt.executeUpdate();
        }
    }

    public void syncWithModels(List<String> models, ModelSettings defaults) throws SQLException
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
                    save(new ModelSettings(model, true, current.temperature(), current.topK(),
                            current.topP(), current.minP(), current.presencePenalty(),
                            current.frequencyPenalty(), current.repetitionPenalty()));
                }
            }
            else
            {
                save(new ModelSettings(model, true, defaults.temperature(), defaults.topK(),
                        defaults.topP(), defaults.minP(), defaults.presencePenalty(),
                        defaults.frequencyPenalty(), defaults.repetitionPenalty()));
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
                        save(new ModelSettings(model, false, current.temperature(), current.topK(),
                                current.topP(), current.minP(), current.presencePenalty(),
                                current.frequencyPenalty(), current.repetitionPenalty()));
                    }
                }
            }
        }
    }

    private static ModelSettings fromRow(ResultSet rs) throws SQLException
    {
        return new ModelSettings(
                rs.getString("model_name"),
                rs.getInt("active") == 1,
                rs.getDouble("temperature"),
                rs.getInt("top_k"),
                rs.getDouble("top_p"),
                rs.getDouble("min_p"),
                rs.getDouble("presence_penalty"),
                rs.getDouble("frequency_penalty"),
                rs.getDouble("repetition_penalty"));
    }
}
