/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.health.checks;

import java.util.Arrays;

import io.helidon.config.Config;
import io.helidon.health.HealthCheck;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

class HealthChecksTest {
    @Test
    void defaultChecks() {
        assertThat(Arrays.stream(HealthChecks.healthChecks()).map(HealthCheck::name).toList(),
                   containsInAnyOrder("deadlock", "diskSpace", "heapMemory"));
    }

    @Test
    void configuredChecks() {
        assertThat(Arrays.stream(HealthChecks.healthChecks(Config.empty())).map(HealthCheck::name).toList(),
                   containsInAnyOrder("deadlock", "diskSpace", "heapMemory"));
    }

    @Test
    void providerChecks() {
        assertThat(new BuiltInHealthCheckProvider().healthChecks(Config.empty()).stream().map(HealthCheck::name).toList(),
                   containsInAnyOrder("deadlock", "diskSpace", "heapMemory"));
    }
}
