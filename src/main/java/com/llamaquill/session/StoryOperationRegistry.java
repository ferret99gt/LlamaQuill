package com.llamaquill.session;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class StoryOperationRegistry
{
    private final AtomicLong sequence = new AtomicLong();
    private final Map<Long, Operation> active = new HashMap<>();

    public synchronized Operation begin(StorySession session, String kind)
    {
        Objects.requireNonNull(session, "session");
        String normalizedKind = kind == null || kind.isBlank() ? "background work" : kind.trim();
        Operation operation = new Operation(sequence.incrementAndGet(), session, normalizedKind);
        active.put(operation.id(), operation);
        return operation;
    }

    public synchronized boolean complete(Operation operation)
    {
        return operation != null && active.remove(operation.id(), operation);
    }

    public synchronized int activeCount(String storyId)
    {
        int count = 0;
        for (Operation operation : active.values())
        {
            if (operation.session().storyId().equals(storyId))
            {
                count++;
            }
        }
        return count;
    }

    public synchronized boolean hasActive(String storyId)
    {
        return activeCount(storyId) > 0;
    }

    public synchronized void cancelAll()
    {
        for (Operation operation : active.values())
        {
            operation.cancel();
        }
        active.clear();
    }

    public static final class Operation
    {
        private final long id;
        private final StorySession session;
        private final String kind;
        private volatile boolean cancelled;

        private Operation(long id, StorySession session, String kind)
        {
            this.id = id;
            this.session = session;
            this.kind = kind;
        }

        public long id()
        {
            return id;
        }

        public StorySession session()
        {
            return session;
        }

        public String kind()
        {
            return kind;
        }

        public boolean cancelled()
        {
            return cancelled;
        }

        private void cancel()
        {
            cancelled = true;
        }
    }
}
