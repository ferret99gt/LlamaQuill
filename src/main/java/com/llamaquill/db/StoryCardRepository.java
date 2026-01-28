package com.llamaquill.db;

import com.llamaquill.model.StoryCard;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class StoryCardRepository
{
    private final Connection connection;

    public StoryCardRepository(Connection connection)
    {
        this.connection = connection;
    }

    public void insert(StoryCard card) throws SQLException
    {
        try (PreparedStatement stmt = connection.prepareStatement("""
                INSERT INTO story_cards (
                    id, story_id, title, triggers, content, pinned
                ) VALUES (?, ?, ?, ?, ?, ?)
                """))
        {
            stmt.setString(1, card.id());
            stmt.setString(2, card.storyId());
            stmt.setString(3, card.title());
            stmt.setString(4, card.triggers());
            stmt.setString(5, card.content());
            stmt.setInt(6, card.pinned() ? 1 : 0);
            stmt.executeUpdate();
        }
    }

    public void update(StoryCard card) throws SQLException
    {
        try (PreparedStatement stmt = connection.prepareStatement("""
                UPDATE story_cards
                SET title = ?, triggers = ?, content = ?, pinned = ?
                WHERE id = ?
                """))
        {
            stmt.setString(1, card.title());
            stmt.setString(2, card.triggers());
            stmt.setString(3, card.content());
            stmt.setInt(4, card.pinned() ? 1 : 0);
            stmt.setString(5, card.id());
            stmt.executeUpdate();
        }
    }

    public List<StoryCard> listForStory(String storyId) throws SQLException
    {
        try (PreparedStatement stmt = connection.prepareStatement("""
                SELECT id, story_id, title, triggers, content, pinned
                FROM story_cards
                WHERE story_id = ?
                ORDER BY pinned DESC, title ASC
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
    }

    public void delete(String id) throws SQLException
    {
        try (PreparedStatement stmt = connection.prepareStatement("""
                DELETE FROM story_cards WHERE id = ?
                """))
        {
            stmt.setString(1, id);
            stmt.executeUpdate();
        }
    }

    private StoryCard mapCard(ResultSet rs) throws SQLException
    {
        return new StoryCard(rs.getString("id"), rs.getString("story_id"), rs.getString("title"),
                rs.getString("triggers"), rs.getString("content"), rs.getInt("pinned") == 1);
    }
}
