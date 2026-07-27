package com.llamaquill.db;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class AppPathsTest
{
    @TempDir
    Path temporaryDirectory;

    @Test
    void copiesTheLegacyWorkingDirectoryDatabaseAndLeavesTheOriginalIntact() throws Exception
    {
        Path stableData = temporaryDirectory.resolve("stable");
        Path legacyData = temporaryDirectory.resolve("old-working-directory").resolve("data");
        Files.createDirectories(legacyData);
        byte[] original = { 1, 2, 3, 4 };
        Files.write(legacyData.resolve(AppPaths.DATABASE_FILE_NAME), original);

        AppPaths paths = AppPaths.forDirectories(stableData, legacyData);
        AppPaths.Preparation preparation = paths.prepare();

        assertEquals(paths.legacyDatabaseFile(), preparation.copiedLegacyDatabase().orElseThrow());
        assertArrayEquals(original, Files.readAllBytes(paths.databaseFile()));
        assertArrayEquals(original, Files.readAllBytes(paths.legacyDatabaseFile()));
        assertTrue(Files.isDirectory(paths.backupDirectory()));
    }

    @Test
    void preparationIsIdempotentAndNeverOverwritesTheStableDatabase() throws Exception
    {
        Path stableData = temporaryDirectory.resolve("stable");
        Path legacyData = temporaryDirectory.resolve("legacy");
        Files.createDirectories(stableData);
        Files.createDirectories(legacyData);
        Files.write(stableData.resolve(AppPaths.DATABASE_FILE_NAME), new byte[] { 9 });
        Files.write(legacyData.resolve(AppPaths.DATABASE_FILE_NAME), new byte[] { 1 });

        AppPaths paths = AppPaths.forDirectories(stableData, legacyData);
        AppPaths.Preparation preparation = paths.prepare();

        assertTrue(preparation.copiedLegacyDatabase().isEmpty());
        assertArrayEquals(new byte[] { 9 }, Files.readAllBytes(paths.databaseFile()));
    }
}
