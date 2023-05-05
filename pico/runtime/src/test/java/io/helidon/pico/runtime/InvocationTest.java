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

package io.helidon.pico.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import io.helidon.common.types.TypedElementNameDefault;
import io.helidon.pico.api.Interceptor;
import io.helidon.pico.api.InvocationContext;
import io.helidon.pico.api.InvocationContextDefault;
import io.helidon.pico.api.InvocationException;

import jakarta.inject.Provider;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("unchecked")
class InvocationTest {
    TestInterceptor first = new TestInterceptor("first");
    TestInterceptor second = new TestInterceptor("second");
    InvocationContext dummyCtx = InvocationContextDefault.builder()
            .elementInfo(TypedElementNameDefault.builder().elementName("test").typeName(InvocationTest.class).build())
            .interceptors(List.of(first.provider, second.provider));
    ArrayList<Object[]> calls = new ArrayList<>();

    @Test
    void normalCaseWithInterceptors() {
        Object[] args = new Object[] {};
        Boolean result = Invocation.createInvokeAndSupply(dummyCtx, (arguments) -> calls.add(arguments), args);
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
    void normalCaseWithNoInterceptors() {
        InvocationContext dummyCtx = InvocationContextDefault.builder()
                .elementInfo(TypedElementNameDefault.builder().elementName("test").typeName(InvocationTest.class).build())
                .interceptors(List.of());

        Object[] args = new Object[] {};
        Boolean result = Invocation.createInvokeAndSupply(dummyCtx, (arguments) -> calls.add(arguments), args);
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
        Function<Object[], Object> fnc = (arguments) -> { throw re; };
        InvocationException e = assertThrows(InvocationException.class,
                                             () -> Invocation.createInvokeAndSupply(dummyCtx, fnc, args));
        assertThat(e.getMessage(), equalTo("Error in interceptor chain processing"));
        assertThat(e.targetWasCalled(), is(true));
        assertThat(e.getCause(), is(re));
        assertThat(calls.size(), equalTo(0));
    }

    @Test
    void illegalCallToInterceptorProceedTwice() {
        first.control.timesToCallProceed(2);
        Object[] args = new Object[] {};
        Function<Object[], Boolean> fnc = (arguments) -> calls.add(arguments);
        InvocationException e = assertThrows(InvocationException.class,
                                             () -> Invocation.createInvokeAndSupply(dummyCtx, fnc, args));
        assertThat(e.getMessage(),
                   equalTo("Duplicate invocation, or unknown call type: io.helidon.pico.runtime.InvocationTest test"));
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
        Function<Object[], Boolean> fnc = (arguments) -> calls.add(arguments);
        InvocationException e = assertThrows(InvocationException.class,
                                             () -> Invocation.createInvokeAndSupply(dummyCtx, fnc, args));
        assertThat(e.getMessage(),
                   equalTo("Duplicate invocation, or unknown call type: io.helidon.pico.runtime.InvocationTest test"));
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
        Function<Object[], Boolean> fnc = (arguments) -> calls.add(arguments);
        InvocationException e = assertThrows(InvocationException.class,
                                          () -> Invocation.createInvokeAndSupply(dummyCtx, fnc, args));
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
    void exceptionThrownInInterceptorPriorToReachingTarget() {
        first.control.timesToCatchException(1).timesToCallProceed(2);
        second.control.exceptionBeforeProceed(new RuntimeException("before"));
        Object[] args = new Object[] {};
        Function<Object[], Boolean> fnc = (arguments) -> calls.add(arguments);
        Boolean result = Invocation.createInvokeAndSupply(dummyCtx, fnc, args);
        assertThat(result, is(true));
        assertThat(first.callCount.get(), equalTo(1));
        assertThat(first.proceedCount.get(), equalTo(2));
        assertThat(first.downstreamExceptionCount.get(), equalTo(1));
        assertThat(second.callCount.get(), equalTo(2));
        assertThat(second.proceedCount.get(), equalTo(1));
        assertThat(second.downstreamExceptionCount.get(), equalTo(0));
        assertThat(calls.size(), equalTo(1));
    }

    @Test
    void exceptionThrownInInterceptorAfterReachingTarget() {
        first.control.timesToCatchException(1).timesToCallProceed(2);
        second.control.exceptionAfterProceed(new RuntimeException("after"));
        Object[] args = new Object[] {};
        Function<Object[], Boolean> fnc = (arguments) -> calls.add(arguments);
        InvocationException e = assertThrows(InvocationException.class,
                                             () -> Invocation.createInvokeAndSupply(dummyCtx, fnc, args));
        assertThat(e.getMessage(),
                   equalTo("Duplicate invocation, or unknown call type: io.helidon.pico.runtime.InvocationTest test"));
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
    void exceptionThrownMultipleTimesInSecond() {
        first.control.timesToCatchException(3).timesToCallProceed(3);
        second.control.exceptionBeforeProceed(new RuntimeException("before"));
        second.control.exceptionAfterProceed(new RuntimeException("after"));
        Object[] args = new Object[] {};
        Function<Object[], Boolean> fnc = (arguments) -> calls.add(arguments);
        Boolean result = Invocation.createInvokeAndSupply(dummyCtx, fnc, args);
        assertThat("because exception happened after we called proceed in second the value is lost", result, nullValue());
        assertThat(first.callCount.get(), equalTo(1));
        assertThat(first.proceedCount.get(), equalTo(3));
        assertThat(first.downstreamExceptionCount.get(), equalTo(3));
        assertThat(second.callCount.get(), equalTo(2));
        assertThat(second.proceedCount.get(), equalTo(1));
        assertThat(second.downstreamExceptionCount.get(), equalTo(0));
        assertThat(calls.size(), equalTo(1));
    }

    @Test
    void shortCircuitInFirst() {
        first.control.shortCircuitValue(false);
        Object[] args = new Object[] {};
        Function<Object[], Boolean> fnc = (arguments) -> calls.add(arguments);
        Boolean result = Invocation.createInvokeAndSupply(dummyCtx, fnc, args);
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
    void shortCircuitInSecond() {
        second.control.shortCircuitValue(false);
        Object[] args = new Object[] {};
        Function<Object[], Boolean> fnc = (arguments) -> calls.add(arguments);
        Boolean result = Invocation.createInvokeAndSupply(dummyCtx, fnc, args);
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
    void firstDoingAllOfTheProceedCalls() {
        first.control.timesToCallProceed(2);
        second.control.timesToCallProceed(0);
        Object[] args = new Object[] {};
        Function<Object[], Boolean> fnc = (arguments) -> calls.add(arguments);
        Boolean result = Invocation.createInvokeAndSupply(dummyCtx, fnc, args);
        assertThat(result, is(true));
        assertThat(first.callCount.get(), equalTo(1));
        assertThat(first.proceedCount.get(), equalTo(2));
        assertThat(first.downstreamExceptionCount.get(), equalTo(0));
        assertThat(second.callCount.get(), equalTo(1));
        assertThat(second.proceedCount.get(), equalTo(0));
        assertThat(second.downstreamExceptionCount.get(), equalTo(0));
        assertThat(calls.size(), equalTo(1));
    }

    static class ConcreteProvider<T> implements Provider<T> {
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
        ControlDefault.Builder control = ControlDefault.builder();
        ConcreteProvider<Interceptor> provider = new ConcreteProvider<>(new Interceptor() {
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
                        v = chain.proceed(args);
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

}
