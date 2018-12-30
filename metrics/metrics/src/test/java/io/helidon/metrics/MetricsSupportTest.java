/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

import javax.json.JsonObject;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link MetricsSupport}.
 */
class MetricsSupportTest {

    private static Registry base;
    private static Registry vendor;
    private static Registry app;

    @BeforeAll
    static void initClass() {
        RegistryFactory rf = RegistryFactory.create();
        base = rf.getARegistry(MetricRegistry.Type.BASE);
        vendor = rf.getARegistry(MetricRegistry.Type.VENDOR);
        app = rf.getARegistry(MetricRegistry.Type.APPLICATION);

        Counter counter = app.counter("appCounter");
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
}
