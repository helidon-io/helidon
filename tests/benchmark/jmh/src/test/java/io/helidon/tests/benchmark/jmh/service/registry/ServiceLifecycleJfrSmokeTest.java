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

package io.helidon.tests.benchmark.jmh.service.registry;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.helidon.common.types.TypeName;
import io.helidon.service.registry.Service;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;

class ServiceLifecycleJfrSmokeTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"OFF", "DISABLED", "ENABLED"})
    void managedCallbacksAndScopeIdentity(String mode) {
        var state = new ServiceLifecycleJmhBenchmark();
        state.recordingMode = mode;
        ServiceLifecycleJmhBenchmark.Consumer first;
        ServiceLifecycleJmhBenchmark.Consumer second;
        try {
            state.setup();
            try (var _ = state.scopes().createScope(Service.PerRequest.TYPE, "smoke", Map.of())) {
                first = state.registry().get(ServiceLifecycleJmhBenchmark.Consumer.class);
                assertThat("consumer is shared inside one request scope",
                           state.registry().get(ServiceLifecycleJmhBenchmark.Consumer.class), sameInstance(first));
                assertThat("injection resolves the dependency in the same request scope",
                           state.registry().get(ServiceLifecycleJmhBenchmark.ManagedDependency.class),
                           sameInstance(first.dependency));
                callbacks(first, 0);
                callbacks(first.dependency, 0);
            }
            callbacks(first, 1);
            callbacks(first.dependency, 1);
            second = (ServiceLifecycleJmhBenchmark.Consumer) state.requestScopeLifecycle();
            assertThat("a new scope creates a new consumer", second, not(sameInstance(first)));
            assertThat("a new scope creates a new dependency", second.dependency, not(sameInstance(first.dependency)));
            callbacks(second, 1);
            callbacks(second.dependency, 1);
        } finally {
            state.tearDown();
        }
        // Registry shutdown must not destroy already closed request-scope instances a second time.
        callbacks(first, 1);
        callbacks(first.dependency, 1);
        callbacks(second, 1);
        callbacks(second.dependency, 1);
    }

    @Test
    void controlledRecordingObservesManagedCallbacks() throws Exception {
        var state = new ServiceLifecycleJmhBenchmark();
        state.recordingMode = "OFF";
        try {
            state.setup();
            try (var recording = new Recording()) {
                recording.enable(ServiceLifecycleJmhBenchmark.LIFECYCLE_EVENT).withThreshold(Duration.ZERO);
                recording.start();
                state.requestScopeLifecycle();
                recording.stop();
                Path file = directory.resolve("service-lifecycle.jfr");
                recording.dump(file);
                var events = RecordingFile.readAllEvents(file).stream()
                        .filter(event -> ServiceLifecycleJmhBenchmark.LIFECYCLE_EVENT.equals(event.getEventType().getName()))
                        .toList();
                boolean expected = Boolean.parseBoolean(System.getProperty("service.lifecycle.jfr.expectEvents", "true"));
                assertThat("two managed services, each with four lifecycle phases", events, hasSize(expected ? 8 : 0));
                if (expected) {
                    for (var service : List.of(ServiceLifecycleJmhBenchmark.Consumer.class,
                                               ServiceLifecycleJmhBenchmark.ManagedDependency.class)) {
                        for (String phase : List.of("CONSTRUCTING", "INJECTING", "POST_CONSTRUCTING", "PRE_DESTROYING")) {
                            var matching = events.stream()
                                    .filter(event -> TypeName.create(service).fqName().equals(event.getString("serviceType")))
                                    .filter(event -> phase.equals(event.getString("phase")))
                                    .toList();
                            assertThat(service.getSimpleName() + " " + phase, matching, hasSize(1));
                            assertThat("successful " + phase, matching.getFirst().getString("outcome"), is("SUCCESS"));
                        }
                    }
                }
            }
        } finally {
            state.tearDown();
        }
    }

    private static void callbacks(ServiceLifecycleJmhBenchmark.ManagedInstance instance, int destroyed) {
        assertThat("injection callback count", instance.injected, is(1));
        assertThat("post-construct callback count", instance.initialized, is(1));
        assertThat("pre-destroy callback count", instance.destroyed, is(destroyed));
    }
}
