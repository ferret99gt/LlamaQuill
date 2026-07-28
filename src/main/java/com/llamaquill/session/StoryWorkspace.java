package com.llamaquill.session;

import com.llamaquill.model.Block;
import com.llamaquill.model.Story;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Owns the in-memory state for the story currently open in the application.
 *
 * <p>The workspace keeps the story, its rendered block snapshot, and the
 * session revision together so callers cannot update one without advancing the
 * others. It deliberately has no JavaFX or persistence dependencies.</p>
 */
public final class StoryWorkspace
{
    private Story story;
    private List<Block> blocks = List.of();
    private StorySession session;
    private long nextSessionRevision;

    public boolean isOpen()
    {
        return story != null;
    }

    public Story story()
    {
        return story;
    }

    public List<Block> blocks()
    {
        return blocks;
    }

    public StorySession session()
    {
        return session;
    }

    public StorySession open(Story story, List<Block> blocks)
    {
        Story requestedStory = Objects.requireNonNull(story, "story");
        List<Block> requestedBlocks = snapshotFor(requestedStory.id(), blocks);
        this.story = requestedStory;
        this.blocks = requestedBlocks;
        this.session = StorySession.open(requestedStory.id(), ++nextSessionRevision, requestedBlocks);
        return session;
    }

    public void clear()
    {
        story = null;
        blocks = List.of();
        session = null;
    }

    public void updateStory(Story updatedStory)
    {
        Story requestedStory = Objects.requireNonNull(updatedStory, "updatedStory");
        requireOpen();
        if (!story.id().equals(requestedStory.id()))
        {
            throw new IllegalArgumentException("Updated story must match the open story");
        }
        story = requestedStory;
    }

    public StorySession advance(List<Block> updatedBlocks)
    {
        requireOpen();
        blocks = snapshotFor(story.id(), updatedBlocks);
        session = session.advance(blocks);
        return session;
    }

    public StorySession advance(Story updatedStory, List<Block> updatedBlocks)
    {
        updateStory(updatedStory);
        return advance(updatedBlocks);
    }

    public StorySession replaceHead(Block updatedHead)
    {
        requireOpen();
        Block requestedHead = Objects.requireNonNull(updatedHead, "updatedHead");
        if (blocks.isEmpty() || !blocks.getLast().id().equals(requestedHead.id()))
        {
            throw new IllegalArgumentException("Updated block must match the open story head");
        }
        if (!story.id().equals(requestedHead.storyId()))
        {
            throw new IllegalArgumentException("Updated block must belong to the open story");
        }

        List<Block> updatedBlocks = new ArrayList<>(blocks);
        updatedBlocks.set(updatedBlocks.size() - 1, requestedHead);
        return advance(updatedBlocks);
    }

    public Block findBlock(String blockId)
    {
        if (blockId == null)
        {
            return null;
        }
        for (Block block : blocks)
        {
            if (blockId.equals(block.id()))
            {
                return block;
            }
        }
        return null;
    }

    public boolean updateBlockText(String blockId, String expectedText, String updatedText)
    {
        requireOpen();
        Block existing = findBlock(blockId);
        if (existing == null || !Objects.equals(existing.text(), expectedText))
        {
            return false;
        }
        Block updated = new Block(existing.id(), existing.storyId(), existing.role(),
                updatedText == null ? "" : updatedText, existing.createdAt(), existing.position());
        List<Block> updatedBlocks = new ArrayList<>(blocks);
        updatedBlocks.set(updatedBlocks.indexOf(existing), updated);
        advance(updatedBlocks);
        return true;
    }

    public boolean canApply(StorySession source)
    {
        return session != null && session.canApplyResultFrom(source);
    }

    private void requireOpen()
    {
        if (story == null || session == null)
        {
            throw new IllegalStateException("No story is open");
        }
    }

    private static List<Block> snapshotFor(String storyId, List<Block> blocks)
    {
        Objects.requireNonNull(blocks, "blocks");
        List<Block> snapshot = List.copyOf(blocks);
        for (Block block : snapshot)
        {
            if (block == null || !storyId.equals(block.storyId()))
            {
                throw new IllegalArgumentException("Every block must belong to the open story");
            }
        }
        return snapshot;
    }
}
