/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.dbclient.health;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.reactive.Awaitable;
import io.helidon.dbclient.DbClient;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;

/**
 * Database health check.
 */
public abstract class DbClientHealthCheck implements HealthCheck {

    /* Local logger instance. */
    private static final Logger LOGGER = Logger.getLogger(DbClientHealthCheck.class.getName());
    /* Default hHealth check timeout in seconds (to wait for statement execution response). */
    private static final int DEFAULT_TIMEOUT_SECONDS = 10;

    /**
     * Create a health check with default settings for the database.
     * This health check will execute DML statement named {@code ping} to verify database status.
     *
     * @param dbClient A database that implements {@link io.helidon.dbclient.DbClient#ping()}
     * @return health check that can be used with
     * {@link io.helidon.health.HealthSupport.Builder#addReadiness(org.eclipse.microprofile.health.HealthCheck...)}
     * or {@link io.helidon.health.HealthSupport.Builder#addLiveness(org.eclipse.microprofile.health.HealthCheck...)}
     */
    public static DbClientHealthCheck create(DbClient dbClient) {
        return builder(dbClient).build();
    }

    /**
     * A fluent API builder to create a fully customized database health check.
     *
     * @param dbClient database
     * @return a new builder
     */
    public static Builder builder(DbClient dbClient) {
        return new Builder(dbClient);
    }

    /* Helidon database client. */
    private final DbClient dbClient;
    /* Health check name. */
    private final String name;
    /* Health check timeout length (to wait for statement execution response). */
    private final long timeoutDuration;
    /* Health check timeout units (to wait for statement execution response). */
    private final TimeUnit timeoutUnit;

    private DbClientHealthCheck(Builder builder) {
        this.dbClient = builder.database;
        this.name = builder.name;
        this.timeoutDuration = builder.timeoutDuration;
        this.timeoutUnit = builder.timeoutUnit;
    }

    /**
     * Execute the ping statement.
     *
     * @return {@code Awaitable} instance to wait for
     */
    protected abstract Awaitable<?> execPing();

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = HealthCheckResponse.builder().name(name);

        try {
            execPing().await(timeoutDuration, timeoutUnit);
            builder.up();
        } catch (Throwable e) {
            builder.down();
            builder.withData("ErrorMessage", e.getMessage());
            builder.withData("ErrorClass", e.getClass().getName());
            LOGGER.log(Level.FINER, e, () -> String.format(
                    "Database %s is not responding: %s", dbClient.dbType(), e.getMessage()));
        }

