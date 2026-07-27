package com.llamaquill.db;

import com.llamaquill.model.StoryAutoCardsSettings;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class StoryAutoCardsRepository
{
    private final Database database;

    public StoryAutoCardsRepository(Database database)
    {
        this.database = database;
    }

    public Optional<StoryAutoCardsSettings> load(String storyId) throws SQLException
    {
        return database.withConnection(connection ->
        {
            try (PreparedStatement stmt = connection.prepareStatement("""
                    SELECT story_id, enabled, update_existing, create_new, pin_new, preview_first
                    FROM story_auto_cards
                    WHERE story_id = ?
                    """))
            {
                stmt.setString(1, storyId);
                try (ResultSet rs = stmt.executeQuery())
                {
                    if (!rs.next())
                    {
                        return Optional.empty();
                    }
                    return Optional.of(new StoryAutoCardsSettings(
                            rs.getString("story_id"),
                            rs.getInt("enabled") == 1,
                            rs.getInt("update_existing") == 1,
                            rs.getInt("create_new") == 1,
                            rs.getInt("pin_new") == 1,
                            rs.getInt("preview_first") == 1));
                }
            }
        });
    }

    public void save(StoryAutoCardsSettings settings) throws SQLException
    {
        database.useConnection(connection ->
        {
            try (PreparedStatement stmt = connection.prepareStatement("""
                    INSERT INTO story_auto_cards (
                        story_id, enabled, update_existing, create_new, pin_new, preview_first
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT(story_id) DO UPDATE SET
                        enabled = excluded.enabled,
                        update_existing = excluded.update_existing,
                        create_new = excluded.create_new,
                        pin_new = excluded.pin_new,
                        preview_first = excluded.preview_first
                    """))
            {
                stmt.setString(1, settings.storyId());
                stmt.setInt(2, settings.enabled() ? 1 : 0);
                stmt.setInt(3, settings.updateExisting() ? 1 : 0);
                stmt.setInt(4, settings.createNew() ? 1 : 0);
                stmt.setInt(5, settings.pinNew() ? 1 : 0);
                stmt.setInt(6, settings.previewFirst() ? 1 : 0);
                stmt.executeUpdate();
            }
        });
    }
}
