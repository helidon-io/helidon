/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.integrations.micrometer.cdi;

import java.util.stream.IntStream;

import javax.inject.Inject;

import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@HelidonTest
@AddBean(MeteredBean.class)
public class MeteredBeanTest {

    @Inject
    MeteredBean meteredBean;

    @Inject
    MeterRegistry registry;

    @Test
    public void testSimpleCounted() {
        int exp = 3;
        IntStream.range(0, exp).forEach(i -> meteredBean.count());
        assertThat("Value from simple counted meter", registry.counter(MeteredBean.COUNTED).count(), is((double) exp));
    }

    @Test
    public void testSinglyTimed() {
        int exp = 4;
        IntStream.range(0, exp).forEach(i -> meteredBean.timed());
        assertThat("Count from singly-timed meter", registry.timer(MeteredBean.TIMED_1).count(), is((long) exp));
    }

    @Test
    public void testDoublyTimed() {
        int exp = 5;
        IntStream.range(0, exp).forEach(i -> meteredBean.timedA());
        assertThat("Count from doubly-timed meter (A)", registry.timer(MeteredBean.TIMED_A).count(), is((long) exp));
        assertThat("Count from doubly-timed meter (B)", registry.timer(MeteredBean.TIMED_B).count(), is((long) exp));
    }

    @Test
    public void testFailable() {
        int OKCalls = 2;
        int expFailed = 3;
        IntStream.range(0, OKCalls).forEach(i -> meteredBean.failable(false));
        IntStream.range(0, expFailed).forEach(i -> assertThrows(RuntimeException.class,
                () -> meteredBean.failable(true)));
        assertThat("Count from failed calls", registry.counter(MeteredBean.COUNTED_ONLY_FOR_FAILURE).count(),
                is((double) expFailed));
    }
}
