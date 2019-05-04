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

import io.helidon.db.HelidonDb;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;

/**
 * Database health check.
 */
public final class DbHealthCheck implements HealthCheck {
    private final HelidonDb db;
    private final String name;

    private DbHealthCheck(Builder builder) {
        this.db = builder.database;
        this.name = builder.name;
    }

    public static DbHealthCheck create(HelidonDb db) {
        return builder(db).build();
    }

    public static Builder builder(HelidonDb db) {
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

    public static final class Builder implements io.helidon.common.Builder<DbHealthCheck> {
        private final HelidonDb database;
        private String name;

        private Builder(HelidonDb database) {
            this.database = database;
            this.name = database.dbType();
        }

        @Override
        public DbHealthCheck build() {
            return new DbHealthCheck(this);
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }
    }
}
