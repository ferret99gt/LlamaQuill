package com.llamaquill.session;

import com.llamaquill.model.Block;
import com.llamaquill.model.Role;
import com.llamaquill.model.Story;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryWorkspaceTest
{
    @Test
    void ownsAnImmutableSnapshotAndAdvancesTheSessionWithItsHead()
    {
        StoryWorkspace workspace = new StoryWorkspace();
        List<Block> source = new ArrayList<>(List.of(block("first", "story-a", "one")));

        StorySession opened = workspace.open(story("story-a", "First title"), source);
        source.add(block("outside", "story-a", "not part of the snapshot"));

        assertEquals(1, workspace.blocks().size());
        assertThrows(UnsupportedOperationException.class,
                () -> workspace.blocks().add(block("mutate", "story-a", "no")));
        assertTrue(workspace.canApply(opened));

        StorySession advanced = workspace.advance(List.of(
                block("first", "story-a", "one"),
                block("second", "story-a", "two")));

        assertEquals("second", workspace.blocks().getLast().id());
        assertEquals("second", advanced.headBlockId());
        assertEquals(opened.revision() + 1, advanced.revision());
        assertFalse(workspace.canApply(opened));
    }

    @Test
    void openingAnotherStoryRejectsResultsFromThePreviousWorkspace()
    {
        StoryWorkspace workspace = new StoryWorkspace();
        StorySession first = workspace.open(
                story("story-a", "First"),
                List.of(block("same-looking-head", "story-a", "one")));

        StorySession second = workspace.open(
                story("story-b", "Second"),
                List.of(block("same-looking-head", "story-b", "two")));

        assertFalse(workspace.canApply(first));
        assertTrue(workspace.canApply(second));
        assertNotEquals(first.revision(), second.revision());
    }

    @Test
    void updatesOnlyTheOpenStoryAndOpenHead()
    {
        StoryWorkspace workspace = new StoryWorkspace();
        workspace.open(story("story-a", "Old"), List.of(block("head", "story-a", "old")));

        workspace.updateStory(story("story-a", "New"));
        workspace.replaceHead(block("head", "story-a", "new"));

        assertEquals("New", workspace.story().title());
        assertEquals("new", workspace.blocks().getLast().text());
        assertThrows(IllegalArgumentException.class,
                () -> workspace.updateStory(story("story-b", "Wrong")));
        assertThrows(IllegalArgumentException.class,
                () -> workspace.replaceHead(block("other", "story-a", "Wrong")));
    }

    @Test
    void validatesBlockOwnershipAndClearsAllCoupledState()
    {
        StoryWorkspace workspace = new StoryWorkspace();

        assertThrows(IllegalArgumentException.class,
                () -> workspace.open(story("story-a", "Story"),
                        List.of(block("wrong", "story-b", "Wrong story"))));

        workspace.open(story("story-a", "Story"), List.of());
        workspace.clear();

        assertFalse(workspace.isOpen());
        assertNull(workspace.story());
        assertNull(workspace.session());
        assertTrue(workspace.blocks().isEmpty());
        assertThrows(IllegalStateException.class, () -> workspace.advance(List.of()));
    }

    @Test
    void appliesAndConditionallyRollsBackOptimisticBlockEdits()
    {
        StoryWorkspace workspace = new StoryWorkspace();
        workspace.open(story("story-a", "Story"), List.of(block("block", "story-a", "old")));

        assertTrue(workspace.updateBlockText("block", "old", "new"));
        assertEquals("new", workspace.findBlock("block").text());
        assertFalse(workspace.updateBlockText("block", "old", "stale overwrite"));
        assertTrue(workspace.updateBlockText("block", "new", "old"));
        assertEquals("old", workspace.findBlock("block").text());
    }

    private static Story story(String id, String title)
    {
        return new Story(id, title, "system", "plot", "author", "created", "updated");
    }

    private static Block block(String id, String storyId, String text)
    {
        return new Block(id, storyId, Role.ASSISTANT, text, "created", 0);
    }
}
