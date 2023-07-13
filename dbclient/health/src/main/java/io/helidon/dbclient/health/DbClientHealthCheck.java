/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.helidon.common.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;

/**
 * Database health check.
 */
public abstract class DbClientHealthCheck implements HealthCheck {

    /* Local logger instance. */
    private static final System.Logger LOGGER = System.getLogger(DbClientHealthCheck.class.getName());

    /* Default hHealth check timeout in seconds (to wait for statement execution response). */
    private static final int DEFAULT_TIMEOUT_SECONDS = 10;
    private final Duration timeout;

    /**
     * Create a health check with configured settings for the database.
     * This health check will execute health check as defined in provided {@link Config} node.
     *
     * @param dbClient database client used to execute health check statement
     * @param config   {@link Config} node with health check configuration
     * @return health check that can be used with health services
     */
    public static DbClientHealthCheck create(DbClient dbClient, Config config) {
        return builder(dbClient).config(config).build();
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

    private final DbClient dbClient;
    private final String name;

    private DbClientHealthCheck(Builder builder) {
        this.dbClient = builder.database;
        this.name = builder.name();
        this.timeout = Duration.of(builder.timeoutDuration, builder.timeoutUnit.toChronoUnit());
    }

    /**
     * Execute the ping statement.
     */
    protected abstract void execPing();

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponse.Builder builder = HealthCheckResponse.builder();
        try {
            execPing();
            builder.status(HealthCheckResponse.Status.UP);
        } catch (Throwable e) {
            builder.status(HealthCheckResponse.Status.DOWN);
            builder.detail("ErrorMessage", e.getMessage());
            builder.detail("ErrorClass", e.getClass().getName());
            LOGGER.log(System.Logger.Level.TRACE,
                    () -> String.format("Database %s is not responding: %s", dbClient.dbType(), e.getMessage()), e);
        }

        return builder.build();
    }

    protected DbClient dbClient() {
        return dbClient;
    }

    // Getters for local usage and jUnit tests

    @Override
    public String name() {
        return name;
    }

    Duration timeout() {
        return timeout;
    }

    /**
     * Database health check which calls DBClient's {@code namedDml} method.
     */
    static final class DbClientHealthCheckAsNamedDml extends DbClientHealthCheck {

        /* Name of the statement. */
        private final String statementName;

        private DbClientHealthCheckAsNamedDml(Builder builder) {
            super(builder);
            this.statementName = builder.statementName();
            LOGGER.log(System.Logger.Level.TRACE, "Created an instance of DbClientHealthCheckAsNamedDml");
        }

        @Override
        protected void execPing() {
            dbClient().execute().namedDml(statementName);
        }

        // Getter for jUnit tests
        String statementName() {
            return statementName;
        }

    }

    /**
     * Database health check which calls DBClient's {@code dml} method.
     */
    static final class DbClientHealthCheckAsDml extends DbClientHealthCheck {

        /* Custom statement. */
        private final String statement;

        private DbClientHealthCheckAsDml(Builder builder) {
            super(builder);
            this.statement = builder.statement();
            LOGGER.log(System.Logger.Level.TRACE, "Created an instance of DbClientHealthCheckAsDml");
        }

        @Override
        protected void execPing() {
            dbClient().execute().dml(statement);
        }

        // Getter for jUnit tests
        String statement() {
            return statement;
        }

    }

    /**
     * Database health check which calls DBClient's {@code namedQuery} method.
     */
    static final class DbClientHealthCheckAsNamedQuery extends DbClientHealthCheck {

        /* Name of the statement. */
        private final String statementName;

        private DbClientHealthCheckAsNamedQuery(Builder builder) {
            super(builder);
            this.statementName = builder.statementName();
            LOGGER.log(System.Logger.Level.TRACE, "Created an instance of DbClientHealthCheckAsNamedQuery");
        }

        @Override
        protected void execPing() {
            dbClient().execute().namedQuery(statementName).forEach(it -> {});
        }

        // Getter for jUnit tests
        String statementName() {
            return statementName;
        }

    }

    /**
     * Database health check which calls DBClient's {@code query} method.
     */
    static final class DbClientHealthCheckAsQuery extends DbClientHealthCheck {

        /* Custom statement. */
        private final String statement;

        private DbClientHealthCheckAsQuery(Builder builder) {
            super(builder);
            this.statement = builder.statement();
            LOGGER.log(System.Logger.Level.TRACE, "Created an instance of DbClientHealthCheckAsQuery");
        }

        @Override
        protected void execPing() {
            dbClient().execute().query(statement).forEach(it -> {});
        }

