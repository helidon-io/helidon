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
import java.util.Map;
import java.util.Optional;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;
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
            + "\"displayName\":\"theDisplayName\"}}";

    private static MetricImpl impl;
    private static MetricID implID;
    private static MetricImpl implWithoutDescription;
    private static MetricID implWithoutDescriptionID;

    @BeforeAll
    public static void initClass() {
        Metadata meta = new HelidonMetadata("theName",
                                     "theDisplayName",
                                     "theDescription",
                                     MetricType.COUNTER,
                                     MetricUnits.NONE);

        impl = new MetricImpl("base", meta) {
            @Override
            public void prometheusData(StringBuilder sb, String name, Map<String,String> tags) {
                prometheusType(sb, name, "counter");
                prometheusHelp(sb, name);
                sb.append(name).append(" ").append("45");
            }

            @Override
            public void jsonData(JsonObjectBuilder builder, MetricID metricID) {
                builder.add(metricID.getName(), 45);
            }
        };
        implID = new MetricID(impl.prometheusName("theName"));

        meta = new HelidonMetadata("counterWithoutDescription", MetricType.COUNTER);

        implWithoutDescription = new MetricImpl("base", meta) {
            @Override
            public void prometheusData(StringBuilder sb, String name, Map<String,String> tags) {
                prometheusType(sb, name, "counter");
                prometheusHelp(sb, name);
                sb.append(name).append(" ").append("45");
            }

            @Override
            public void jsonData(JsonObjectBuilder builder, MetricID metricID) {
                builder.add(metricID.getName(), 45);
            }
        };
        implWithoutDescriptionID = new MetricID("counterWithoutDescription");
    }

    @Test
    void testPrometheusName() {
        assertAll("Various name transformations based on the 3.2.1 section of the spec",
                  () -> assertThat(impl.prometheusName("theName"), is("base_theName")),
                  () -> assertThat(impl.prometheusName("a.b.c.d"), is("base_a_b_c_d")),
                  () -> assertThat(impl.prometheusName("a b c d"), is("base_a_b_c_d")),
                  () -> assertThat(impl.prometheusName("a-b-c-d"), is("base_a_b_c_d")),
                  () -> assertThat(impl.prometheusName("a2.b.cC.d"), is("base_a2_b_cC_d")),
                  () -> assertThat(impl.prometheusName("a:b.c.d"), is("base_a_b_c_d")),
                  () -> assertThat(impl.prometheusName("a .b..c_.d"), is("base_a_b_c_d")),
                  () -> assertThat(impl.prometheusName("_aB..c_.d"), is("base_aB_c_d")));
    }

    @Test
    void testUtilMethods() {
        assertThat(impl.camelToSnake("ahojJakSeMate"), is("ahoj_jak_se_mate"));
        assertThat(impl.prometheusNameWithUnits("the_name", Optional.empty()), is("base_the_name"));
        assertThat(impl.prometheusNameWithUnits("the_name", Optional.of("seconds")), is("base_the_name_seconds"));
    }

    @Test
    void testPrometheus() {
        String expected = "# TYPE base_theName counter\n"
                + "# HELP base_theName theDescription\n"
                + "base_theName 45";
        final StringBuilder sb = new StringBuilder();
        impl.prometheusData(sb, implID.getName(), implID.getTags());
        assertThat(sb.toString(), is(expected));
    }

    @Test
    void testPrometheusWithoutDescription() {
        String expected = "# TYPE base_counterWithoutDescription counter\n"
                + "# HELP base_counterWithoutDescription \n"
                + "base_counterWithoutDescription 45";
        final StringBuilder sb = new StringBuilder();
        implWithoutDescription.prometheusData(sb, "base_" + implWithoutDescriptionID.getName(), implWithoutDescriptionID.getTags());
        assertThat(sb.toString(), is(expected));
    }

    @Test
    void testJsonData() {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add("theName", 45);
        JsonObject expected = builder.build();

        builder = Json.createObjectBuilder();
        impl.jsonData(builder, new MetricID("theName"));
        assertThat(builder.build(), is(expected));
    }

    @Test
    void testJsonMeta() {
        JsonObject expected = Json.createReader(new StringReader(JSON_META)).readObject();

        JsonObjectBuilder builder = Json.createObjectBuilder();
        impl.jsonMeta(builder);
        assertThat(builder.build(), is(expected));
    }

    @Test
    void testJsonEscaping() {
        assertThat(MetricImpl.jsonEscape("plain"), is("plain"));
        assertThat(MetricImpl.jsonEscape("not\bplain\tby\"a\nlong\\shot"),
                is("not\\bplain\\tby\\\"a\\nlong\\\\shot"));
    }
}
