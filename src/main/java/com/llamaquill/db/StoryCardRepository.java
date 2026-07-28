package com.llamaquill.db;

import com.llamaquill.model.StoryCard;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StoryCardRepository
{
    private final Database database;

    public StoryCardRepository(Database database)
    {
        this.database = database;
    }

    public void insert(StoryCard card) throws SQLException
    {
        database.useConnection(connection ->
        {
            try (PreparedStatement stmt = connection.prepareStatement("""
                    INSERT INTO story_cards (
                        id, story_id, title, triggers, content, type, notes, pinned
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """))
            {
                stmt.setString(1, card.id());
                stmt.setString(2, card.storyId());
                stmt.setString(3, card.title());
                stmt.setString(4, card.triggers());
                stmt.setString(5, card.content());
                stmt.setString(6, card.type());
                stmt.setString(7, card.notes());
                stmt.setInt(8, card.pinned() ? 1 : 0);
                stmt.executeUpdate();
            }
        });
    }

    public void update(StoryCard card) throws SQLException
    {
        database.useConnection(connection ->
        {
            try (PreparedStatement stmt = connection.prepareStatement("""
                    UPDATE story_cards
                    SET title = ?, triggers = ?, content = ?, type = ?, notes = ?, pinned = ?
                    WHERE id = ?
                    """))
            {
                stmt.setString(1, card.title());
                stmt.setString(2, card.triggers());
                stmt.setString(3, card.content());
                stmt.setString(4, card.type());
                stmt.setString(5, card.notes());
                stmt.setInt(6, card.pinned() ? 1 : 0);
                stmt.setString(7, card.id());
                stmt.executeUpdate();
            }
        });
    }

    public List<StoryCard> listForStory(String storyId) throws SQLException
    {
        return database.withConnection(connection ->
        {
            try (PreparedStatement stmt = connection.prepareStatement("""
                    SELECT id, story_id, title, triggers, content, type, notes, pinned
                    FROM story_cards
                    WHERE story_id = ?
                    ORDER BY
                        CASE WHEN TRIM(type) = '' THEN 'Untyped' ELSE type END COLLATE NOCASE,
                        pinned DESC,
                        title COLLATE NOCASE
                    """))
            {
                stmt.setString(1, storyId);
                try (ResultSet rs = stmt.executeQuery())
                {
                    List<StoryCard> cards = new ArrayList<>();
                    while (rs.next())
                    {
                        cards.add(mapCard(rs));
                    }
                    return cards;
                }
            }
        });
    }

    public Optional<StoryCard> findById(String id) throws SQLException
    {
        return database.withConnection(connection ->
        {
            try (PreparedStatement stmt = connection.prepareStatement("""
                    SELECT id, story_id, title, triggers, content, type, notes, pinned
                    FROM story_cards
                    WHERE id = ?
                    """))
            {
                stmt.setString(1, id);
                try (ResultSet rs = stmt.executeQuery())
                {
                    return rs.next() ? Optional.of(mapCard(rs)) : Optional.empty();
                }
            }
        });
    }

    public void deleteForStory(String storyId) throws SQLException
    {
        database.useConnection(connection ->
        {
            try (PreparedStatement stmt = connection.prepareStatement("""
                    DELETE FROM story_cards WHERE story_id = ?
                    """))
            {
                stmt.setString(1, storyId);
                stmt.executeUpdate();
            }
        });
    }

    public void delete(String id) throws SQLException
    {
        database.useConnection(connection ->
        {
            try (PreparedStatement stmt = connection.prepareStatement("""
                    DELETE FROM story_cards WHERE id = ?
                    """))
            {
                stmt.setString(1, id);
                stmt.executeUpdate();
            }
        });
    }

    private StoryCard mapCard(ResultSet rs) throws SQLException
    {
        return new StoryCard(rs.getString("id"), rs.getString("story_id"), rs.getString("title"), rs.getString("triggers"),
                rs.getString("content"), rs.getString("type"), rs.getString("notes"), rs.getInt("pinned") == 1);
    }
}
