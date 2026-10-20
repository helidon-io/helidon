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
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.helidon.common.GenericType;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.service.registry.Dependency;
import io.helidon.service.registry.DependencyContext;
import io.helidon.service.registry.InterceptionMetadata;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Service registry benchmarks for dependency injection and shutdown.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
@Fork(1)
public class ServiceRegistryJmhBenchmark {
    /**
     * Resolve a new consumer that injects a shared singleton.
     *
     * @param state shared registry state
     * @return the newly created consumer
     */
    @Benchmark
    public PerLookupConsumer getPerLookupConsumerWithSingletonDependency(InjectionState state) {
        return state.registry.get(PerLookupConsumer.class);
    }

    /**
     * Resolve a new consumer that injects a new per-lookup dependency.
     *
     * @param state registry shared by all benchmark threads
     * @return the newly created consumer
     */
    @Benchmark
    public PerLookupConsumer getPerLookupConsumerWithPerLookupDependency(PerLookupDependencyState state) {
        return state.registry.get(PerLookupConsumer.class);
    }

    /**
     * Construct and shut down a registry containing a singleton dependency chain.
     *
     * @param state isolated lifecycle configuration
     * @return the destroyed consumer
     */
    @Benchmark
    public SingletonConsumer createAndShutdownSingletonDependencyChain(LifecycleState state) {
        ServiceRegistryManager manager = ServiceRegistryManager.create(state.config);
        try {
            return manager.registry().get(SingletonConsumer.class);
        } finally {
            manager.shutdown();
        }
    }

    /**
     * Registry shared by threads resolving per-lookup consumers.
     */
    @State(Scope.Benchmark)
    public static class InjectionState {
        private ServiceRegistryManager manager;
        private ServiceRegistry registry;
        private Connection connection;

        /**
         * Initialize the registry and verify injection identity.
         */
        @Setup(Level.Trial)
        public void setUp() {
            manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                           .discoverServices(false)
                                                           .discoverServicesFromServiceLoader(false)
                                                           .addServiceDescriptor(new ConnectionDescriptor())
                                                           .addServiceDescriptor(new PerLookupConsumerDescriptor())
                                                           .build());
            registry = manager.registry();
            connection = registry.get(Connection.class);
            PerLookupConsumer first = registry.get(PerLookupConsumer.class);
            PerLookupConsumer second = registry.get(PerLookupConsumer.class);
            if (first == second || first.connection != connection || second.connection != connection) {
                throw new IllegalStateException("Per-lookup consumers must inject the same singleton connection");
            }
        }

