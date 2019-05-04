/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.db.health;

import java.util.concurrent.atomic.AtomicReference;

import io.helidon.db.Db;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;

/**
 * Database health check.
 */
public final class DbHealthCheck implements HealthCheck {
    private final Db db;
    private final String name;

    private DbHealthCheck(Builder builder) {
        this.db = builder.database;
        this.name = builder.name;
    }

    /**
     * Create a health check for the database.
     *
     * @param db A database that implements {@link io.helidon.db.Db#ping()}
     * @return health check that can be used with
     * {@link io.helidon.health.HealthSupport.Builder#add(org.eclipse.microprofile.health.HealthCheck...)}
     */
    public static DbHealthCheck create(Db db) {
        return builder(db).build();
    }

    /**
     * A fluent API builder to create a fully customized database health check.
     *
     * @param db database
     * @return a new builder
     */
    public static Builder builder(Db db) {
        return new Builder(db);
    }

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = HealthCheckResponse.builder()
                .name(name);

        AtomicReference<Throwable> throwable = new AtomicReference<>();
        try {
            db.ping().toCompletableFuture()
                    .exceptionally(theThrowable -> {
                        throwable.set(theThrowable);
                        return null;
                    })
                    .get();
        } catch (Exception e) {
            builder.down();
            throwable.set(e);
        }

        Throwable thrown = throwable.get();

        if (null == thrown) {
            builder.up();
        } else {
            thrown = thrown.getCause();
            builder.down();
            builder.withData("ErrorMessage", thrown.getMessage());
            builder.withData("ErrorClass", thrown.getClass().getName());
        }
        return builder.build();
    }

    /**
     * Fluent API builder for {@link io.helidon.db.health.DbHealthCheck}.
     */
    public static final class Builder implements io.helidon.common.Builder<DbHealthCheck> {
        private final Db database;
        private String name;

        private Builder(Db database) {
            this.database = database;
            this.name = database.dbType();
        }

        @Override
        public DbHealthCheck build() {
            return new DbHealthCheck(this);
        }

        /**
         * Customized name of the health check.
         * Default uses {@link io.helidon.db.Db#dbType()}.
         *
         * @param name name of the health check
         * @return updated builder instance
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }
    }
}
