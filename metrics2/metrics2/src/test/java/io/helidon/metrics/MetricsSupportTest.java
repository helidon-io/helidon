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

import java.util.Collections;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link MetricsSupport}.
 */
class MetricsSupportTest {

    private static Registry base;
    private static Registry vendor;
    private static Registry app;

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());


    @BeforeAll
    static void initClass() {
        RegistryFactory rf = RegistryFactory.getInstance();
        base = rf.getARegistry(MetricRegistry.Type.BASE);
        vendor = rf.getARegistry(MetricRegistry.Type.VENDOR);
        app = rf.getARegistry(MetricRegistry.Type.APPLICATION);

        Counter counter = app.counter("appCounter",
                new Tag("color", "blue"), new Tag("brightness", "dim"));
        counter.inc();
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
}
