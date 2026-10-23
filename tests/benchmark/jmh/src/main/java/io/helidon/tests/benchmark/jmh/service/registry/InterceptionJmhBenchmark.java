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

import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.registry.Interception;
import io.helidon.service.registry.InterceptionContext;
import io.helidon.service.registry.InterceptionInvoker;
import io.helidon.service.registry.InterceptionMetadata;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Matching interception costs, including a zero-interceptor baseline.
 * Invokers come from the real service registry; handwritten dispatch uses the same
 * varargs calls as generated intercepted methods. Generated-wrapper integration
 * coverage belongs to the service interception tests; these fixtures are handwritten.
 * Interceptors consume context annotation metadata; Integer results stay in the boxing cache.
 * Constructor results escape to JMH and
 * include the allocation of the actual target object in every parameter case.
 * Use {@code -prof gc} for allocations and compare normal JVM settings with
 * {@code -jvmArgsAppend -XX:-DoEscapeAnalysis} to distinguish scalar replacement.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
@Fork(1)
public class InterceptionJmhBenchmark {
    private static final int VALUE = 7;
    private static final TypeName TRIGGER = TypeName.create(InterceptionJmhBenchmark.class.getName() + ".Trigger");
    private static final List<Annotation> ANNOTATIONS = List.of(Annotation.create(TRIGGER));
    private static final TargetDescriptor TARGET = new TargetDescriptor();

    @Benchmark
    public int methodNoArgs(InvocationState state) throws Exception {
        return state.methodNoArgs.invoke();
    }

    @Benchmark
    public int methodArgument(InvocationState state) throws Exception {
        return state.methodArgument.invoke(VALUE);
    }

    @Benchmark
    public Object constructorNoArgs(InvocationState state) throws Exception {
        return state.constructorNoArgs.invoke();
    }

    @Benchmark
    public Object constructorArgument(InvocationState state) throws Exception {
        return state.constructorArgument.invoke(VALUE);
    }

    /**
     * One immutable set of invokers shared by all benchmark threads.
     */
    @State(Scope.Benchmark)
    public static class InvocationState {
        @Param({"0", "1", "2", "4"})
        public int interceptorCount;

        private ServiceRegistryManager manager;
        private InterceptionInvoker<Integer> methodNoArgs;
        private InterceptionInvoker<Integer> methodArgument;
        private InterceptionInvoker<Payload> constructorNoArgs;
        private InterceptionInvoker<Payload> constructorArgument;

        @Setup(Level.Trial)
        public void setUp() throws Exception {
            var config = ServiceRegistryConfig.builder()
                    .discoverServices(false)
                    .discoverServicesFromServiceLoader(false)
                    .interceptionEnabled(true);
            for (int i = 0; i < interceptorCount; i++) {
                config.putServiceInstance(new InterceptorDescriptor(i), new IncrementingInterceptor());
            }
            manager = ServiceRegistryManager.create(config.build());
            try {
                var metadata = manager.registry().get(InterceptionMetadata.class);
                var instance = new Payload(VALUE);
                methodNoArgs = metadata.createInvoker(instance, TARGET, Set.of(), ANNOTATIONS,
                                                      element(ElementKind.METHOD, "noArgs", int.class, false),
                                                      _ -> instance.noArgs(), Set.of());
                methodArgument = metadata.createInvoker(instance, TARGET, Set.of(), ANNOTATIONS,
                                                        element(ElementKind.METHOD, "argument", int.class, true),
                                                        args -> instance.argument((Integer) args[0]), Set.of());
                constructorNoArgs = metadata.createInvoker(TARGET, Set.of(), ANNOTATIONS,
                                                           element(ElementKind.CONSTRUCTOR, "<init>", Payload.class, false),
                                                           _ -> new Payload(VALUE), Set.of());
                constructorArgument = metadata.createInvoker(TARGET, Set.of(), ANNOTATIONS,
                                                             element(ElementKind.CONSTRUCTOR, "<init>", Payload.class, true),
                                                             args -> new Payload((Integer) args[0]), Set.of());
                // Each matching interceptor adds one to the result. Verify the whole chain,
                // rather than relying on registration alone to establish matching.
                int expected = VALUE + interceptorCount;
                if (methodNoArgs.invoke() != expected
                        || methodArgument.invoke(VALUE) != expected
                        || constructorNoArgs.invoke().value != expected
                        || constructorArgument.invoke(VALUE).value != expected) {
                    throw new IllegalStateException("Expected " + interceptorCount + " matching interceptors per invocation");
                }
            } catch (Exception | Error e) {
                manager.shutdown();
                throw e;
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            manager.shutdown();
        }

        private static TypedElementInfo element(ElementKind kind, String name, Class<?> result, boolean hasArgument) {
            var builder = TypedElementInfo.builder()
                    .kind(kind)
                    .elementName(name)
                    .enclosingType(TARGET.serviceType())
                    .typeName(TypeName.create(result));
            if (hasArgument) {
                builder.addParameterArgument(TypedElementInfo.builder()
                                                     .kind(ElementKind.PARAMETER)
                                                     .elementName("value")
                                                     .typeName(TypeName.create(int.class))
                                                     .build());
            }
            return builder.build();
        }
    }

    static final class Payload {
        int value;

        private Payload(int value) {
            this.value = value;
        }

        private int noArgs() {
            return value;
        }

        private int argument(int argument) {
            return argument;
        }
    }

    private static final class IncrementingInterceptor implements Interception.Interceptor {
        @Override
        @SuppressWarnings("unchecked")
        public <V> V proceed(InterceptionContext ctx, Chain<V> chain, Object... args) throws Exception {
            V result = chain.proceed(args);
            if (result instanceof Payload payload) {
                payload.value += ctx.typeAnnotations().size();
                return result;
            }
            return (V) Integer.valueOf((Integer) result + ctx.typeAnnotations().size());
        }
    }

    private static final class TargetDescriptor implements ServiceDescriptor<Payload> {
        @Override
        public TypeName serviceType() {
            return TypeName.create(Payload.class);
        }

        @Override
        public TypeName descriptorType() {
            return TypeName.create(TargetDescriptor.class);
        }
    }

    private static final class InterceptorDescriptor implements ServiceDescriptor<IncrementingInterceptor> {
        private final TypeName serviceType;
        private final TypeName descriptorType;

        private InterceptorDescriptor(int index) {
            serviceType = TypeName.create(IncrementingInterceptor.class.getName() + index);
            descriptorType = TypeName.create(InterceptorDescriptor.class.getName() + index);
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
            return Set.of(ResolvedType.create(Interception.Interceptor.class));
        }

        @Override
        public Set<Qualifier> qualifiers() {
            return Set.of(Qualifier.createNamed(TRIGGER.fqName()));
        }

        @Override
        public TypeName scope() {
            return Service.Singleton.TYPE;
        }
    }
}
