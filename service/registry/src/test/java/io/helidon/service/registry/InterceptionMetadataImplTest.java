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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InterceptionMetadataImplTest {
    private static final Annotation TRIGGER = Annotation.create(TypeName.create("test.Intercepted"));
    private static final ServiceDescriptor<Object> SERVICE = new TargetDescriptor();
    private static final TypedElementInfo METHOD = TypedElementInfo.builder()
            .kind(ElementKind.METHOD)
            .elementName("message")
            .typeName(TypeName.create(String.class))
            .enclosingType(SERVICE.serviceType())
            .build();
    private static final TypedElementInfo CONSTRUCTOR = TypedElementInfo.builder()
            .kind(ElementKind.CONSTRUCTOR)
            .elementName("<init>")
            .typeName(SERVICE.serviceType())
            .enclosingType(SERVICE.serviceType())
            .build();

    @Test
    void testNoMatchingInterceptorReturnsTarget() {
        var creations = new AtomicInteger();
        var descriptor = new InterceptorDescriptor(1, Service.PerLookup.TYPE, () -> {
            creations.incrementAndGet();
            return new ObservingInterceptor((_, _) -> { });
        });
        try (var fixture = new Fixture(descriptor)) {
            InterceptionInvoker<String> target = args -> (String) args[0];
            assertThat(fixture.metadata.createInvoker(SERVICE, Set.of(), List.of(), CONSTRUCTOR, target, Set.of()),
                       sameInstance(target));
            var instanceInvoker = fixture.metadata.createInvoker(new Object(), SERVICE, Set.of(), List.of(), METHOD,
                                                                  target, Set.of());
            assertThat(instanceInvoker, sameInstance(target));
            assertThat(creations.get(), is(0));
        }
    }

    @Test
    void testConstructorAndInstanceMetadata() throws Exception {
        var contexts = new ArrayList<InterceptionContext>();
        var instance = new Object();
        var interceptor = new ObservingInterceptor((ctx, _) -> contexts.add(ctx));
        try (var fixture = new Fixture(new InterceptorDescriptor(1, () -> interceptor))) {
            var constructor = fixture.metadata.createInvoker(SERVICE, Set.of(), List.of(TRIGGER), CONSTRUCTOR,
                                                              _ -> instance, Set.of());
            var method = fixture.metadata.createInvoker(instance, SERVICE, Set.of(), List.of(TRIGGER), METHOD,
                                                         args -> args[0], Set.of());
            assertThat(constructor.invoke(), sameInstance(instance));
            assertThat(constructor.invoke(), sameInstance(instance));
            assertThat(method.invoke("first"), is("first"));
            assertThat(method.invoke("second"), is("second"));

            assertThat(contexts.size(), is(4));
            for (var ctx : contexts) {
                assertThat(ctx.serviceInfo(), sameInstance(SERVICE));
                assertThat(ctx.typeAnnotations(), is(List.of(TRIGGER)));
            }
            for (var ctx : contexts.subList(0, 2)) {
                assertThat(ctx.serviceInstance(), is(Optional.empty()));
                assertThat(ctx.elementInfo(), is(CONSTRUCTOR));
            }
            for (var ctx : contexts.subList(2, contexts.size())) {
                assertThat(ctx.serviceInstance().orElseThrow(), sameInstance(instance));
                assertThat(ctx.elementInfo(), is(METHOD));
            }
        }
    }

    @Test
    void testSingleAndMultipleInterceptorsPreserveOrderAndArguments() throws Exception {
        var calls = new ArrayList<String>();
        var first = new ObservingInterceptor((_, args) -> {
            calls.add("first");
            args[0] = args[0] + "-first";
        });
        var second = new ObservingInterceptor((_, args) -> {
            calls.add("second");
            args[0] = args[0] + "-second";
        });
        var firstDescriptor = new InterceptorDescriptor(2, () -> first);
        var secondDescriptor = new InterceptorDescriptor(1, () -> second);
        for (var descriptors : List.of(List.of(firstDescriptor), List.of(secondDescriptor, firstDescriptor))) {
            calls.clear();
            try (var fixture = new Fixture(descriptors.toArray(InterceptorDescriptor[]::new))) {
                var invoker = fixture.invoker(args -> {
                    calls.add("target");
                    return (String) args[0];
                });
                if (descriptors.size() == 1) {
                    assertThat(invoker.invoke("value"), is("value-first"));
                    assertThat(calls, is(List.of("first", "target")));
                } else {
                    assertThat(invoker.invoke("value"), is("value-first-second"));
                    assertThat(calls, is(List.of("first", "second", "target")));
                }
            }
        }
    }

    @Test
    void testInterceptorSuppliersRemainLazyAndRespectScope() throws Exception {
        for (var scope : List.of(Service.PerLookup.TYPE, Service.Singleton.TYPE)) {
            var creations = new AtomicInteger();
            var observedInstances = new ArrayList<Integer>();
            var descriptor = new InterceptorDescriptor(1, scope, () -> {
                int id = creations.incrementAndGet();
                return new ObservingInterceptor((_, _) -> observedInstances.add(id));
            });
            try (var fixture = new Fixture(descriptor)) {
                var invoker = fixture.invoker(args -> (String) args[0]);
                assertThat("Invoker creation must not activate interceptors", creations.get(), is(0));
                assertThat(invoker.invoke("one"), is("one"));
                assertThat(invoker.invoke("two"), is("two"));
                assertThat("Interceptor identities for " + scope, observedInstances,
                           is(scope.equals(Service.PerLookup.TYPE) ? List.of(1, 2) : List.of(1, 1)));
            }
        }
    }

    @Test
    void testShortCircuitDoesNotActivateLaterInterceptor() throws Exception {
        var firstCalls = new AtomicInteger();
        var laterCreations = new AtomicInteger();
        var targetCalls = new AtomicInteger();
        var shortCircuit = new Interception.Interceptor() {
            @Override
            public <V> V proceed(InterceptionContext ctx, Chain<V> chain, Object... args) {
                firstCalls.incrementAndGet();
                return null;
            }
        };
        var later = new InterceptorDescriptor(1, Service.PerLookup.TYPE, () -> {
            laterCreations.incrementAndGet();
            return new ObservingInterceptor((_, _) -> { });
        });
        try (var fixture = new Fixture(new InterceptorDescriptor(2, () -> shortCircuit), later)) {
            var invoker = fixture.invoker(_ -> {
                targetCalls.incrementAndGet();
                return null;
            });
            invoker.invoke();
            invoker.invoke();
            assertThat(firstCalls.get(), is(2));
            assertThat("Unreached per-lookup interceptor creations", laterCreations.get(), is(0));
            assertThat("Short-circuited target calls", targetCalls.get(), is(0));
        }
    }

    @Test
    void testOverlappingCallsHaveIndependentInvocationState() throws Exception {
        var barrier = new CyclicBarrier(2);
        var interceptor = new Interception.Interceptor() {
            @Override
            public <V> V proceed(InterceptionContext ctx, Chain<V> chain, Object... args) throws Exception {
                barrier.await(10, TimeUnit.SECONDS);
                return chain.proceed(args);
            }
        };
        var calls = new AtomicInteger();
        try (var fixture = new Fixture(new InterceptorDescriptor(2, () -> interceptor),
                                       new InterceptorDescriptor(1, () -> new ObservingInterceptor((_, args) -> {
                                           calls.incrementAndGet();
                                           args[0] = args[0] + "-intercepted";
                                       })))) {
            var invoker = fixture.invoker(args -> (String) args[0]);
            var executor = Executors.newFixedThreadPool(2);
            try {
                var first = executor.submit(() -> invoker.invoke("one"));
                var second = executor.submit(() -> invoker.invoke("two"));
                assertThat(first.get(15, TimeUnit.SECONDS), is("one-intercepted"));
                assertThat(second.get(15, TimeUnit.SECONDS), is("two-intercepted"));
                assertThat(calls.get(), is(2));
            } finally {
                executor.shutdownNow();
                assertThat("Invocation workers terminated", executor.awaitTermination(15, TimeUnit.SECONDS), is(true));
            }
        }
    }

    @Test
    void testReentrantCallUsesIndependentInvocationState() throws Exception {
        var reference = new AtomicReference<InterceptionInvoker<String>>();
        var calls = new ArrayList<String>();
        var interceptor = new Interception.Interceptor() {
            @Override
            public <V> V proceed(InterceptionContext ctx, Chain<V> chain, Object... args) throws Exception {
                calls.add("interceptor-" + args[0]);
                if (args[0].equals("outer")) {
                    assertThat(reference.get().invoke("inner"), is("inner"));
                }
                return chain.proceed(args);
            }
        };
        try (var fixture = new Fixture(new InterceptorDescriptor(1, () -> interceptor))) {
            var invoker = fixture.invoker(args -> {
                calls.add("target-" + args[0]);
                return (String) args[0];
            });
            reference.set(invoker);
            assertThat(invoker.invoke("outer"), is("outer"));
            assertThat(calls, is(List.of("interceptor-outer", "interceptor-inner", "target-inner", "target-outer")));
        }
    }

    @Test
    void testDeclaredCheckedExceptionsAndSubclassesArePreserved() {
        try (var fixture = new Fixture(new InterceptorDescriptor(1, () -> new ObservingInterceptor((_, _) -> { })))) {
            for (var failure : List.of(new IOException("declared"), new FileNotFoundException("subclass"))) {
                var invoker = fixture.metadata.createInvoker(new Object(), SERVICE, Set.of(), List.of(TRIGGER), METHOD,
                                                              _ -> {
                                                                  throw failure;
                                                              }, Set.of(IOException.class));
                assertThat(assertThrows(IOException.class, invoker::invoke), sameInstance(failure));
            }
            var failure = new IOException("undeclared");
            var invoker = fixture.invoker(_ -> {
                throw failure;
            });
            var exception = assertThrows(InterceptionException.class, invoker::invoke);
            assertThat(exception.getCause(), sameInstance(failure));
            assertThat(exception.targetWasCalled(), is(true));
        }
    }

    @Test
    void testRetryAfterFailureAndDuplicateAfterSuccess() throws Exception {
        var attempts = new AtomicInteger();
        var innerCalls = new AtomicInteger();
        var retrying = new Interception.Interceptor() {
            @Override
            public <V> V proceed(InterceptionContext ctx, Chain<V> chain, Object... args) throws Exception {
                V result;
                try {
                    result = chain.proceed(args);
                } catch (IOException _) {
                    result = chain.proceed(args);
                }
                var duplicate = assertThrows(InterceptionException.class, () -> chain.proceed(args));
                assertThat(duplicate.targetWasCalled(), is(true));
                return result;
            }
        };
        try (var fixture = new Fixture(new InterceptorDescriptor(2, () -> retrying),
                                       new InterceptorDescriptor(1, () -> new ObservingInterceptor((_, _) -> {
                                           innerCalls.incrementAndGet();
                                       })))) {
            var invoker = fixture.metadata.createInvoker(new Object(), SERVICE, Set.of(), List.of(TRIGGER), METHOD,
                                                          _ -> {
                                                              if (attempts.incrementAndGet() == 1) {
                                                                  throw new IOException("retry");
                                                              }
                                                              return "success";
                                                          }, Set.of(IOException.class));
            assertThat(invoker.invoke(), is("success"));
            assertThat(attempts.get(), is(2));
            assertThat(innerCalls.get(), is(2));
        }
    }

    private static class Fixture implements AutoCloseable {
        private final ServiceRegistryManager manager;
        private final InterceptionMetadata metadata;

        private Fixture(InterceptorDescriptor... descriptors) {
            var config = ServiceRegistryConfig.builder()
                    .discoverServices(false)
                    .discoverServicesFromServiceLoader(false);
            for (var descriptor : descriptors) {
                config.addServiceDescriptor(descriptor);
            }
            manager = ServiceRegistryManager.create(config.build());
            metadata = manager.registry().get(InterceptionMetadata.class);
        }

        @Override
        public void close() {
            manager.shutdown();
        }

        private <T> InterceptionInvoker<T> invoker(InterceptionInvoker<T> target) {
            return metadata.createInvoker(new Object(), SERVICE, Set.of(), List.of(TRIGGER), METHOD, target, Set.of());
        }
    }

    private static class TargetDescriptor implements ServiceDescriptor<Object> {
        @Override
        public TypeName serviceType() {
            return TypeName.create("test.InterceptedService");
        }

        @Override
        public TypeName descriptorType() {
            return TypeName.create(TargetDescriptor.class);
        }
    }

    private static class InterceptorDescriptor implements ServiceDescriptor<Interception.Interceptor> {
        private final int weight;
        private final TypeName scope;
        private final Supplier<Interception.Interceptor> factory;

        private InterceptorDescriptor(int weight, Supplier<Interception.Interceptor> factory) {
            this(weight, Service.Singleton.TYPE, factory);
        }

        private InterceptorDescriptor(int weight, TypeName scope, Supplier<Interception.Interceptor> factory) {
            this.weight = weight;
            this.scope = scope;
            this.factory = factory;
        }

        @Override
        public TypeName serviceType() {
            return TypeName.create("test.Interceptor" + weight);
        }

        @Override
        public TypeName descriptorType() {
            return TypeName.create("test.Interceptor" + weight + "Descriptor");
        }

        @Override
        public Set<ResolvedType> contracts() {
            return Set.of(ResolvedType.create(Interception.Interceptor.class));
        }

        @Override
        public Set<Qualifier> qualifiers() {
            return Set.of(Qualifier.createNamed(TRIGGER.typeName().fqName()));
        }

        @Override
        public TypeName scope() {
            return scope;
        }

        @Override
        public double weight() {
            return weight;
        }

        @Override
        public Object instantiate(DependencyContext ctx, InterceptionMetadata interceptionMetadata) {
            return factory.get();
        }
    }

    private static class ObservingInterceptor implements Interception.Interceptor {
        private final BiConsumer<InterceptionContext, Object[]> observer;

        private ObservingInterceptor(BiConsumer<InterceptionContext, Object[]> observer) {
            this.observer = observer;
        }

        @Override
        public <V> V proceed(InterceptionContext ctx, Chain<V> chain, Object... args) throws Exception {
            observer.accept(ctx, args);
            return chain.proceed(args);
        }
    }
}
