/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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

package io.helidon.microprofile.grpc.server;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;

/**
 * A simple {@link HealthCheck} implementation
 * that always returns the same response.
 */
public class ConstantHealthCheck implements HealthCheck {

    private final HealthCheckResponse response;

    private ConstantHealthCheck(HealthCheckResponse response) {
        this.response = response;
    }

    @Override
    public HealthCheckResponse call() {
        return response;
    }

    /**
     * Obtain a {@link HealthCheck} that always returns a status of up.
     *
     * @param name the service name that the health check is for
     * @return a {@link HealthCheck} that always returns a status of up
     */
    public static HealthCheck up(String name) {
        return new ConstantHealthCheck(HealthCheckResponse.named(name).up().build());
    }

    /**
     * Obtain a {@link HealthCheck} that always returns a status of down.
     *
     * @param name the service name that the health check is for
     * @return a {@link HealthCheck} that always returns a status of down
     */
    public static HealthCheck down(String name) {
        return new ConstantHealthCheck(HealthCheckResponse.named(name).down().build());
    }
}
