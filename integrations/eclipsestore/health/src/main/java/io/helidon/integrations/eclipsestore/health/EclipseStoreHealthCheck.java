/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.integrations.eclipsestore.health;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;

import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;


/**
 * Eclipse Store health check.
 */
public class EclipseStoreHealthCheck implements HealthCheck {

    private static final String DEFAULT_NAME = "EclipseStore";
    private static final long DEFAULT_TIMEOUT_SECONDS = 10;

    private final EmbeddedStorageManager embeddedStorageManager;
    private final long timeoutDuration;
    private final TimeUnit timeoutUnit;
    private final String name;

    private EclipseStoreHealthCheck(Builder builder) {
        this.embeddedStorageManager = builder.embeddedStorageManager;
        this.timeoutDuration = builder.timeoutDuration;
        this.timeoutUnit = builder.timeoutUnit;
        this.name = builder.name;
    }

    /**
     * Create a default health check for Eclipse Store.
     *
     * @param embeddedStorageManager the EmbeddedStorageManager used by the the health check
     * @return default health check for Eclipse Store.
     */
    public static EclipseStoreHealthCheck create(EmbeddedStorageManager embeddedStorageManager) {
        return builder(embeddedStorageManager).build();
    }

    /**
     * A fluent API builder to create a health check for Eclipse Store.
     *
     * @param embeddedStorageManager EmbeddedStorageManager
     * @return a new builder
     */
    public static Builder builder(EmbeddedStorageManager embeddedStorageManager) {
        return new Builder(embeddedStorageManager);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponse.Builder builder = HealthCheckResponse.builder();

        try {
            CompletableFuture<Boolean> status = CompletableFuture.supplyAsync(embeddedStorageManager::isRunning)
                    .orTimeout(timeoutDuration, timeoutUnit);

            builder.status(status.get());
        } catch (Throwable e) {
            builder.status(false)
                    .detail("ErrorMessage", e.getMessage())
                    .detail("ErrorClass", e.getClass().getName());
        }

        return builder.build();
    }

    /**
     * Builder for EclipseStoreHealthCheck.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, EclipseStoreHealthCheck> {

        private final EmbeddedStorageManager embeddedStorageManager;

        private long timeoutDuration;
        private TimeUnit timeoutUnit;
        private String name;

        private Builder(EmbeddedStorageManager embeddedStorageManager) {
            this.embeddedStorageManager = embeddedStorageManager;
            this.name = DEFAULT_NAME;
            this.timeoutDuration = DEFAULT_TIMEOUT_SECONDS;
            this.timeoutUnit = TimeUnit.SECONDS;
        }

        @Override
        public EclipseStoreHealthCheck build() {
            return new EclipseStoreHealthCheck(this);
        }

        /**
         * Customized name of the health check.
         *
         * @param name name of the health check
         * @return updated builder instance
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Set custom timeout to wait for statement execution response. Default value is
         * 10 seconds.
         *
         * @param duration the maximum time to wait for statement execution response
         * @param timeUnit the time unit of the timeout argument
         * @return updated builder instance
         * @deprecated use {@link #timeout(Duration)} instead
         */
        @Deprecated(since = "4.0.0", forRemoval = true)
        public Builder timeout(long duration, TimeUnit timeUnit) {
            this.timeoutDuration = duration;
            this.timeoutUnit = timeUnit;
            return this;
        }

        /**
         * Set custom timeout to wait for statement execution response. Default value is
         * 10 seconds.
         *
         * @param duration the maximum time to wait for statement execution response
         * @return updated builder instance
         */
        public Builder timeout(Duration duration) {
            this.timeoutDuration = duration.toNanos();
            this.timeoutUnit = TimeUnit.NANOSECONDS;
            return this;
        }
    }
}
