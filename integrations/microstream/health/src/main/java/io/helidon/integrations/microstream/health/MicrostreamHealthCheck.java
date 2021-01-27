/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.integrations.microstream.health;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import one.microstream.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;

/**
 * Microstream health check.
 */
public class MicrostreamHealthCheck implements HealthCheck {

    private static final String DEFAULT_NAME = "Microstream";
    private static final long DEFAULT_TIMEOUT_SECONDS = 10;

    private EmbeddedStorageManager embeddedStorageManager;
    private long timeoutDuration;
    private TimeUnit timeoutUnit;
    private String name;

    private MicrostreamHealthCheck() {
        super();
    }

    private MicrostreamHealthCheck(Builder builder) {
        this.embeddedStorageManager = builder.embeddedStorageManager;
        this.timeoutDuration = builder.timeoutDuration;
        this.timeoutUnit = builder.timeoutUnit;
        this.name = builder.name;
    }

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = HealthCheckResponse.builder().name(name);

        try {
            CompletableFuture<Boolean> status = CompletableFuture.supplyAsync(() -> embeddedStorageManager.isRunning())
                    .orTimeout(timeoutDuration, timeoutUnit);

            if (status.get()) {
                builder.up();
            } else {
                builder.down();
            }
        } catch (Throwable e) {
            builder.down();
            builder.withData("ErrorMessage", e.getMessage());
            builder.withData("ErrorClass", e.getClass().getName());
        }

        return builder.build();
    }

    /**
     * Create a default health check for Microstream.
     *
     * @param embeddedStorageManager the EmbeddedStorageManager used by the the health check
     * @return default health check for Microstream
     */
    public static MicrostreamHealthCheck create(EmbeddedStorageManager embeddedStorageManager) {
        return builder(embeddedStorageManager).build();
    }

    /**
     * A fluent API builder to create a health check for Microstream.
     *
     * @param embeddedStorageManager EmbeddedStorageManager
     * @return a new builder
     */
    public static Builder builder(EmbeddedStorageManager embeddedStorageManager) {
        return new Builder(embeddedStorageManager);
    }

    /**
     *
     * Builder for MicrostreamHealthCheck.
     *
     */
    public static class Builder implements io.helidon.common.Builder<MicrostreamHealthCheck> {

        private EmbeddedStorageManager embeddedStorageManager;
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
        public MicrostreamHealthCheck build() {
            return new MicrostreamHealthCheck(this);
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
         */
        public Builder timeout(long duration, TimeUnit timeUnit) {
            this.timeoutDuration = duration;
            this.timeoutUnit = timeUnit;
            return this;
        }
    }
}
