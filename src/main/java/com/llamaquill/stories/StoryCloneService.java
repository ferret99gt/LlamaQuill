package com.llamaquill.stories;

import com.llamaquill.db.BlockRepository;
import com.llamaquill.db.Database;
import com.llamaquill.db.ImageRepository;
import com.llamaquill.db.StoryCardRepository;
import com.llamaquill.db.StoryRepository;
import com.llamaquill.model.Block;
import com.llamaquill.model.Role;
import com.llamaquill.model.Story;
import com.llamaquill.model.StoryCard;
import com.llamaquill.model.StoryImage;
import com.llamaquill.util.Ids;
import com.llamaquill.util.Timestamps;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public final class StoryCloneService
{
    private final Database database;
    private final StoryRepository storyRepository;
    private final BlockRepository blockRepository;
    private final StoryCardRepository cardRepository;
    private final ImageRepository imageRepository;

    public StoryCloneService(Database database, StoryRepository storyRepository, BlockRepository blockRepository,
            StoryCardRepository cardRepository, ImageRepository imageRepository)
    {
        this.database = Objects.requireNonNull(database, "database");
        this.storyRepository = Objects.requireNonNull(storyRepository, "storyRepository");
        this.blockRepository = Objects.requireNonNull(blockRepository, "blockRepository");
        this.cardRepository = Objects.requireNonNull(cardRepository, "cardRepository");
        this.imageRepository = Objects.requireNonNull(imageRepository, "imageRepository");
    }

    public Story cloneStory(String sourceStoryId, StoryCloneRequest request) throws SQLException
    {
        String normalizedSourceId = requireValue(sourceStoryId, "Source story is required.");
        Objects.requireNonNull(request, "request");
        String normalizedTitle = requireValue(request.newName(), "Story name cannot be empty.");

        return database.transaction(connection ->
        {
            Story source = storyRepository.findById(normalizedSourceId).orElseThrow(
                    () -> new SQLException("Story no longer exists: " + normalizedSourceId));
            String now = Timestamps.now();
            Story clone = new Story(
                    Ids.newId(),
                    normalizedTitle,
                    request.includeStoryDetails() ? source.systemPrompt() : "",
                    request.includeStoryDetails() ? source.plotEssentials() : "",
                    request.includeStoryDetails() ? source.authorNote() : "",
                    request.includeStoryDetails() ? source.storyCardGenerationContext() : "",
                    request.includeStoryDetails() && source.forcePinAllStoryCards(),
                    now,
                    now);
            storyRepository.insert(clone);

            if (request.includeStoryCards())
            {
                cloneCards(source.id(), clone.id());
            }
            cloneBlocks(source.id(), clone.id(), request, now);
            return clone;
        });
    }

    private void cloneCards(String sourceStoryId, String cloneStoryId) throws SQLException
    {
        for (StoryCard source : cardRepository.listForStory(sourceStoryId))
        {
            cardRepository.insert(new StoryCard(
                    Ids.newId(), cloneStoryId, source.title(), source.triggers(), source.content(),
                    source.type(), source.notes(), source.pinned()));
        }
    }

    private void cloneBlocks(String sourceStoryId, String cloneStoryId, StoryCloneRequest request, String createdAt)
            throws SQLException
    {
        if (!request.includeInitialBlock() && !request.includeAllBlocks())
        {
            return;
        }

        List<Block> sourceBlocks = blockRepository.listForStory(sourceStoryId);
        int blockCount = request.includeAllBlocks() ? sourceBlocks.size() : Math.min(1, sourceBlocks.size());
        for (int index = 0; index < blockCount; index++)
        {
            Block source = sourceBlocks.get(index);
            String clonedText = source.text();
            if (source.role() == Role.IMAGE)
            {
                clonedText = cloneImage(sourceStoryId, cloneStoryId, source.text(), createdAt);
            }
            blockRepository.insert(new Block(
                    Ids.newId(), cloneStoryId, source.role(), clonedText, createdAt, index));
        }
    }

    private String cloneImage(String sourceStoryId, String cloneStoryId, String imageId, String createdAt)
            throws SQLException
    {
        StoryImage source = imageRepository.findById(imageId).orElseThrow(
                () -> new SQLException("Story image no longer exists: " + imageId));
        if (!sourceStoryId.equals(source.storyId()))
        {
            throw new SQLException("Story image does not belong to the source story: " + imageId);
        }

        String cloneImageId = Ids.newId();
        byte[] imageBytes = source.imageBytes() == null ? null : source.imageBytes().clone();
        imageRepository.insert(new StoryImage(
                cloneImageId, cloneStoryId, source.prompt(), source.mimeType(), source.width(), source.height(),
                source.workflowJson(), imageBytes, createdAt));
        return cloneImageId;
    }

    private static String requireValue(String value, String message)
    {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty())
        {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
