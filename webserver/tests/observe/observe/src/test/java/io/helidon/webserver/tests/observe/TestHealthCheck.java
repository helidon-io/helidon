/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests.observe;

import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;

class TestHealthCheck implements HealthCheck {
    private final AtomicInteger calls = new AtomicInteger();
    private final String message;

    TestHealthCheck(String message) {
        this.message = message;
    }

    @Override
    public String path() {
        return "test";
    }

    @Override
    public HealthCheckResponse call() {
        calls.incrementAndGet();
        return HealthCheckResponse.builder()
                .detail("message", message)
                .status(true)
                .build();
    }

    int calls() {
        return calls.get();
    }

    void reset() {
        calls.set(0);
    }
}
