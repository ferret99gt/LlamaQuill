package com.llamaquill.session;

import com.llamaquill.model.Block;

import java.util.List;
import java.util.Objects;

public record StorySession(String storyId, long revision, String headBlockId)
{
    public StorySession
    {
        if (storyId == null || storyId.isBlank())
        {
            throw new IllegalArgumentException("storyId must not be blank");
        }
    }

    public static StorySession open(String storyId, long revision, List<Block> blocks)
    {
        Objects.requireNonNull(blocks, "blocks");
        String headBlockId = blocks.isEmpty() ? null : blocks.getLast().id();
        return new StorySession(storyId, revision, headBlockId);
    }

    public boolean sameStory(StorySession other)
    {
        return other != null && storyId.equals(other.storyId);
    }

    public boolean sameView(StorySession other)
    {
        return equals(other);
    }

    public boolean canApplyResultFrom(StorySession source)
    {
        return source != null && storyId.equals(source.storyId)
                && (revision == source.revision || Objects.equals(headBlockId, source.headBlockId));
    }

    public StorySession advance(List<Block> blocks)
    {
        return open(storyId, revision + 1, blocks);
    }
}
