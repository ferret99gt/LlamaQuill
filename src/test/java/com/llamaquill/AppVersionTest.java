package com.llamaquill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AppVersionTest
{
    @Test
    void currentVersionIsTheFirstMigrationSuccessor()
    {
        assertEquals("0.1.0", AppVersion.FIRST_MIGRATION_SOURCE);
        assertEquals("0.2.0", AppVersion.CURRENT);
        assertEquals(3, AppVersion.DATABASE_SCHEMA);
        assertTrue(AppVersion.displayName().endsWith(AppVersion.CURRENT));
    }
}
