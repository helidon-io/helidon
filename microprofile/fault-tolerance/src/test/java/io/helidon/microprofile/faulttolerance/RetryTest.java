/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.faulttolerance;

import java.util.concurrent.Future;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Class RetryTest.
 */
public class RetryTest extends FaultToleranceTest {

    static Stream<Arguments> createBeans() {
        return Stream.of(
                Arguments.of(newBean(RetryBean.class), "ManagedRetryBean"),
                Arguments.of(newNamedBean(SyntheticRetryBean.class), "SyntheticRetryBean"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("createBeans")
    public void testRetryBean(RetryBean bean, String unused) {
        assertThat(bean.getInvocations(), is(0));
        bean.retry();
        assertThat(bean.getInvocations(), is(3));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("createBeans")
    public void testRetryBeanFallback(RetryBean bean, String unused) {
        assertThat(bean.getInvocations(), is(0));
        String value = bean.retryWithFallback();
        assertThat(bean.getInvocations(), is(2));
        assertThat(value, is("fallback"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("createBeans")
    public void testRetryAsync(RetryBean bean, String unused) throws Exception {
        Future<String> future = bean.retryAsync();
        future.get();
        assertThat(bean.getInvocations(), is(3));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("createBeans")
    public void testRetryWithDelayAndJitter(RetryBean bean, String unused) throws Exception {
        long millis = System.currentTimeMillis();
        String value = bean.retryWithDelayAndJitter();
        assertThat(System.currentTimeMillis() - millis, greaterThan(200L));
    }
}
