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
 * Possible types of health checks.
 */
public enum HealthCheckType {
    /**
     * Readiness health check.
     * Indicates that the server is ready to serve requests.
     */
    READINESS("ready"),
    /**
     * Liveness health check.
     * Indicates that the server is still up and running.
     */
    LIVENESS("live"),
    /**
     * Startup health check.
     * Indicates that mandatory start operation has been executed.
     */
    STARTUP("started");
    private final String defaultPath;

    HealthCheckType(String defaultPath) {
        this.defaultPath = defaultPath;
    }

    /**
     * Default endpoint of a health check type, relative to the root of health checks (such as {@code ready} for readiness,
     * so full path by default would be {@code /observe/health/ready}).
     *
     * @return default endpoint
     */
    public String defaultEndpoint() {
        return defaultPath;
    }
}
