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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.helidon.common.GenericType;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.service.registry.Dependency;
import io.helidon.service.registry.DependencyContext;
import io.helidon.service.registry.InterceptionMetadata;
import io.helidon.service.registry.PerRequestScopeHandler__ServiceDescriptor;
import io.helidon.service.registry.Scopes;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

import jdk.jfr.Recording;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;

/**
 * Managed request-scope lifecycle overhead with a reused registry and two lightweight services.
 * The enabled mode retains the event's default 10 ms threshold; event collection is checked
 * separately by a smoke test with a zero threshold. Event names keep this workload usable
 * against a baseline runtime that does not define the event.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(1)
@State(Scope.Thread)
public class ServiceLifecycleJmhBenchmark {
    static final String LIFECYCLE_EVENT = "io.helidon.ServiceLifecycle";

    /** No recording, a recording with the event disabled, or the event enabled at its default threshold. */
    @Param({"OFF", "DISABLED", "ENABLED"})
    public String recordingMode;

    private ServiceRegistryManager manager;
    private Recording recording;
    private ServiceRegistry registry;
    private Scopes scopes;

    @Setup
    public void setup() {
        if (!List.of("OFF", "DISABLED", "ENABLED").contains(recordingMode)) {
            throw new IllegalArgumentException("Unknown recording mode: " + recordingMode);
        }
        try {
            if (!"OFF".equals(recordingMode)) {
                recording = new Recording();
                if ("ENABLED".equals(recordingMode)) {
                    recording.enable(LIFECYCLE_EVENT);
                } else {
                    recording.disable(LIFECYCLE_EVENT);
                }
                recording.start();
            }
            manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                           .discoverServices(false)
                                                           .discoverServicesFromServiceLoader(false)
                                                           .addServiceDescriptor(
                                                                   PerRequestScopeHandler__ServiceDescriptor.INSTANCE)
                                                           .addServiceDescriptor(new DependencyDescriptor())
                                                           .addServiceDescriptor(new ConsumerDescriptor())
                                                           .build());
            registry = manager.registry();
            scopes = registry.get(Scopes.class);
            // Initialize the scope handler and registry caches outside measurement.
            requestScopeLifecycle();
        } catch (RuntimeException | Error failure) {
            try {
                tearDown();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    @Benchmark
    public Object requestScopeLifecycle() {
        try (var _ = scopes.createScope(Service.PerRequest.TYPE, "benchmark", Map.of())) {
            return registry.get(Consumer.class);
        }
    }

    @TearDown
    public void tearDown() {
        try {
            if (manager != null) {
                manager.shutdown();
                manager = null;
            }
        } finally {
            if (recording != null) {
                recording.close();
                recording = null;
            }
        }
    }

    ServiceRegistry registry() {
        return registry;
    }

    Scopes scopes() {
        return scopes;
    }

    static class ManagedInstance {
        int injected;
        int initialized;
        int destroyed;
    }

    static final class Consumer extends ManagedInstance {
        final ManagedDependency dependency;

        private Consumer(ManagedDependency dependency) {
            this.dependency = dependency;
        }
    }

    static final class ManagedDependency extends ManagedInstance {
    }

    private abstract static class LifecycleDescriptor<T extends ManagedInstance> implements ServiceDescriptor<T> {
        private final TypeName serviceType;
        private final TypeName descriptorType;
        private final Set<ResolvedType> contracts;

        private LifecycleDescriptor(Class<T> serviceClass, Class<?> descriptorClass) {
            serviceType = TypeName.create(serviceClass);
            descriptorType = TypeName.create(descriptorClass);
            contracts = Set.of(ResolvedType.create(serviceClass));
        }

        @Override
        public TypeName serviceType() {
            return serviceType;
        }

        @Override
        public TypeName descriptorType() {
            return descriptorType;
        }

        @Override
        public Set<ResolvedType> contracts() {
            return contracts;
        }

        @Override
        public TypeName scope() {
            return Service.PerRequest.TYPE;
        }

        @Override
        public void inject(DependencyContext ctx, InterceptionMetadata metadata, Set<String> injected, T instance) {
            instance.injected++;
        }

        @Override
        public void postConstruct(T instance) {
            instance.initialized++;
        }

        @Override
        public void preDestroy(T instance) {
            instance.destroyed++;
        }
    }

    private static final class DependencyDescriptor extends LifecycleDescriptor<ManagedDependency> {
        private DependencyDescriptor() {
            super(ManagedDependency.class, DependencyDescriptor.class);
        }

        @Override
        public Object instantiate(DependencyContext ctx, InterceptionMetadata metadata) {
            return new ManagedDependency();
        }
    }

    private static final class ConsumerDescriptor extends LifecycleDescriptor<Consumer> {
        private final List<Dependency> dependencies;

        private ConsumerDescriptor() {
            super(Consumer.class, ConsumerDescriptor.class);
            dependencies = List.of(Dependency.builder()
                                          .elementKind(ElementKind.CONSTRUCTOR)
                                          .contract(TypeName.create(ManagedDependency.class))
                                          .descriptor(descriptorType())
                                          .descriptorConstant("DEPENDENCY")
                                          .name("dependency")
                                          .service(serviceType())
                                          .typeName(TypeName.create(ManagedDependency.class))
                                          .contractType(GenericType.create(ManagedDependency.class))
                                          .build());
        }

        @Override
        public List<Dependency> dependencies() {
            return dependencies;
        }

        @Override
        public Object instantiate(DependencyContext ctx, InterceptionMetadata metadata) {
            return new Consumer(ctx.dependency(dependencies.getFirst()));
        }
    }
}
