package com.llamaquill.stories;

import com.llamaquill.db.BlockRepository;
import com.llamaquill.db.Database;
import com.llamaquill.model.Block;
import com.llamaquill.model.Role;
import com.llamaquill.model.Story;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public final class StoryBlockService
{
    private final Database database;
    private final BlockRepository blockRepository;
    private final ImageDeleter imageDeleter;

    public StoryBlockService(Database database, BlockRepository blockRepository, ImageDeleter imageDeleter)
    {
        this.database = Objects.requireNonNull(database, "database");
        this.blockRepository = Objects.requireNonNull(blockRepository, "blockRepository");
        this.imageDeleter = Objects.requireNonNull(imageDeleter, "imageDeleter");
    }

    public List<Block> deleteHead(Story story, Block expectedHead) throws SQLException
    {
        requireOwnedBlock(story, expectedHead);
        database.inTransaction(connection ->
        {
            if (!blockRepository.deleteHeadIfCurrent(story.id(), expectedHead.id()))
            {
                throw staleBlock(expectedHead.id());
            }
            deleteLinkedImage(expectedHead);
        });
        return blockRepository.listForStory(story.id());
    }

    public List<Block> delete(Story story, Block block) throws SQLException
    {
        requireOwnedBlock(story, block);
        database.inTransaction(connection ->
        {
            if (!blockRepository.deleteByIdForStory(block.id(), story.id()))
            {
                throw staleBlock(block.id());
            }
            deleteLinkedImage(block);
        });
        return blockRepository.listForStory(story.id());
    }

    public void updateText(Block block, String text) throws SQLException
    {
        Objects.requireNonNull(block, "block");
        if (!blockRepository.updateTextForStory(block.id(), block.storyId(), value(text)))
        {
            throw staleBlock(block.id());
        }
    }

    public boolean replaceHeadIfCurrent(Block block) throws SQLException
    {
        Objects.requireNonNull(block, "block");
        return blockRepository.replaceHeadIfCurrent(block);
    }

    private void deleteLinkedImage(Block block) throws SQLException
    {
        if (block.role() == Role.IMAGE)
        {
            imageDeleter.delete(block.text());
        }
    }

    private static void requireOwnedBlock(Story story, Block block)
    {
        Objects.requireNonNull(story, "story");
        Objects.requireNonNull(block, "block");
        if (!story.id().equals(block.storyId()))
        {
            throw new IllegalArgumentException("Block does not belong to the selected story.");
        }
    }

    private static SQLException staleBlock(String blockId)
    {
        return new SQLException("Story block changed or no longer exists: " + blockId);
    }

    private static String value(String value)
    {
        return value == null ? "" : value;
    }

    @FunctionalInterface
    public interface ImageDeleter
    {
        void delete(String imageId) throws SQLException;
    }
}
