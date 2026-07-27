package com.llamaquill.session;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class StoryRetryHistory<T>
{
    private Scope scope;
    private final List<T> entries = new ArrayList<>();
    private int selectedIndex = -1;

    public void activate(StorySession session)
    {
        Scope requested = Scope.from(session);
        if (!requested.equals(scope))
        {
            scope = requested;
            entries.clear();
            selectedIndex = -1;
        }
    }

    public void clear(StorySession session)
    {
        activate(session);
        entries.clear();
        selectedIndex = -1;
    }

    public void add(StorySession session, T entry)
    {
        activate(session);
        entries.add(Objects.requireNonNull(entry, "entry"));
        selectedIndex = entries.size() - 1;
    }

    public List<T> entries(StorySession session)
    {
        activate(session);
        return List.copyOf(entries);
    }

    public int size(StorySession session)
    {
        activate(session);
        return entries.size();
    }

    public boolean isEmpty(StorySession session)
    {
        return size(session) == 0;
    }

    public int selectedIndex(StorySession session)
    {
        activate(session);
        return selectedIndex;
    }

    public void select(StorySession session, int index)
    {
        activate(session);
        if (index < 0 || index >= entries.size())
        {
            throw new IndexOutOfBoundsException(index);
        }
        selectedIndex = index;
    }

    public T selected(StorySession session)
    {
        activate(session);
        if (selectedIndex < 0 || selectedIndex >= entries.size())
        {
            return null;
        }
        return entries.get(selectedIndex);
    }

    private record Scope(String storyId, String headBlockId)
    {
        private static Scope from(StorySession session)
        {
            Objects.requireNonNull(session, "session");
            return new Scope(session.storyId(), session.headBlockId());
        }
    }
}
