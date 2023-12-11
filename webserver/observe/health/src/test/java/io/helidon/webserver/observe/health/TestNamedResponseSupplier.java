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
package io.helidon.webserver.observe.health;

import java.util.Optional;

import io.helidon.common.testing.junit5.OptionalMatcher;
import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.health.HealthCheckType;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;

class TestNamedResponseSupplier {


    private static final String NAMED_CHECK_NAME = "testCheck";
    @Test
    void checkForName() {
        HealthObserver healthObserver = HealthObserver.builder()
                .addCheck(() -> HealthCheckResponse.builder() // Tests the newly-added method for fixing issue #7827
                        .status(true)
                        .build(),
                          HealthCheckType.READINESS,
                          NAMED_CHECK_NAME)
                .addCheck(() -> HealthCheckResponse.builder() // Tests the pre-existing behavior
                        .status(true)
                        .build(),
                          HealthCheckType.LIVENESS)
                .build();

        Optional<HealthCheck> namedCheck = healthObserver.prototype()
                .healthChecks()
                .stream()
                        .filter(hc -> hc.name().equals(NAMED_CHECK_NAME))
                .findFirst();

        assertThat("Named check via supplier", namedCheck, OptionalMatcher.optionalPresent());

        Optional<HealthCheck> unnamedCheck = healthObserver.prototype()
                .healthChecks()
                .stream()
                .filter(hc -> !hc.name().equals(NAMED_CHECK_NAME))
                .findFirst();

        assertThat("Unnamed check via supplier", unnamedCheck, OptionalMatcher.optionalPresent());
    }
}