        // Getter for jUnit tests
        String statement() {
            return statement;
        }

    }

    /**
     * Fluent API builder for {@link DbClientHealthCheck}.
     * Default health check setup will call named DML statement with name {@code ping}.
     * This named DML statement shall be configured in {@code statements} section
     * of the DBClient configuration file.
     */
    public static final class Builder implements io.helidon.common.Builder<Builder, DbClientHealthCheck> {

        // Name of Config node with custom health check name.
        private static final String CONFIG_NAME = "name";
        // Name of Config node with statement type.
        private static final String CONFIG_TYPE = "type";
        // Name of Config node with statement to be executed in database.
        private static final String CONFIG_STMT = "statement";
        // Name of Config node with statement name to be executed in database.
        private static final String CONFIG_STMT_NAME = "statementName";
        // Name of Config node with timeout duration value.
        private static final String CONFIG_TIMEOUT_DURATION = "timeout";
        // Name of Config node with timeout units.
        private static final String CONFIG_TIMEOUT_UNIT = "timeUnit";

        // Size of TimeUnit enum.
        private static final int TIME_UNIT_SIZE = TimeUnit.values().length;
        // Configuration strings resolver based on TimeUnit enum content.
        private static final Map<String, TimeUnit> NAME_TO_TIME_UNIT = new HashMap<>(TIME_UNIT_SIZE);

        // Initialize TimeUnit resolver content.
        static {
            for (TimeUnit value : TimeUnit.values()) {
                NAME_TO_TIME_UNIT.put(value.name().toLowerCase(), value);
            }
        }

        // Helidon database client.
        private final DbClient database;
        // Health check name.
        private String name;
        // Health check timeout length (to wait for statement execution response).
        private long timeoutDuration;
        // Health check timeout units (to wait for statement execution response).
        private TimeUnit timeoutUnit;

        // Those two boolean variables define 4 ways of query execution:
        //
        // +-------------------+----------+--------+------------+-------+
        // | DbExecute         | namedDML | dml    | namedQuery | query |
        // +-------------------+----------+--------+------------+-------+
        // | isDML             | true     | true   | false      | false |
        // | isNamedStatement  | true     | false | true       | false |
        // +-------------------+----------+--------+------------+-------+
        // The best performance optimized solution seems to be polymorphism for part of check method.

        // Health check statement is DML when {@code true} and query when {@code false}.
        private boolean isDML;
        // Whether to use named statement or statement passed as an argument.
        // Holds information about latest statement definition.
        private boolean isNamedStatement;

        // Name of the statement.
        private String statementName;
        // Custom statement string.
        private String statement;

        private Builder(DbClient database) {
            this.database = database;
            this.name = database.dbType();
            this.timeoutDuration = DEFAULT_TIMEOUT_SECONDS;
            this.timeoutUnit = TimeUnit.SECONDS;
            this.isDML = false;
            this.isNamedStatement = true;
            this.statementName = null;
            this.statement = null;
        }