        return builder.build();
    }

    protected DbClient dbClient() {
        return dbClient;
    }

    /**
     * Database health check which calls default DBClient's {@code ping} method.
     */
    private static final class DbClientHealthCheckAsPing extends DbClientHealthCheck {

        private DbClientHealthCheckAsPing(Builder builder) {
            super(builder);
            LOGGER.finest("Created an instance of DbClientHealthCheckAsPing");
        }

        @Override
        protected Awaitable<Void> execPing() {
            return dbClient().ping();
        }

    }

    /**
     * Database health check which calls DBClient's {@code namedDml} method.
     */
    private static final class DbClientHealthCheckAsNamedDml extends DbClientHealthCheck {

        /* Name of the statement. */
        private final String statementName;

        private DbClientHealthCheckAsNamedDml(Builder builder) {
            super(builder);
            this.statementName = builder.statementName;
            LOGGER.finest("Created an instance of DbClientHealthCheckAsNamedDml");
        }

        @Override
        protected Awaitable<?> execPing() {
            return dbClient().execute(exec -> exec.namedDml(statementName));
        }

    }

    /**
     * Database health check which calls DBClient's {@code dml} method.
     */
    private static final class DbClientHealthCheckAsDml extends DbClientHealthCheck {

        /* Custom statement. */
        private final String statement;

        private DbClientHealthCheckAsDml(Builder builder) {
            super(builder);
            this.statement = builder.statement;
            LOGGER.finest("Created an instance of DbClientHealthCheckAsDml");
        }

        @Override
        protected Awaitable<?> execPing() {
            return dbClient().execute(exec -> exec.dml(statement));
        }

    }

    /**
     * Database health check which calls DBClient's {@code namedQuery} method.
     */
    private static final class DbClientHealthCheckAsNamedQuery extends DbClientHealthCheck {

        /* Name of the statement. */
        private final String statementName;

        private DbClientHealthCheckAsNamedQuery(Builder builder) {
            super(builder);
            this.statementName = builder.statementName;
            LOGGER.finest("Created an instance of DbClientHealthCheckAsNamedQuery");
        }

        @Override
        protected Awaitable<?> execPing() {
            return dbClient()
                    .execute(exec -> exec.namedQuery(statementName).forEach(it -> {}));
        }

    }

    /**
     * Database health check which calls DBClient's {@code query} method.
     */
    private static final class DbClientHealthCheckAsQuery extends DbClientHealthCheck {

        /* Custom statement. */
        private final String statement;

        private DbClientHealthCheckAsQuery(Builder builder) {
            super(builder);
            this.statement = builder.statement;
            LOGGER.finest("Created an instance of DbClientHealthCheckAsQuery");
        }

        @Override
        protected Awaitable<?> execPing() {
            return dbClient()
                    .execute(exec -> exec.query(statement).forEach(it -> {}));
        }

    }

    /**
     * Fluent API builder for {@link DbClientHealthCheck}.
     * Default health check setup will call named DML statement with name {@code ping}.
     * This named DML statement shall be configured in {@code statements} section
     * of the DBClient configuration file.
     */
    public static final class Builder implements io.helidon.common.Builder<DbClientHealthCheck> {

        /* Helidon database client. */
        private final DbClient database;
        /* Health check name. */
        private String name;
        /* Health check timeout length (to wait for statement execution response). */
        private long timeoutDuration;
        /* Health check timeout units (to wait for statement execution response). */
        private TimeUnit timeoutUnit;

        // Those two boolean variables define 4 ways of query execution:
        //
        // +-----------+----------+--------+------------+-------+
        // | DbExecute | namedDML | dml    | namedQuery | query |
        // +-----------+----------+--------+------------+-------+
        // | isDML     | true     | true   | false      | false |
        // | named     | true     | faslse | true       | false |
        // +-----------+----------+--------+------------+-------+
        // The best performance optimized solution seems to be polymorphysm for part of check method.

        /* Health check statement is DML when {@code true} and query when {@code false}. */
        private boolean isDML;
        /* Whether to use named statement or statement passed as an argument. */
        private boolean isNamedstatement;

        /** Name of the statement. */
        private String statementName;
        /** Custom statement. */
        private String statement;

        private Builder(DbClient database) {
            this.database = database;
            this.name = database.dbType();
            this.timeoutDuration = DEFAULT_TIMEOUT_SECONDS;
            this.timeoutUnit = TimeUnit.SECONDS;
            this.isDML = true;
            this.isNamedstatement = true;
            this.statementName = null;
            this.statement = null;
        }

        // Defines polymorphysm for ping statement execution based on isDML and isNamedstatement values.
        // Default health check is to call DBClient's ping method (when no customization is set).
        @Override
        public DbClientHealthCheck build() {
            if (isDML) {
                if (isNamedstatement) {
                    return statementName == null
                            ? new DbClientHealthCheckAsPing(this) : new DbClientHealthCheckAsNamedDml(this);
                } else {
                    return new DbClientHealthCheckAsDml(this);
                }
            } else {
                if (isNamedstatement && statementName == null) {
                    statementName = DbClient.PING_STATEMENT_NAME;
                }
                return isNamedstatement
                        ? new DbClientHealthCheckAsNamedQuery(this) : new DbClientHealthCheckAsQuery(this);
            }
        }

        /**
         * Customized name of the health check.
         * Default uses {@link io.helidon.dbclient.DbClient#dbType()}.
         *
         * @param name name of the health check
         * @return updated builder instance
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Set health check statement type to query.
         * Default health check statement type is DML.
         *
         * @return updated builder instance
         */
        public Builder query() {
            this.isDML = false;
            this.isNamedstatement = true;
            return this;
        }

        /**
         * Set custom statement name.
         * Default statement name value is {@code ping}.
         *
         * @param name custom statement name.
         * @return updated builder instance
         */
        public Builder statementName(String name) {
            if (statement != null) {
                throw new UnsupportedOperationException(
                        "Can't use both statementName and statement methods in a single builder instance!");
            }
            this.isNamedstatement = true;
            this.statementName = name;
            return this;
        }

        /**
         * Set custom statement.
         *
         * @param statement custom statement name.
         * @return updated builder instance
         */
        public Builder statement(String statement) {
            if (statementName != null) {
                throw new UnsupportedOperationException(
                        "Can't use both statementName and statement methods in a single builder instance!");
            }
            this.isNamedstatement = false;
            this.statement = statement;
            return this;
        }

        /**
         * Set custom timeout to wait for statement execution response.
         * Default value is {@code 10} seconds.
         *
         * @param duration the maximum time to wait for statement execution response
         * @param timeUnit    the time unit of the timeout argument
         * @return updated builder instance
         */
        public Builder timeout(long duration, TimeUnit timeUnit) {
            this.timeoutDuration = duration;
            this.timeoutUnit = timeUnit;
            return this;
        }

    }

}
