/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

/**
 * A health check.
 * Health checks are called when a request to health status comes over the wire.
 */
@FunctionalInterface
public interface HealthCheck {
    /**
     * Type of this health check.
     *
     * @return type, defaults to {@link HealthCheckType#LIVENESS}
     */
    default HealthCheckType type() {
        return HealthCheckType.LIVENESS;
    }

    /**
     * Name of this health check, used in output when details are requested.
     *
     * @return name of this health check, defaults to simple class name
     */
    default String name() {
        return getClass().getSimpleName();
    }

    /**
     * Path of this health check, to support single health-check queries.
     *
     * @return path to use, by default returns {@link #name()}
     */
    default String path() {
        return name();
    }

    /**
     * Call a health check.
     *
     * @return health response
     */
    HealthCheckResponse call();
}
