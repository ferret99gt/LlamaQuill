package com.llamaquill.db;

import com.llamaquill.model.ModelAutoCardsSettings;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class ModelAutoCardsRepository
{
    private final Connection connection;

    public ModelAutoCardsRepository(Connection connection)
    {
        this.connection = connection;
    }

    public Optional<ModelAutoCardsSettings> load(String modelName) throws SQLException
    {
        try (PreparedStatement stmt = connection.prepareStatement("""
                SELECT model_name, create_prompt, update_prompt, summarize_prompt,
                       max_tokens_create, max_tokens_update, max_tokens_summarize, temperature_override
                FROM model_auto_cards
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
                Double tempOverride = null;
                try
                {
                    tempOverride = rs.getObject("temperature_override", Double.class);
                }
                catch (SQLException ignored)
                {
                    String raw = rs.getString("temperature_override");
                    if (raw != null && !raw.isBlank())
                    {
                        try
                        {
                            tempOverride = Double.parseDouble(raw.trim());
                        }
                        catch (NumberFormatException ignoredParse)
                        {
                            tempOverride = null;
                        }
                    }
                }
                return Optional.of(new ModelAutoCardsSettings(
                        rs.getString("model_name"),
                        rs.getString("create_prompt"),
                        rs.getString("update_prompt"),
                        rs.getString("summarize_prompt"),
                        rs.getInt("max_tokens_create"),
                        rs.getInt("max_tokens_update"),
                        rs.getInt("max_tokens_summarize"),
                        tempOverride));
            }
        }
    }

    public void save(ModelAutoCardsSettings settings) throws SQLException
    {
        try (PreparedStatement stmt = connection.prepareStatement("""
                INSERT INTO model_auto_cards (
                    model_name, create_prompt, update_prompt, summarize_prompt,
                    max_tokens_create, max_tokens_update, max_tokens_summarize, temperature_override
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(model_name) DO UPDATE SET
                    create_prompt = excluded.create_prompt,
                    update_prompt = excluded.update_prompt,
                    summarize_prompt = excluded.summarize_prompt,
                    max_tokens_create = excluded.max_tokens_create,
                    max_tokens_update = excluded.max_tokens_update,
                    max_tokens_summarize = excluded.max_tokens_summarize,
                    temperature_override = excluded.temperature_override
                """))
        {
            stmt.setString(1, settings.modelName());
            stmt.setString(2, settings.createPrompt());
            stmt.setString(3, settings.updatePrompt());
            stmt.setString(4, settings.summarizePrompt());
            stmt.setInt(5, settings.maxTokensCreate());
            stmt.setInt(6, settings.maxTokensUpdate());
            stmt.setInt(7, settings.maxTokensSummarize());
            if (settings.temperatureOverride() == null)
            {
                stmt.setNull(8, java.sql.Types.REAL);
            }
            else
            {
                stmt.setDouble(8, settings.temperatureOverride());
            }
            stmt.executeUpdate();
        }
    }
}
