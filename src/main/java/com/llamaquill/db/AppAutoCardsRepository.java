package com.llamaquill.db;

import com.llamaquill.model.AppAutoCardsSettings;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class AppAutoCardsRepository
{
    private static final int SETTINGS_ID = 1;

    private final Connection connection;

    public AppAutoCardsRepository(Connection connection)
    {
        this.connection = connection;
    }

    public Optional<AppAutoCardsSettings> load() throws SQLException
    {
        try (PreparedStatement stmt = connection.prepareStatement("""
                SELECT run_mode, min_gap_seconds, default_enabled, cooldown_turns, max_cards_per_run,
                       candidate_window, card_length_limit, summarize_instead_of_trim, verbosity, logging_level
                FROM app_auto_cards
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
                return Optional.of(new AppAutoCardsSettings(
                        rs.getString("run_mode"),
                        rs.getInt("min_gap_seconds"),
                        rs.getInt("default_enabled") == 1,
                        rs.getInt("cooldown_turns"),
                        rs.getInt("max_cards_per_run"),
                        rs.getInt("candidate_window"),
                        rs.getInt("card_length_limit"),
                        rs.getInt("summarize_instead_of_trim") == 1,
                        rs.getString("verbosity"),
                        rs.getString("logging_level")));
            }
        }
    }

    public void save(AppAutoCardsSettings settings) throws SQLException
    {
        try (PreparedStatement stmt = connection.prepareStatement("""
                INSERT INTO app_auto_cards (
                    id, run_mode, min_gap_seconds, default_enabled, cooldown_turns, max_cards_per_run,
                    candidate_window, card_length_limit, summarize_instead_of_trim, verbosity, logging_level
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    run_mode = excluded.run_mode,
                    min_gap_seconds = excluded.min_gap_seconds,
                    default_enabled = excluded.default_enabled,
                    cooldown_turns = excluded.cooldown_turns,
                    max_cards_per_run = excluded.max_cards_per_run,
                    candidate_window = excluded.candidate_window,
                    card_length_limit = excluded.card_length_limit,
                    summarize_instead_of_trim = excluded.summarize_instead_of_trim,
                    verbosity = excluded.verbosity,
                    logging_level = excluded.logging_level
                """))
        {
            stmt.setInt(1, SETTINGS_ID);
            stmt.setString(2, settings.runMode());
            stmt.setInt(3, settings.minGapSeconds());
            stmt.setInt(4, settings.defaultEnabled() ? 1 : 0);
            stmt.setInt(5, settings.cooldownTurns());
            stmt.setInt(6, settings.maxCardsPerRun());
            stmt.setInt(7, settings.candidateWindow());
            stmt.setInt(8, settings.cardLengthLimit());
            stmt.setInt(9, settings.summarizeInsteadOfTrim() ? 1 : 0);
            stmt.setString(10, settings.verbosity());
            stmt.setString(11, settings.loggingLevel());
            stmt.executeUpdate();
        }
    }
}
