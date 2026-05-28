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

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.helidon.common.GenericType;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.service.registry.DependencyContext;
import io.helidon.service.registry.FactoryType;
import io.helidon.service.registry.GeneratedService;
import io.helidon.service.registry.InterceptionMetadata;
import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.service.registry.ServiceInfo;
import io.helidon.service.registry.ServiceInstance;
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
import org.openjdk.jmh.infra.Blackhole;

/**
 * Service registry benchmarks for lookup matching and generated interception wrappers.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
@Fork(1)
public class ServiceRegistryJmhBenchmark {
    private static final int DIRECT_SERVICE_COUNT = 128;
    private static final int HIDDEN_FACTORY_COUNT = 32;
    private static final int WRAPPED_INSTANCE_COUNT = 16;
    private static final TypeName DIRECT_CONTRACT_TYPE = TypeName.create(DirectContract.class);
    private static final ResolvedType DIRECT_CONTRACT = ResolvedType.create(DIRECT_CONTRACT_TYPE);
    private static final ResolvedType SERVICES_FACTORY_CONTRACT = ResolvedType.create(Service.ServicesFactory.TYPE);
    private static final Qualifier WRAPPER_QUALIFIER = Qualifier.createNamed("wrapped");
    private static final GenericType<Payload> PAYLOAD_TYPE = GenericType.create(Payload.class);

    @Benchmark
    public List<ServiceInfo> lookupEmptyCriteriaDescriptorsNoCache(RegistryState state) {
        return state.noCacheRegistry.lookupServices(Lookup.EMPTY);
    }

    @Benchmark
    public List<ServiceInfo> lookupDirectContractNoCache(RegistryState state) {
        return state.noCacheRegistry.lookupServices(state.directContractLookup);
    }

    @Benchmark
    public List<ServiceInfo> lookupHiddenFactoryContractNoCache(RegistryState state) {
        return state.noCacheRegistry.lookupServices(state.servicesFactoryContractLookup);
    }

    @Benchmark
    public List<ServiceInfo> lookupHiddenFactoryServiceTypeNoCache(RegistryState state) {
        return state.noCacheRegistry.lookupServices(state.hiddenFactoryServiceTypeLookup);
    }

    @Benchmark
    public List<ServiceInstance<Object>> lookupHiddenFactoryContractInstancesNoCache(RegistryState state) {
        return state.noCacheRegistry.lookupInstances(state.servicesFactoryContractLookup);
    }

    @Benchmark
    public List<ServiceInstance<Object>> lookupHiddenFactoryServiceTypeInstancesNoCache(RegistryState state) {
        return state.noCacheRegistry.lookupInstances(state.hiddenFactoryServiceTypeLookup);
    }

    @Benchmark
    public List<ServiceInfo> lookupDirectContractCacheHit(RegistryState state) {
        return state.cacheRegistry.lookupServices(state.directContractLookup);
    }

    @Benchmark
    public DirectContract getDirectSingleton(RegistryState state) {
        return state.noCacheRegistry.get(state.directContractLookup);
    }

    @Benchmark
    public Payload supplierFactoryWrapperGet(WrapperState state) {
        return state.supplierWrapper.get();
    }

    @Benchmark
    public Optional<Service.QualifiedInstance<Payload>> injectionPointFactoryWrapperFirstOnly(WrapperState state) {
        return state.injectionPointFactoryWrapper.first(state.lookup);
    }

    @Benchmark
    public void injectionPointFactoryWrapperFirstAndGet(WrapperState state, Blackhole blackhole) {
        state.injectionPointFactoryWrapper.first(state.lookup)
                .ifPresent(it -> blackhole.consume(it.get()));
    }

    @Benchmark
    public List<Service.QualifiedInstance<Payload>> injectionPointFactoryWrapperSingleListOnly(WrapperState state) {
        return state.singleInjectionPointFactoryWrapper.list(state.lookup);
    }

    @Benchmark
    public void injectionPointFactoryWrapperSingleListAndGet(WrapperState state, Blackhole blackhole) {
        for (Service.QualifiedInstance<Payload> instance : state.singleInjectionPointFactoryWrapper.list(state.lookup)) {
            blackhole.consume(instance.get());
        }
    }

    @Benchmark
    public List<Service.QualifiedInstance<Payload>> injectionPointFactoryWrapperMultiListOnly(WrapperState state) {
        return state.multiInjectionPointFactoryWrapper.list(state.lookup);
    }

    @Benchmark
    public void injectionPointFactoryWrapperMultiListAndGet(WrapperState state, Blackhole blackhole) {
        for (Service.QualifiedInstance<Payload> instance : state.multiInjectionPointFactoryWrapper.list(state.lookup)) {
            blackhole.consume(instance.get());
        }
    }

    @Benchmark
    public List<Service.QualifiedInstance<Payload>> servicesFactoryWrapperListOnly(WrapperState state) {
        return state.servicesFactoryWrapper.services();
    }

    @Benchmark
    public void servicesFactoryWrapperListAndGet(WrapperState state, Blackhole blackhole) {
        for (Service.QualifiedInstance<Payload> instance : state.servicesFactoryWrapper.services()) {
            blackhole.consume(instance.get());
        }
    }

    @Benchmark
    public List<Service.QualifiedInstance<Payload>> qualifiedFactoryWrapperListOnly(WrapperState state) {
        return state.qualifiedFactoryWrapper.list(WRAPPER_QUALIFIER, state.lookup, PAYLOAD_TYPE);
    }

    @Benchmark
    public void qualifiedFactoryWrapperListAndGet(WrapperState state, Blackhole blackhole) {
        for (Service.QualifiedInstance<Payload> instance
                : state.qualifiedFactoryWrapper.list(WRAPPER_QUALIFIER, state.lookup, PAYLOAD_TYPE)) {
            blackhole.consume(instance.get());
        }
    }

    @State(Scope.Benchmark)
    public static class RegistryState {
        private ServiceRegistryManager noCacheManager;
        private ServiceRegistryManager cacheManager;
        private ServiceRegistry noCacheRegistry;
        private ServiceRegistry cacheRegistry;
        private Lookup directContractLookup;
        private Lookup servicesFactoryContractLookup;
        private Lookup hiddenFactoryServiceTypeLookup;

        @Setup(Level.Trial)
        public void setUp() {
            directContractLookup = Lookup.create(DIRECT_CONTRACT_TYPE);
            servicesFactoryContractLookup = Lookup.create(Service.ServicesFactory.TYPE);
            hiddenFactoryServiceTypeLookup = Lookup.builder()
                    .serviceType(hiddenFactoryType(0))
                    .build();

            List<ServiceDescriptor<?>> descriptors = descriptors();
            noCacheManager = manager(descriptors, false);
            noCacheRegistry = noCacheManager.registry();
            cacheManager = manager(descriptors, true);
            cacheRegistry = cacheManager.registry();

            cacheRegistry.lookupServices(directContractLookup);
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            if (noCacheManager != null) {
                noCacheManager.shutdown();
            }
            if (cacheManager != null) {
                cacheManager.shutdown();
            }
        }

        private static ServiceRegistryManager manager(List<ServiceDescriptor<?>> descriptors, boolean cacheEnabled) {
            ServiceRegistryConfig config = ServiceRegistryConfig.builder()
                    .discoverServices(false)
                    .discoverServicesFromServiceLoader(false)
                    .lookupCacheEnabled(cacheEnabled)
                    .serviceDescriptors(descriptors)
                    .build();
            return ServiceRegistryManager.create(config);
        }

        private static List<ServiceDescriptor<?>> descriptors() {
            List<ServiceDescriptor<?>> result = new ArrayList<>(DIRECT_SERVICE_COUNT + HIDDEN_FACTORY_COUNT);
            for (int i = 0; i < DIRECT_SERVICE_COUNT; i++) {
                result.add(BenchmarkDescriptor.direct(i));
            }
            for (int i = 0; i < HIDDEN_FACTORY_COUNT; i++) {
                result.add(BenchmarkDescriptor.hiddenServicesFactory(i));
            }
            return result;
        }
    }

    @State(Scope.Thread)
    public static class WrapperState {
        private Lookup lookup;
        private SupplierWrapper supplierWrapper;
        private InjectionPointFactoryWrapper injectionPointFactoryWrapper;
        private InjectionPointFactoryWrapper singleInjectionPointFactoryWrapper;
        private InjectionPointFactoryWrapper multiInjectionPointFactoryWrapper;
        private ServicesFactoryWrapper servicesFactoryWrapper;
        private QualifiedFactoryWrapper qualifiedFactoryWrapper;

        @Setup(Level.Trial)
        public void setUp() {
            lookup = Lookup.create(Payload.class);
            supplierWrapper = new SupplierWrapper(() -> new Payload(0));
            injectionPointFactoryWrapper = new InjectionPointFactoryWrapper(new BenchmarkInjectionPointFactory());
            singleInjectionPointFactoryWrapper = new InjectionPointFactoryWrapper(new BenchmarkInjectionPointFactory(1));
            multiInjectionPointFactoryWrapper = new InjectionPointFactoryWrapper(
                    new BenchmarkInjectionPointFactory(WRAPPED_INSTANCE_COUNT));
            servicesFactoryWrapper = new ServicesFactoryWrapper(new BenchmarkServicesFactory());
            qualifiedFactoryWrapper = new QualifiedFactoryWrapper(new BenchmarkQualifiedFactory());
        }
    }

    private interface DirectContract {
        int value();
    }

    private record DirectService(int value) implements DirectContract {
    }

    private record Payload(int value) {
    }

    private record BenchmarkQualifiedInstance<T>(Supplier<T> supplier, Set<Qualifier> qualifiers)
            implements Service.QualifiedInstance<T> {
        @Override
        public T get() {
            return supplier.get();
        }
    }

    private static final class BenchmarkDescriptor<T> implements ServiceDescriptor<T> {
        private final TypeName serviceType;
        private final TypeName descriptorType;
        private final Set<ResolvedType> contracts;
        private final Set<ResolvedType> factoryContracts;
        private final FactoryType factoryType;
        private final double weight;
        private final Supplier<? extends T> supplier;

        private BenchmarkDescriptor(TypeName serviceType,
                                    TypeName descriptorType,
                                    Set<ResolvedType> contracts,
                                    Set<ResolvedType> factoryContracts,
                                    FactoryType factoryType,
                                    double weight,
                                    Supplier<? extends T> supplier) {
            this.serviceType = serviceType;
            this.descriptorType = descriptorType;
            this.contracts = contracts;
            this.factoryContracts = factoryContracts;
            this.factoryType = factoryType;
            this.weight = weight;
            this.supplier = supplier;
        }

        static BenchmarkDescriptor<DirectContract> direct(int index) {
            return new BenchmarkDescriptor<>(
                    TypeName.create(ServiceRegistryJmhBenchmark.class.getName() + ".DirectService" + index),
                    TypeName.create(ServiceRegistryJmhBenchmark.class.getName() + ".DirectService" + index
                                            + "__ServiceDescriptor"),
                    Set.of(DIRECT_CONTRACT),
                    Set.of(),
                    FactoryType.SERVICE,
                    DIRECT_SERVICE_COUNT - index,
                    () -> new DirectService(index));
        }

        static BenchmarkDescriptor<Service.ServicesFactory<DirectContract>> hiddenServicesFactory(int index) {
            return new BenchmarkDescriptor<>(
                    hiddenFactoryType(index),
                    TypeName.create(ServiceRegistryJmhBenchmark.class.getName() + ".HiddenFactory" + index
                                            + "__ServiceDescriptor"),
                    Set.of(),
                    Set.of(SERVICES_FACTORY_CONTRACT),
                    FactoryType.SERVICES,
                    HIDDEN_FACTORY_COUNT - index,
                    () -> () -> List.of(Service.QualifiedInstance.create(new DirectService(index))));
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
        public Set<ResolvedType> factoryContracts() {
            return factoryContracts;
        }

        @Override
        public FactoryType factoryType() {
            return factoryType;
        }

        @Override
        public TypeName scope() {
            return Service.Singleton.TYPE;
        }

        @Override
        public double weight() {
            return weight;
        }

        @Override
        public Object instantiate(DependencyContext ctx, InterceptionMetadata interceptionMetadata) {
            return supplier.get();
        }
    }

    private static final class BenchmarkServicesFactory implements Service.ServicesFactory<Payload> {
        @Override
        public List<Service.QualifiedInstance<Payload>> services() {
            List<Service.QualifiedInstance<Payload>> result = new ArrayList<>(WRAPPED_INSTANCE_COUNT);
            for (int i = 0; i < WRAPPED_INSTANCE_COUNT; i++) {
                int value = i;
                result.add(new BenchmarkQualifiedInstance<>(() -> new Payload(value), Set.of(WRAPPER_QUALIFIER)));
            }
            return result;
        }
    }

    private static final class BenchmarkInjectionPointFactory implements Service.InjectionPointFactory<Payload> {
        private final int instanceCount;

        private BenchmarkInjectionPointFactory() {
            this(WRAPPED_INSTANCE_COUNT);
        }

        private BenchmarkInjectionPointFactory(int instanceCount) {
            this.instanceCount = instanceCount;
        }

        @Override
        public Optional<Service.QualifiedInstance<Payload>> first(Lookup lookup) {
            return Optional.of(new BenchmarkQualifiedInstance<>(() -> new Payload(0), Set.of()));
        }

        @Override
        public List<Service.QualifiedInstance<Payload>> list(Lookup lookup) {
            List<Service.QualifiedInstance<Payload>> result = new ArrayList<>(instanceCount);
            for (int i = 0; i < instanceCount; i++) {
                int value = i;
                result.add(new BenchmarkQualifiedInstance<>(() -> new Payload(value), Set.of()));
            }
            return result;
        }
    }

    private static final class BenchmarkQualifiedFactory implements Service.QualifiedFactory<Payload, Annotation> {
        @Override
        public Optional<Service.QualifiedInstance<Payload>> first(Qualifier qualifier,
                                                                  Lookup lookup,
                                                                  GenericType<Payload> type) {
            return Optional.of(new BenchmarkQualifiedInstance<>(() -> new Payload(0), Set.of(qualifier)));
        }

        @Override
        public List<Service.QualifiedInstance<Payload>> list(Qualifier qualifier,
                                                             Lookup lookup,
                                                             GenericType<Payload> type) {
            List<Service.QualifiedInstance<Payload>> result = new ArrayList<>(WRAPPED_INSTANCE_COUNT);
            for (int i = 0; i < WRAPPED_INSTANCE_COUNT; i++) {
                int value = i;
                result.add(new BenchmarkQualifiedInstance<>(() -> new Payload(value), Set.of(qualifier)));
            }
            return result;
        }
    }

    private static final class SupplierWrapper extends GeneratedService.SupplierFactoryInterceptionWrapper<Payload> {
        private SupplierWrapper(Supplier<Payload> delegate) {
            super(delegate);
        }

        @Override
        protected Payload wrap(Payload originalInstance) {
            return new Payload(originalInstance.value() + 1);
        }
    }

    private static final class InjectionPointFactoryWrapper
            extends GeneratedService.IpFactoryInterceptionWrapper<Payload> {
        private InjectionPointFactoryWrapper(Service.InjectionPointFactory<Payload> delegate) {
            super(delegate);
        }

        @Override
        protected Payload wrap(Payload originalInstance) {
            return new Payload(originalInstance.value() + 1);
        }
    }

    private static final class ServicesFactoryWrapper extends GeneratedService.ServicesFactoryInterceptionWrapper<Payload> {
        private ServicesFactoryWrapper(Service.ServicesFactory<Payload> delegate) {
            super(delegate);
        }

        @Override
        protected Payload wrap(Payload originalInstance) {
            return new Payload(originalInstance.value() + 1);
        }
    }

    private static final class QualifiedFactoryWrapper
            extends GeneratedService.QualifiedFactoryInterceptionWrapper<Payload, Annotation> {
        private QualifiedFactoryWrapper(Service.QualifiedFactory<Payload, Annotation> delegate) {
            super(delegate);
        }

        @Override
        protected Payload wrap(Payload originalInstance) {
            return new Payload(originalInstance.value() + 1);
        }
    }

    private static TypeName hiddenFactoryType(int index) {
        return TypeName.create(ServiceRegistryJmhBenchmark.class.getName() + ".HiddenFactory" + index);
    }
}
