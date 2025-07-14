/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.observe.health;

import java.util.Map;

import io.helidon.common.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.health.HealthCheckType;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

class TestExclude {

    private static final HealthCheck CUSTOM_CHECK_1 = new HealthCheck() {
        @Override
        public HealthCheckType type() {
            return HealthCheckType.READINESS;
        }

        @Override
        public String name() {
            return "custom-1";
        }

        @Override
        public HealthCheckResponse call() {
            return HealthCheckResponse.builder().status(HealthCheckResponse.Status.DOWN).build();
        }
    };

    @Test
    void testExcluded() {
        HealthObserver observerWithCustomCheck = HealthObserverConfig.builder()
                .useSystemServices(true)
                .addCheck(CUSTOM_CHECK_1)
                .build();

        assertThat("Without exclude",
                   observerWithCustomCheck.all().stream().map(HealthCheck::name).toList(),
                   allOf(hasItem("custom-1"),
                         hasItem("diskSpace"),
                         hasItem("heapMemory")));

        HealthObserver observerWithoutCustomCheck = HealthObserverConfig.builder()
                .useSystemServices(true)
                .addCheck(CUSTOM_CHECK_1)
                .addExcluded("custom-1")
                .build();

        assertThat("With programmatic exclude",
                   observerWithoutCustomCheck.all().stream().map(HealthCheck::name).toList(),
                   allOf(not(hasItem("custom-1")),
                         hasItem("diskSpace"),
                         hasItem("heapMemory")));

    }

    @Test
    void testExcludeUsingConfig() {
        HealthObserver observerWithCustomCheck = HealthObserverConfig.builder()
                .addCheck(CUSTOM_CHECK_1)
                .useSystemServices(true)
                .build();

        assertThat("Without exclude",
                   observerWithCustomCheck.all().stream().map(HealthCheck::name).toList(),
                   allOf(hasItem("custom-1"),
                         hasItem("heapMemory"),
                         hasItem("diskSpace"),
                         hasItem("deadlock")));

        Config config = io.helidon.config.Config.just(ConfigSources.create(
                Map.of("server.features.observe.observers.health.exclude", "deadlock,custom-1",
                       "server.features.observe.observers.health.use-system-services", "true")));

        HealthObserver observerWithoutCustomCheck = HealthObserverConfig.builder()
                .config(config.get("server.features.observe.observers.health"))
                .addCheck(CUSTOM_CHECK_1)
                .build();

        assertThat("With exclude using config",
                   observerWithoutCustomCheck.all().stream().map(HealthCheck::name).toList(),
                   allOf(not(hasItem("custom-1")),
                         not(hasItem("deadlock")),
                         hasItem("diskSpace"),
                         hasItem("heapMemory")));
    }
}
