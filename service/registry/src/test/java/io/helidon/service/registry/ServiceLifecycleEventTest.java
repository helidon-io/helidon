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

package io.helidon.service.registry;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.common.types.TypeName;

import jdk.jfr.EventType;
import jdk.jfr.Recording;
import jdk.jfr.SettingDescriptor;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceLifecycleEventTest {
    private static final String EVENT_NAME = "io.helidon.ServiceLifecycle";
    private static final ActivationRequest ACTIVE = ActivationRequest.builder().targetPhase(ActivationPhase.ACTIVE).build();

    @TempDir
    Path directory;

    @Test
    void recordsManagedCallbacksOnceAndExcludesLookups() throws IOException {
        var descriptor = new TestDescriptor(Service.Singleton.TYPE, null);
        try (var fixture = new Fixture(descriptor); var recording = recording()) {
            recording.start();
            assertThat(fixture.activator.activate(ACTIVE).success(), is(true));
            assertThat(fixture.activator.activate(ACTIVE).success(), is(true));
            fixture.activator.instances(Lookup.EMPTY);
            fixture.activator.instances(Lookup.EMPTY);
            assertThat(fixture.activator.deactivate().success(), is(true));
            fixture.activator.deactivate();
            recording.stop();

            var events = events(recording);
            assertThat(events.stream().map(it -> it.getString("phase")).toList(),
                       is(List.of("CONSTRUCTING", "INJECTING", "POST_CONSTRUCTING", "PRE_DESTROYING")));
            assertThat(descriptor.calls, is(List.of("construct", "inject", "postConstruct", "preDestroy")));
            for (var event : events) {
                assertThat(event.getString("serviceType"), is(descriptor.serviceType().fqName()));
                assertThat(event.getString("outcome"), is("SUCCESS"));
                assertThat(event.getStackTrace(), notNullValue());
                assertThat(event.getThread().getJavaThreadId(), is(Thread.currentThread().threadId()));
                assertThat(event.getEventType().getFields().stream()
                                   .filter(it -> it.getTypeName().equals(String.class.getName()))
                                   .map(it -> it.getName())
                                   .collect(Collectors.toSet()),
                           is(Set.of("serviceType", "phase", "outcome")));
            }
        }
    }

    @Test
    void recordsFastFailureAndPreservesOriginalException() throws IOException {
        var failure = new IllegalStateException("private-service-detail");
        var descriptor = new TestDescriptor(Service.Singleton.TYPE, failure);
        try (var fixture = new Fixture(descriptor); var recording = recording()) {
            recording.start();
            assertThat(assertThrows(IllegalStateException.class, () -> fixture.activator.activate(ACTIVE)),
                       sameInstance(failure));
            recording.stop();

            var events = events(recording);
            assertThat(events.size(), is(1));
            assertThat(events.getFirst().getString("phase"), is("CONSTRUCTING"));
            assertThat(events.getFirst().getString("outcome"), is("FAILURE"));
            assertThat(descriptor.calls, is(List.of("construct")));
        }
    }

    @Test
    void excludesPerLookupCreation() throws IOException {
        var descriptor = new TestDescriptor(Service.PerLookup.TYPE, null);
        try (var fixture = new Fixture(descriptor); var recording = recording()) {
            recording.start();
            fixture.activator.activate(ACTIVE);
            fixture.activator.instances(Lookup.EMPTY);
            fixture.activator.instances(Lookup.EMPTY);
            fixture.activator.deactivate();
            recording.stop();

            assertThat("the per-lookup service was created", descriptor.calls.contains("construct"), is(true));
            assertThat(events(recording), is(List.of()));
        }
    }

    @Test
    void recordsFailureInEachLifecyclePhase() throws IOException {
        for (var phase : List.of(ActivationPhase.INJECTING,
                                 ActivationPhase.POST_CONSTRUCTING,
                                 ActivationPhase.PRE_DESTROYING)) {
            var failure = new IllegalStateException("private-service-detail");
            var descriptor = new TestDescriptor(Service.Singleton.TYPE, failure, phase);
            try (var fixture = new Fixture(descriptor); var recording = recording()) {
                recording.start();
                if (phase == ActivationPhase.PRE_DESTROYING) {
                    fixture.activator.activate(ACTIVE);
                    assertThat(assertThrows(IllegalStateException.class, fixture.activator::deactivate),
                               sameInstance(failure));
                } else {
                    assertThat(assertThrows(IllegalStateException.class, () -> fixture.activator.activate(ACTIVE)),
                               sameInstance(failure));
                }
                recording.stop();

                var events = events(recording);
                assertThat(events.getLast().getString("phase"), is(phase.name()));
                assertThat(events.getLast().getString("outcome"), is("FAILURE"));
                assertThat(events.stream().filter(it -> it.getString("outcome").equals("FAILURE")).count(), is(1L));
            }
        }
    }

    @Test
    void defaultsRemainControllableByJfr() {
        var type = EventType.getEventType(ServiceLifecycleEvent.class);
        var settings = type.getSettingDescriptors().stream()
                .collect(Collectors.toMap(SettingDescriptor::getName, SettingDescriptor::getDefaultValue));
        assertThat(type.getName(), is(EVENT_NAME));
        assertThat(type.getCategoryNames(), is(List.of("Helidon", "Service Registry")));
        assertThat(settings.get("enabled"), is("true"));
        assertThat(settings.get("threshold"), is("10 ms"));
        assertThat(settings.get("stackTrace"), is("true"));
    }

    @Test
    void disabledRecordingStillRunsLifecycle() throws IOException {
        var descriptor = new TestDescriptor(Service.Singleton.TYPE, null);
        try (var fixture = new Fixture(descriptor); var recording = new Recording()) {
            recording.disable(EVENT_NAME);
            recording.start();
            fixture.activator.activate(ACTIVE);
            fixture.activator.deactivate();
            recording.stop();
            assertThat(events(recording), is(List.of()));
            assertThat(descriptor.calls, is(List.of("construct", "inject", "postConstruct", "preDestroy")));
        }
    }

    private static Recording recording() {
        var recording = new Recording();
        recording.enable(EVENT_NAME).withThreshold(Duration.ZERO).withStackTrace();
        return recording;
    }

    private List<RecordedEvent> events(Recording recording) throws IOException {
        var file = directory.resolve("service-lifecycle.jfr");
        recording.dump(file);
        return RecordingFile.readAllEvents(file).stream()
                .filter(it -> it.getEventType().getName().equals(EVENT_NAME))
                .filter(it -> it.getThread().getJavaThreadId() == Thread.currentThread().threadId())
                .toList();
    }

    private static final class Fixture implements AutoCloseable {
        private final ServiceRegistryManager manager;
        private final Activator<Object> activator;

        private Fixture(TestDescriptor descriptor) {
            manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                            .discoverServices(false)
                                                            .discoverServicesFromServiceLoader(false)
                                                            .interceptionEnabled(false)
                                                            .build());
            var registry = (CoreServiceRegistry) manager.registry();
            var provider = new ServiceProvider<>(registry, descriptor);
            activator = Activators.create(registry, provider).get();
        }

        @Override
        public void close() {
            manager.shutdown();
        }
    }

    private static final class TestDescriptor implements ServiceDescriptor<Object> {
        private final TypeName scope;
        private final IllegalStateException failure;
        private final ActivationPhase failurePhase;
        private final List<String> calls = new ArrayList<>();

        private TestDescriptor(TypeName scope, IllegalStateException failure) {
            this(scope, failure, ActivationPhase.CONSTRUCTING);
        }

        private TestDescriptor(TypeName scope, IllegalStateException failure, ActivationPhase failurePhase) {
            this.scope = scope;
            this.failure = failure;
            this.failurePhase = failurePhase;
        }

        @Override
        public TypeName serviceType() {
            return TypeName.create("test.RecordedManagedService");
        }

        @Override
        public TypeName descriptorType() {
            return TypeName.create(TestDescriptor.class);
        }

        @Override
        public TypeName scope() {
            return scope;
        }

        @Override
        public Object instantiate(DependencyContext ctx, InterceptionMetadata metadata) {
            calls.add("construct");
            if (failure != null && failurePhase == ActivationPhase.CONSTRUCTING) {
                throw failure;
            }
            return new Object() {
                @Override
                public String toString() {
                    throw new AssertionError("Service instances must not be formatted for diagnostics");
                }
            };
        }

        @Override
        public void inject(DependencyContext ctx, InterceptionMetadata metadata, Set<String> injected, Object instance) {
            calls.add("inject");
            if (failure != null && failurePhase == ActivationPhase.INJECTING) {
                throw failure;
            }
        }

        @Override
        public void postConstruct(Object instance) {
            calls.add("postConstruct");
            if (failure != null && failurePhase == ActivationPhase.POST_CONSTRUCTING) {
                throw failure;
            }
        }

        @Override
        public void preDestroy(Object instance) {
            calls.add("preDestroy");
            if (failure != null && failurePhase == ActivationPhase.PRE_DESTROYING) {
                throw failure;
            }
        }
    }
}
