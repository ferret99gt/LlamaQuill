package com.llamaquill.db;

import com.llamaquill.model.Story;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StoryRepository
{
    private final Database database;

    public StoryRepository(Database database)
    {
        this.database = database;
    }

    public void insert(Story story) throws SQLException
    {
        database.useConnection(connection ->
        {
            try (PreparedStatement stmt = connection.prepareStatement("""
                    INSERT INTO stories (
                        id, title, system_prompt, plot_essentials, author_note, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """))
            {
                stmt.setString(1, story.id());
                stmt.setString(2, story.title());
                stmt.setString(3, story.systemPrompt());
                stmt.setString(4, story.plotEssentials());
                stmt.setString(5, story.authorNote());
                stmt.setString(6, story.createdAt());
                stmt.setString(7, story.updatedAt());
                stmt.executeUpdate();
            }
        });
    }

    public void update(Story story) throws SQLException
    {
        database.useConnection(connection ->
        {
            try (PreparedStatement stmt = connection.prepareStatement(
                    """
                            UPDATE stories
                            SET title = ?, system_prompt = ?, plot_essentials = ?, author_note = ?, updated_at = ?
                            WHERE id = ?
                            """))
            {
                stmt.setString(1, story.title());
                stmt.setString(2, story.systemPrompt());
                stmt.setString(3, story.plotEssentials());
                stmt.setString(4, story.authorNote());
                stmt.setString(5, story.updatedAt());
                stmt.setString(6, story.id());
                stmt.executeUpdate();
            }
        });
    }

    public void updateTitle(String storyId, String title, String updatedAt) throws SQLException
    {
        database.useConnection(connection ->
        {
            try (PreparedStatement stmt = connection.prepareStatement(
                    """
                            UPDATE stories
                            SET title = ?, updated_at = ?
                            WHERE id = ?
                            """))
            {
                stmt.setString(1, title);
                stmt.setString(2, updatedAt);
                stmt.setString(3, storyId);
                stmt.executeUpdate();
            }
        });
    }

    public Story touch(String storyId, String updatedAt) throws SQLException
    {
        return database.transaction(connection ->
        {
            try (PreparedStatement stmt = connection.prepareStatement("""
                    UPDATE stories
                    SET updated_at = ?
                    WHERE id = ?
                    """))
            {
                stmt.setString(1, updatedAt);
                stmt.setString(2, storyId);
                if (stmt.executeUpdate() != 1)
                {
                    throw new SQLException("Story no longer exists: " + storyId);
                }
            }
            return findById(storyId).orElseThrow(
                    () -> new SQLException("Story no longer exists: " + storyId));
        });
    }

    public Optional<Story> findById(String id) throws SQLException
    {
        return database.withConnection(connection ->
        {
            try (PreparedStatement stmt = connection.prepareStatement(
                    """
                            SELECT id, title, system_prompt, plot_essentials, author_note, created_at, updated_at
                            FROM stories WHERE id = ?
                            """))
            {
                stmt.setString(1, id);
                try (ResultSet rs = stmt.executeQuery())
                {
                    if (!rs.next())
                    {
                        return Optional.empty();
                    }
                    return Optional.of(mapStory(rs));
                }
            }
        });
    }

    public List<Story> listAll() throws SQLException
    {
        return database.withConnection(connection ->
        {
            try (PreparedStatement stmt = connection.prepareStatement(
                    """
                            SELECT id, title, system_prompt, plot_essentials, author_note, created_at, updated_at
                            FROM stories
                            ORDER BY updated_at DESC
                            """))
            {
                try (ResultSet rs = stmt.executeQuery())
                {
                    List<Story> stories = new ArrayList<>();
                    while (rs.next())
                    {
                        stories.add(mapStory(rs));
                    }
                    return stories;
                }
            }
        });
    }

    public void delete(String id) throws SQLException
    {
        database.useConnection(connection ->
        {
            try (PreparedStatement stmt = connection.prepareStatement("""
                    DELETE FROM stories WHERE id = ?
                    """))
            {
                stmt.setString(1, id);
                stmt.executeUpdate();
            }
        });
    }

    private Story mapStory(ResultSet rs) throws SQLException
    {
        return new Story(rs.getString("id"), rs.getString("title"), rs.getString("system_prompt"),
                rs.getString("plot_essentials"), rs.getString("author_note"),
                rs.getString("created_at"), rs.getString("updated_at"));
    }
}
