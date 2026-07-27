package com.llamaquill.db;

import com.llamaquill.AppVersion;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Database implements AutoCloseable
{
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final AppPaths paths;
    private final String jdbcUrl;
    private final ThreadLocal<Connection> transactionConnection = new ThreadLocal<>();
    private final Set<Connection> openConnections = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();
    private StartupReport startupReport;

    private Database(AppPaths paths)
    {
        this.paths = Objects.requireNonNull(paths, "paths");
        jdbcUrl = "jdbc:sqlite:" + paths.databaseFile();
    }

    public static Database open() throws SQLException
    {
        return open(AppPaths.resolve());
    }

    public static Database open(AppPaths paths) throws SQLException
    {
        Objects.requireNonNull(paths, "paths");
        AppPaths.Preparation preparation;
        try
        {
            preparation = paths.prepare();
        }
        catch (IOException e)
        {
            throw new SQLException("Failed to prepare the LlamaQuill data directory: " + paths.dataDirectory(), e);
        }

        Database database = new Database(paths);
        try
        {
            DatabaseMigrator.MigrationReport migration = database.withConnection(
                    connection -> DatabaseMigrator.migrate(connection, paths));
            database.startupReport = new StartupReport(preparation, migration);
            return database;
        }
        catch (SQLException | RuntimeException e)
        {
            database.closeAfterFailedOpen(e);
            throw e;
        }
    }

    public AppPaths paths()
    {
        return paths;
    }

    public StartupReport startupReport()
    {
        return startupReport;
    }

    public Path createBackup() throws SQLException
    {
        String timestamp = BACKUP_TIMESTAMP.format(LocalDateTime.now(ZoneOffset.UTC));
        Path backup = paths.backupDirectory().resolve(
                "llamaquill-manual-" + AppVersion.CURRENT + "-" + timestamp + ".db");
        useConnection(connection ->
        {
            try (PreparedStatement statement = connection.prepareStatement("VACUUM INTO ?"))
            {
                statement.setString(1, backup.toString());
                statement.execute();
            }
        });
        return backup;
    }

    public Diagnostics diagnostics() throws SQLException
    {
        return withConnection(connection ->
        {
            int schemaVersion = scalarInt(connection, "PRAGMA user_version");
            String integrity = scalarText(connection, "PRAGMA integrity_check");
            String journalMode = scalarText(connection, "PRAGMA journal_mode");
            int foreignKeyViolations;
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("PRAGMA foreign_key_check"))
            {
                foreignKeyViolations = 0;
                while (result.next())
                {
                    foreignKeyViolations++;
                }
            }
            int orphanImages = scalarInt(connection, """
                    SELECT COUNT(*)
                    FROM images image
                    LEFT JOIN blocks block ON block.role = 'image' AND block.text = image.id
                    WHERE block.id IS NULL
                    """);
            int brokenImageBlocks = scalarInt(connection, """
                    SELECT COUNT(*)
                    FROM blocks block
                    LEFT JOIN images image ON image.id = block.text
                    WHERE block.role = 'image' AND image.id IS NULL
                    """);
            return new Diagnostics(paths.databaseFile(), schemaVersion, integrity, journalMode, foreignKeyViolations,
                    orphanImages, brokenImageBlocks);
        });
    }

    public <T> T withConnection(SqlWork<T> work) throws SQLException
    {
        Objects.requireNonNull(work, "work");
        Connection current = transactionConnection.get();
        if (current != null)
        {
            return work.execute(current);
        }

        Connection connection = openConnection();
        try
        {
            return work.execute(connection);
        }
        finally
        {
            closeConnection(connection);
        }
    }

    public void useConnection(SqlAction action) throws SQLException
    {
        Objects.requireNonNull(action, "action");
        withConnection(connection ->
        {
            action.execute(connection);
            return null;
        });
    }

    public <T> T transaction(SqlWork<T> work) throws SQLException
    {
        Objects.requireNonNull(work, "work");
        Connection current = transactionConnection.get();
        if (current != null)
        {
            return work.execute(current);
        }

        Connection connection = openConnection();
        boolean oldAutoCommit = connection.getAutoCommit();
        try
        {
            connection.setAutoCommit(false);
            transactionConnection.set(connection);
            T result = work.execute(connection);
            connection.commit();
            return result;
        }
        catch (SQLException | RuntimeException | Error e)
        {
            rollback(connection, e);
            throw e;
        }
        finally
        {
            transactionConnection.remove();
            try
            {
                connection.setAutoCommit(oldAutoCommit);
            }
            finally
            {
                closeConnection(connection);
            }
        }
    }

    public void inTransaction(SqlAction action) throws SQLException
    {
        Objects.requireNonNull(action, "action");
        transaction(connection ->
        {
            action.execute(connection);
            return null;
        });
    }

    private Connection openConnection() throws SQLException
    {
        if (closed.get())
        {
            throw new SQLException("Database is closed.");
        }

        Connection connection = DriverManager.getConnection(jdbcUrl);
        boolean configured = false;
        try
        {
            try (Statement statement = connection.createStatement())
            {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA busy_timeout = 5000");
            }
            openConnections.add(connection);
            configured = true;
            return connection;
        }
        finally
        {
            if (!configured)
            {
                connection.close();
            }
        }
    }

    private void closeConnection(Connection connection) throws SQLException
    {
        openConnections.remove(connection);
        connection.close();
    }

    private static void rollback(Connection connection, Throwable failure)
    {
        try
        {
            connection.rollback();
        }
        catch (SQLException rollbackFailure)
        {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private static int scalarInt(Connection connection, String sql) throws SQLException
    {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql))
        {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private static String scalarText(Connection connection, String sql) throws SQLException
    {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql))
        {
            return result.next() ? result.getString(1) : "";
        }
    }

    private void closeAfterFailedOpen(Throwable failure)
    {
        try
        {
            close();
        }
        catch (SQLException closeFailure)
        {
            failure.addSuppressed(closeFailure);
        }
    }

    @Override
    public void close() throws SQLException
    {
        if (!closed.compareAndSet(false, true))
        {
            return;
        }

        SQLException failure = null;
        for (Connection connection : openConnections)
        {
            try
            {
                connection.close();
            }
            catch (SQLException e)
            {
                if (failure == null)
                {
                    failure = e;
                }
                else
                {
                    failure.addSuppressed(e);
                }
            }
        }
        openConnections.clear();
        transactionConnection.remove();
        if (failure != null)
        {
            throw failure;
        }
    }

    @FunctionalInterface
    public interface SqlWork<T>
    {
        T execute(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlAction
    {
        void execute(Connection connection) throws SQLException;
    }

    public record StartupReport(AppPaths.Preparation pathPreparation,
            DatabaseMigrator.MigrationReport migration)
    {
        public StartupReport
        {
            pathPreparation = Objects.requireNonNull(pathPreparation, "pathPreparation");
            migration = Objects.requireNonNull(migration, "migration");
        }
    }

    public record Diagnostics(Path databaseFile, int schemaVersion, String integrityResult,
            String journalMode, int foreignKeyViolations, int orphanImages, int brokenImageBlocks)
    {
        public boolean healthy()
        {
            return "ok".equalsIgnoreCase(integrityResult) && foreignKeyViolations == 0
                    && orphanImages == 0 && brokenImageBlocks == 0;
        }
    }
}
