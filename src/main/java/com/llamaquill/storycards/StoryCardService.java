package com.llamaquill.storycards;

import com.llamaquill.db.StoryCardRepository;
import com.llamaquill.model.StoryCard;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public final class StoryCardService
{
    private final StoryCardRepository repository;

    public StoryCardService(StoryCardRepository repository)
    {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public List<StoryCard> listForStory(String storyId) throws SQLException
    {
        return repository.listForStory(requireId(storyId, "storyId"));
    }

    public void create(StoryCard card) throws SQLException
    {
        requireCard(card);
        if (repository.findById(card.id()).isPresent())
        {
            throw new SQLException("Story card already exists: " + card.id());
        }
        repository.insert(card);
    }

    public void update(StoryCard card) throws SQLException
    {
        requireCard(card);
        StoryCard existing = repository.findById(card.id()).orElseThrow(
                () -> new SQLException("Story card no longer exists: " + card.id()));
        if (!existing.storyId().equals(card.storyId()))
        {
            throw new IllegalArgumentException("A story card cannot be moved to another story.");
        }
        repository.update(card);
    }

    public void delete(StoryCard card) throws SQLException
    {
        requireCard(card);
        StoryCard existing = repository.findById(card.id()).orElseThrow(
                () -> new SQLException("Story card no longer exists: " + card.id()));
        if (!existing.storyId().equals(card.storyId()))
        {
            throw new IllegalArgumentException("Story card ownership changed before deletion.");
        }
        repository.delete(card.id());
    }

    private static StoryCard requireCard(StoryCard card)
    {
        Objects.requireNonNull(card, "card");
        requireId(card.id(), "card.id");
        requireId(card.storyId(), "card.storyId");
        return card;
    }

    private static String requireId(String id, String name)
    {
        String normalized = id == null ? "" : id.trim();
        if (normalized.isEmpty())
        {
            throw new IllegalArgumentException(name + " is required.");
        }
        return normalized;
    }
}
