package com.softwareverde.bitcoin.server.database.impl;

import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.database.Database;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.module.DatabaseConfigurer;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.DatabaseInitializer;
import com.softwareverde.database.mysql.MysqlDatabase;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.mysql.embedded.DatabaseCommandLineArguments;
import com.softwareverde.database.mysql.embedded.EmbeddedMysqlDatabase;
import com.softwareverde.database.mysql.properties.Credentials;
import com.softwareverde.io.Logger;

public class DatabaseImpl extends Database {
    public static class InitFile {
        public final String sqlInitFile;
        public final Integer databaseVersion;

        public InitFile(final String sqlInitFile, final Integer databaseVersion) {
            this.sqlInitFile = sqlInitFile;
            this.databaseVersion = databaseVersion;
        }
    }

    public static final InitFile BITCOIN = new InitFile("/queries/bitcoin_init.sql", 1);
    public static final InitFile STRATUM = new InitFile("/queries/stratum_init.sql", 1);

    public static Database newInstance(final InitFile initFile, final Configuration.DatabaseProperties databaseProperties) {
        return newInstance(initFile, databaseProperties, 512, new Runnable() {
            @Override
            public void run() {
                // Nothing.
            }
        });
    }

    public static Database newInstance(final InitFile sqlInitFile, final Configuration.DatabaseProperties databaseProperties, final Integer maxPeerCount, final Runnable onShutdownCallback) {
        final DatabaseInitializer databaseInitializer = new DatabaseInitializer(sqlInitFile.sqlInitFile, sqlInitFile.databaseVersion, new DatabaseInitializer.DatabaseUpgradeHandler() {
            @Override
            public Boolean onUpgrade(final int currentVersion, final int requiredVersion) { return false; }
        });

        try {
            if (databaseProperties.useEmbeddedDatabase()) {
                // Initialize the embedded database...
                final DatabaseCommandLineArguments commandLineArguments = new DatabaseCommandLineArguments();
                final Integer maxDatabaseThreadCount = Math.max(512, (maxPeerCount * 8));
                DatabaseConfigurer.configureCommandLineArguments(commandLineArguments, maxDatabaseThreadCount, databaseProperties);

                Logger.log("[Initializing Database]");
                final EmbeddedMysqlDatabase embeddedMysqlDatabase = new EmbeddedMysqlDatabase(databaseProperties, databaseInitializer, commandLineArguments);

                if (onShutdownCallback != null) {
                    embeddedMysqlDatabase.setPreShutdownHook(onShutdownCallback);
                }

                return new DatabaseImpl(embeddedMysqlDatabase);
            }
            else {
                // Connect to the remote database...
                final Credentials credentials = databaseProperties.getCredentials();
                final Credentials rootCredentials = databaseProperties.getRootCredentials();
                final Credentials maintenanceCredentials = databaseInitializer.getMaintenanceCredentials(databaseProperties);

                final MysqlDatabaseConnectionFactory rootDatabaseConnectionFactory = new MysqlDatabaseConnectionFactory(databaseProperties.getHostname(), databaseProperties.getPort(), "", rootCredentials.username, rootCredentials.password);
                final MysqlDatabaseConnectionFactory maintenanceDatabaseConnectionFactory = new MysqlDatabaseConnectionFactory(databaseProperties, maintenanceCredentials);
                // final MysqlDatabaseConnectionFactory databaseConnectionFactory = new MysqlDatabaseConnectionFactory(connectionUrl, credentials.username, credentials.password);

                try (final MysqlDatabaseConnection maintenanceDatabaseConnection = maintenanceDatabaseConnectionFactory.newConnection()) {
                    final Integer databaseVersion = databaseInitializer.getDatabaseVersionNumber(maintenanceDatabaseConnection);
                    if (databaseVersion < 0) {
                        try (final MysqlDatabaseConnection rootDatabaseConnection = rootDatabaseConnectionFactory.newConnection()) {
                            databaseInitializer.initializeSchema(rootDatabaseConnection, databaseProperties);
                        }
                    }
                }
                catch (final DatabaseException exception) {
                    try (final MysqlDatabaseConnection rootDatabaseConnection = rootDatabaseConnectionFactory.newConnection()) {
                        databaseInitializer.initializeSchema(rootDatabaseConnection, databaseProperties);
                    }
                }

                try (final MysqlDatabaseConnection maintenanceDatabaseConnection = maintenanceDatabaseConnectionFactory.newConnection()) {
                    databaseInitializer.initializeDatabase(maintenanceDatabaseConnection);
                }

                return new DatabaseImpl(new MysqlDatabase(databaseProperties, credentials));
            }
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
        }

        return null;
    }

    protected DatabaseImpl(final MysqlDatabase core) {
        super(core);
    }

    @Override
    public DatabaseConnection newConnection() throws DatabaseException {
        return new DatabaseConnectionImpl(((MysqlDatabase) _core).newConnection());
    }

    @Override
    public DatabaseConnectionFactory newConnectionFactory() {
        return new DatabaseConnectionFactoryImpl(((MysqlDatabase) _core).newConnectionFactory());
    }
}
