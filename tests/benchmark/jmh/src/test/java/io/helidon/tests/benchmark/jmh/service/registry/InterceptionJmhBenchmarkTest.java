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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

class InterceptionJmhBenchmarkTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 4})
    void allWorkloadsInvokeEveryMatchingInterceptor(int interceptorCount) throws Exception {
        var state = new InterceptionJmhBenchmark.InvocationState();
        state.interceptorCount = interceptorCount;
        state.setUp();
        try {
            var benchmark = new InterceptionJmhBenchmark();
            int expected = 7 + interceptorCount;
            for (int i = 0; i < 3; i++) {
                assertThat("No-argument method result", benchmark.methodNoArgs(state), is(expected));
                assertThat("One-argument method result", benchmark.methodArgument(state), is(expected));
                var noArgs = (InterceptionJmhBenchmark.Payload) benchmark.constructorNoArgs(state);
                var argument = (InterceptionJmhBenchmark.Payload) benchmark.constructorArgument(state);
                assertThat("No-argument constructor result", noArgs.value, is(expected));
                assertThat("One-argument constructor result", argument.value, is(expected));
                assertThat("Constructors must create distinct objects", noArgs, not(sameInstance(argument)));
            }
        } finally {
            state.tearDown();
        }
    }
}
