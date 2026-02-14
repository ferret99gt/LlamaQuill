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
                SELECT cooldown_turns, max_cards_per_run,
                       candidate_window, card_length_limit, summarize_instead_of_trim,
                       candidate_selection_mode, context_mode
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
                        rs.getInt("cooldown_turns"),
                        rs.getInt("max_cards_per_run"),
                        rs.getInt("candidate_window"),
                        rs.getInt("card_length_limit"),
                        rs.getInt("summarize_instead_of_trim") == 1,
                        rs.getString("candidate_selection_mode"),
                        rs.getString("context_mode")));
            }
        }
    }

    public void save(AppAutoCardsSettings settings) throws SQLException
    {
        try (PreparedStatement stmt = connection.prepareStatement("""
                INSERT INTO app_auto_cards (
                    id, cooldown_turns, max_cards_per_run,
                    candidate_window, card_length_limit, summarize_instead_of_trim,
                    candidate_selection_mode, context_mode
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    cooldown_turns = excluded.cooldown_turns,
                    max_cards_per_run = excluded.max_cards_per_run,
                    candidate_window = excluded.candidate_window,
                    card_length_limit = excluded.card_length_limit,
                    summarize_instead_of_trim = excluded.summarize_instead_of_trim,
                    candidate_selection_mode = excluded.candidate_selection_mode,
                    context_mode = excluded.context_mode
                """))
        {
            stmt.setInt(1, SETTINGS_ID);
            stmt.setInt(2, settings.cooldownTurns());
            stmt.setInt(3, settings.maxCardsPerRun());
            stmt.setInt(4, settings.candidateWindow());
            stmt.setInt(5, settings.cardLengthLimit());
            stmt.setInt(6, settings.summarizeInsteadOfTrim() ? 1 : 0);
            stmt.setString(7, settings.candidateSelectionMode());
            stmt.setString(8, settings.contextMode());
            stmt.executeUpdate();
        }
    }
}
