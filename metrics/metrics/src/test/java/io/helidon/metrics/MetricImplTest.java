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
import java.util.Optional;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * Unit test for {@link MetricImpl}.
 */
class MetricImplTest {
    private static final String JSON_META = "{"
            + "\"theName\":"
            + "{"
            + "\"unit\":\"none\","
            + "\"type\":\"counter\","
            + "\"description\":\"theDescription\","
            + "\"displayName\":\"theDisplayName\","
            + "\"tags\":\"a=b,c=d\"}}";

    private static MetricImpl impl;
    private static MetricImpl implWithoutDescription;

    @BeforeAll
    public static void initClass() {
        Metadata meta = new Metadata("theName",
                                     "theDisplayName",
                                     "theDescription",
                                     MetricType.COUNTER,
                                     MetricUnits.NONE,
                                     "a=b,c=d");

        impl = new MetricImpl("base", meta) {
            @Override
            protected void prometheusData(StringBuilder sb, String name, String tags) {
                prometheusType(sb, name, "counter");
                prometheusHelp(sb, name);
                sb.append(name).append(" ").append("45");
            }

            @Override
            public void jsonData(JsonObjectBuilder builder) {
                //TODO how with tags?
                builder.add(getName(), 45);
            }
        };

        meta = new Metadata("counterWithoutDescription", MetricType.COUNTER);

        implWithoutDescription = new MetricImpl("base", meta) {
            @Override
            protected void prometheusData(StringBuilder sb, String name, String tags) {
                prometheusType(sb, name, "counter");
                prometheusHelp(sb, name);
                sb.append(name).append(" ").append("45");
            }

            @Override
            public void jsonData(JsonObjectBuilder builder) {
                //TODO how with tags?
                builder.add(getName(), 45);
            }
        };
    }

    @Test
    void testPrometheusName() {
        assertAll("Various name transformations based on the 3.2.1 section of the spec",
                  () -> assertThat(impl.prometheusName("theName"), is("base_theName")),
                  () -> assertThat(impl.prometheusName("a.b.c.d"), is("base_a_b_c_d")),
                  () -> assertThat(impl.prometheusName("a b c d"), is("base_a_b_c_d")),
                  () -> assertThat(impl.prometheusName("a-b-c-d"), is("base_a_b_c_d")),
                  () -> assertThat(impl.prometheusName("a2.b.cC.d"), is("base_a2_b_cC_d")),
                  () -> assertThat(impl.prometheusName("a_b.c.d"), is("base_a_b_c_d")),
                  () -> assertThat(impl.prometheusName("a .b..c_.d"), is("base_a_b_c_d")),
                  () -> assertThat(impl.prometheusName("_aB..c_.d"), is("base_aB_c_d")));
    }

    @Test
    void testUtilMethods() {
        assertThat(impl.camelToSnake("ahojJakSeMate"), is("ahoj_jak_se_mate"));
        assertThat(impl.prometheusNameWithUnits("the_name", Optional.empty()), is("the_name"));
        assertThat(impl.prometheusNameWithUnits("the_name", Optional.of("seconds")), is("the_name_seconds"));
    }

    @Test
    void testPrometheus() {
        String expected = "# TYPE base_theName counter\n"
                + "# HELP base_theName theDescription\n"
                + "base_theName 45";
        assertThat(impl.prometheusData(), is(expected));
    }

    @Test
    void testPrometheusWithoutDescription() {
        String expected = "# TYPE base_counterWithoutDescription counter\n"
                + "# HELP base_counterWithoutDescription \n"
                + "base_counterWithoutDescription 45";
        assertThat(implWithoutDescription.prometheusData(), is(expected));
    }

    @Test
    void testJsonData() {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add("theName", 45);
        JsonObject expected = builder.build();

        builder = Json.createObjectBuilder();
        impl.jsonData(builder);
        assertThat(builder.build(), is(expected));
    }

    @Test
    void testJsonMeta() {
        JsonObject expected = Json.createReader(new StringReader(JSON_META)).readObject();

        JsonObjectBuilder builder = Json.createObjectBuilder();
        impl.jsonMeta(builder);
        assertThat(builder.build(), is(expected));
    }
}
