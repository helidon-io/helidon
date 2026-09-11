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

package io.helidon.security.providers.oidc.common;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.faulttolerance.CircuitBreakerConfig;
import io.helidon.faulttolerance.RetryConfig;
import io.helidon.faulttolerance.TimeoutConfig;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.faulttolerance.FaultTolerance.FT_METRICS_DEFAULT_ENABLED;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@Testing.Test
class OidcJwkMetricsTest {
    private final MeterRegistry meterRegistry;

    OidcJwkMetricsTest(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @BeforeAll
    static void setupTest() {
        Services.set(Config.class,
                     Config.just(ConfigSources.create(Map.of(FT_METRICS_DEFAULT_ENABLED, "true"))));
    }

    @Test
    void testTenantRebuildsKeepJwkMetricsBounded() {
        var retry = RetryConfig.builder()
                .calls(2)
                .delay(Duration.ZERO)
                .overallTimeout(Duration.ofSeconds(5))
                .buildPrototype();
        var timeout = TimeoutConfig.builder()
                .timeout(Duration.ofSeconds(1))
                .currentThread(true)
                .buildPrototype();
        var circuitBreaker = CircuitBreakerConfig.builder()
                .volume(2)
                .errorRatio(100)
                .successThreshold(1)
                .delay(Duration.ofSeconds(5))
                .buildPrototype();

        for (int build = 1; build <= 10; build++) {
            var tenant = TenantConfig.tenantBuilder()
                    .name("tenant")
                    .clientId("client")
                    .clientSecret("secret")
                    .identityUri(URI.create("https://identity.example"))
                    .oidcMetadataWellKnown(false)
                    .jwkRetry(retry)
                    .jwkTimeout(timeout)
                    .jwkCircuitBreaker(circuitBreaker)
                    .build();

            tenant.jwkRetry();
            tenant.jwkTimeout();
            tenant.jwkCircuitBreaker();

            var ftMeters = meterRegistry.meters(meter -> meter.id().name().startsWith("ft."));
            assertThat("FT meter count after tenant build " + build, ftMeters.size(), is(6));
        }
    }
}
