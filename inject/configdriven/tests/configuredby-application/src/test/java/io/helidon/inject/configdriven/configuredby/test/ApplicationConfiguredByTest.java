/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.configdriven.configuredby.test;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.helidon.inject.RegistryServiceProvider;
import io.helidon.inject.configdriven.configuredby.application.test.ASimpleRunLevelService;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.Lookup;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalPresent;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

/**
 * Designed to re-run the same tests from base, but using the application-created DI model instead.
 */
class ApplicationConfiguredByTest extends AbstractConfiguredByTest {
    private static final MeterRegistry METER_REGISTRY = Metrics.globalRegistry();

    /**
     * In application mode, we should not have many lookups recorded.
     */
    @Test
    void verifyMinimalLookups() {
        Counter counter = lookupCounter();

        long initialCount = counter.count();
        resetWith(io.helidon.config.Config.builder(createRootDefault8080TestingConfigSource())
                          .disableEnvironmentVariablesSource()
                          .disableSystemPropertiesSource()
                          .build());
        long lookupCount = counter.count() - initialCount;


        // everything is handled by Application class, now even config beans
        // the CbrProvider itself uses an announced dependency that is fulfilled by the app if present.
        assertThat("There should only be no lookups",
                   lookupCount,
                   is(0L));
    }

    @Test
    void startupAndShutdownRunLevelServices() {
        resetWith(io.helidon.config.Config.builder(createRootDefault8080TestingConfigSource())
                          .disableEnvironmentVariablesSource()
                          .disableSystemPropertiesSource()
                          .build());

        Counter lookupCounter = lookupCounter();
        long initialCount = lookupCounter.count();

        MatcherAssert.assertThat(ASimpleRunLevelService.getPostConstructCount(),
                                 is(0));
        assertThat(ASimpleRunLevelService.getPreDestroyCount(),
                   is(0));

        Lookup criteria = Lookup.builder()
                .runLevel(Injection.RunLevel.STARTUP)
                .build();
        List<RegistryServiceProvider<Object>> startups = services.all(criteria);
        List<String> desc = startups.stream().map(RegistryServiceProvider::description).collect(Collectors.toList());

        assertThat(desc,
                   contains(ASimpleRunLevelService.class.getSimpleName() + ":INIT"));
        startups.forEach(RegistryServiceProvider::get);

        long endingLookupCount = lookupCounter.count() - initialCount;

        assertThat(endingLookupCount,
                   is(1L));

        assertThat(ASimpleRunLevelService.getPostConstructCount(),
                   is(1));
        assertThat(ASimpleRunLevelService.getPreDestroyCount(),
                   is(0));

        injectionServices.shutdown();
        assertThat(ASimpleRunLevelService.getPostConstructCount(),
                   is(1));
        assertThat(ASimpleRunLevelService.getPreDestroyCount(),
                   is(1));
    }

    private Counter lookupCounter() {
        Optional<Counter> counterMeter = METER_REGISTRY.counter("io.helidon.inject.lookups", List.of());
        assertThat(counterMeter, optionalPresent());
        return counterMeter.get();
    }

}
