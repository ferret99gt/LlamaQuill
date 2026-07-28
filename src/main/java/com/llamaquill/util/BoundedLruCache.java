package com.llamaquill.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small synchronized least-recently-used cache for heavyweight local assets.
 */
public final class BoundedLruCache<K, V>
{
    private final int maximumEntries;
    private final Map<K, V> entries;

    public BoundedLruCache(int maximumEntries)
    {
        if (maximumEntries < 1)
        {
            throw new IllegalArgumentException("maximumEntries must be positive");
        }
        this.maximumEntries = maximumEntries;
        this.entries = new LinkedHashMap<>(maximumEntries, 0.75f, true)
        {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest)
            {
                return size() > BoundedLruCache.this.maximumEntries;
            }
        };
    }

    public synchronized V get(K key)
    {
        return entries.get(key);
    }

    public synchronized void put(K key, V value)
    {
        entries.put(key, value);
    }

    public synchronized void remove(K key)
    {
        entries.remove(key);
    }

    public synchronized int size()
    {
        return entries.size();
    }

    public synchronized void clear()
    {
        entries.clear();
    }
}
