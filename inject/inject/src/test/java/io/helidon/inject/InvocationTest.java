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

package io.helidon.inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.Interception.Interceptor;
import io.helidon.inject.service.InvocationContext;
import io.helidon.inject.service.Invoker;
import io.helidon.inject.service.ServiceInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InvocationTest {
    TestInterceptor first;
    TestInterceptor second;
    InvocationContext dummyCtx;
    ArrayList<Object[]> calls = new ArrayList<>();
    List<ConcreteProvider<Interceptor>> interceptors;

    @BeforeEach
    void reset() {
        first = new TestInterceptor("first");
        second = new TestInterceptor("second");
        interceptors = List.of(first.provider, second.provider);
        dummyCtx = InvocationContext.builder()
                .serviceInfo(new DummyServiceInfo())
                .elementInfo(TypedElementInfo.builder()
                                     .elementName("test")
                                     .kind(ElementKind.METHOD)
                                     .typeName(TypeName.create(InvocationTest.class)))
                .build();
        calls.clear();
    }

    @Test
    void normalCaseWithInterceptors() throws Exception {
        Object[] args = new Object[] {};
        Boolean result = Invocation.createInvokeAndSupply(dummyCtx,
                                                          interceptors,
                                                          (arguments) -> calls.add(arguments),
                                                          args,
                                                          Set.of());
        assertThat(result, is(true));
        assertThat(first.callCount.get(), equalTo(1));
        assertThat(first.proceedCount.get(), equalTo(1));
        assertThat(first.downstreamExceptionCount.get(), equalTo(0));
        assertThat(second.callCount.get(), equalTo(1));
        assertThat(second.proceedCount.get(), equalTo(1));
        assertThat(second.downstreamExceptionCount.get(), equalTo(0));
        assertThat(calls.size(), equalTo(1));
    }

    @Test
    void normalCaseWithNoInterceptors() throws Exception {
        InvocationContext dummyCtx = InvocationContext.builder()
                .serviceInfo(new DummyServiceInfo())
                .elementInfo(TypedElementInfo.builder()
                                     .elementName("test")
                                     .kind(ElementKind.METHOD)
                                     .typeName(TypeName.create(InvocationTest.class))
                                     .build())
                .build();

        Object[] args = new Object[] {};
        Boolean result = Invocation.createInvokeAndSupply(dummyCtx,
                                                          List.of(),
                                                          (arguments) -> calls.add(arguments),
                                                          args,
                                                          Set.of());
        assertThat(result, is(true));
        assertThat(first.callCount.get(), equalTo(0));
        assertThat(first.proceedCount.get(), equalTo(0));
        assertThat(first.downstreamExceptionCount.get(), equalTo(0));
        assertThat(second.callCount.get(), equalTo(0));
        assertThat(second.proceedCount.get(), equalTo(0));
        assertThat(second.downstreamExceptionCount.get(), equalTo(0));
        assertThat(calls.size(), equalTo(1));

        calls.clear();
        RuntimeException re = new RuntimeException("forced");
        Invoker<Object> fnc = (arguments) -> {
            throw re;
        };
        InvocationException e = assertThrows(InvocationException.class,
                                             () -> Invocation.createInvokeAndSupply(dummyCtx, List.of(), fnc, args, Set.of()));
        assertThat(e.getMessage(), equalTo("Error in interceptor chain processing"));
        assertThat(e.targetWasCalled(), is(true));
        assertThat(e.getCause(), is(re));
        assertThat(calls.size(), equalTo(0));
    }

    @Test
    void illegalCallToInterceptorProceedTwice() {
        first.control.timesToCallProceed(2);
        Object[] args = new Object[] {};
        Invoker<Boolean> fnc = (arguments) -> calls.add(arguments);
        InvocationException e = assertThrows(InvocationException.class,
                                             () -> Invocation.createInvokeAndSupply(dummyCtx, interceptors, fnc, args, Set.of()));
        assertThat(e.getMessage(),
                   equalTo("Duplicate invocation, or unknown call type: io.helidon.inject.InvocationTest test"));
        assertThat(e.targetWasCalled(), is(true));
        assertThat(first.callCount.get(), equalTo(1));
        assertThat(first.proceedCount.get(), equalTo(2));
        assertThat(first.downstreamExceptionCount.get(), equalTo(1));
        assertThat(second.callCount.get(), equalTo(1));
        assertThat(second.proceedCount.get(), equalTo(1));
        assertThat(second.downstreamExceptionCount.get(), equalTo(0));
        assertThat(calls.size(), equalTo(1));
    }

    @Test
    void illegalCallToTargetProceedTwice() {
        second.control.timesToCallProceed(2);
        Object[] args = new Object[] {};
        Invoker<Boolean> fnc = arguments -> calls.add(arguments);
        InvocationException e = assertThrows(InvocationException.class,
                                             () -> Invocation.createInvokeAndSupply(dummyCtx, interceptors, fnc, args, Set.of()));
        assertThat(e.getMessage(),
                   equalTo("Duplicate invocation, or unknown call type: io.helidon.inject.InvocationTest test"));
        assertThat(e.targetWasCalled(), is(true));
        assertThat(first.callCount.get(), equalTo(1));
        assertThat(first.proceedCount.get(), equalTo(1));
        assertThat(first.downstreamExceptionCount.get(), equalTo(1));
        assertThat(second.callCount.get(), equalTo(1));
        assertThat(second.proceedCount.get(), equalTo(2));
        assertThat(second.downstreamExceptionCount.get(), equalTo(1));
        assertThat(calls.size(), equalTo(1));
    }

    @Test
    void illegalCallToProceedAfterSuccessfulCallToTargetButExceptionInInterceptor() {
        first.control.timesToCallProceed(2).timesToCatchException(1);
        second.control.exceptionAfterProceed(new RuntimeException("after"));
        Object[] args = new Object[] {};
        Invoker<Boolean> fnc = (arguments) -> calls.add(arguments);
        InvocationException e = assertThrows(InvocationException.class,
                                             () -> Invocation.createInvokeAndSupply(dummyCtx, interceptors, fnc, args, Set.of()));
        assertThat(e.targetWasCalled(), is(true));
        assertThat(first.callCount.get(), equalTo(1));
        assertThat(first.proceedCount.get(), equalTo(2));
        assertThat(first.downstreamExceptionCount.get(), equalTo(2));
        assertThat(second.callCount.get(), equalTo(1));
        assertThat(second.proceedCount.get(), equalTo(1));
        assertThat(second.downstreamExceptionCount.get(), equalTo(0));
        assertThat(calls.size(), equalTo(1));
    }

    @Test
    void exceptionThrownInInterceptorPriorToReachingTarget() throws Exception {
        // catch exception once, call proceed once - the result should be success
        first.control.timesToCatchException(1).timesToCallProceed(1);
        second.control.exceptionBeforeProceed(new RuntimeException("before"));
        Object[] args = new Object[] {};
        Invoker<Boolean> fnc = (arguments) -> calls.add(arguments);
        Boolean result = Invocation.createInvokeAndSupply(dummyCtx, interceptors, fnc, args, Set.of());
        assertThat(result, nullValue());
        assertThat(first.callCount.get(), equalTo(1));
        assertThat(first.proceedCount.get(), equalTo(1));
        assertThat(first.downstreamExceptionCount.get(), equalTo(1));
        assertThat(second.callCount.get(), equalTo(1));
        assertThat(second.proceedCount.get(), equalTo(0));
        assertThat(second.downstreamExceptionCount.get(), equalTo(0));
        assertThat(calls.size(), equalTo(0));
    }

    @Test
    void exceptionThrownInInterceptorAfterReachingTarget() {
        first.control.timesToCatchException(1).timesToCallProceed(2);
        second.control.exceptionAfterProceed(new RuntimeException("after"));
        Object[] args = new Object[] {};
        Invoker<Boolean> fnc = (arguments) -> calls.add(arguments);
        InvocationException e = assertThrows(InvocationException.class,
                                             () -> Invocation.createInvokeAndSupply(dummyCtx, interceptors, fnc, args, Set.of()));
        assertThat(e.getMessage(),
                   equalTo("Duplicate invocation, or unknown call type: io.helidon.inject.InvocationTest test"));
        assertThat(e.targetWasCalled(), is(true));
        assertThat(first.callCount.get(), equalTo(1));
        assertThat(first.proceedCount.get(), equalTo(2));
        assertThat(first.downstreamExceptionCount.get(), equalTo(2));
        assertThat(second.callCount.get(), equalTo(1));
        assertThat(second.proceedCount.get(), equalTo(1));
        assertThat(second.downstreamExceptionCount.get(), equalTo(0));
        assertThat(calls.size(), equalTo(1));
    }

    @Test
    void exceptionThrownMultipleTimesInSecond() throws Exception {
        first.control.timesToCatchException(3).timesToCallProceed(3);
        second.control.exceptionBeforeProceed(new RuntimeException("before"));
        second.control.exceptionAfterProceed(new RuntimeException("after"));
        Object[] args = new Object[] {};
        Invoker<Boolean> fnc = (arguments) -> calls.add(arguments);
        Boolean result = Invocation.createInvokeAndSupply(dummyCtx, interceptors, fnc, args, Set.of());
        assertThat("because exception happened after we called proceed in second the value is lost", result, nullValue());
        assertThat(first.callCount.get(), equalTo(1));
        assertThat(first.proceedCount.get(), equalTo(3));
        assertThat(first.downstreamExceptionCount.get(), equalTo(3));
        assertThat(second.callCount.get(), equalTo(3));
        assertThat(second.proceedCount.get(), equalTo(0));
        assertThat(second.downstreamExceptionCount.get(), equalTo(0));
        assertThat(calls.size(), equalTo(0));
    }

    @Test
    void shortCircuitInFirst() throws Exception {
        first.control.shortCircuitValue(false);
        Object[] args = new Object[] {};
        Invoker<Boolean> fnc = (arguments) -> calls.add(arguments);
        Boolean result = Invocation.createInvokeAndSupply(dummyCtx, interceptors, fnc, args, Set.of());
        assertThat(result, is(false));
        assertThat(first.callCount.get(), equalTo(1));
        assertThat(first.proceedCount.get(), equalTo(0));
        assertThat(first.downstreamExceptionCount.get(), equalTo(0));
        assertThat(second.callCount.get(), equalTo(0));
        assertThat(second.proceedCount.get(), equalTo(0));
        assertThat(second.downstreamExceptionCount.get(), equalTo(0));
        assertThat(calls.size(), equalTo(0));
    }

    @Test
    void shortCircuitInSecond() throws Exception {
        second.control.shortCircuitValue(false);
        Object[] args = new Object[] {};
        Invoker<Boolean> fnc = (arguments) -> calls.add(arguments);
        Boolean result = Invocation.createInvokeAndSupply(dummyCtx, interceptors, fnc, args, Set.of());
        assertThat(result, is(false));
        assertThat(first.callCount.get(), equalTo(1));
        assertThat(first.proceedCount.get(), equalTo(1));
        assertThat(first.downstreamExceptionCount.get(), equalTo(0));
        assertThat(second.callCount.get(), equalTo(1));
        assertThat(second.proceedCount.get(), equalTo(0));
        assertThat(second.downstreamExceptionCount.get(), equalTo(0));
        assertThat(calls.size(), equalTo(0));
    }

    @Test
    void firstDoingAllOfTheProceedCalls() throws Exception {
        first.control.timesToCallProceed(2);
        second.control.timesToCallProceed(0);
        Object[] args = new Object[] {};
        Invoker<Boolean> fnc = (arguments) -> calls.add(arguments);
        Boolean result = Invocation.createInvokeAndSupply(dummyCtx, interceptors, fnc, args, Set.of());
        assertThat(result, is(true));
        assertThat(first.callCount.get(), equalTo(1));
        assertThat(first.proceedCount.get(), equalTo(2));
        assertThat(first.downstreamExceptionCount.get(), equalTo(0));
        assertThat(second.callCount.get(), equalTo(1));
        assertThat(second.proceedCount.get(), equalTo(0));
        assertThat(second.downstreamExceptionCount.get(), equalTo(0));
        assertThat(calls.size(), equalTo(1));
    }

    static class ConcreteProvider<T> implements Supplier<T> {
        private final T delegate;

        ConcreteProvider(T delegate) {
            this.delegate = delegate;
        }

        @Override
        public T get() {
            return delegate;
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }

    static class TestInterceptor {
        final String name;
        AtomicInteger callCount = new AtomicInteger();
        AtomicInteger proceedCount = new AtomicInteger();
        AtomicInteger downstreamExceptionCount = new AtomicInteger();
        Control.Builder control = Control.builder();
        ConcreteProvider<Interceptor> provider = new ConcreteProvider<>(new Interceptor() {
            @SuppressWarnings("unchecked")
            @Override
            public <V> V proceed(InvocationContext ctx, Chain<V> chain, Object... args) {
                callCount.incrementAndGet();

                RuntimeException re = control.exceptionBeforeProceed().orElse(null);
                if (re != null) {
                    control.exceptionBeforeProceed(Optional.empty());
                    throw re;
                }

                if (control.shortCircuitValue().isPresent()) {
                    return (V) control.shortCircuitValue().get();
                }

                V v = null;
                int countDown = control.timesToCallProceed();
                while (countDown-- > 0) {
                    proceedCount.incrementAndGet();

                    try {
                        try {
                            v = chain.proceed(args);
                        } catch (RuntimeException e) {
                            throw e;
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    } catch (RuntimeException e) {
                        downstreamExceptionCount.incrementAndGet();
                        if (control.timesToCatchException() <= 0) {
                            throw e;
                        }
                        control.timesToCatchException(control.timesToCatchException() - 1);
                    }

                    re = control.exceptionAfterProceed().orElse(null);
                    if (re != null) {
                        control.exceptionAfterProceed(Optional.empty());
                        throw re;
                    }
                }

                return v;
            }
        });

        TestInterceptor(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static class DummyServiceInfo implements ServiceInfo {
        @Override
        public TypeName serviceType() {
            return TypeName.create(DummyServiceInfo.class);
        }

        @Override
        public Set<TypeName> scopes() {
            return Set.of(Injection.Singleton.TYPE_NAME);
        }
    }
}