        // Defines polymorphism for ping statement execution based on isDML and isNamedStatement values.
        // Default health check is to call DBClient's ping method (when no customization is set).
        @Override
        public DbClientHealthCheck build() {
            // Statement defined as name in statements config node
            if (isNamedStatement) {
                // Statement null check is required just here because isNamedStatement is set to true by default.
                if (statementName == null) {
                    throw new HealthCheckBuilderException(
                            "No statement name or statement custom string was defined.");
                }
                return isDML
                        ? new DbClientHealthCheckAsNamedDml(this)
                        : new DbClientHealthCheckAsNamedQuery(this);
                // Statement defined as custom string
            } else {
                return isDML
                        ? new DbClientHealthCheckAsDml(this)
                        : new DbClientHealthCheckAsQuery(this);
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
         * Set health check parameters using {@link Config} node.
         * <p>
         * Configuration node expected content:<ul>
         * <li><b>name:</b> custom health check name.</li>
         * <li><b>type:</b> statement type (default value is <b>{@code query}</b>).</li>
         * <li><b>statement:</b> statement to be executed in database.</li>
         * <li><b>statementName:</b> name of statement to be executed in database. Statement
         * with given name must exist in statements {@link Config} node.</li>
         * <li><b>timeout:</b> timeout value.</li>
         * <li><b>timeUnit:</b> units of timeout value (default value is <b>{@code seconds}</b>).</li></ul>
         * Only one of <b>statement</b> and <b>statementName</b> parameters is allowed.
         *
         * @param config {@link Config} instance with health check parameters
         * @return updated builder instance
         */
        public Builder config(Config config) {
            config.get(CONFIG_NAME)
                    .as(String.class)
                    .ifPresent(checkName -> this.name = checkName);
            // Lambda expressions require final variables but code needs it's value to be changed.
            // Statement definition:
            //  - false: not set
            //  - true:  statement string already set
            final boolean[] stmtDef = new boolean[]{false};
            config.get(CONFIG_STMT)
                    .as(String.class)
                    .ifPresent(stmt -> {
                        stmtDef[0] = true;              // Statement definition as statement string
                        this.isNamedStatement = false;
                        this.statement = stmt;
                    });
            config.get(CONFIG_STMT_NAME)
                    .as(String.class)
                    .ifPresent(stmtName -> {
                        if (stmtDef[0]) {               // Collision with statement definition as statement string
                            throw new HealthCheckBuilderException(
                                    String.format(
                                            "Duplicate statement definition in health check config: "
                                                    + "statement \"%s\" and statement name %s",
                                            this.statement,
                                            stmtName));
                        }
                        this.isNamedStatement = true;
                        this.statementName = stmtName;
                    });
            config.get(CONFIG_TYPE)
                    .as(String.class)
                    .ifPresent(type -> {
                        HealthCheckStMtType stmtType = HealthCheckStMtType.nameToType(type);
                        if (stmtType == null) {
                            throw new HealthCheckBuilderException(
                                    String.format("Unknown statement type: %s", type));
                        }
                        this.isDML = switch (stmtType) {
                            case DML -> true;
                            case QUERY -> false;
                        };
                    });
            try {
                config.get(CONFIG_TIMEOUT_DURATION)
                        .as(Long.class)
                        .ifPresent(duration -> this.timeoutDuration = duration);
                // Number conversion may fail
            } catch (Throwable t) {
                throw new HealthCheckBuilderException(
                        String.format("Could not set timeout duration: %s",
                                t.getMessage()),
                        t);
            }
            config.get(CONFIG_TIMEOUT_UNIT)
                    .as(String.class)
                    .ifPresent(tmUnit -> {
                        final TimeUnit timeUnit = NAME_TO_TIME_UNIT.get(tmUnit.toLowerCase());
                        if (timeUnit == null) {
                            throw new HealthCheckBuilderException(
                                    String.format("Unknown timeout unit name: %s", tmUnit));
                        }
                        this.timeoutUnit = timeUnit;
                    });
            return this;
        }

        /**
         * Set health check statement type to query.
         * Allows to override value set in {@link Config} node.
         * Default health check statement type is query.
         *
         * @return updated builder instance
         */
        public Builder query() {
            this.isDML = false;
            return this;
        }

        /**
         * Set health check statement type to DML.
         * Allows to override value set in {@link Config} node.
         * Default health check statement type is query.
         *
         * @return updated builder instance
         */
        public Builder dml() {
            this.isDML = true;
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
                throw new HealthCheckBuilderException(
                        "Can't use both statementName and statement methods in a single builder instance!");
            }
            this.isNamedStatement = true;
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
                throw new HealthCheckBuilderException(
                        "Can't use both statementName and statement methods in a single builder instance!");
            }
            this.isNamedStatement = false;
            this.statement = statement;
            return this;
        }

        /**
         * Set custom timeout to wait for statement execution response.
         * Default value is {@code 10} seconds.
         *
         * @param duration the maximum time to wait for statement execution response
         * @param timeUnit the time unit of the timeout argument
         * @return updated builder instance
         * @deprecated use {@link #timeout(Duration)} instead
         */
        @Deprecated(since = "4.0.0")
        public Builder timeout(long duration, TimeUnit timeUnit) {
            this.timeoutDuration = duration;
            this.timeoutUnit = timeUnit;
            return this;
        }

        /**
         * Set custom timeout to wait for statement execution response.
         * Default value is {@code 10} seconds.
         *
         * @param timeout the maximum time to wait for statement execution response
         * @return updated builder instance
         */
        public Builder timeout(Duration timeout) {
            this.timeoutDuration = timeout.toNanos();
            this.timeoutUnit = TimeUnit.NANOSECONDS;
            return this;
        }

        // Getters for local usage and jUnit tests

        String name() {
            return name;
        }

        long timeoutDuration() {
            return timeoutDuration;
        }

        TimeUnit timeoutUnit() {
            return timeoutUnit;
        }

        boolean isDML() {
            return isDML;
        }

        boolean isNamedStatement() {
            return isNamedStatement;
        }

        String statementName() {
            return statementName;
        }

        String statement() {
            return statement;
        }

    }

}
