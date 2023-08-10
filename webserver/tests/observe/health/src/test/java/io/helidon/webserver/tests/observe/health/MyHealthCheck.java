/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests.observe.health;

import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.health.HealthCheckType;

class MyHealthCheck implements HealthCheck {
    private volatile HealthCheckResponse.Status status = HealthCheckResponse.Status.UP;

    @Override
    public HealthCheckType type() {
        return HealthCheckType.READINESS;
    }

    @Override
    public String name() {
        return "ready-1";
    }

    @Override
    public String path() {
        return "mine1";
    }

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.builder()
                .status(status)
                .detail("detail", "message")
                .build();
    }

    void status(HealthCheckResponse.Status newStatus) {
        this.status = newStatus;
    }
}
