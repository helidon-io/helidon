/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.integrations.micrometer.cdi;

import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.json.JsonObject;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Class HelloWorldTest.
 */
@HelidonTest
@AddBean(HelloWorldResource.class)
public class HelloWorldTest {

    @Inject
    WebTarget webTarget;

    @Inject
    MeterRegistry registry;

    @Test
    public void testMetrics() {
        final int iterations = 2;
        IntStream.range(0, iterations).forEach(
                i -> webTarget
                        .path("helloworld")
                        .request()
                        .accept(MediaType.TEXT_PLAIN_TYPE)
                        .get(String.class));
        assertThat("Value of explicitly-updated counter", registry.counter(HelloWorldResource.MESSAGE_COUNTER).count(),
                is((double) iterations));

        checkMicrometerURL(iterations);
    }

    @Test
    void testTimer() {
        int exp = 4;
        IntStream.range(0, exp).forEach(
                i -> webTarget
                        .path("helloworld/withArg/Joe")
                        .request(MediaType.TEXT_PLAIN_TYPE)
                        .get(String.class));

        Timer timer = registry.timer(HelloWorldResource.MESSAGE_TIMER);
        assertThat("Timer ", timer, is(notNullValue()));
        assertThat("Timer count", timer.count(), is((long) exp));

        Timer timer2 = registry.timer(HelloWorldResource.MESSAGE_TIMER_2);
        assertThat("Timer 2", timer2, is(notNullValue()));
        assertThat("Timer 2 count", timer2.count(),
                is((long) exp));
    }

    @Test
    public void testSlowTimer() {
        int exp = 3;
        AtomicReference<String> result = new AtomicReference<>();
        IntStream.range(0, exp).forEach(
                i -> result.set(webTarget
                                    .path("helloworld/slow")
                                    .request()
                                    .accept(MediaType.TEXT_PLAIN_TYPE)
                                    .get(String.class)));
        assertThat("Returned from HTTP request", result.get(), is(HelloWorldResource.SLOW_RESPONSE));
        Timer slowTimer = registry.timer(HelloWorldResource.SLOW_MESSAGE_TIMER);
        assertThat("Slow message timer", slowTimer, is(notNullValue()));
        assertThat("Slow message timer count", slowTimer.count(), is((long) exp));
    }

    @Test
    public void testFastFailCounter() {
        int exp = 8;
        IntStream.range(0, exp).forEach(
                i -> {
                    try {
                        webTarget
                                .path("helloworld/fail/" + (i % 2 == 0 ? "false" : "true"))
                                .request()
                                .accept(MediaType.TEXT_PLAIN_TYPE)
                                .get(String.class);
                    } catch (InternalServerErrorException ex) {
                        // expected half the time
                    }
                });

        Counter counter = registry.counter(HelloWorldResource.FAST_MESSAGE_COUNTER);
        assertThat("Failed message counter", counter, is(notNullValue()));
        assertThat("Failed message counter count", counter.count(), is((double) exp / 2));
    }

    @Test
    public void testSlowFailNoCounter() {
        try {
            webTarget
                    .path("helloworld/slowFailNoCounter")
                    .request()
                    .accept(MediaType.TEXT_PLAIN_TYPE)
                    .get(String.class);
        } catch (InternalServerErrorException ex) {
            // expected
        }
        System.out.println("Finished slowFailNoCounter test");
    }

    @Test
    public void testSlowFailCounter() {
        int exp = 6;
        IntStream.range(0, exp).forEach(
                i -> {
                    try {
                        webTarget
                                .path("helloworld/slowWithFail/" + (i % 2 == 0 ? "false" : "true"))
                                .request()
                                .accept(MediaType.TEXT_PLAIN_TYPE)
                                .get(String.class);
                    } catch (InternalServerErrorException ex) {
                        // expected half the time
                    }
                });

        Counter counter = registry.counter(HelloWorldResource.SLOW_MESSAGE_FAIL_COUNTER);
        assertThat("Failed message counter", counter, is(notNullValue()));
        assertThat("Failed message counter count", counter.count(), is((double) 3));
    }

    void checkMicrometerURL(int iterations) {
        String result = webTarget
                .path("micrometer")
                .request()
                .accept(MediaType.TEXT_PLAIN_TYPE)
                .get(String.class);
        assertThat(HelloWorldResource.MESSAGE_COUNTER + " present in /micrometer output",
                result.contains(HelloWorldResource.MESSAGE_COUNTER), is(true));
    }
}
