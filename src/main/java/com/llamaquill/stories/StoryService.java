package com.llamaquill.stories;

import com.llamaquill.db.BlockRepository;
import com.llamaquill.db.StoryRepository;
import com.llamaquill.model.Block;
import com.llamaquill.model.Story;
import com.llamaquill.util.Ids;
import com.llamaquill.util.Timestamps;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public final class StoryService
{
    private final StoryRepository storyRepository;
    private final BlockRepository blockRepository;

    public StoryService(StoryRepository storyRepository, BlockRepository blockRepository)
    {
        this.storyRepository = Objects.requireNonNull(storyRepository, "storyRepository");
        this.blockRepository = Objects.requireNonNull(blockRepository, "blockRepository");
    }

    public List<Story> list() throws SQLException
    {
        return storyRepository.listAll();
    }

    public StoryDocument loadOrCreate(String defaultTitle, String defaultSystemPrompt) throws SQLException
    {
        List<Story> stories = list();
        Story story = stories.isEmpty()
                ? create(defaultTitle, defaultSystemPrompt)
                : stories.getFirst();
        return load(story);
    }

    public Story create(String title, String defaultSystemPrompt) throws SQLException
    {
        String normalizedTitle = requireTitle(title);
        String now = Timestamps.now();
        Story story = new Story(Ids.newId(), normalizedTitle, value(defaultSystemPrompt), "", "", now, now);
        storyRepository.insert(story);
        return story;
    }

    public StoryDocument load(Story story) throws SQLException
    {
        Objects.requireNonNull(story, "story");
        return new StoryDocument(story, blockRepository.listForStory(story.id()));
    }

    public StoryDocument reload(String storyId, Story preferredStory) throws SQLException
    {
        String id = requireId(storyId, "storyId");
        Story story = preferredStory != null && id.equals(preferredStory.id())
                ? preferredStory
                : storyRepository.findById(id).orElseThrow(
                        () -> new SQLException("Story no longer exists: " + id));
        return load(story);
    }

    public Story rename(Story story, String title) throws SQLException
    {
        Objects.requireNonNull(story, "story");
        String normalizedTitle = requireTitle(title);
        if (normalizedTitle.equals(story.title()))
        {
            return story;
        }
        Story updated = new Story(story.id(), normalizedTitle, story.systemPrompt(), story.plotEssentials(),
                story.authorNote(), story.storyCardGenerationContext(), story.forcePinAllStoryCards(),
                story.createdAt(), Timestamps.now());
        storyRepository.updateTitle(updated.id(), updated.title(), updated.updatedAt());
        return updated;
    }

    public Story updateDetails(Story story, String systemPrompt, String plotEssentials, String authorNote)
            throws SQLException
    {
        Objects.requireNonNull(story, "story");
        String normalizedSystem = value(systemPrompt);
        String normalizedPlot = value(plotEssentials);
        String normalizedNote = value(authorNote);
        if (normalizedSystem.equals(story.systemPrompt())
                && normalizedPlot.equals(story.plotEssentials())
                && normalizedNote.equals(story.authorNote()))
        {
            return story;
        }
        Story updated = new Story(story.id(), story.title(), normalizedSystem, normalizedPlot, normalizedNote,
                story.storyCardGenerationContext(), story.forcePinAllStoryCards(),
                story.createdAt(), Timestamps.now());
        storyRepository.update(updated);
        return updated;
    }

    public Story updateStoryCardGenerationContext(Story story, String context) throws SQLException
    {
        Objects.requireNonNull(story, "story");
        String normalizedContext = value(context).trim();
        if (normalizedContext.equals(story.storyCardGenerationContext()))
        {
            return story;
        }
        return storyRepository.updateStoryCardGenerationContext(
                story.id(), normalizedContext, Timestamps.now());
    }

    public Story updateForcePinAllStoryCards(Story story, boolean forcePinAll) throws SQLException
    {
        Objects.requireNonNull(story, "story");
        if (forcePinAll == story.forcePinAllStoryCards())
        {
            return story;
        }
        return storyRepository.updateForcePinAllStoryCards(
                story.id(), forcePinAll, Timestamps.now());
    }

    public Story touch(Story story) throws SQLException
    {
        Objects.requireNonNull(story, "story");
        return storyRepository.touch(story.id(), Timestamps.now());
    }

    public void delete(Story story) throws SQLException
    {
        Objects.requireNonNull(story, "story");
        storyRepository.delete(story.id());
    }

    private static String requireTitle(String title)
    {
        String normalized = value(title).trim();
        if (normalized.isEmpty())
        {
            throw new IllegalArgumentException("Story title is required.");
        }
        return normalized;
    }

    private static String requireId(String id, String name)
    {
        String normalized = value(id).trim();
        if (normalized.isEmpty())
        {
            throw new IllegalArgumentException(name + " is required.");
        }
        return normalized;
    }

    private static String value(String value)
    {
        return value == null ? "" : value;
    }

    public record StoryDocument(Story story, List<Block> blocks)
    {
        public StoryDocument
        {
            story = Objects.requireNonNull(story, "story");
            blocks = List.copyOf(blocks == null ? List.of() : blocks);
        }
    }
}
