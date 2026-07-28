package com.llamaquill.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoundedLruCacheTest
{
    @Test
    void evictsTheLeastRecentlyUsedEntryAtTheConfiguredBound()
    {
        BoundedLruCache<String, String> cache = new BoundedLruCache<>(2);
        cache.put("first", "one");
        cache.put("second", "two");

        assertEquals("one", cache.get("first"));
        cache.put("third", "three");

        assertEquals(2, cache.size());
        assertNull(cache.get("second"));
        assertEquals("one", cache.get("first"));
        assertEquals("three", cache.get("third"));
    }

    @Test
    void rejectsAnUnboundedOrEmptyConfiguration()
    {
        assertThrows(IllegalArgumentException.class, () -> new BoundedLruCache<>(0));
    }
}
