/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.health;

import java.util.Map;
import java.util.TreeMap;

/**
 * Health check response.
 */
public interface HealthCheckResponse {
    /**
     * A new response builder.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Status of this health check.
     *
     * @return status
     */
    Status status();

    /**
     * Details of this health check.
     * This information will be transferred over the network when details are printed!
     *
     * @return details of this health check
     */
    Map<String, Object> details();

    /**
     * Health check status.
     */
    enum Status {
        /**
         * This health check is fine.
         */
        UP,
        /**
         * This health check failed its precondition.
         */
        DOWN,
        /**
         * This health check failed with an exception that was not expected.
         */
        ERROR
    }

    /**
     * Fluent API builder for {@link HealthCheckResponse}.
     */
    class Builder implements io.helidon.common.Builder<Builder, HealthCheckResponse> {

        // Use a TreeMap to preserve stability of the details in JSON output.
        private final Map<String, Object> details = new TreeMap<>();
        private Status status = Status.UP;

        @Override
        public HealthCheckResponse build() {
            // Use a new map in case the builder is reused and mutated after this  invocation of build().
            return new HealthResponseImpl(this.status, new TreeMap<>(this.details));
        }

        /**
         * Status of health check, defaults to {@link HealthCheckResponse.Status#UP}.
         *
         * @param status status
         * @return updated builder
         */
        public Builder status(Status status) {
            this.status = status;
            return this;
        }

        /**
         * Status of health check, defaults to {@link HealthCheckResponse.Status#UP}.
         *
         * @param status status as a boolean ({@code true} for {@link HealthCheckResponse.Status#UP}), ({@code false} for {@link HealthCheckResponse.Status#DOWN})
         * @return updated builder
         */
        public Builder status(boolean status) {
            this.status = status ? Status.UP :  Status.DOWN;
            return this;
        }

        /**
         * Add a detail of this health check, used when details are enabled.
         *
         * @param name  name of the detail
         * @param value value of the detail
         * @return updated builder
         */
        public Builder detail(String name, Object value) {
            this.details.put(name, value);
            return this;
        }

        Map<String, Object> details() {
            return details;
        }

        Status status() {
            return status;
        }
    }
}
