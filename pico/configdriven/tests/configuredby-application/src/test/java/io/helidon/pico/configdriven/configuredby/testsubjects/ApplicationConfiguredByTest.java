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

package io.helidon.pico.configdriven.configuredby.testsubjects;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.pico.DefaultServiceInfoCriteria;
import io.helidon.pico.Metrics;
import io.helidon.pico.RunLevel;
import io.helidon.pico.ServiceInfoCriteria;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.configdriven.configuredby.application.testsubjects.ASimpleRunLevelService;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;

/**
 * Designed to re-run the same tests, but using the application-created DI model instead.
 */
class ApplicationConfiguredByTest extends ConfiguredByTest {

    /**
     * In application mode, we should not have any lookups recorded.
     */
    @Test
    void verifyNoLookups() {
        Metrics metrics = picoServices.metrics().orElseThrow();
        Set<ServiceInfoCriteria> criteriaSearchLog = picoServices.lookups().orElseThrow();
        Set<String> contractSearchLog = criteriaSearchLog.stream().flatMap(it -> it.contractsImplemented().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(contractSearchLog,
                   containsInAnyOrder(
                           // config beans are always looked up
                           "io.helidon.builder.config.testsubjects.fakes.FakeServerConfig",
                           // tracer doesn't really exist, so it is looked up out of best-effort (as an optional injection dep)
                           "io.helidon.builder.config.testsubjects.fakes.FakeTracer"
                           ));
        assertThat("lookup log: " + criteriaSearchLog,
                   metrics.lookupCount().get(), is(2));
    }

    @Test
    public void startupAndShutdownRunLevelServices() {
        Metrics metrics = picoServices.metrics().orElseThrow();
        int startingLookupCount = metrics.lookupCount().orElseThrow();

        assertThat(ASimpleRunLevelService.getPostConstructCount(), is(0));
        assertThat(ASimpleRunLevelService.getPreDestroyCount(), is(0));

        ServiceInfoCriteria criteria = DefaultServiceInfoCriteria.builder()
                .runLevel(RunLevel.STARTUP)
                .build();
        List<ServiceProvider<Object>> startups = services.lookupAll(criteria);
        List<String> desc = startups.stream().map(ServiceProvider::description).collect(Collectors.toList());
        assertThat(desc, contains(ASimpleRunLevelService.class.getSimpleName() + ":INIT"));
        startups.forEach(ServiceProvider::get);

        metrics = picoServices.metrics().orElseThrow();
        int endingLookupCount = metrics.lookupCount().orElseThrow();
        assertThat(endingLookupCount - startingLookupCount, is(1));

        assertThat(ASimpleRunLevelService.getPostConstructCount(), is(1));
        assertThat(ASimpleRunLevelService.getPreDestroyCount(), is(0));

        picoServices.shutdown();
        assertThat(ASimpleRunLevelService.getPostConstructCount(), is(1));
        assertThat(ASimpleRunLevelService.getPreDestroyCount(), is(1));
    }

}
