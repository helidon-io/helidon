/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.metrics;

import java.io.StringReader;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * Unit test for {@link HelidonCounter}.
 */
class HelidonCounterTest {
    private static Metadata meta;
    private HelidonCounter counter;
    private HelidonCounter wrappingCounter;

    @BeforeAll
    static void initClass() {
        meta = new Metadata("theName",
                            "theDisplayName",
                            "theDescription",
                            MetricType.COUNTER,
                            MetricUnits.NONE,
                            "a=b,c=d");
    }

    @BeforeEach
    void resetCounter() {
        Counter wrapped = new Counter() {
            @Override
            public void inc() {

            }

            @Override
            public void inc(long n) {

            }

            @Override
            public void dec() {

            }

            @Override
            public void dec(long n) {

            }

            @Override
            public long getCount() {
                return 49;
            }
        };
        counter = HelidonCounter.create("base", meta);
        wrappingCounter = HelidonCounter.create("base", meta, wrapped);
    }

    @Test
    void testValue() {
        testValues(0);
    }

    @Test
    void testInc() {
        testValues(0);
        counter.inc();
        wrappingCounter.inc();
        testValues(1);
    }

    @Test
    void testIncWithParam() {
        testValues(0);
        counter.inc(49);
        wrappingCounter.inc();
        testValues(49);
    }

    @Test
    void testDec() {
        testValues(0);
        counter.inc();
        wrappingCounter.inc();
        testValues(1);
        counter.dec();
        wrappingCounter.dec();
        testValues(0);
    }

    @Test
    void testDecWithParam() {
        testValues(0);
        counter.inc(49);
        wrappingCounter.inc(49);
        testValues(49);
        counter.dec(7);
        wrappingCounter.dec(7);
        testValues(42);
    }

    @Test
    void testPrometheusData() {
        counter.inc(17);
        wrappingCounter.inc(17);

        String expected = "# TYPE base:the_name counter\n"
                + "# HELP base:the_name theDescription\n"
                + "base:the_name{a=\"b\",c=\"d\"} 17\n";

        assertThat(counter.prometheusData(), is(expected));

        expected = "# TYPE base:the_name counter\n"
                + "# HELP base:the_name theDescription\n"
                + "base:the_name{a=\"b\",c=\"d\"} 49\n";
        assertThat(wrappingCounter.prometheusData(), is(expected));
    }

    @Test
    void testJsonData() {
        counter.inc(47);
        wrappingCounter.inc(47);

        JsonObject expected = Json.createReader(new StringReader("{\"theName\": 47}")).readObject();
        JsonObjectBuilder builder = Json.createObjectBuilder();
        counter.jsonData(builder);
        assertThat(builder.build(), is(expected));

        expected = Json.createReader(new StringReader("{\"theName\": 49}")).readObject();
        builder = Json.createObjectBuilder();
        wrappingCounter.jsonData(builder);
        assertThat(builder.build(), is(expected));
    }

    private void testValues(long counterValue) {
        assertThat(counter.getCount(), is(counterValue));
        assertThat(wrappingCounter.getCount(), is(49L));
    }
}
