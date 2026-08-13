package com.llamaquill.db;

import com.llamaquill.model.StoryImage;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class ImageRepository
{
    private final Database database;

    public ImageRepository(Database database)
    {
        this.database = database;
    }

    public void insert(StoryImage image) throws SQLException
    {
        database.useConnection(connection ->
        {
            try (PreparedStatement stmt = connection.prepareStatement("""
                    INSERT INTO images (
                        id, story_id, prompt, mime_type, width, height, batch_size,
                        workflow_json, image_bytes, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """))
            {
                stmt.setString(1, image.id());
                stmt.setString(2, image.storyId());
                stmt.setString(3, image.prompt());
                stmt.setString(4, image.mimeType());
                stmt.setInt(5, image.width());
                stmt.setInt(6, image.height());
                stmt.setInt(7, image.batchSize());
                stmt.setString(8, image.workflowJson());
                stmt.setBytes(9, image.imageBytes());
                stmt.setString(10, image.createdAt());
                stmt.executeUpdate();
            }
        });
    }

    public Optional<StoryImage> findById(String id) throws SQLException
    {
        return database.withConnection(connection ->
        {
            try (PreparedStatement stmt = connection.prepareStatement("""
                    SELECT id, story_id, prompt, mime_type, width, height, batch_size,
                           workflow_json, image_bytes, created_at
                    FROM images
                    WHERE id = ?
                    """))
            {
                stmt.setString(1, id);
                try (ResultSet rs = stmt.executeQuery())
                {
                    if (!rs.next())
                    {
                        return Optional.empty();
                    }
                    return Optional.of(mapImage(rs));
                }
            }
        });
    }

    public void deleteById(String id) throws SQLException
    {
        database.useConnection(connection ->
        {
            try (PreparedStatement stmt = connection.prepareStatement("""
                    DELETE FROM images WHERE id = ?
                    """))
            {
                stmt.setString(1, id);
                stmt.executeUpdate();
            }
        });
    }

    private StoryImage mapImage(ResultSet rs) throws SQLException
    {
        return new StoryImage(
                rs.getString("id"),
                rs.getString("story_id"),
                rs.getString("prompt"),
                rs.getString("mime_type"),
                rs.getInt("width"),
                rs.getInt("height"),
                rs.getInt("batch_size"),
                rs.getString("workflow_json"),
                rs.getBytes("image_bytes"),
                rs.getString("created_at"));
    }
}