        /**
         * Shut down the registry and verify singleton destruction.
         */
        @TearDown(Level.Trial)
        public void tearDown() {
            manager.shutdown();
            if (!connection.stopped) {
                throw new IllegalStateException("Singleton connection was not destroyed");
            }
        }
    }

    /**
     * Registry shared by threads resolving consumers with per-lookup dependencies.
     */
    @State(Scope.Benchmark)
    public static class PerLookupDependencyState {
        private ServiceRegistryManager manager;
        private ServiceRegistry registry;

        /**
         * Initialize the registry and verify fresh consumers and dependencies.
         */
        @Setup(Level.Trial)
        public void setUp() {
            manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                           .discoverServices(false)
                                                           .discoverServicesFromServiceLoader(false)
                                                           .addServiceDescriptor(
                                                                   new ConnectionDescriptor(Service.PerLookup.TYPE))
                                                           .addServiceDescriptor(new PerLookupConsumerDescriptor())
                                                           .build());
            registry = manager.registry();
            PerLookupConsumer first = registry.get(PerLookupConsumer.class);
            PerLookupConsumer second = registry.get(PerLookupConsumer.class);
            if (first == second || first.connection == second.connection) {
                throw new IllegalStateException("Per-lookup consumers must inject fresh per-lookup connections");
            }
        }

        /**
         * Shut down the shared registry.
         */
        @TearDown(Level.Trial)
        public void tearDown() {
            manager.shutdown();
        }
    }

    /**
     * Per-thread configuration for isolated lifecycle measurements.
     */
    @State(Scope.Thread)
    public static class LifecycleState {
        private ServiceRegistryConfig config;

        /**
         * Initialize the chain configuration and verify lifecycle completion.
         */
        @Setup(Level.Trial)
        public void setUp() {
            config = ServiceRegistryConfig.builder()
                    .discoverServices(false)
                    .discoverServicesFromServiceLoader(false)
                    .addServiceDescriptor(new ConnectionDescriptor())
                    .addServiceDescriptor(new MiddleDescriptor())
                    .addServiceDescriptor(new SingletonConsumerDescriptor())
                    .build();
            ServiceRegistryManager manager = ServiceRegistryManager.create(config);
            SingletonConsumer consumer;
            try {
                ServiceRegistry registry = manager.registry();
                consumer = registry.get(SingletonConsumer.class);
                if (consumer.middle != registry.get(Middle.class)
                        || consumer.middle.connection != registry.get(Connection.class)) {
                    throw new IllegalStateException("Singleton chain must inject the registered service instances");
                }
            } finally {
                manager.shutdown();
            }
            // Both runtimes must complete all callbacks; ordering is covered by lifecycle regression tests.
            if (!consumer.stopped || !consumer.middle.stopped || !consumer.middle.connection.stopped) {
                throw new IllegalStateException("Singleton chain was not fully destroyed");
            }
        }
    }

    private static final class Connection {
        private boolean stopped;
    }

    private static final class Middle {
        private final Connection connection;
        private boolean stopped;

        private Middle(Connection connection) {
            this.connection = connection;
        }
    }

    private static final class SingletonConsumer {
        private final Middle middle;
        private boolean stopped;

        private SingletonConsumer(Middle middle) {
            this.middle = middle;
        }
    }

    private record PerLookupConsumer(Connection connection) {
    }

    private abstract static class LifecycleDescriptor<T> implements ServiceDescriptor<T> {
        private final TypeName serviceType;
        private final TypeName descriptorType;
        private final Set<ResolvedType> contracts;
        private final List<Dependency> dependencies;

        private LifecycleDescriptor(Class<T> serviceClass, Class<?> descriptorClass, Class<?> dependencyClass) {
            serviceType = TypeName.create(serviceClass);
            descriptorType = TypeName.create(descriptorClass);
            contracts = Set.of(ResolvedType.create(serviceClass));
            dependencies = dependencyClass == null ? List.of() : List.of(Dependency.builder()
                    .elementKind(ElementKind.CONSTRUCTOR)
                    .contract(TypeName.create(dependencyClass))
                    .descriptor(descriptorType)
                    .descriptorConstant("DEPENDENCY")
                    .name("dependency")
                    .service(serviceType)
                    .typeName(TypeName.create(dependencyClass))
                    .contractType(GenericType.create(dependencyClass))
                    .build());
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
        public List<Dependency> dependencies() {
            return dependencies;
        }
    }

    private static final class ConnectionDescriptor extends LifecycleDescriptor<Connection> {
        private final TypeName scope;

        private ConnectionDescriptor() {
            this(Service.Singleton.TYPE);
        }

        private ConnectionDescriptor(TypeName scope) {
            super(Connection.class, ConnectionDescriptor.class, null);
            this.scope = scope;
        }

        @Override
        public TypeName scope() {
            return scope;
        }

        @Override
        public Object instantiate(DependencyContext ctx, InterceptionMetadata interceptionMetadata) {
            return new Connection();
        }

        @Override
        public void preDestroy(Connection instance) {
            instance.stopped = true;
        }
    }

    private static final class MiddleDescriptor extends LifecycleDescriptor<Middle> {
        private MiddleDescriptor() {
            super(Middle.class, MiddleDescriptor.class, Connection.class);
        }

        @Override
        public Object instantiate(DependencyContext ctx, InterceptionMetadata interceptionMetadata) {
            return new Middle(ctx.dependency(dependencies().getFirst()));
        }

        @Override
        public void preDestroy(Middle instance) {
            instance.stopped = true;
        }
    }

    private static final class SingletonConsumerDescriptor extends LifecycleDescriptor<SingletonConsumer> {
        private SingletonConsumerDescriptor() {
            super(SingletonConsumer.class, SingletonConsumerDescriptor.class, Middle.class);
        }

        @Override
        public Object instantiate(DependencyContext ctx, InterceptionMetadata interceptionMetadata) {
            return new SingletonConsumer(ctx.dependency(dependencies().getFirst()));
        }

        @Override
        public void preDestroy(SingletonConsumer instance) {
            instance.stopped = true;
        }
    }

    private static final class PerLookupConsumerDescriptor extends LifecycleDescriptor<PerLookupConsumer> {
        private PerLookupConsumerDescriptor() {
            super(PerLookupConsumer.class, PerLookupConsumerDescriptor.class, Connection.class);
        }

        @Override
        public TypeName scope() {
            return Service.PerLookup.TYPE;
        }

        @Override
        public Object instantiate(DependencyContext ctx, InterceptionMetadata interceptionMetadata) {
            return new PerLookupConsumer(ctx.dependency(dependencies().getFirst()));
        }
    }

}
