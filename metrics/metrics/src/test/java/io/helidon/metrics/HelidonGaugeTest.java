/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

import java.util.Collections;
import java.util.regex.Pattern;

import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;


public class HelidonGaugeTest {

    private static final int EXPECTED_VALUE = 1;
    private static final Pattern JSON_RESULT_PATTERN = Pattern.compile("\\{\"myValue[^\"]*\":1.0\\}");

    private static final JsonBuilderFactory FACTORY = Json.createBuilderFactory(Collections.emptyMap());

    private static Metadata meta;

    @BeforeAll
    static void initClass() {
        meta = Metadata.builder()
                .withName("aGauge")
                .withDisplayName("aGauge")
                .withDescription("aGauge")
                .withType(MetricType.GAUGE)
                .withUnit(MetricUnits.NONE)
                .build();
    }

    @Test
    void testJson() {
        MyValue myValue = new MyValue(EXPECTED_VALUE);
        Gauge<MyValue> myGauge = new Gauge<MyValue>() {

            @Override
            public MyValue getValue() {
                return myValue;
            }
        };

        HelidonGauge<?> gauge = HelidonGauge.create("base", meta, myGauge);
        JsonObjectBuilder builder = FACTORY.createObjectBuilder();
        gauge.jsonData(builder, new MetricID("myValue"));
        JsonObject json = builder.build();

        String s = json.toString();
        assertThat("JSON string " + s + " does not match pattern " + JSON_RESULT_PATTERN.toString(),
                JSON_RESULT_PATTERN.matcher(s).matches());
    }

    public static class MyValue extends Number {

        private final Double value;

        public MyValue(double value) {
            this.value = Double.valueOf(value);
        }

        @Override
        public int intValue() {
            return value.intValue();
        }

        @Override
        public long longValue() {
            return value.longValue();
        }

        @Override
        public float floatValue() {
            return value.floatValue();
        }

        @Override
        public double doubleValue() {
            return value.doubleValue();
        }
    }
}
