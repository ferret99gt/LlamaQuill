package com.llamaquill.db;

import com.llamaquill.model.Block;
import com.llamaquill.model.Role;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BlockRepository
{
    private final Connection connection;

    public BlockRepository(Connection connection)
    {
        this.connection = connection;
    }

    public void insert(Block block) throws SQLException
    {
        try (PreparedStatement stmt = connection.prepareStatement("""
                INSERT INTO blocks (
                    id, story_id, role, text, created_at, position
                ) VALUES (?, ?, ?, ?, ?, ?)
                """))
        {
            stmt.setString(1, block.id());
            stmt.setString(2, block.storyId());
            stmt.setString(3, block.role().wire());
            stmt.setString(4, block.text());
            stmt.setString(5, block.createdAt());
            stmt.setInt(6, block.position());
            stmt.executeUpdate();
        }
    }

    public List<Block> listForStory(String storyId) throws SQLException
    {
        try (PreparedStatement stmt = connection.prepareStatement("""
                SELECT id, story_id, role, text, created_at, position
                FROM blocks
                WHERE story_id = ?
                ORDER BY position ASC
                """))
        {
            stmt.setString(1, storyId);
            try (ResultSet rs = stmt.executeQuery())
            {
                List<Block> blocks = new ArrayList<>();
                while (rs.next())
                {
                    blocks.add(mapBlock(rs));
                }
                return blocks;
            }
        }
    }

    public Optional<Block> findHead(String storyId) throws SQLException
    {
        try (PreparedStatement stmt = connection.prepareStatement("""
                SELECT id, story_id, role, text, created_at, position
                FROM blocks
                WHERE story_id = ?
                ORDER BY position DESC
                LIMIT 1
                """))
        {
            stmt.setString(1, storyId);
            try (ResultSet rs = stmt.executeQuery())
            {
                if (!rs.next())
                {
                    return Optional.empty();
                }
                return Optional.of(mapBlock(rs));
            }
        }
    }

    public int nextPosition(String storyId) throws SQLException
    {
        try (PreparedStatement stmt = connection.prepareStatement("""
                SELECT COALESCE(MAX(position), 0) + 1 AS next_pos
                FROM blocks
                WHERE story_id = ?
                """))
        {
            stmt.setString(1, storyId);
            try (ResultSet rs = stmt.executeQuery())
            {
                return rs.getInt("next_pos");
            }
        }
    }

    public void deleteById(String id) throws SQLException
    {
        try (PreparedStatement stmt = connection.prepareStatement("""
                DELETE FROM blocks WHERE id = ?
                """))
        {
            stmt.setString(1, id);
            stmt.executeUpdate();
        }
    }

    public void deleteHead(String storyId) throws SQLException
    {
        try (PreparedStatement stmt = connection.prepareStatement("""
                DELETE FROM blocks
                WHERE id = (
                    SELECT id FROM blocks WHERE story_id = ? ORDER BY position DESC LIMIT 1
                )
                """))
        {
            stmt.setString(1, storyId);
            stmt.executeUpdate();
        }
    }

    public void updateText(String id, String text) throws SQLException
    {
        try (PreparedStatement stmt = connection.prepareStatement("""
                UPDATE blocks SET text = ? WHERE id = ?
                """))
        {
            stmt.setString(1, text);
            stmt.setString(2, id);
            stmt.executeUpdate();
        }
    }

    public void replaceHead(Block block) throws SQLException
    {
        try (PreparedStatement stmt = connection.prepareStatement("""
                UPDATE blocks
                SET text = ?, created_at = ?, role = ?
                WHERE id = ?
                """))
        {
            stmt.setString(1, block.text());
            stmt.setString(2, block.createdAt());
            stmt.setString(3, block.role().wire());
            stmt.setString(4, block.id());
            stmt.executeUpdate();
        }
    }

    private Block mapBlock(ResultSet rs) throws SQLException
    {
        return new Block(rs.getString("id"), rs.getString("story_id"),
                Role.fromWire(rs.getString("role")), rs.getString("text"),
                rs.getString("created_at"), rs.getInt("position"));
    }
}
