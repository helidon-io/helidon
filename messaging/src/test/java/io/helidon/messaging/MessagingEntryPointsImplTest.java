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

package io.helidon.messaging;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.registry.Interception;
import io.helidon.service.registry.InterceptionContext;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.service.registry.ServiceInstance;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessagingEntryPointsImplTest {
    private static final ServiceDescriptor<Object> DESCRIPTOR = new ServiceDescriptor<>() {
        @Override
        public TypeName serviceType() {
            return TypeName.create(MessagingEntryPointsImplTest.class);
        }

        @Override
        public TypeName descriptorType() {
            return TypeName.create("test.MessagingEntryPointsImplTest__ServiceDescriptor");
        }
    };
    private static final TypedElementInfo METHOD = TypedElementInfo.builder()
            .kind(ElementKind.METHOD)
            .elementName("consume")
            .typeName(TypeNames.PRIMITIVE_VOID)
            .build();

    @Test
    void invokesGenericEntryPointInterceptorWithOriginalMetadata() throws Exception {
        RecordingInterceptor interceptor = new RecordingInterceptor();
        MessagingEntryPointsImpl entryPoints = new MessagingEntryPointsImpl(List.of(serviceInstance(interceptor)));
        Object service = new Object();
        Message<String> message = Message.create("test");

        MessagingEntryPoint.Handler<Object> handler = entryPoints.handler(
                DESCRIPTOR,
                Set.of(),
                List.of(Annotation.create(Deprecated.class)),
                METHOD,
                (ignored, incoming) -> Optional.of(incoming));
        Optional<Message<?>> result = handler.handle(service, message);

        assertThat(result.orElseThrow(), sameInstance(message));
        assertThat(interceptor.invocations(), is(1));
        assertThat(interceptor.context().serviceInfo(), sameInstance(DESCRIPTOR));
        assertThat(interceptor.context().elementInfo(), sameInstance(METHOD));
        assertThat(interceptor.context().serviceInstance().orElseThrow(), sameInstance(service));
        assertThat(interceptor.arguments()[0], sameInstance(message));

        RuntimeException expected = new RuntimeException("handler failed");
        MessagingEntryPoint.Handler<Object> failing = entryPoints.handler(
                DESCRIPTOR,
                Set.of(),
                List.of(),
                METHOD,
                (ignoredService, ignoredMessage) -> {
                    throw expected;
                });
        RuntimeException actual = assertThrows(RuntimeException.class, () -> failing.handle(service, message));
        assertThat(actual, sameInstance(expected));
    }

    @Test
    void wrapsCheckedInterceptorFailure() {
        Exception expected = new Exception("checked interceptor failure");
        Interception.EntryPointInterceptor interceptor = new Interception.EntryPointInterceptor() {
            @Override
            public <T> T proceed(InterceptionContext context,
                                 Interception.Interceptor.Chain<T> chain,
                                 Object... arguments) throws Exception {
                throw expected;
            }
        };
        MessagingEntryPointsImpl entryPoints = new MessagingEntryPointsImpl(List.of(serviceInstance(interceptor)));
        MessagingEntryPoint.Handler<Object> handler = entryPoints.handler(
                DESCRIPTOR,
                Set.of(),
                List.of(),
                METHOD,
                (ignoredService, message) -> Optional.of(message));

        MessagingException actual = assertThrows(MessagingException.class,
                                                  () -> handler.handle(new Object(), Message.create("test")));
        assertThat(actual.getCause(), sameInstance(expected));
    }

    @Test
    void interceptsACompleteImmutableBatchExactlyOnce() throws Exception {
        RecordingInterceptor interceptor = new RecordingInterceptor();
        MessagingEntryPointsImpl entryPoints = new MessagingEntryPointsImpl(List.of(serviceInstance(interceptor)));
        Object service = new Object();
        AtomicInteger handlerInvocations = new AtomicInteger();
        AtomicReference<Object> receivedService = new AtomicReference<>();
        AtomicReference<MessageBatch<?>> received = new AtomicReference<>();
        MessagingEntryPoint.BatchHandler<Object> handler = entryPoints.batchHandler(
                DESCRIPTOR,
                Set.of(),
                List.of(),
                METHOD,
                (serviceInstance, batch) -> {
                    handlerInvocations.incrementAndGet();
                    receivedService.set(serviceInstance);
                    received.set(batch);
                });
        MessageBatch<?> batch = MessageBatch.create(List.of(Message.create("one"), Message.create("two")));

        handler.handle(service, batch);

        assertThat(interceptor.invocations(), is(1));
        assertThat(interceptor.context().serviceInstance().orElseThrow(), sameInstance(service));
        assertThat(receivedService.get(), sameInstance(service));
        assertThat(interceptor.arguments().length, is(1));
        assertThat(interceptor.arguments()[0], sameInstance(batch));
        assertThat(received.get(), sameInstance(batch));
        assertThat(received.get(), is(interceptor.arguments()[0]));
        assertThat(received.get().size(), is(2));
        assertThat(handlerInvocations.get(), is(1));
        assertThrows(UnsupportedOperationException.class, () -> received.get().messages().clear());
    }

    @Test
    void restoresInterruptWhenBatchInterceptorIsInterrupted() {
        InterruptedException expected = new InterruptedException("interceptor interrupted");
        Interception.EntryPointInterceptor interceptor = new Interception.EntryPointInterceptor() {
            @Override
            public <T> T proceed(InterceptionContext context,
                                 Interception.Interceptor.Chain<T> chain,
                                 Object... arguments) throws Exception {
                throw expected;
            }
        };
        MessagingEntryPointsImpl entryPoints = new MessagingEntryPointsImpl(List.of(serviceInstance(interceptor)));
        MessagingEntryPoint.BatchHandler<Object> handler = entryPoints.batchHandler(
                DESCRIPTOR,
                Set.of(),
                List.of(),
                METHOD,
                (ignoredService, ignoredBatch) -> { });

        try {
            MessagingException actual = assertThrows(
                    MessagingException.class,
                    () -> handler.handle(new Object(), MessageBatch.create(Message.create("test"))));
            assertThat(actual.getCause(), sameInstance(expected));
            assertThat(Thread.currentThread().isInterrupted(), is(true));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void preservesBatchReplacedByInterceptor() throws Exception {
        Object service = new Object();
        AtomicReference<MessageBatch<?>> replacement = new AtomicReference<>();
        Interception.EntryPointInterceptor interceptor = new Interception.EntryPointInterceptor() {
            @Override
            public <T> T proceed(InterceptionContext context,
                                 Interception.Interceptor.Chain<T> chain,
                                 Object... arguments) throws Exception {
                MessageBatch<?> batch = MessageBatch.create(List.of(Message.create("replacement")));
                replacement.set(batch);
                arguments[0] = batch;
                return chain.proceed(arguments);
            }
        };
        MessagingEntryPointsImpl entryPoints = new MessagingEntryPointsImpl(List.of(serviceInstance(interceptor)));
        AtomicReference<MessageBatch<?>> received = new AtomicReference<>();

        entryPoints.batchHandler(DESCRIPTOR,
                                 Set.of(),
                                 List.of(),
                                 METHOD,
                                 (ignoredService, batch) -> received.set(batch))
                .handle(service, MessageBatch.create(List.of(Message.create("original"))));

        assertThat(received.get(), sameInstance(replacement.get()));
        assertThrows(UnsupportedOperationException.class, () -> received.get().messages().clear());
    }

    @Test
    void reusesServiceInstanceWhenInterceptorRetriesTarget() throws Exception {
        Object service = new Object();
        Message<String> message = Message.create("retry");
        AtomicInteger attempts = new AtomicInteger();
        List<Object> invocationTargets = new ArrayList<>();
        AtomicReference<Object> interceptedService = new AtomicReference<>();
        Interception.EntryPointInterceptor interceptor = new Interception.EntryPointInterceptor() {
            @Override
            public <T> T proceed(InterceptionContext context,
                                 Interception.Interceptor.Chain<T> chain,
                                 Object... arguments) throws Exception {
                interceptedService.set(context.serviceInstance().orElseThrow());
                try {
                    return chain.proceed(arguments);
                } catch (IllegalStateException ignored) {
                    return chain.proceed(arguments);
                }
            }
        };
        MessagingEntryPointsImpl entryPoints = new MessagingEntryPointsImpl(List.of(serviceInstance(interceptor)));
        MessagingEntryPoint.Handler<Object> handler = entryPoints.handler(
                DESCRIPTOR,
                Set.of(),
                List.of(),
                METHOD,
                (serviceInstance, incoming) -> {
                    invocationTargets.add(serviceInstance);
                    if (attempts.getAndIncrement() == 0) {
                        throw new IllegalStateException("retry");
                    }
                    return Optional.of(incoming);
                });

        Optional<Message<?>> result = handler.handle(service, message);

        assertThat(result.orElseThrow(), sameInstance(message));
        assertThat(attempts.get(), is(2));
        assertThat(invocationTargets.size(), is(2));
        assertThat(interceptedService.get(), sameInstance(service));
        assertThat(invocationTargets.get(0), sameInstance(service));
        assertThat(invocationTargets.get(1), sameInstance(service));
    }

    private static ServiceInstance<Interception.EntryPointInterceptor> serviceInstance(
            Interception.EntryPointInterceptor interceptor) {
        return new ServiceInstance<>() {
            @Override
            public Interception.EntryPointInterceptor get() {
                return interceptor;
            }

            @Override
            public Set<Qualifier> qualifiers() {
                return Set.of();
            }

            @Override
            public Set<ResolvedType> contracts() {
                return Set.of();
            }

            @Override
            public TypeName scope() {
                return Service.Singleton.TYPE;
            }

            @Override
            public double weight() {
                return 0;
            }

            @Override
            public TypeName serviceType() {
                return TypeName.create(interceptor.getClass());
            }
        };
    }

    private static final class RecordingInterceptor implements Interception.EntryPointInterceptor {
        private final AtomicInteger invocations = new AtomicInteger();
        private volatile InterceptionContext context;
        private volatile Object[] arguments;

        @Override
        public <T> T proceed(InterceptionContext invocationContext,
                             Interception.Interceptor.Chain<T> chain,
                             Object... arguments) throws Exception {
            invocations.incrementAndGet();
            context = invocationContext;
            this.arguments = arguments;
            return chain.proceed(arguments);
        }

        private int invocations() {
            return invocations.get();
        }

        private InterceptionContext context() {
            return context;
        }

        private Object[] arguments() {
            return arguments;
        }
    }
}
