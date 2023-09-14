/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
package io.helidon.examples.metrics.filtering.mp;

import java.time.Duration;

import io.helidon.microprofile.tests.junit5.HelidonTest;

import jakarta.inject.Inject;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Timer;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@HelidonTest
public class MainTest {

    @Inject
    private MetricRegistry appRegistry;

    @Test
    void checkEnabledMetric() {
        Counter personalizedGreetingsCounter = appRegistry.counter(GreetResource.COUNTER_FOR_PERSONALIZED_GREETINGS);
        long before = personalizedGreetingsCounter.getCount();
        personalizedGreetingsCounter.inc();
        assertThat("Enabled counter value change",
                   personalizedGreetingsCounter.getCount() - before, is(1L));
    }

    @Test
    void checkDisabledMetric() {
        Timer getsTimer = appRegistry.timer(GreetResource.TIMER_FOR_GETS);
        long before = getsTimer.getCount();
        getsTimer.update(Duration.ofSeconds(1));
        assertThat("Disabled timer value change",
                   getsTimer.getCount() - before,
                   is(0L));
    }
}
