/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Map;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonBuilderFactory;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for {@link MetricsSupport}.
 */
class MetricsSupportTest {

    private static Registry base;
    private static Registry vendor;
    private static Registry app;

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());

    private static final MetricID METRIC_USED_HEAP = new MetricID("memory.usedHeap");

    private static final String CONCURRENT_GAUGE_NAME = "appConcurrentGauge";
    private static final int RED_CONCURRENT_GAUGE_COUNT = 1;
    private static final int BLUE_CONCURRENT_GAUGE_COUNT = 2;

    private static String globalTagsJsonSuffix;

    @BeforeAll
    static void initClass() {
        RegistryFactory rf = RegistryFactory.getInstance();
        base = rf.getARegistry(MetricRegistry.Type.BASE);
        vendor = rf.getARegistry(MetricRegistry.Type.VENDOR);
        app = rf.getARegistry(MetricRegistry.Type.APPLICATION);

        Counter counter = app.counter("appCounter",
                new Tag("color", "blue"), new Tag("brightness", "dim"));
        counter.inc();

        ConcurrentGauge concurrentGauge = app.concurrentGauge(CONCURRENT_GAUGE_NAME, new Tag("color", "blue"));
        for (int i = 0; i < BLUE_CONCURRENT_GAUGE_COUNT; i++) {
            concurrentGauge.inc();
        }

        concurrentGauge = app.concurrentGauge(CONCURRENT_GAUGE_NAME, new Tag("color", "red"));
        for (int i = 0; i < RED_CONCURRENT_GAUGE_COUNT; i++) {
            concurrentGauge.inc();
        }

        String globalTags = System.getenv("MP_METRICS_TAGS");
        if (globalTags == null) {
            globalTagsJsonSuffix = "";
        } else {
            globalTagsJsonSuffix = ";" + globalTags.replaceAll(",", ";");
        }
    }

    @Test
    void testPrometheusDataAll() {
        String data = MetricsSupport.toPrometheusData(app);
        System.out.println(data);
    }

    @Test
    void testPrometheusDataMultiple() {
        String data = MetricsSupport.toPrometheusData(app, base);
        System.out.println(data);
    }

    @Test
    void testJsonDataAll() {
        JsonObject jsonObject = MetricsSupport.toJsonData(app);
        System.out.println("jsonObject = " + jsonObject);
    }

    @Test
    void testJsonDataMultiple() {
        JsonObject jsonObject = MetricsSupport.toJsonData(app, base);
        System.out.println("jsonObject = " + jsonObject);
    }

    @Test
    void testJsonMetaAll() {
        JsonObject jsonObject = MetricsSupport.toJsonMeta(app);
        System.out.println("jsonObject = " + jsonObject);
    }

    @Test
    void testJsonMetaMultiple() {
        JsonObject jsonObject = MetricsSupport.toJsonMeta(app, base);
        System.out.println("jsonObject = " + jsonObject);
    }

    @Test
    void testJsonDataWithTags() {
        JsonObject jsonObject = MetricsSupport.toJsonData(app);
        // Check for presence of tags and correct ordering.
        assertTrue(jsonObject.containsKey("appCounter;brightness=dim;color=blue"));
    }

    @Test
    void testMergingJsonObjectBuilder() {
        JsonObjectBuilder builder = MetricsSupport.createMergingJsonObjectBuilder(JSON.createObjectBuilder());
        builder.add("commonObj", JSON.createObjectBuilder()
                        .add("intA", 4)
                        .add("longB", 6l))
                .add("commonArray", JSON.createArrayBuilder()
                        .add("integration")
                        .add(6))
                .add("otherStuff", "this really is other stuff")
                .add("commonArray", JSON.createArrayBuilder()
                        .add("demo")
                        .add(7))
                .add("commonObj", JSON.createObjectBuilder()
                        .add("doubleA", 8d)
                        .add("differentStuff", "this is even more different"));
        JsonObject jo = builder.build();

        JsonObject commonObj = jo.getJsonObject("commonObj");
        assertEquals(4, commonObj.getInt("intA"));
        assertEquals(8d, commonObj.getJsonNumber("doubleA").doubleValue());

        assertEquals("this really is other stuff", jo.getString("otherStuff"));

        JsonArray commonArray = jo.getJsonArray("commonArray");
        assertEquals("integration", commonArray.getJsonArray(0).getString(0));
        assertEquals(6, commonArray.getJsonArray(0).getInt(1));
        assertEquals("demo", commonArray.getJsonArray(1).getString(0));
        assertEquals(7, commonArray.getJsonArray(1).getInt(1));
    }

    @Test
    void testBaseMetricsDisabled() {
        Config config = Config.builder()
                .sources(ConfigSources.create(Map.of(
                        "base.enabled", "false")))
                .build();
        RegistryFactory myRF = RegistryFactory.create(config);
        Registry myBase = myRF.getARegistry(MetricRegistry.Type.BASE);
        assertFalse(myBase.getGauges().containsKey(METRIC_USED_HEAP), "Base registry incorrectly contains "
                + METRIC_USED_HEAP + " when base was configured as disabled");
    }

    @Test
    void testPrometheusDataNoTypeDups() throws Exception {
        Set<String> found = new HashSet<>();
        String data = MetricsSupport.toPrometheusData(app, base);
        try (BufferedReader reader = new BufferedReader(new StringReader(data))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] tokens = line.split(" ");
                if (tokens.length > 3 && tokens[1].equals("TYPE")) {
                    String metric = tokens[2];
                    assertFalse(found.contains(metric));
                    found.add(metric);
                }
            }
        }
    }

    @Test
    void testJsonDataMultipleMetricsSameName() {
        // Make sure the JSON format for all metrics matching a name lists the name once with tagged instances as children.
        JsonObject multiple = MetricsSupport.jsonDataByName(app, CONCURRENT_GAUGE_NAME);
        assertNotNull(multiple);
        JsonObject top = multiple.getJsonObject(CONCURRENT_GAUGE_NAME);
        assertNotNull(top);
        JsonNumber blueNumber = top.getJsonNumber("current;color=blue" + globalTagsJsonSuffix);
        assertNotNull(blueNumber);
        assertEquals(BLUE_CONCURRENT_GAUGE_COUNT, blueNumber.longValue());
        JsonNumber redNumber = top.getJsonNumber("current;color=red" + globalTagsJsonSuffix);
        assertNotNull(redNumber);
        assertEquals(RED_CONCURRENT_GAUGE_COUNT, redNumber.longValue());
    }
}
