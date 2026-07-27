package com.llamaquill.db;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class AppPaths
{
    public static final String DATA_DIRECTORY_PROPERTY = "llamaquill.dataDir";
    public static final String DATA_DIRECTORY_ENVIRONMENT = "LLAMAQUILL_DATA_DIR";
    public static final String DATABASE_FILE_NAME = "llamaquill.db";

    private final Path dataDirectory;
    private final Path databaseFile;
    private final Path backupDirectory;
    private final Path legacyDatabaseFile;

    private AppPaths(Path dataDirectory, Path legacyDataDirectory)
    {
        this.dataDirectory = normalize(dataDirectory);
        databaseFile = this.dataDirectory.resolve(DATABASE_FILE_NAME);
        backupDirectory = this.dataDirectory.resolve("backups");
        legacyDatabaseFile = normalize(legacyDataDirectory).resolve(DATABASE_FILE_NAME);
    }

    public static AppPaths resolve()
    {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        String override = firstNonBlank(
                System.getProperty(DATA_DIRECTORY_PROPERTY),
                System.getenv(DATA_DIRECTORY_ENVIRONMENT));
        Path dataDirectory = override == null ? defaultDataDirectory() : Path.of(override);
        return new AppPaths(dataDirectory, workingDirectory.resolve("data"));
    }

    public static AppPaths forDirectories(Path dataDirectory, Path legacyDataDirectory)
    {
        return new AppPaths(
                Objects.requireNonNull(dataDirectory, "dataDirectory"),
                Objects.requireNonNull(legacyDataDirectory, "legacyDataDirectory"));
    }

    public Path dataDirectory()
    {
        return dataDirectory;
    }

    public Path databaseFile()
    {
        return databaseFile;
    }

    public Path backupDirectory()
    {
        return backupDirectory;
    }

    public Path legacyDatabaseFile()
    {
        return legacyDatabaseFile;
    }

    public Preparation prepare() throws IOException
    {
        Files.createDirectories(dataDirectory);
        Files.createDirectories(backupDirectory);

        if (Files.exists(databaseFile) || !Files.isRegularFile(legacyDatabaseFile)
                || databaseFile.equals(legacyDatabaseFile))
        {
            return new Preparation(Optional.empty());
        }

        copyLegacyDatabaseAtomically();
        return new Preparation(Optional.of(legacyDatabaseFile));
    }

    private void copyLegacyDatabaseAtomically() throws IOException
    {
        Path temporaryDatabase = dataDirectory.resolve(DATABASE_FILE_NAME + ".migrating");
        Path temporaryWal = dataDirectory.resolve(DATABASE_FILE_NAME + ".migrating-wal");
        Path temporaryShm = dataDirectory.resolve(DATABASE_FILE_NAME + ".migrating-shm");

        Files.deleteIfExists(temporaryDatabase);
        Files.deleteIfExists(temporaryWal);
        Files.deleteIfExists(temporaryShm);

        try
        {
            Files.copy(legacyDatabaseFile, temporaryDatabase, StandardCopyOption.COPY_ATTRIBUTES);
            copyIfPresent(companion(legacyDatabaseFile, "-wal"), temporaryWal);
            copyIfPresent(companion(legacyDatabaseFile, "-shm"), temporaryShm);

            moveIfPresent(temporaryWal, companion(databaseFile, "-wal"));
            moveIfPresent(temporaryShm, companion(databaseFile, "-shm"));
            moveAtomically(temporaryDatabase, databaseFile);
        }
        catch (IOException e)
        {
            if (!Files.exists(databaseFile))
            {
                Files.deleteIfExists(companion(databaseFile, "-wal"));
                Files.deleteIfExists(companion(databaseFile, "-shm"));
            }
            throw e;
        }
        finally
        {
            Files.deleteIfExists(temporaryDatabase);
            Files.deleteIfExists(temporaryWal);
            Files.deleteIfExists(temporaryShm);
        }
    }

    private static Path defaultDataDirectory()
    {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String userHome = System.getProperty("user.home", ".");

        if (osName.contains("win"))
        {
            String localAppData = System.getenv("LOCALAPPDATA");
            Path base = localAppData == null || localAppData.isBlank()
                    ? Path.of(userHome, "AppData", "Local")
                    : Path.of(localAppData);
            return base.resolve("LlamaQuill");
        }
        if (osName.contains("mac"))
        {
            return Path.of(userHome, "Library", "Application Support", "LlamaQuill");
        }

        String xdgDataHome = System.getenv("XDG_DATA_HOME");
        Path base = xdgDataHome == null || xdgDataHome.isBlank()
                ? Path.of(userHome, ".local", "share")
                : Path.of(xdgDataHome);
        return base.resolve("llamaquill");
    }

    private static void copyIfPresent(Path source, Path target) throws IOException
    {
        if (Files.isRegularFile(source))
        {
            Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private static void moveIfPresent(Path source, Path target) throws IOException
    {
        if (Files.exists(source))
        {
            moveAtomically(source, target);
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException
    {
        try
        {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (AtomicMoveNotSupportedException e)
        {
            Files.move(source, target);
        }
    }

    private static Path companion(Path database, String suffix)
    {
        return database.resolveSibling(database.getFileName() + suffix);
    }

    private static Path normalize(Path path)
    {
        return path.toAbsolutePath().normalize();
    }

    private static String firstNonBlank(String first, String second)
    {
        if (first != null && !first.isBlank())
        {
            return first.trim();
        }
        if (second != null && !second.isBlank())
        {
            return second.trim();
        }
        return null;
    }

    public record Preparation(Optional<Path> copiedLegacyDatabase)
    {
        public Preparation
        {
            copiedLegacyDatabase = Objects.requireNonNull(copiedLegacyDatabase, "copiedLegacyDatabase");
        }
    }
}
